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
package org.openhab.binding.googledrive.internal.backup;

import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.googledrive.internal.api.dto.DriveFile;

/**
 * The {@link RetentionPolicy} decides which backups in Google Drive are removed.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public final class RetentionPolicy {

    private RetentionPolicy() {
    }

    /**
     * Returns the backups that exceed the number to keep, oldest first. Only files matching the pattern are
     * considered, other files in the folder are never touched.
     *
     * @param files the files in the Drive folder
     * @param keep number of backups to keep, 0 keeps all
     * @param pattern pattern of the backup files
     */
    public static List<DriveFile> filesToRemove(List<DriveFile> files, int keep, FilePattern pattern) {
        List<DriveFile> backups = backups(files, pattern);
        if (keep <= 0 || backups.size() <= keep) {
            return List.of();
        }
        return backups.subList(0, backups.size() - keep);
    }

    /**
     * Returns the files matching the pattern, sorted oldest first (by creation time, then by name).
     */
    public static List<DriveFile> backups(List<DriveFile> files, FilePattern pattern) {
        return files.stream().filter(f -> !f.trashed).filter(f -> {
            String name = f.name;
            return name != null && pattern.matches(name);
        }).sorted(Comparator.comparing(DriveFile::getCreatedTime).thenComparing(f -> {
            String name = f.name;
            return name != null ? name : "";
        })).toList();
    }
}
