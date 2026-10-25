/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.googledrive.internal.handler;

import static org.openhab.binding.googledrive.internal.GoogleDriveBindingConstants.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.googledrive.internal.action.GoogleDriveAccountActions;
import org.openhab.binding.googledrive.internal.api.DeviceCodeLogin;
import org.openhab.binding.googledrive.internal.api.DeviceCodeLogin.PollResult;
import org.openhab.binding.googledrive.internal.api.DriveApi;
import org.openhab.binding.googledrive.internal.api.GoogleDriveAuthException;
import org.openhab.binding.googledrive.internal.api.GoogleDriveConfigurationException;
import org.openhab.binding.googledrive.internal.api.GoogleDriveException;
import org.openhab.binding.googledrive.internal.api.TokenProvider;
import org.openhab.binding.googledrive.internal.api.dto.DeviceCodeResponse;
import org.openhab.binding.googledrive.internal.config.GoogleDriveAccountConfiguration;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link GoogleDriveAccountHandler} is the bridge to a Google Drive account. It runs the OAuth2 device code
 * login, keeps the tokens in the openHAB OAuth store (which also refreshes them) and provides the Drive API to its
 * child things.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveAccountHandler extends BaseBridgeHandler implements TokenProvider {

    private static final int RECONNECT_SECONDS = 60;
    private static final int SLOW_DOWN_SECONDS = 5;
    private static final int MIN_POLL_SECONDS = 5;
    private static final String INVALID_GRANT = "invalid_grant";

    private final Logger logger = LoggerFactory.getLogger(GoogleDriveAccountHandler.class);

    private final OAuthFactory oAuthFactory;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private GoogleDriveAccountConfiguration config = new GoogleDriveAccountConfiguration();
    private @Nullable OAuthClientService oAuthService;
    private @Nullable DeviceCodeLogin login;
    private @Nullable DriveApi driveApi;

    private @Nullable ScheduledFuture<?> connectJob;
    private @Nullable ScheduledFuture<?> loginPollJob;
    private @Nullable ScheduledFuture<?> quotaJob;

    /** Device code of a running login, null when no login is in progress. */
    private @Nullable DeviceCodeResponse pendingCode;
    private Instant pendingCodeExpiry = Instant.MIN;
    private long pollSeconds = MIN_POLL_SECONDS;

    public GoogleDriveAccountHandler(Bridge bridge, OAuthFactory oAuthFactory, HttpClient httpClient) {
        super(bridge);
        this.oAuthFactory = oAuthFactory;
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        config = getConfigAs(GoogleDriveAccountConfiguration.class);

        if (config.clientId.isBlank() || config.clientSecret.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error.missing-client");
            return;
        }

        oAuthService = createOAuthService();
        login = new DeviceCodeLogin(httpClient, gson, config.clientId.trim(), config.clientSecret.trim());
        driveApi = new DriveApi(httpClient, gson, this);

        updateStatus(ThingStatus.UNKNOWN);
        scheduleConnect(0);
    }

    @Override
    public void dispose() {
        cancelJobs();
        pendingCode = null;
        oAuthService = null;
        login = null;
        driveApi = null;
        oAuthFactory.ungetOAuthService(getHandle());
        super.dispose();
    }

    @Override
    public void handleRemoval() {
        oAuthFactory.deleteServiceAndAccessToken(getHandle());
        super.handleRemoval();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType && getThing().getStatus() == ThingStatus.ONLINE) {
            String channel = channelUID.getId();
            if (CHANNEL_QUOTA_USED.equals(channel) || CHANNEL_QUOTA_TOTAL.equals(channel)) {
                scheduler.execute(this::refreshQuota);
            }
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(GoogleDriveAccountActions.class);
    }

    /**
     * Returns the Drive API of this account, or null if the bridge is not initialized.
     */
    public @Nullable DriveApi getDriveApi() {
        return driveApi;
    }

    @Override
    public String getAccessToken(boolean forceRefresh) throws GoogleDriveException {
        OAuthClientService service = oAuthService;
        if (service == null) {
            throw new GoogleDriveAuthException("Account is not initialized");
        }
        try {
            AccessTokenResponse token = forceRefresh ? service.refreshToken() : service.getAccessTokenResponse();
            String accessToken = token != null ? token.getAccessToken() : null;
            if (accessToken == null) {
                throw new GoogleDriveAuthException("Not logged in");
            }
            return accessToken;
        } catch (OAuthResponseException e) {
            if (INVALID_GRANT.equals(e.getError())) {
                authenticationLost();
                throw new GoogleDriveAuthException("Google rejected the login: " + e.getErrorDescription(), e);
            }
            throw new GoogleDriveException("Token refresh failed: " + e.getError(), e);
        } catch (OAuthException | IOException e) {
            throw new GoogleDriveException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    /**
     * Removes the stored login and starts a new device code login.
     */
    public synchronized void logout() {
        logger.info("Google Drive account {}: logging out, a new login is required", getThing().getUID());
        cancelJobs();
        pendingCode = null;
        resetTokens();
        updateState(CHANNEL_QUOTA_USED, UnDefType.UNDEF);
        updateState(CHANNEL_QUOTA_TOTAL, UnDefType.UNDEF);
        scheduler.execute(this::startLogin);
    }

    /**
     * Called when Google no longer accepts the stored refresh token (revoked by the user, or expired because the
     * Google Cloud project is still in "Testing" status). Starts a new login.
     */
    public synchronized void authenticationLost() {
        if (pendingCode != null) {
            return;
        }
        logger.warn(
                "Google Drive account {}: Google no longer accepts the stored login, a new login is required. "
                        + "If this happens every 7 days, set the Google Cloud project to 'In production'.",
                getThing().getUID());
        cancelJobs();
        resetTokens();
        scheduler.execute(this::startLogin);
    }

    private void connect() {
        OAuthClientService service = oAuthService;
        if (service == null) {
            return;
        }
        try {
            AccessTokenResponse token = service.getAccessTokenResponse();
            if (token == null || token.getAccessToken() == null) {
                startLogin();
            } else {
                goOnline();
            }
        } catch (OAuthResponseException e) {
            if (INVALID_GRANT.equals(e.getError())) {
                authenticationLost();
            } else {
                communicationError("Token refresh failed: " + e.getError());
            }
        } catch (OAuthException | IOException e) {
            communicationError("Token refresh failed: " + e.getMessage());
        }
    }

    private synchronized void startLogin() {
        DeviceCodeLogin login = this.login;
        if (login == null) {
            return;
        }
        try {
            DeviceCodeResponse code = login.requestCode();
            pendingCode = code;
            pendingCodeExpiry = Instant.now().plusSeconds(code.expiresIn > 0 ? code.expiresIn : 1800);
            pollSeconds = Math.max(code.interval, MIN_POLL_SECONDS);

            String url = code.getVerificationUrl();
            String userCode = code.userCode;
            logger.info("Google Drive account {}: open {} and enter the code {}", getThing().getUID(), url, userCode);
            updateState(CHANNEL_LOGIN_CODE, new StringType(userCode));
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "@text/offline.config-pending.login [\"" + url + "\", \"" + userCode + "\"]");
            schedulePoll(pollSeconds);
        } catch (GoogleDriveConfigurationException e) {
            logger.debug("Google rejected the OAuth client: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error.invalid-client [\"" + e.getMessage() + "\"]");
        } catch (GoogleDriveException e) {
            communicationError(e.getMessage());
        }
    }

    private synchronized void pollLogin() {
        DeviceCodeLogin login = this.login;
        OAuthClientService service = oAuthService;
        DeviceCodeResponse code = pendingCode;
        String deviceCode = code != null ? code.deviceCode : null;
        if (login == null || service == null || deviceCode == null) {
            return;
        }
        if (Instant.now().isAfter(pendingCodeExpiry)) {
            startLogin();
            return;
        }
        try {
            PollResult result = login.poll(deviceCode);
            AccessTokenResponse token = result.token();
            switch (result.status()) {
                case SUCCESS:
                    if (token != null) {
                        service.importAccessTokenResponse(token);
                        pendingCode = null;
                        logger.info("Google Drive account {}: login successful", getThing().getUID());
                        goOnline();
                    }
                    break;
                case PENDING:
                    schedulePoll(pollSeconds);
                    break;
                case SLOW_DOWN:
                    pollSeconds += SLOW_DOWN_SECONDS;
                    schedulePoll(pollSeconds);
                    break;
                case EXPIRED:
                    startLogin();
                    break;
                case DENIED:
                    pendingCode = null;
                    updateState(CHANNEL_LOGIN_CODE, UnDefType.NULL);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "@text/offline.config-error.access-denied");
                    break;
            }
        } catch (GoogleDriveConfigurationException e) {
            pendingCode = null;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error.invalid-client [\"" + e.getMessage() + "\"]");
        } catch (GoogleDriveException e) {
            // Keep polling, the user may still complete the login once the connection is back.
            logger.debug("Login poll failed: {}", e.getMessage());
            schedulePoll(pollSeconds);
        } catch (OAuthException e) {
            communicationError("Cannot store the login: " + e.getMessage());
        }
    }

    private void goOnline() {
        updateState(CHANNEL_LOGIN_CODE, UnDefType.NULL);
        updateStatus(ThingStatus.ONLINE);
        startQuotaJob();
    }

    private void refreshQuota() {
        DriveApi api = driveApi;
        if (api == null) {
            return;
        }
        try {
            DriveApi.Quota quota = api.getQuota();
            updateState(CHANNEL_QUOTA_USED, new QuantityType<>(quota.used(), Units.BYTE));
            Long total = quota.total();
            updateState(CHANNEL_QUOTA_TOTAL, total != null ? new QuantityType<>(total, Units.BYTE) : UnDefType.UNDEF);
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (GoogleDriveAuthException e) {
            logger.debug("Quota refresh failed, login lost: {}", e.getMessage());
        } catch (GoogleDriveException e) {
            logger.debug("Quota refresh failed: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private void communicationError(@Nullable String message) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        scheduleConnect(RECONNECT_SECONDS);
    }

    private OAuthClientService createOAuthService() {
        return oAuthFactory.createOAuthClientService(getHandle(), OAUTH_TOKEN_URL, null, config.clientId.trim(),
                config.clientSecret.trim(), OAUTH_SCOPE, false);
    }

    private void resetTokens() {
        oAuthFactory.deleteServiceAndAccessToken(getHandle());
        oAuthService = createOAuthService();
    }

    private String getHandle() {
        return getThing().getUID().getAsString();
    }

    private synchronized void scheduleConnect(long delaySeconds) {
        cancel(connectJob);
        connectJob = scheduler.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }

    private synchronized void schedulePoll(long delaySeconds) {
        cancel(loginPollJob);
        loginPollJob = scheduler.schedule(this::pollLogin, delaySeconds, TimeUnit.SECONDS);
    }

    private synchronized void startQuotaJob() {
        cancel(quotaJob);
        quotaJob = null;
        if (config.quotaRefresh > 0) {
            quotaJob = scheduler.scheduleWithFixedDelay(this::refreshQuota, 0, config.quotaRefresh, TimeUnit.MINUTES);
        }
    }

    private synchronized void cancelJobs() {
        cancel(connectJob);
        cancel(loginPollJob);
        cancel(quotaJob);
        connectJob = null;
        loginPollJob = null;
        quotaJob = null;
    }

    private static void cancel(@Nullable ScheduledFuture<?> job) {
        if (job != null) {
            job.cancel(false);
        }
    }
}
