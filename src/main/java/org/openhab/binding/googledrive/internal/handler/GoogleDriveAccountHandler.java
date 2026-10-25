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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.googledrive.internal.config.GoogleDriveAccountConfiguration;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GoogleDriveAccountHandler} is the bridge to a Google Drive account. It handles the OAuth2 device code
 * login and provides the Drive API to its child things.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveAccountHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(GoogleDriveAccountHandler.class);

    private final OAuthFactory oAuthFactory;
    private final HttpClient httpClient;

    private GoogleDriveAccountConfiguration config = new GoogleDriveAccountConfiguration();

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

        // The device code login is implemented in milestone M2.
        logger.debug("Google Drive account {} initialized", getThing().getUID());
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "@text/offline.config-pending.login-not-implemented");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // All account channels are read-only.
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    OAuthFactory getOAuthFactory() {
        return oAuthFactory;
    }

    HttpClient getHttpClient() {
        return httpClient;
    }
}
