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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.googledrive.internal.api.dto.About;
import org.openhab.binding.googledrive.internal.api.dto.ApiError;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * The {@link DriveApi} is a small client for the Google Drive REST API v3.
 *
 * @author Pierre - Initial contribution
 */
@NonNullByDefault
public class DriveApi {

    /** Storage quota in bytes; total is null for unlimited storage. */
    public record Quota(long used, @Nullable Long total) {
    }

    private static final String API_URL = "https://www.googleapis.com/drive/v3";
    private static final int TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient;
    private final Gson gson;
    private final TokenProvider tokenProvider;

    public DriveApi(HttpClient httpClient, Gson gson, TokenProvider tokenProvider) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Reads the storage quota of the account.
     */
    public Quota getQuota() throws GoogleDriveException {
        String body = get(API_URL + "/about", "fields", "storageQuota(limit,usage)");
        return parseQuota(gson, body);
    }

    private String get(String url, String... params) throws GoogleDriveException {
        ContentResponse response = send(url, params, tokenProvider.getAccessToken(false));
        if (response.getStatus() == HttpStatus.UNAUTHORIZED_401) {
            // The token may have been revoked or expired early, try once with a fresh one.
            response = send(url, params, tokenProvider.getAccessToken(true));
        }
        int status = response.getStatus();
        if (status == HttpStatus.UNAUTHORIZED_401) {
            throw new GoogleDriveAuthException(errorMessage(gson, status, response.getContentAsString()));
        }
        if (status < 200 || status >= 300) {
            throw new GoogleDriveException(errorMessage(gson, status, response.getContentAsString()), status);
        }
        return response.getContentAsString();
    }

    private ContentResponse send(String url, String[] params, String accessToken) throws GoogleDriveException {
        Request request = httpClient.newRequest(url).method(HttpMethod.GET).timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        try {
            return request.send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveException("Request interrupted", e);
        } catch (TimeoutException | ExecutionException e) {
            throw new GoogleDriveException("Cannot reach Google Drive: " + e.getMessage(), e);
        }
    }

    static Quota parseQuota(Gson gson, String body) throws GoogleDriveException {
        About about;
        try {
            about = gson.fromJson(body, About.class);
        } catch (JsonParseException e) {
            throw new GoogleDriveException("Invalid quota response", e);
        }
        About.StorageQuota quota = about != null ? about.storageQuota : null;
        if (quota == null) {
            throw new GoogleDriveException("Quota missing in response");
        }
        Long used = parseLong(quota.usage);
        return new Quota(used != null ? used : 0, parseLong(quota.limit));
    }

    /**
     * Builds a readable message from a Drive API error body.
     */
    static String errorMessage(Gson gson, int status, String body) {
        try {
            ApiError apiError = gson.fromJson(body, ApiError.class);
            ApiError.Error error = apiError != null ? apiError.error : null;
            if (error != null) {
                String message = error.message;
                List<ApiError.Detail> details = error.errors;
                String reason = details != null && !details.isEmpty() ? details.get(0).reason : null;
                return "HTTP " + status + (reason != null ? " " + reason : "")
                        + (message != null ? ": " + message : "");
            }
        } catch (JsonParseException e) {
            // fall through to the plain status
        }
        return "HTTP " + status;
    }

    private static @Nullable Long parseLong(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
