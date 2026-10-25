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

import com.google.gson.annotations.SerializedName;

/**
 * Response of the Google device code endpoint.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public class DeviceCodeResponse {

    private static final String DEFAULT_VERIFICATION_URL = "https://www.google.com/device";

    @SerializedName("device_code")
    public @Nullable String deviceCode;

    @SerializedName("user_code")
    public @Nullable String userCode;

    /** Google uses verification_url, RFC 8628 verification_uri; both are accepted. */
    @SerializedName("verification_url")
    public @Nullable String verificationUrl;

    @SerializedName("verification_uri")
    public @Nullable String verificationUri;

    @SerializedName("expires_in")
    public long expiresIn;

    public long interval;

    public String getVerificationUrl() {
        String url = verificationUrl != null ? verificationUrl : verificationUri;
        return url != null ? url : DEFAULT_VERIFICATION_URL;
    }
}
