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
package org.openhab.binding.googledrive.internal.api;

import static org.openhab.binding.googledrive.internal.GoogleDriveBindingConstants.*;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.openhab.binding.googledrive.internal.api.dto.DeviceCodeResponse;
import org.openhab.binding.googledrive.internal.api.dto.TokenResponse;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * The {@link DeviceCodeLogin} implements the OAuth2 device authorization grant (RFC 8628) against Google.
 * <p>
 * The openHAB core implementation does not send the client secret when polling for the token, which Google requires,
 * so the login is done here. The resulting tokens are handed to the core OAuth client service, which stores and
 * refreshes them.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class DeviceCodeLogin {

    /** Outcome of one token poll. */
    public enum PollStatus {
        /** The user has granted access, the token is available. */
        SUCCESS,
        /** The user has not finished the login yet. */
        PENDING,
        /** Polling too fast, the interval must be increased. */
        SLOW_DOWN,
        /** The user has denied access. */
        DENIED,
        /** The device code has expired, a new one is needed. */
        EXPIRED
    }

    public record PollResult(PollStatus status, @Nullable AccessTokenResponse token) {
    }

    private static final int TIMEOUT_SECONDS = 20;
    private static final String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    private final HttpClient httpClient;
    private final Gson gson;
    private final String clientId;
    private final String clientSecret;

    public DeviceCodeLogin(HttpClient httpClient, Gson gson, String clientId, String clientSecret) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Requests a new device code and user code from Google.
     */
    public DeviceCodeResponse requestCode() throws GoogleDriveException {
        Fields fields = new Fields();
        fields.put("client_id", clientId);
        fields.put("scope", OAUTH_SCOPE);
        ContentResponse response = post(OAUTH_DEVICE_URL, fields);
        return parseDeviceCode(gson, response.getStatus(), response.getContentAsString());
    }

    /**
     * Asks Google once whether the user has completed the login for the given device code.
     */
    public PollResult poll(String deviceCode) throws GoogleDriveException {
        Fields fields = new Fields();
        fields.put("client_id", clientId);
        fields.put("client_secret", clientSecret);
        fields.put("device_code", deviceCode);
        fields.put("grant_type", GRANT_TYPE_DEVICE_CODE);
        ContentResponse response = post(OAUTH_TOKEN_URL, fields);
        return parseToken(gson, response.getStatus(), response.getContentAsString(), Instant.now());
    }

    private ContentResponse post(String url, Fields fields) throws GoogleDriveException {
        try {
            return httpClient.newRequest(url).method(HttpMethod.POST).timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .content(new FormContentProvider(fields)).send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveException("Request interrupted", e);
        } catch (TimeoutException | ExecutionException e) {
            throw new GoogleDriveException("Cannot reach Google: " + e.getMessage(), e);
        }
    }

    static DeviceCodeResponse parseDeviceCode(Gson gson, int status, String body) throws GoogleDriveException {
        if (status == HttpStatus.OK_200) {
            DeviceCodeResponse code = fromJson(gson, body, DeviceCodeResponse.class);
            if (code != null && code.deviceCode != null && code.userCode != null) {
                return code;
            }
            throw new GoogleDriveException("Incomplete device code response", status);
        }
        throw toException(fromJson(gson, body, TokenResponse.class), status);
    }

    static PollResult parseToken(Gson gson, int status, String body, Instant now) throws GoogleDriveException {
        TokenResponse token = fromJson(gson, body, TokenResponse.class);
        String accessToken = token != null ? token.accessToken : null;
        if (status == HttpStatus.OK_200 && token != null && accessToken != null) {
            AccessTokenResponse result = new AccessTokenResponse();
            result.setAccessToken(accessToken);
            result.setRefreshToken(token.refreshToken);
            result.setExpiresIn(token.expiresIn);
            result.setTokenType(token.tokenType);
            result.setScope(token.scope);
            result.setCreatedOn(now);
            return new PollResult(PollStatus.SUCCESS, result);
        }

        String error = token != null ? token.error : null;
        if (error != null) {
            switch (error) {
                case "authorization_pending":
                    return new PollResult(PollStatus.PENDING, null);
                case "slow_down":
                    return new PollResult(PollStatus.SLOW_DOWN, null);
                case "access_denied":
                    return new PollResult(PollStatus.DENIED, null);
                case "expired_token":
                case "invalid_grant":
                    return new PollResult(PollStatus.EXPIRED, null);
                default:
                    break;
            }
        }
        throw toException(token, status);
    }

    private static GoogleDriveException toException(@Nullable TokenResponse error, int status) {
        String code = error != null ? error.error : null;
        String description = error != null ? error.errorDescription : null;
        String message = (code != null ? code : "HTTP " + status) + (description != null ? ": " + description : "");
        if ("invalid_client".equals(code) || "unauthorized_client".equals(code) || "invalid_scope".equals(code)
                || "restricted_client".equals(code)) {
            return new GoogleDriveConfigurationException(message, status);
        }
        return new GoogleDriveException(message, status);
    }

    private static <T> @Nullable T fromJson(Gson gson, String body, Class<T> type) {
        try {
            return gson.fromJson(body, type);
        } catch (JsonParseException e) {
            return null;
        }
    }
}
