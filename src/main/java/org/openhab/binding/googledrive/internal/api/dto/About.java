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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Response of the Drive API about endpoint (only the storage quota is requested).
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public class About {

    public @Nullable StorageQuota storageQuota;

    /**
     * Storage quota in bytes. The Drive API sends the int64 values as strings; limit is missing for unlimited
     * storage.
     */
    public static class StorageQuota {
        public @Nullable String limit;
        public @Nullable String usage;
    }
}
