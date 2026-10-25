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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.googledrive.internal.api.dto.About;
import org.openhab.binding.googledrive.internal.api.dto.ApiError;
import org.openhab.binding.googledrive.internal.api.dto.DriveFile;
import org.openhab.binding.googledrive.internal.api.dto.FileList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /** Receives the progress of an upload. */
    @FunctionalInterface
    public interface ProgressListener {
        void progress(long bytesSent, long bytesTotal);
    }

    public static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    public static final String ROOT_FOLDER = "root";

    /** Chunk size for resumable uploads, must be a multiple of 256 KiB. */
    static final int CHUNK_SIZE = 8 * 1024 * 1024;

    private static final String API_URL = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String JSON = "application/json; charset=UTF-8";
    private static final int TIMEOUT_SECONDS = 30;
    private static final int CHUNK_TIMEOUT_SECONDS = 300;
    private static final int MAX_CHUNK_RETRIES = 5;
    private static final int HTTP_RESUME_INCOMPLETE = 308;

    private final Logger logger = LoggerFactory.getLogger(DriveApi.class);

    private final HttpClient httpClient;
    private final Gson gson;
    private final TokenProvider tokenProvider;

    @FunctionalInterface
    private interface RequestFactory {
        Request create(String accessToken);
    }

    /** State of a resumable upload session: the next offset, or the finished file. */
    private record UploadState(long offset, @Nullable DriveFile file) {
    }

    public DriveApi(HttpClient httpClient, Gson gson, TokenProvider tokenProvider) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Reads the storage quota of the account.
     */
    public Quota getQuota() throws GoogleDriveException {
        String body = executeOk(token -> request(HttpMethod.GET.asString(), API_URL + "/about", token).param("fields",
                "storageQuota(limit,usage)"));
        return parseQuota(gson, body);
    }

    /**
     * Returns the file or folder with the given id, or null if it does not exist (or is not visible to the binding).
     */
    public @Nullable DriveFile getFile(String id) throws GoogleDriveException {
        ContentResponse response = execute(token -> request(HttpMethod.GET.asString(), API_URL + "/files/" + id, token)
                .param("fields", DriveFile.FIELDS));
        if (response.getStatus() == HttpStatus.NOT_FOUND_404) {
            return null;
        }
        return fromJson(checkOk(response), DriveFile.class);
    }

    /**
     * Finds a folder by name inside a parent folder. Only folders created by the binding are visible.
     */
    public @Nullable DriveFile findFolder(String name, String parentId) throws GoogleDriveException {
        String query = "name = '" + escapeQuery(name) + "' and mimeType = '" + FOLDER_MIME_TYPE + "' and '"
                + escapeQuery(parentId) + "' in parents and trashed = false";
        List<DriveFile> folders = list(query, "createdTime");
        return folders.isEmpty() ? null : folders.get(0);
    }

    /**
     * Creates a folder inside a parent folder.
     */
    public DriveFile createFolder(String name, String parentId) throws GoogleDriveException {
        String metadata = gson.toJson(Map.of("name", name, "mimeType", FOLDER_MIME_TYPE, "parents", List.of(parentId)));
        String body = executeOk(token -> request(HttpMethod.POST.asString(), API_URL + "/files", token)
                .param("fields", DriveFile.FIELDS)
                .content(new StringContentProvider(JSON, metadata, StandardCharsets.UTF_8)));
        return requireFile(fromJson(body, DriveFile.class));
    }

    /**
     * Lists all files (no folders) in a folder, oldest first.
     */
    public List<DriveFile> listFiles(String folderId) throws GoogleDriveException {
        String query = "'" + escapeQuery(folderId) + "' in parents and trashed = false and mimeType != '"
                + FOLDER_MIME_TYPE + "'";
        return list(query, "createdTime");
    }

    /**
     * Moves a file to the trash of the Drive (it can be restored for 30 days).
     */
    public void trash(String id) throws GoogleDriveException {
        String body = gson.toJson(Map.of("trashed", true));
        ContentResponse response = execute(token -> request("PATCH", API_URL + "/files/" + id, token)
                .param("fields", "id").content(new StringContentProvider(JSON, body, StandardCharsets.UTF_8)));
        if (response.getStatus() != HttpStatus.NOT_FOUND_404) {
            checkOk(response);
        }
    }

    /**
     * Deletes a file permanently.
     */
    public void delete(String id) throws GoogleDriveException {
        ContentResponse response = execute(
                token -> request(HttpMethod.DELETE.asString(), API_URL + "/files/" + id, token));
        if (response.getStatus() != HttpStatus.NOT_FOUND_404) {
            checkOk(response);
        }
    }

    /**
     * Uploads a local file into a folder with a resumable upload. The file is sent in chunks; after a network or
     * server error the upload continues from the last byte Google has confirmed.
     *
     * @return the created file, including its MD5 checksum
     * @throws GoogleDriveException if the upload fails; an HTTP status 404 or 410 means the upload session expired
     *             and the upload has to be started again
     */
    public DriveFile upload(Path file, String name, String mimeType, String folderId,
            @Nullable ProgressListener listener) throws GoogleDriveException, IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long total = channel.size();
            String sessionUrl = startUpload(name, mimeType, folderId, total);
            ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(CHUNK_SIZE, Math.max(total, 1)));
            long offset = 0;
            int failures = 0;

            while (true) {
                int length = (int) Math.min(buffer.capacity(), total - offset);
                buffer.clear();
                buffer.limit(length);
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer, offset + buffer.position()) < 0) {
                        throw new IOException("File " + file + " became shorter during the upload");
                    }
                }
                buffer.flip();

                UploadState state;
                try {
                    state = sendChunk(sessionUrl, buffer, offset, length, total);
                } catch (GoogleDriveException e) {
                    if (!e.isRetryable() || ++failures > MAX_CHUNK_RETRIES) {
                        throw e;
                    }
                    long delay = 1L << Math.min(failures, 5);
                    logger.debug("Upload chunk failed ({}), retry {} in {}s", e.getMessage(), failures, delay);
                    sleep(delay);
                    try {
                        state = queryUpload(sessionUrl, total);
                    } catch (GoogleDriveException queryError) {
                        if (!queryError.isRetryable()) {
                            throw queryError;
                        }
                        // Still no connection: send the same chunk again on the next pass.
                        state = new UploadState(offset, null);
                    }
                }

                DriveFile result = state.file();
                if (result != null) {
                    if (listener != null) {
                        listener.progress(total, total);
                    }
                    return result;
                }
                if (state.offset() > offset) {
                    failures = 0;
                }
                offset = state.offset();
                if (listener != null) {
                    listener.progress(offset, total);
                }
            }
        }
    }

    private String startUpload(String name, String mimeType, String folderId, long total) throws GoogleDriveException {
        String metadata = gson.toJson(Map.of("name", name, "parents", List.of(folderId)));
        ContentResponse response = execute(token -> request(HttpMethod.POST.asString(), UPLOAD_URL, token)
                .param("uploadType", "resumable").param("fields", DriveFile.FIELDS)
                .header("X-Upload-Content-Type", mimeType).header("X-Upload-Content-Length", String.valueOf(total))
                .content(new StringContentProvider(JSON, metadata, StandardCharsets.UTF_8)));
        checkOk(response);
        String location = response.getHeaders().get(HttpHeader.LOCATION);
        if (location == null || location.isBlank()) {
            throw new GoogleDriveException("Upload session URL missing in response", response.getStatus());
        }
        return location;
    }

    private UploadState sendChunk(String sessionUrl, ByteBuffer chunk, long offset, int length, long total)
            throws GoogleDriveException {
        String range = length == 0 ? "bytes */" + total : "bytes " + offset + "-" + (offset + length - 1) + "/" + total;
        ContentResponse response = execute(token -> request(HttpMethod.PUT.asString(), sessionUrl, token)
                .timeout(CHUNK_TIMEOUT_SECONDS, TimeUnit.SECONDS).header(HttpHeader.CONTENT_RANGE, range)
                .content(new ByteBufferContentProvider("application/octet-stream", chunk.duplicate())));
        return toUploadState(response);
    }

    private UploadState queryUpload(String sessionUrl, long total) throws GoogleDriveException {
        ContentResponse response = execute(token -> request(HttpMethod.PUT.asString(), sessionUrl, token)
                .header(HttpHeader.CONTENT_RANGE, "bytes */" + total)
                .content(new ByteBufferContentProvider(ByteBuffer.allocate(0))));
        return toUploadState(response);
    }

    private UploadState toUploadState(ContentResponse response) throws GoogleDriveException {
        int status = response.getStatus();
        if (status == HttpStatus.OK_200 || status == HttpStatus.CREATED_201) {
            return new UploadState(0, requireFile(fromJson(response.getContentAsString(), DriveFile.class)));
        }
        if (status == HTTP_RESUME_INCOMPLETE) {
            return new UploadState(parseRange(response.getHeaders().get(HttpHeader.RANGE)), null);
        }
        throw toException(gson, status, response.getContentAsString());
    }

    private List<DriveFile> list(String query, String orderBy) throws GoogleDriveException {
        List<DriveFile> result = new ArrayList<>();
        String pageToken = null;
        do {
            String currentToken = pageToken;
            String body = executeOk(token -> {
                Request request = request(HttpMethod.GET.asString(), API_URL + "/files", token).param("q", query)
                        .param("orderBy", orderBy).param("pageSize", "1000").param("spaces", "drive")
                        .param("fields", "nextPageToken,files(" + DriveFile.FIELDS + ")");
                return currentToken != null ? request.param("pageToken", currentToken) : request;
            });
            FileList page = fromJson(body, FileList.class);
            List<DriveFile> files = page != null ? page.files : null;
            if (files != null) {
                result.addAll(files);
            }
            pageToken = page != null ? page.nextPageToken : null;
        } while (pageToken != null);
        return result;
    }

    private Request request(String method, String url, String accessToken) {
        // Redirects are not followed: Google answers a partial upload with "308 Resume Incomplete" without a
        // Location header, which Jetty would otherwise treat as a failed redirect.
        return httpClient.newRequest(url).method(method).timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(false).header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken);
    }

    /**
     * Sends a request; on HTTP 401 the token is refreshed and the request is sent once more.
     */
    private ContentResponse execute(RequestFactory factory) throws GoogleDriveException {
        ContentResponse response = send(factory.create(tokenProvider.getAccessToken(false)));
        if (response.getStatus() == HttpStatus.UNAUTHORIZED_401) {
            // The token may have been revoked or expired early, try once with a fresh one.
            response = send(factory.create(tokenProvider.getAccessToken(true)));
        }
        if (response.getStatus() == HttpStatus.UNAUTHORIZED_401) {
            throw new GoogleDriveAuthException(errorMessage(gson, response.getStatus(), response.getContentAsString()));
        }
        return response;
    }

    private String executeOk(RequestFactory factory) throws GoogleDriveException {
        return checkOk(execute(factory));
    }

    private String checkOk(ContentResponse response) throws GoogleDriveException {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            throw toException(gson, status, response.getContentAsString());
        }
        return response.getContentAsString();
    }

    private ContentResponse send(Request request) throws GoogleDriveException {
        try {
            return request.send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveException("Request interrupted", e);
        } catch (TimeoutException | ExecutionException e) {
            throw new GoogleDriveException("Cannot reach Google Drive: " + e.getMessage(), e);
        }
    }

    private <T> @Nullable T fromJson(String body, Class<T> type) throws GoogleDriveException {
        try {
            return gson.fromJson(body, type);
        } catch (JsonParseException e) {
            throw new GoogleDriveException("Invalid response from Google Drive", e);
        }
    }

    private static DriveFile requireFile(@Nullable DriveFile file) throws GoogleDriveException {
        if (file == null || file.id == null) {
            throw new GoogleDriveException("File missing in response");
        }
        return file;
    }

    private static void sleep(long seconds) throws GoogleDriveException {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveException("Upload interrupted", e);
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
     * Returns the next upload offset from the Range header of a 308 response ("bytes=0-1234" means 1235 bytes
     * were received). A missing header means nothing was received yet.
     */
    static long parseRange(@Nullable String range) {
        if (range == null) {
            return 0;
        }
        int dash = range.lastIndexOf('-');
        if (dash < 0) {
            return 0;
        }
        try {
            return Long.parseLong(range.substring(dash + 1).trim()) + 1;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Escapes a value for use inside single quotes in a Drive API query.
     */
    static String escapeQuery(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Builds an exception from a Drive API error body.
     */
    static GoogleDriveException toException(Gson gson, int status, String body) {
        String reason = null;
        try {
            ApiError apiError = gson.fromJson(body, ApiError.class);
            ApiError.Error error = apiError != null ? apiError.error : null;
            List<ApiError.Detail> details = error != null ? error.errors : null;
            reason = details != null && !details.isEmpty() ? details.get(0).reason : null;
        } catch (JsonParseException e) {
            // no structured error
        }
        return new GoogleDriveException(errorMessage(gson, status, body), status, reason, null);
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
