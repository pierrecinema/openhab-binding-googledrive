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
import org.openhab.binding.googledrive.internal.handler.GoogleDriveAccountHandler;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * The {@link GoogleDriveAccountActions} provides rule actions for the Google Drive account bridge.
 *
 * @author Pierrecinema - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = GoogleDriveAccountActions.class)
@ThingActionsScope(name = "googledrive")
@NonNullByDefault
public class GoogleDriveAccountActions implements ThingActions {

    private @Nullable GoogleDriveAccountHandler handler;

    @Override
    public void setThingHandler(ThingHandler handler) {
        if (handler instanceof GoogleDriveAccountHandler accountHandler) {
            this.handler = accountHandler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "@text/action.logout.label", description = "@text/action.logout.description")
    public void logout() {
        GoogleDriveAccountHandler handler = this.handler;
        if (handler != null) {
            handler.logout();
        }
    }

    public static void logout(ThingActions actions) {
        ((GoogleDriveAccountActions) actions).logout();
    }
}
