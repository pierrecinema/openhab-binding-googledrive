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

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.googledrive.internal.config.GoogleDriveBackupConfiguration;
import org.openhab.core.OpenHAB;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;

/**
 * The {@link GoogleDriveBackupHandler} watches the local backup folder and uploads new backups to Google Drive.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveBackupHandler extends BaseThingHandler {

    private GoogleDriveBackupConfiguration config = new GoogleDriveBackupConfiguration();

    public GoogleDriveBackupHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(GoogleDriveBackupConfiguration.class);

        Path source = getSourceFolder();
        if (!Files.isDirectory(source)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error.source-folder-missing [\"" + source + "\"]");
            return;
        }

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }
        if (bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        // Watching and uploading is implemented in milestone M3.
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Upload on command is implemented in milestone M3.
    }

    /**
     * Returns the configured source folder or the default openHAB backup folder (userdata/backup).
     */
    Path getSourceFolder() {
        if (!config.sourceFolder.isBlank()) {
            return Path.of(config.sourceFolder);
        }
        return Path.of(OpenHAB.getUserDataFolder(), "backup");
    }
}
