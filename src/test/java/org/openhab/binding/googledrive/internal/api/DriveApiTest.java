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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.googledrive.internal.api.dto.DriveFile;

import com.google.gson.Gson;

/**
 * Tests for {@link DriveApi}.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
class DriveApiTest {

    private final Gson gson = new Gson();

    @Test
    void parseQuotaWithLimit() throws GoogleDriveException {
        String body = """
                {"storageQuota":{"limit":"16106127360","usage":"5368709120"}}
                """;
        DriveApi.Quota quota = DriveApi.parseQuota(gson, body);
        assertEquals(5368709120L, quota.used());
        assertEquals(16106127360L, quota.total());
    }

    @Test
    void parseQuotaUnlimited() throws GoogleDriveException {
        DriveApi.Quota quota = DriveApi.parseQuota(gson, "{\"storageQuota\":{\"usage\":\"42\"}}");
        assertEquals(42, quota.used());
        assertNull(quota.total());
    }

    @Test
    void parseQuotaMissing() {
        assertThrows(GoogleDriveException.class, () -> DriveApi.parseQuota(gson, "{}"));
        assertThrows(GoogleDriveException.class, () -> DriveApi.parseQuota(gson, "not json"));
    }

    @Test
    void errorMessageFromDriveError() {
        String body = """
                {"error":{"code":403,"message":"The user's Drive storage quota has been exceeded.",
                 "errors":[{"reason":"storageQuotaExceeded","message":"quota"}]}}
                """;
        assertEquals("HTTP 403 storageQuotaExceeded: The user's Drive storage quota has been exceeded.",
                DriveApi.errorMessage(gson, 403, body));
    }

    @Test
    void errorMessageWithoutBody() {
        assertEquals("HTTP 502", DriveApi.errorMessage(gson, 502, "<html>Bad Gateway</html>"));
    }

    @Test
    void parseRangeOfResumableUpload() {
        assertEquals(0, DriveApi.parseRange(null));
        assertEquals(8388608, DriveApi.parseRange("bytes=0-8388607"));
        assertEquals(1, DriveApi.parseRange("bytes=0-0"));
        assertEquals(0, DriveApi.parseRange("garbage"));
    }

    @Test
    void chunkSizeIsMultipleOf256KiB() {
        assertEquals(0, DriveApi.CHUNK_SIZE % (256 * 1024));
    }

    @Test
    void escapeQueryQuotes() {
        assertEquals("Pierre\\'s \\\\ Backups", DriveApi.escapeQuery("Pierre's \\ Backups"));
    }

    @Test
    void exceptionReasonAndRetry() {
        String quota = """
                {"error":{"code":403,"message":"quota","errors":[{"reason":"storageQuotaExceeded"}]}}
                """;
        GoogleDriveException full = DriveApi.toException(gson, 403, quota);
        assertEquals("storageQuotaExceeded", full.getReason());
        assertFalse(full.isRetryable());

        String rate = """
                {"error":{"code":403,"message":"slow","errors":[{"reason":"userRateLimitExceeded"}]}}
                """;
        assertTrue(DriveApi.toException(gson, 403, rate).isRetryable());
        assertTrue(DriveApi.toException(gson, 503, "").isRetryable());
        assertTrue(DriveApi.toException(gson, 429, "").isRetryable());
        assertFalse(DriveApi.toException(gson, 404, "").isRetryable());
        assertFalse(new GoogleDriveAuthException("login lost").isRetryable());
    }

    @Test
    void driveFileParsing() {
        DriveFile file = gson.fromJson("""
                {"id":"1","name":"b.zip","size":"12345","md5Checksum":"abc","createdTime":"2026-10-26T03:00:00.123Z"}
                """, DriveFile.class);
        assertNotNull(file);
        assertEquals(12345, file.getSize());
        assertEquals(Instant.parse("2026-10-26T03:00:00.123Z"), file.getCreatedTime());
        assertEquals(0, new DriveFile().getSize());
        assertEquals(Instant.EPOCH, new DriveFile().getCreatedTime());
    }
}
