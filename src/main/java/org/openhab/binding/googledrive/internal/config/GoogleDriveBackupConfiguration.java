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
package org.openhab.binding.googledrive.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link GoogleDriveBackupConfiguration} class contains fields mapping the backup thing configuration
 * parameters.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveBackupConfiguration {

    public static final String RETENTION_TRASH = "TRASH";
    public static final String RETENTION_DELETE = "DELETE";

    /** Empty means the default openHAB backup folder. */
    public String sourceFolder = "";
    public String filePattern = "*.zip";
    public String targetFolder = "openHAB Backups";
    public int retentionCount = 10;
    public String retentionMode = RETENTION_TRASH;
    public boolean autoUpload = true;
    public int stableSeconds = 30;
    public boolean uploadExistingOnStart = false;
    public boolean deleteLocalAfterUpload = false;
}
