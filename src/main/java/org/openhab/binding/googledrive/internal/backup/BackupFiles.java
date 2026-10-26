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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Helpers for local backup files and the target folder path.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public final class BackupFiles {

    public static final String DEFAULT_FOLDER = "openHAB Backups";

    private BackupFiles() {
    }

    /**
     * Lists the regular files in a folder whose names match the pattern, newest (last modified) last.
     */
    public static List<Path> list(Path folder, FilePattern pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).filter(p -> pattern.matches(fileName(p))).forEach(result::add);
        }
        result.sort(Comparator.comparingLong(BackupFiles::lastModified).thenComparing(BackupFiles::fileName));
        return result;
    }

    /**
     * Returns the newest matching file, or null if there is none.
     */
    public static @Nullable Path newest(Path folder, FilePattern pattern) throws IOException {
        List<Path> files = list(folder, pattern);
        return files.isEmpty() ? null : files.get(files.size() - 1);
    }

    /**
     * Returns the local backups to delete so that {@code keep} backups stay on the server.
     * <p>
     * Only backups that are in Google Drive, or older than the newest backup in Google Drive, are returned. A backup
     * that is newer than everything in Google Drive (for example because the upload failed) is never deleted.
     *
     * @param local local backups, oldest first
     * @param keep number of local backups to keep, 0 keeps all
     * @param inDrive tells whether a local backup is in Google Drive
     */
    public static List<Path> localFilesToRemove(List<Path> local, int keep, Predicate<Path> inDrive) {
        if (keep <= 0 || local.size() <= keep) {
            return List.of();
        }
        int newestInDrive = -1;
        for (int i = local.size() - 1; i >= 0; i--) {
            if (inDrive.test(local.get(i))) {
                newestInDrive = i;
                break;
            }
        }
        int limit = Math.min(local.size() - keep, newestInDrive + 1);
        return limit > 0 ? List.copyOf(local.subList(0, limit)) : List.of();
    }

    /**
     * Calculates the MD5 checksum of a file as lower case hex string (the format used by Google Drive).
     */
    public static String md5(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 not available", e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Splits the configured target folder into its path segments ("openHAB/Backups" becomes two folders).
     * An empty value means the default folder.
     */
    public static List<String> folderPath(@Nullable String targetFolder) {
        if (targetFolder == null || targetFolder.isBlank()) {
            return List.of(DEFAULT_FOLDER);
        }
        List<String> segments = Arrays.stream(targetFolder.split("/")).map(String::trim).filter(s -> !s.isEmpty())
                .toList();
        return segments.isEmpty() ? List.of(DEFAULT_FOLDER) : segments;
    }

    public static String fileName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
