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
 * The {@link GoogleDriveException} is thrown when a request to Google fails, for example because of a network
 * problem or an unexpected HTTP status.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    public GoogleDriveException(String message) {
        this(message, 0, null);
    }

    public GoogleDriveException(String message, int httpStatus) {
        this(message, httpStatus, null);
    }

    public GoogleDriveException(String message, @Nullable Throwable cause) {
        this(message, 0, cause);
    }

    public GoogleDriveException(String message, int httpStatus, @Nullable Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the HTTP status of the failed request, or 0 if no response was received.
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
