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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link GoogleDriveBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveBindingConstants {

    public static final String BINDING_ID = "googledrive";

    // Thing types
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_BACKUP = new ThingTypeUID(BINDING_ID, "backup");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_BACKUP);

    // Account channels
    public static final String CHANNEL_QUOTA_USED = "quotaUsed";
    public static final String CHANNEL_QUOTA_TOTAL = "quotaTotal";
    public static final String CHANNEL_LOGIN_CODE = "loginCode";

    // Backup channels
    public static final String CHANNEL_UPLOAD = "upload";
    public static final String CHANNEL_STATUS = "status";
    public static final String CHANNEL_PROGRESS = "progress";
    public static final String CHANNEL_LAST_UPLOAD = "lastUpload";
    public static final String CHANNEL_LAST_FILE = "lastFile";
    public static final String CHANNEL_LAST_SIZE = "lastSize";
    public static final String CHANNEL_REMOTE_COUNT = "remoteCount";
    public static final String CHANNEL_LAST_ERROR = "lastError";
    public static final String CHANNEL_EVENT = "event";

    // Google OAuth2 (RFC 8628 device authorization grant)
    public static final String OAUTH_DEVICE_URL = "https://oauth2.googleapis.com/device/code";
    public static final String OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/drive.file";
}
