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
    private final @Nullable String reason;

    public GoogleDriveException(String message) {
        this(message, 0, null, null);
    }

    public GoogleDriveException(String message, int httpStatus) {
        this(message, httpStatus, null, null);
    }

    public GoogleDriveException(String message, @Nullable Throwable cause) {
        this(message, 0, null, cause);
    }

    public GoogleDriveException(String message, int httpStatus, @Nullable Throwable cause) {
        this(message, httpStatus, null, cause);
    }

    public GoogleDriveException(String message, int httpStatus, @Nullable String reason, @Nullable Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.reason = reason;
    }

    /**
     * Returns the HTTP status of the failed request, or 0 if no response was received.
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns the Drive API error reason (for example "storageQuotaExceeded"), or null if unknown.
     */
    public @Nullable String getReason() {
        return reason;
    }

    /**
     * Returns true if the same request may succeed when it is retried later (network problems, rate limits and
     * server errors).
     */
    public boolean isRetryable() {
        String reason = this.reason;
        return httpStatus == 0 || httpStatus == 429 || httpStatus >= 500
                || (httpStatus == 403 && reason != null && reason.toLowerCase().contains("ratelimit"));
    }
}
