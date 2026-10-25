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

/**
 * The {@link GoogleDriveConfigurationException} is thrown when Google rejects the configured OAuth client, for
 * example because the client ID or secret is wrong or the client has the wrong type.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveConfigurationException extends GoogleDriveException {

    private static final long serialVersionUID = 1L;

    public GoogleDriveConfigurationException(String message, int httpStatus) {
        super(message, httpStatus);
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}
