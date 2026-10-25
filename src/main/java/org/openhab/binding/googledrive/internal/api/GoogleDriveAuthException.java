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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link GoogleDriveAuthException} is thrown when Google no longer accepts the stored login, so the user has to
 * log in again.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveAuthException extends GoogleDriveException {

    private static final long serialVersionUID = 1L;

    public GoogleDriveAuthException(String message) {
        super(message);
    }

    public GoogleDriveAuthException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
