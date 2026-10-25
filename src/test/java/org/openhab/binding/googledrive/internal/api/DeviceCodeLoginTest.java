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
import org.openhab.binding.googledrive.internal.api.DeviceCodeLogin.PollResult;
import org.openhab.binding.googledrive.internal.api.DeviceCodeLogin.PollStatus;
import org.openhab.binding.googledrive.internal.api.dto.DeviceCodeResponse;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;

import com.google.gson.Gson;

/**
 * Tests for {@link DeviceCodeLogin}.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
class DeviceCodeLoginTest {

    private final Gson gson = new Gson();

    @Test
    void parseDeviceCodeWithGoogleFieldNames() throws GoogleDriveException {
        String body = """
                {"device_code":"dev-123","user_code":"ABCD-EFGH","expires_in":1800,"interval":5,
                 "verification_url":"https://www.google.com/device"}
                """;
        DeviceCodeResponse code = DeviceCodeLogin.parseDeviceCode(gson, 200, body);
        assertEquals("dev-123", code.deviceCode);
        assertEquals("ABCD-EFGH", code.userCode);
        assertEquals(1800, code.expiresIn);
        assertEquals(5, code.interval);
        assertEquals("https://www.google.com/device", code.getVerificationUrl());
    }

    @Test
    void parseDeviceCodeWithRfcFieldName() throws GoogleDriveException {
        String body = """
                {"device_code":"d","user_code":"u","verification_uri":"https://example.com/activate"}
                """;
        assertEquals("https://example.com/activate",
                DeviceCodeLogin.parseDeviceCode(gson, 200, body).getVerificationUrl());
    }

    @Test
    void parseDeviceCodeInvalidClientIsConfigurationError() {
        String body = """
                {"error":"invalid_client","error_description":"The OAuth client was not found."}
                """;
        GoogleDriveException e = assertThrows(GoogleDriveConfigurationException.class,
                () -> DeviceCodeLogin.parseDeviceCode(gson, 401, body));
        assertEquals(401, e.getHttpStatus());
        assertTrue(String.valueOf(e.getMessage()).contains("invalid_client"));
    }

    @Test
    void parseDeviceCodeServerErrorIsCommunicationError() {
        GoogleDriveException e = assertThrows(GoogleDriveException.class,
                () -> DeviceCodeLogin.parseDeviceCode(gson, 503, "<html>unavailable</html>"));
        assertFalse(e instanceof GoogleDriveConfigurationException);
        assertEquals("HTTP 503", e.getMessage());
    }

    @Test
    void parseTokenSuccess() throws GoogleDriveException {
        Instant now = Instant.parse("2026-10-26T10:00:00Z");
        String body = """
                {"access_token":"ya29.token","expires_in":3599,"refresh_token":"1//refresh",
                 "scope":"https://www.googleapis.com/auth/drive.file","token_type":"Bearer"}
                """;
        PollResult result = DeviceCodeLogin.parseToken(gson, 200, body, now);
        assertEquals(PollStatus.SUCCESS, result.status());
        AccessTokenResponse token = result.token();
        assertNotNull(token);
        assertEquals("ya29.token", token.getAccessToken());
        assertEquals("1//refresh", token.getRefreshToken());
        assertEquals(3599, token.getExpiresIn());
        assertEquals(now, token.getCreatedOn());
    }

    @Test
    void parseTokenPollErrors() throws GoogleDriveException {
        assertEquals(PollStatus.PENDING, poll(428, "authorization_pending"));
        assertEquals(PollStatus.SLOW_DOWN, poll(403, "slow_down"));
        assertEquals(PollStatus.DENIED, poll(403, "access_denied"));
        assertEquals(PollStatus.EXPIRED, poll(400, "expired_token"));
        assertEquals(PollStatus.EXPIRED, poll(400, "invalid_grant"));
    }

    @Test
    void parseTokenInvalidClientIsConfigurationError() {
        assertThrows(GoogleDriveConfigurationException.class, () -> poll(401, "invalid_client"));
    }

    @Test
    void parseTokenUnknownErrorIsCommunicationError() {
        GoogleDriveException e = assertThrows(GoogleDriveException.class, () -> poll(500, "internal_failure"));
        assertFalse(e instanceof GoogleDriveConfigurationException);
    }

    private PollStatus poll(int status, String error) throws GoogleDriveException {
        String body = "{\"error\":\"" + error + "\"}";
        return DeviceCodeLogin.parseToken(gson, status, body, Instant.now()).status();
    }
}
