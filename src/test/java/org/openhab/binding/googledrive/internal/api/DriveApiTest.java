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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

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
}
