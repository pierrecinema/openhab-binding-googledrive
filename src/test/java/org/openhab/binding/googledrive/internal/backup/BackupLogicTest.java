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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhab.binding.googledrive.internal.api.dto.DriveFile;

/**
 * Tests for {@link FilePattern}, {@link RetentionPolicy} and {@link BackupFiles}.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
class BackupLogicTest {

    @TempDir
    @NonNullByDefault({})
    Path tempDir;

    @Test
    void filePatternMatchesGlob() {
        FilePattern zip = new FilePattern("*.zip");
        assertTrue(zip.matches("openhab-backup-26_10_26-03_00_00.zip"));
        assertTrue(zip.matches("BACKUP.ZIP"));
        assertFalse(zip.matches("backup.zip.part"));
        assertFalse(zip.matches("notes.txt"));

        FilePattern prefixed = new FilePattern("openhab-backup-??_*.zip");
        assertTrue(prefixed.matches("openhab-backup-26_10_26.zip"));
        assertFalse(prefixed.matches("other-26_10_26.zip"));

        // Regex characters in the pattern are literals
        assertTrue(new FilePattern("backup(1).zip").matches("backup(1).zip"));
        assertFalse(new FilePattern("a.zip").matches("abzip"));

        assertTrue(new FilePattern("").matches("anything"));
    }

    @Test
    void retentionKeepsNewestBackups() {
        List<DriveFile> files = List.of(file("b3.zip", "2026-10-03T03:00:00Z"), file("b1.zip", "2026-10-01T03:00:00Z"),
                file("notes.txt", "2026-09-01T03:00:00Z"), file("b4.zip", "2026-10-04T03:00:00Z"),
                file("b2.zip", "2026-10-02T03:00:00Z"));
        FilePattern zip = new FilePattern("*.zip");

        List<DriveFile> remove = RetentionPolicy.filesToRemove(files, 2, zip);
        assertEquals(List.of("b1.zip", "b2.zip"), remove.stream().map(f -> f.name).toList());

        assertTrue(RetentionPolicy.filesToRemove(files, 0, zip).isEmpty(), "0 keeps all");
        assertTrue(RetentionPolicy.filesToRemove(files, 4, zip).isEmpty());
        assertTrue(RetentionPolicy.filesToRemove(files, 10, zip).isEmpty());
        assertEquals(4, RetentionPolicy.backups(files, zip).size(), "other files are not counted");
    }

    @Test
    void retentionIgnoresTrashedFiles() {
        DriveFile trashed = file("old.zip", "2026-01-01T00:00:00Z");
        trashed.trashed = true;
        List<DriveFile> files = List.of(trashed, file("new.zip", "2026-10-01T00:00:00Z"));
        assertTrue(RetentionPolicy.filesToRemove(files, 1, new FilePattern("*.zip")).isEmpty());
    }

    @Test
    void folderPathSplitsSegments() {
        assertEquals(List.of("openHAB Backups"), BackupFiles.folderPath(null));
        assertEquals(List.of("openHAB Backups"), BackupFiles.folderPath("  "));
        assertEquals(List.of("openHAB Backups"), BackupFiles.folderPath("/"));
        assertEquals(List.of("Backups"), BackupFiles.folderPath("Backups"));
        assertEquals(List.of("openHAB", "Backups"), BackupFiles.folderPath("/openHAB/ Backups /"));
    }

    @Test
    void listReturnsMatchingFilesOldestFirst() throws IOException {
        Path older = Files.writeString(tempDir.resolve("b-older.zip"), "1");
        Path newer = Files.writeString(tempDir.resolve("a-newer.zip"), "2");
        Files.writeString(tempDir.resolve("ignore.txt"), "3");
        Files.createDirectory(tempDir.resolve("dir.zip"));
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-10-01T00:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-10-02T00:00:00Z")));

        FilePattern zip = new FilePattern("*.zip");
        assertEquals(List.of(older, newer), BackupFiles.list(tempDir, zip));
        assertEquals(newer, BackupFiles.newest(tempDir, zip));
        assertNull(BackupFiles.newest(tempDir, new FilePattern("*.tar")));
    }

    @Test
    void localRetentionKeepsNewestAndProtectsUnuploaded() {
        Path b1 = Path.of("b1.zip");
        Path b2 = Path.of("b2.zip");
        Path b3 = Path.of("b3.zip");
        Path b4 = Path.of("b4.zip");
        Path b5 = Path.of("b5.zip");
        List<Path> local = List.of(b1, b2, b3, b4, b5);

        // All uploaded: keep the newest 2
        assertEquals(List.of(b1, b2, b3), BackupFiles.localFilesToRemove(local, 2, p -> true));

        // Newest two not uploaded yet (e.g. no internet): they stay, the older ones are safe in Drive
        assertEquals(List.of(b1, b2, b3),
                BackupFiles.localFilesToRemove(local, 2, p -> !p.equals(b4) && !p.equals(b5)));
        // b1 and b2 are older than b3, which is in Drive, so they may go as well
        assertEquals(List.of(b1, b2, b3), BackupFiles.localFilesToRemove(local, 2, p -> p.equals(b3)));
        // Keep 1, but b4 and b5 are not uploaded: b5 is kept by the count, b4 is protected
        assertEquals(List.of(b1, b2, b3), BackupFiles.localFilesToRemove(local, 1, p -> p.equals(b3)));

        // Only an old one in Drive: newer ones are protected
        assertEquals(List.of(b1), BackupFiles.localFilesToRemove(local, 1, p -> p.equals(b1)));

        // Nothing in Drive: nothing is deleted
        assertTrue(BackupFiles.localFilesToRemove(local, 1, p -> false).isEmpty());

        // 0 keeps all, fewer files than the limit
        assertTrue(BackupFiles.localFilesToRemove(local, 0, p -> true).isEmpty());
        assertTrue(BackupFiles.localFilesToRemove(local, 5, p -> true).isEmpty());
    }

    @Test
    void completeArchiveIsAccepted() throws IOException {
        Path zip = zip(tempDir.resolve("complete.zip"), 3);
        assertTrue(BackupFiles.isCompleteArchive(zip));
    }

    @Test
    void stalledWriterIsDetected() throws IOException {
        // A writer that stopped half way: the central directory at the end is missing
        Path complete = zip(tempDir.resolve("full.zip"), 3);
        byte[] bytes = Files.readAllBytes(complete);
        Path half = Files.write(tempDir.resolve("half.zip"), java.util.Arrays.copyOf(bytes, bytes.length / 2));
        assertFalse(BackupFiles.isCompleteArchive(half));

        // Only the last few bytes missing
        Path almost = Files.write(tempDir.resolve("almost.zip"), java.util.Arrays.copyOf(bytes, bytes.length - 10));
        assertFalse(BackupFiles.isCompleteArchive(almost));
    }

    @Test
    void damagedOrEmptyArchiveIsRejected() throws IOException {
        Path complete = zip(tempDir.resolve("data.zip"), 1);
        byte[] bytes = Files.readAllBytes(complete);
        // Flip bytes inside the first (stored) entry: the CRC check fails
        bytes[60] ^= 0x5A;
        bytes[61] ^= 0x5A;
        Path damaged = Files.write(tempDir.resolve("damaged.zip"), bytes);
        assertFalse(BackupFiles.isCompleteArchive(damaged));

        Path empty = tempDir.resolve("empty.zip");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(empty))) {
            out.finish();
        }
        assertFalse(BackupFiles.isCompleteArchive(empty), "an archive without files is not a backup");

        assertFalse(BackupFiles.isCompleteArchive(Files.writeString(tempDir.resolve("text.zip"), "no zip")));
        assertTrue(BackupFiles.isCompleteArchive(Files.writeString(tempDir.resolve("backup.tar"), "x")),
                "other formats are not checked");
    }

    private static Path zip(Path file, int entries) throws IOException {
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < entries; i++) {
                byte[] data = ("entry " + i + " ").repeat(2000).getBytes(StandardCharsets.UTF_8);
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("conf/file" + i + ".txt");
                // stored (not compressed), so damaged bytes are caught by the CRC and not by the inflater
                entry.setMethod(java.util.zip.ZipEntry.STORED);
                entry.setSize(data.length);
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                crc.update(data);
                entry.setCrc(crc.getValue());
                out.putNextEntry(entry);
                out.write(data);
                out.closeEntry();
            }
        }
        return file;
    }

    @Test
    void md5MatchesDriveFormat() throws IOException {
        Path file = Files.write(tempDir.resolve("x.zip"), "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("5d41402abc4b2a76b9719d911017c592", BackupFiles.md5(file));
    }

    private static DriveFile file(String name, String created) {
        DriveFile file = new DriveFile();
        file.id = "id-" + name;
        file.name = name;
        file.createdTime = created;
        return file;
    }
}
