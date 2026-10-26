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
package org.openhab.binding.googledrive.internal.handler;

import static org.openhab.binding.googledrive.internal.GoogleDriveBindingConstants.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.googledrive.internal.action.GoogleDriveBackupActions;
import org.openhab.binding.googledrive.internal.api.DriveApi;
import org.openhab.binding.googledrive.internal.api.GoogleDriveAuthException;
import org.openhab.binding.googledrive.internal.api.GoogleDriveException;
import org.openhab.binding.googledrive.internal.api.dto.DriveFile;
import org.openhab.binding.googledrive.internal.backup.BackupFiles;
import org.openhab.binding.googledrive.internal.backup.FilePattern;
import org.openhab.binding.googledrive.internal.backup.RetentionPolicy;
import org.openhab.binding.googledrive.internal.config.GoogleDriveBackupConfiguration;
import org.openhab.core.OpenHAB;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GoogleDriveBackupHandler} watches the local backup folder and uploads new backups to Google Drive.
 * <p>
 * The folder is scanned every few seconds. A new file is uploaded once its size and modification time have not
 * changed for {@code stableSeconds}, because the backup script writes the archive step by step. Uploads run one at a
 * time from a queue; failed uploads are retried a few times.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public class GoogleDriveBackupHandler extends BaseThingHandler {

    public static final String STATUS_IDLE = "IDLE";
    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    public static final String EVENT_STARTED = "UPLOAD_STARTED";
    public static final String EVENT_SUCCESS = "UPLOAD_SUCCESS";
    public static final String EVENT_FAILED = "UPLOAD_FAILED";

    static final String PROPERTY_FOLDER_ID = "folderId";
    static final String PROPERTY_FOLDER_PATH = "folderPath";

    private static final int SCAN_SECONDS = 10;
    private static final int RETRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;

    private final Logger logger = LoggerFactory.getLogger(GoogleDriveBackupHandler.class);

    private GoogleDriveBackupConfiguration config = new GoogleDriveBackupConfiguration();
    private FilePattern pattern = new FilePattern("*.zip");
    private Path sourceFolder = Path.of(".");
    private List<String> folderPath = List.of(BackupFiles.DEFAULT_FOLDER);

    private @Nullable ScheduledFuture<?> scanJob;
    private volatile boolean active;
    private volatile boolean uploading;

    /** Guards the collections below. */
    private final Object lock = new Object();
    /** Files that are already handled (present at start, uploaded, or skipped). */
    private final Set<Path> known = new HashSet<>();
    /** New files waiting until they are completely written. */
    private final Map<Path, Candidate> candidates = new HashMap<>();
    private final Deque<Path> queue = new ArrayDeque<>();
    private final Map<Path, Failure> failures = new HashMap<>();

    /** Guards the Drive folder id. */
    private final Object folderLock = new Object();
    private @Nullable String folderId;
    private boolean folderVerified;

    private record Candidate(long size, long modified, Instant since) {
    }

    private record Failure(int attempts, Instant retryAt) {
    }

    public GoogleDriveBackupHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(GoogleDriveBackupConfiguration.class);
        pattern = new FilePattern(config.filePattern);
        sourceFolder = getSourceFolder();
        folderPath = BackupFiles.folderPath(config.targetFolder);

        if (!Files.isDirectory(sourceFolder)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error.source-folder-missing [\"" + sourceFolder + "\"]");
            return;
        }

        // Reuse the folder created earlier, unless the configured path was changed.
        synchronized (folderLock) {
            String storedPath = getThing().getProperties().get(PROPERTY_FOLDER_PATH);
            folderId = String.join("/", folderPath).equals(storedPath)
                    ? getThing().getProperties().get(PROPERTY_FOLDER_ID)
                    : null;
            folderVerified = false;
        }

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
        } else if (bridge.getStatus() == ThingStatus.ONLINE) {
            start();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            if (Files.isDirectory(sourceFolder)) {
                start();
            }
        } else {
            stop();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void dispose() {
        stop();
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_UPLOAD.equals(channelUID.getId()) && command == OnOffType.ON) {
            uploadLatest();
            updateState(CHANNEL_UPLOAD, OnOffType.OFF);
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(GoogleDriveBackupActions.class);
    }

    /**
     * Queues the newest local backup for upload, also if it was present before.
     *
     * @return false if there is no local backup
     */
    public boolean uploadLatest() {
        try {
            Path newest = BackupFiles.newest(sourceFolder, pattern);
            if (newest == null) {
                logger.info("No backup matching '{}' in {}", config.filePattern, sourceFolder);
                return false;
            }
            enqueueManually(newest);
            return true;
        } catch (IOException e) {
            logger.warn("Cannot read backup folder {}: {}", sourceFolder, e.getMessage());
            return false;
        }
    }

    /**
     * Queues a backup file from the source folder for upload.
     *
     * @return false if the file does not exist
     */
    public boolean uploadFile(String fileName) {
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            logger.warn("Invalid backup file name '{}'", fileName);
            return false;
        }
        Path file = sourceFolder.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            logger.warn("Backup file {} does not exist", file);
            return false;
        }
        enqueueManually(file);
        return true;
    }

    /**
     * Removes old backups from Google Drive according to the configured number to keep.
     */
    public void cleanup() {
        scheduler.execute(() -> {
            DriveApi api = getDriveApi();
            if (api == null) {
                return;
            }
            try {
                cleanup(api, ensureFolder(api));
            } catch (GoogleDriveException e) {
                logger.warn("Cleanup of Google Drive backups failed: {}", e.getMessage());
            }
        });
    }

    private void start() {
        synchronized (lock) {
            if (active) {
                return;
            }
            active = true;
        }
        updateStatus(ThingStatus.ONLINE);
        updateState(CHANNEL_STATUS, new StringType(STATUS_IDLE));
        scheduler.execute(this::startWatching);
    }

    private void stop() {
        active = false;
        ScheduledFuture<?> job = scanJob;
        if (job != null) {
            job.cancel(false);
        }
        scanJob = null;
        synchronized (lock) {
            known.clear();
            candidates.clear();
            queue.clear();
            failures.clear();
        }
    }

    private void startWatching() {
        List<Path> existing;
        try {
            existing = BackupFiles.list(sourceFolder, pattern);
        } catch (IOException e) {
            logger.warn("Cannot read backup folder {}: {}", sourceFolder, e.getMessage());
            existing = List.of();
        }
        synchronized (lock) {
            known.addAll(existing);
        }

        DriveApi api = getDriveApi();
        if (api != null) {
            try {
                String folder = ensureFolder(api);
                List<DriveFile> remote = api.listFiles(folder);
                updateState(CHANNEL_REMOTE_COUNT, new DecimalType(RetentionPolicy.backups(remote, pattern).size()));
                if (config.uploadExistingOnStart) {
                    for (Path file : existing) {
                        if (!isInDrive(file, remote)) {
                            enqueue(file);
                        }
                    }
                }
                cleanupLocal(remote);
            } catch (GoogleDriveException e) {
                logger.warn("Cannot read the Google Drive backup folder: {}", e.getMessage());
                updateState(CHANNEL_LAST_ERROR, new StringType(e.getMessage()));
            }
        }

        if (active) {
            scanJob = scheduler.scheduleWithFixedDelay(this::scan, SCAN_SECONDS, SCAN_SECONDS, TimeUnit.SECONDS);
            processQueue();
        }
    }

    /**
     * Looks for new files and queues those that are completely written.
     */
    private void scan() {
        if (!active) {
            return;
        }
        List<Path> files;
        try {
            files = BackupFiles.list(sourceFolder, pattern);
        } catch (IOException e) {
            logger.debug("Cannot read backup folder {}: {}", sourceFolder, e.getMessage());
            return;
        }

        Instant now = Instant.now();
        boolean waiting;
        synchronized (lock) {
            candidates.keySet().retainAll(files);
            for (Path file : files) {
                if (known.contains(file) || queue.contains(file)) {
                    continue;
                }
                long size;
                long modified;
                try {
                    size = Files.size(file);
                    modified = Files.getLastModifiedTime(file).toMillis();
                } catch (IOException e) {
                    continue;
                }
                Candidate candidate = candidates.get(file);
                if (candidate == null || candidate.size() != size || candidate.modified() != modified) {
                    candidates.put(file, new Candidate(size, modified, now));
                } else if (!candidate.since().plusSeconds(config.stableSeconds).isAfter(now)) {
                    candidates.remove(file);
                    known.add(file);
                    if (config.autoUpload) {
                        logger.debug("New backup {} is complete", file);
                        queue.add(file);
                    } else {
                        logger.debug("New backup {} found, automatic upload is disabled", file);
                    }
                }
            }
            failures.forEach((file, failure) -> {
                if (!failure.retryAt().isAfter(now) && !queue.contains(file)) {
                    queue.add(file);
                }
            });
            waiting = !candidates.isEmpty();
        }

        if (waiting && !uploading) {
            updateState(CHANNEL_STATUS, new StringType(STATUS_WAITING));
        }
        processQueue();
    }

    private void enqueueManually(Path file) {
        synchronized (lock) {
            candidates.remove(file);
            known.add(file);
            failures.remove(file);
        }
        enqueue(file);
        processQueue();
    }

    private void enqueue(Path file) {
        synchronized (lock) {
            if (!queue.contains(file)) {
                queue.add(file);
            }
        }
    }

    private void processQueue() {
        synchronized (lock) {
            if (uploading || queue.isEmpty() || !active) {
                return;
            }
            uploading = true;
        }
        scheduler.execute(() -> {
            try {
                Path next;
                while (active && (next = nextInQueue()) != null) {
                    upload(next);
                }
            } finally {
                uploading = false;
            }
        });
    }

    private @Nullable Path nextInQueue() {
        synchronized (lock) {
            return queue.poll();
        }
    }

    private void upload(Path file) {
        String name = BackupFiles.fileName(file);
        if (!Files.isRegularFile(file)) {
            synchronized (lock) {
                failures.remove(file);
            }
            return;
        }
        DriveApi api = getDriveApi();
        if (api == null) {
            fail(file, "Google Drive account is not available", true);
            return;
        }

        updateState(CHANNEL_STATUS, new StringType(STATUS_UPLOADING));
        updateState(CHANNEL_PROGRESS, new QuantityType<>(0, Units.PERCENT));
        triggerChannel(CHANNEL_EVENT, EVENT_STARTED);

        try {
            long size = Files.size(file);
            String md5 = BackupFiles.md5(file);
            String folder = ensureFolder(api);

            if (isInDrive(name, md5, api.listFiles(folder))) {
                logger.info("Backup {} is already in Google Drive, skipping upload", name);
            } else {
                logger.info("Uploading backup {} ({} bytes) to Google Drive", name, size);
                DriveFile uploaded;
                try {
                    uploaded = api.upload(file, name, mimeType(name), folder, this::progress);
                } catch (GoogleDriveException e) {
                    if (e.getHttpStatus() != 404 && e.getHttpStatus() != 410) {
                        throw e;
                    }
                    // The folder was deleted or the upload session expired: start once more.
                    logger.debug("Upload of {} restarted: {}", name, e.getMessage());
                    synchronized (folderLock) {
                        folderVerified = false;
                    }
                    uploaded = api.upload(file, name, mimeType(name), ensureFolder(api), this::progress);
                }
                String remoteMd5 = uploaded.md5Checksum;
                if (remoteMd5 != null && !md5.equalsIgnoreCase(remoteMd5)) {
                    throw new GoogleDriveException("Checksum of the uploaded file does not match");
                }
                logger.info("Backup {} uploaded to Google Drive", name);
            }

            if (config.deleteLocalAfterUpload) {
                Files.deleteIfExists(file);
                logger.info("Local backup {} deleted after upload", file);
            }
            synchronized (lock) {
                failures.remove(file);
            }

            updateState(CHANNEL_LAST_UPLOAD, new DateTimeType());
            updateState(CHANNEL_LAST_FILE, new StringType(name));
            updateState(CHANNEL_LAST_SIZE, new QuantityType<>(size, Units.BYTE));
            updateState(CHANNEL_PROGRESS, new QuantityType<>(100, Units.PERCENT));
            updateState(CHANNEL_LAST_ERROR, UnDefType.NULL);
            updateState(CHANNEL_STATUS, new StringType(STATUS_OK));
            triggerChannel(CHANNEL_EVENT, EVENT_SUCCESS);

            cleanup(api, folder);
        } catch (GoogleDriveAuthException e) {
            // The account bridge starts a new login; retry once the user has logged in again.
            fail(file, e.getMessage(), true);
        } catch (GoogleDriveException e) {
            fail(file, e.getMessage(), e.isRetryable());
        } catch (IOException e) {
            fail(file, "Cannot read " + file + ": " + e.getMessage(), true);
        }
    }

    private void fail(Path file, @Nullable String message, boolean retry) {
        String text = message != null ? message : "Unknown error";
        int attempts;
        synchronized (lock) {
            Failure previous = failures.get(file);
            attempts = (previous != null ? previous.attempts() : 0) + 1;
            if (retry && attempts < MAX_ATTEMPTS) {
                failures.put(file, new Failure(attempts, Instant.now().plusSeconds(RETRY_MINUTES * 60L)));
            } else {
                failures.remove(file);
            }
        }
        if (retry && attempts < MAX_ATTEMPTS) {
            logger.warn("Upload of {} failed ({}), retrying in {} minutes", file, text, RETRY_MINUTES);
        } else {
            logger.warn("Upload of {} failed: {}", file, text);
        }
        updateState(CHANNEL_LAST_ERROR, new StringType(text));
        updateState(CHANNEL_STATUS, new StringType(STATUS_ERROR));
        triggerChannel(CHANNEL_EVENT, EVENT_FAILED);
    }

    private void progress(long sent, long total) {
        int percent = total > 0 ? (int) (sent * 100 / total) : 100;
        updateState(CHANNEL_PROGRESS, new QuantityType<>(percent, Units.PERCENT));
    }

    private void cleanup(DriveApi api, String folder) {
        try {
            List<DriveFile> remote = api.listFiles(folder);
            // Local cleanup first, while all uploaded backups are still listed in Drive.
            cleanupLocal(remote);
            List<DriveFile> remove = RetentionPolicy.filesToRemove(remote, config.retentionCount, pattern);
            boolean trash = !GoogleDriveBackupConfiguration.RETENTION_DELETE.equals(config.retentionMode);
            int removed = 0;
            for (DriveFile old : remove) {
                String id = old.id;
                if (id == null) {
                    continue;
                }
                if (trash) {
                    api.trash(id);
                } else {
                    api.delete(id);
                }
                removed++;
                logger.info("Old backup {} {} in Google Drive", old.name, trash ? "moved to trash" : "deleted");
            }
            updateState(CHANNEL_REMOTE_COUNT,
                    new DecimalType(RetentionPolicy.backups(remote, pattern).size() - removed));
        } catch (GoogleDriveException e) {
            logger.warn("Cleanup of old backups in Google Drive failed: {}", e.getMessage());
        }
    }

    /**
     * Deletes old local backups beyond {@code localRetentionCount}. Only backups that are in Google Drive, or older
     * than the newest backup in Google Drive, are deleted; newer backups that are not uploaded yet are always kept.
     */
    private void cleanupLocal(List<DriveFile> remote) {
        if (config.localRetentionCount <= 0) {
            return;
        }
        try {
            List<Path> local = BackupFiles.list(sourceFolder, pattern);
            List<Path> remove = BackupFiles.localFilesToRemove(local, config.localRetentionCount,
                    file -> isInDrive(file, remote));
            for (Path file : remove) {
                Files.deleteIfExists(file);
                synchronized (lock) {
                    known.remove(file);
                    failures.remove(file);
                }
                logger.info("Old local backup {} deleted (keeping {} on the server)", file, config.localRetentionCount);
            }
        } catch (IOException e) {
            logger.warn("Cleanup of old local backups in {} failed: {}", sourceFolder, e.getMessage());
        }
    }

    /**
     * Returns the id of the target folder in Google Drive, creating the folders of the path if needed.
     */
    private String ensureFolder(DriveApi api) throws GoogleDriveException {
        synchronized (folderLock) {
            String id = folderId;
            if (id != null && folderVerified) {
                return id;
            }
            if (id != null) {
                DriveFile folder = api.getFile(id);
                if (folder != null && !folder.trashed && DriveApi.FOLDER_MIME_TYPE.equals(folder.mimeType)) {
                    folderVerified = true;
                    return id;
                }
                logger.info("Google Drive backup folder was deleted, creating it again");
            }

            String parent = DriveApi.ROOT_FOLDER;
            for (String segment : folderPath) {
                DriveFile folder = api.findFolder(segment, parent);
                if (folder == null) {
                    folder = api.createFolder(segment, parent);
                    logger.info("Created folder '{}' in Google Drive", segment);
                }
                String folderId = folder.id;
                if (folderId == null) {
                    throw new GoogleDriveException("Folder id missing in response");
                }
                parent = folderId;
            }
            folderId = parent;
            folderVerified = true;
            updateProperty(PROPERTY_FOLDER_ID, parent);
            updateProperty(PROPERTY_FOLDER_PATH, String.join("/", folderPath));
            return parent;
        }
    }

    private @Nullable DriveApi getDriveApi() {
        Bridge bridge = getBridge();
        ThingHandler handler = bridge != null ? bridge.getHandler() : null;
        return handler instanceof GoogleDriveAccountHandler account ? account.getDriveApi() : null;
    }

    private static boolean isInDrive(Path file, List<DriveFile> remote) {
        String name = BackupFiles.fileName(file);
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return false;
        }
        return remote.stream().anyMatch(f -> name.equals(f.name) && f.getSize() == size);
    }

    private static boolean isInDrive(String name, String md5, List<DriveFile> remote) {
        return remote.stream().anyMatch(f -> name.equals(f.name) && md5.equalsIgnoreCase(f.md5Checksum));
    }

    private static String mimeType(String name) {
        return name.toLowerCase().endsWith(".zip") ? "application/zip" : "application/octet-stream";
    }

    /**
     * Returns the configured source folder or the default openHAB backup folder (userdata/backup).
     */
    Path getSourceFolder() {
        if (!config.sourceFolder.isBlank()) {
            return Path.of(config.sourceFolder.trim());
        }
        return Path.of(OpenHAB.getUserDataFolder(), "backup");
    }
}
