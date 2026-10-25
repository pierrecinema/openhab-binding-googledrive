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
package org.openhab.binding.googledrive.internal.api.dto;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Error body returned by the Drive API.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class ApiError {

    public @Nullable Error error;

    public static class Error {
        public int code;
        public @Nullable String message;
        public @Nullable List<Detail> errors;
    }

    public static class Detail {
        public @Nullable String reason;
        public @Nullable String message;
    }
}
