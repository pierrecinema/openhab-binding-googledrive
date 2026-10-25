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
package org.openhab.binding.googledrive.internal.action;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.googledrive.internal.handler.GoogleDriveBackupHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.ActionOutput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * The {@link GoogleDriveBackupActions} provides rule actions for the backup thing.
 *
 * @author Pierrecinema - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = GoogleDriveBackupActions.class)
@ThingActionsScope(name = "googledrive")
@NonNullByDefault
public class GoogleDriveBackupActions implements ThingActions {

    private @Nullable GoogleDriveBackupHandler handler;

    @Override
    public void setThingHandler(ThingHandler handler) {
        if (handler instanceof GoogleDriveBackupHandler backupHandler) {
            this.handler = backupHandler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "@text/action.upload-latest.label", description = "@text/action.upload-latest.description")
    public @ActionOutput(type = "java.lang.Boolean", label = "@text/action.output.queued.label") boolean uploadLatest() {
        GoogleDriveBackupHandler handler = this.handler;
        return handler != null && handler.uploadLatest();
    }

    @RuleAction(label = "@text/action.upload-file.label", description = "@text/action.upload-file.description")
    public @ActionOutput(type = "java.lang.Boolean", label = "@text/action.output.queued.label") boolean uploadFile(
            @ActionInput(name = "fileName", label = "@text/action.input.file-name.label") String fileName) {
        GoogleDriveBackupHandler handler = this.handler;
        return handler != null && handler.uploadFile(fileName);
    }

    @RuleAction(label = "@text/action.cleanup.label", description = "@text/action.cleanup.description")
    public void cleanup() {
        GoogleDriveBackupHandler handler = this.handler;
        if (handler != null) {
            handler.cleanup();
        }
    }

    public static boolean uploadLatest(ThingActions actions) {
        return ((GoogleDriveBackupActions) actions).uploadLatest();
    }

    public static boolean uploadFile(ThingActions actions, String fileName) {
        return ((GoogleDriveBackupActions) actions).uploadFile(fileName);
    }

    public static void cleanup(ThingActions actions) {
        ((GoogleDriveBackupActions) actions).cleanup();
    }
}
