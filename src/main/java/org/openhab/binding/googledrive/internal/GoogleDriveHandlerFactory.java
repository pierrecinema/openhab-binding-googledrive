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
package org.openhab.binding.googledrive.internal;

import static org.openhab.binding.googledrive.internal.GoogleDriveBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.googledrive.internal.handler.GoogleDriveAccountHandler;
import org.openhab.binding.googledrive.internal.handler.GoogleDriveBackupHandler;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link GoogleDriveHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.googledrive", service = ThingHandlerFactory.class)
public class GoogleDriveHandlerFactory extends BaseThingHandlerFactory {

    private final OAuthFactory oAuthFactory;
    private final HttpClientFactory httpClientFactory;

    @Activate
    public GoogleDriveHandlerFactory(@Reference OAuthFactory oAuthFactory,
            @Reference HttpClientFactory httpClientFactory) {
        this.oAuthFactory = oAuthFactory;
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            return new GoogleDriveAccountHandler((Bridge) thing, oAuthFactory, httpClientFactory.getCommonHttpClient());
        } else if (THING_TYPE_BACKUP.equals(thingTypeUID)) {
            return new GoogleDriveBackupHandler(thing);
        }

        return null;
    }
}
