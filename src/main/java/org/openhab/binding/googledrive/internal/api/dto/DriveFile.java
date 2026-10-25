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

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A file or folder in Google Drive (only the fields requested by the binding).
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public class DriveFile {

    public static final String FIELDS = "id,name,mimeType,size,md5Checksum,createdTime,trashed";

    public @Nullable String id;
    public @Nullable String name;
    public @Nullable String mimeType;
    /** int64 sent as string by the Drive API. */
    public @Nullable String size;
    public @Nullable String md5Checksum;
    /** RFC 3339 timestamp. */
    public @Nullable String createdTime;
    public boolean trashed;

    public long getSize() {
        String size = this.size;
        if (size == null) {
            return 0;
        }
        try {
            return Long.parseLong(size.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Instant getCreatedTime() {
        String createdTime = this.createdTime;
        if (createdTime == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(createdTime);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }
}
