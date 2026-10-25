# Google Drive Binding

This binding uploads openHAB backups to [Google Drive](https://drive.google.com) and removes old backups there.

openHAB already creates backups in `userdata/backup` (for example with a rule that runs the backup script).
The binding watches that folder and uploads every new backup file to a folder in your Google Drive.

The binding requires openHAB 5.0 or later.

> **Status:** under development (0.x). Login and backup upload work; the photo frame follows in 1.2.

## Google Cloud Setup

The binding needs your own OAuth client from Google. This is a one-time setup of about 15 minutes.

🇩🇪 Eine ausführliche Schritt-für-Schritt-Anleitung auf Deutsch gibt es hier: [docs/ANLEITUNG-GOOGLE.md](docs/ANLEITUNG-GOOGLE.md).

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create a new project (for example "openHAB").
1. Go to **APIs & Services → Library**, search for **Google Drive API** and enable it.
1. Go to **APIs & Services → OAuth consent screen** (Google Auth Platform):
   - User type **External**, enter an app name and your e-mail address.
   - Add the scope `.../auth/drive.file`.
   - Set the publishing status to **In production**.
     In "Testing" status Google invalidates the login after 7 days.
     A verification by Google is not needed for personal use, the login page just shows a warning.
1. Go to **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **TVs and Limited Input devices**.
   - Copy the **Client ID** and the **Client secret**.

The binding only uses the scope `drive.file`: it can only see and change files it created itself, nothing else in your Drive.

## Login

1. Add the `account` bridge and enter the client ID and the client secret.
1. The bridge goes to `OFFLINE (CONFIGURATION_PENDING)` and its status shows a message like
   *"Open https://www.google.com/device and enter the code ABCD-EFGH"*.
   The code is also written to the openHAB log and to the `loginCode` channel.
1. Open the address on any device, sign in with your Google account, enter the code and allow access.
1. Within a few seconds the bridge goes `ONLINE`.

The login is stored by openHAB and refreshed automatically.
If the code expires before you use it, a new one is shown.
To log in with another account, run the `logout` action of the bridge (or delete and re-add the bridge).

## Supported Things

| Thing      | Type   | Description                                                   |
|------------|--------|---------------------------------------------------------------|
| `account`  | Bridge | Connection to a Google account, login with a device code     |
| `backup`   | Thing  | Watches the local backup folder and uploads new backups       |

## Discovery

Discovery is not supported. Add the `account` bridge and the `backup` thing manually.

## Thing Configuration

### `account` Bridge Configuration

| Name           | Type    | Description                                                              | Default | Required | Advanced |
|----------------|---------|--------------------------------------------------------------------------|---------|----------|----------|
| `clientId`     | text    | OAuth client ID of type "TVs and Limited Input devices"                  | N/A     | yes      | no       |
| `clientSecret` | text    | OAuth client secret                                                      | N/A     | yes      | no       |
| `quotaRefresh` | integer | Interval in minutes for reading the storage quota, 0 disables it         | 60      | no       | yes      |

### `backup` Thing Configuration

| Name                     | Type    | Description                                                        | Default           | Required | Advanced |
|--------------------------|---------|--------------------------------------------------------------------|-------------------|----------|----------|
| `sourceFolder`           | text    | Local backup folder, empty means `userdata/backup`                 | (empty)           | no       | no       |
| `filePattern`            | text    | Glob pattern of the backup files                                   | `*.zip`           | no       | no       |
| `targetFolder`           | text    | Folder name in Google Drive, created if missing                    | `openHAB Backups` | no       | no       |
| `retentionCount`         | integer | Number of backups kept in Google Drive, 0 keeps all                | 10                | no       | no       |
| `retentionMode`          | text    | `TRASH` (move to trash) or `DELETE` (delete permanently)           | `TRASH`           | no       | no       |
| `autoUpload`             | boolean | Upload new backups automatically                                   | true              | no       | no       |
| `stableSeconds`          | integer | Seconds the file size must stay unchanged before upload            | 30                | no       | yes      |
| `uploadExistingOnStart`  | boolean | On start, upload local backups that are not yet in Google Drive    | false             | no       | yes      |
| `deleteLocalAfterUpload` | boolean | Delete the local file after the upload has been verified           | false             | no       | yes      |

### How the backup upload works

- The source folder is checked every 10 seconds.
  A new file is uploaded when its size has not changed for `stableSeconds`, so half-written archives are never uploaded.
- Backups that already exist when the thing starts are **not** uploaded, unless `uploadExistingOnStart` is enabled.
  The `upload` channel (or the `uploadLatest` action) uploads the newest existing backup on demand.
- Files are sent in 8 MB chunks. After a network or server error the upload continues where it stopped.
  A failed upload is retried twice, 5 minutes apart.
- A file with the same name and checksum that is already in Google Drive is not uploaded again.
- After each upload, backups beyond `retentionCount` are moved to the trash (or deleted, see `retentionMode`).
  Only files matching `filePattern` are counted and removed; other files in the folder are never touched.

### The Google Drive folder

The binding can only see files and folders it created itself (scope `drive.file`).
It therefore creates the target folder itself, by default `openHAB Backups` in "My Drive".
A path such as `openHAB/Backups` creates nested folders.

The folder ID is stored in the thing properties (`folderId`), so you can rename or move the folder in Google Drive and uploads keep going there.
If the folder is deleted, it is created again.
A folder you create yourself in Google Drive is not visible to the binding; move the binding's folder instead.

## Channels

### `account` Channels

| Channel      | Type               | Read/Write | Description                                        |
|--------------|--------------------|------------|----------------------------------------------------|
| `quotaUsed`  | Number:DataAmount  | R          | Storage used in the Google account                 |
| `quotaTotal` | Number:DataAmount  | R          | Total storage of the Google account                |
| `loginCode`  | String             | R          | Code to enter on google.com/device                 |

### `backup` Channels

| Channel       | Type                  | Read/Write | Description                                          |
|---------------|-----------------------|------------|------------------------------------------------------|
| `upload`      | Switch                | W          | ON uploads the newest local backup now               |
| `status`      | String                | R          | `IDLE`, `WAITING`, `UPLOADING`, `OK`, `ERROR`        |
| `progress`    | Number:Dimensionless  | R          | Progress of the running upload                       |
| `lastUpload`  | DateTime              | R          | Time of the last successful upload                   |
| `lastFile`    | String                | R          | Name of the last uploaded file                       |
| `lastSize`    | Number:DataAmount     | R          | Size of the last uploaded file                       |
| `remoteCount` | Number                | R          | Number of backups in the Google Drive folder         |
| `lastError`   | String                | R          | Last error message                                   |
| `event`       | Trigger               | -          | `UPLOAD_STARTED`, `UPLOAD_SUCCESS`, `UPLOAD_FAILED`  |

## Actions

| Thing     | Action                  | Description                                                     |
|-----------|-------------------------|-----------------------------------------------------------------|
| `account` | `logout()`              | Removes the stored login and starts a new login                 |
| `backup`  | `uploadLatest()`        | Uploads the newest local backup, returns `true` if one was found |
| `backup`  | `uploadFile(fileName)`  | Uploads a file from the source folder                           |
| `backup`  | `cleanup()`             | Removes old backups according to `retentionCount`               |

```java
val drive = getActions("googledrive", "googledrive:backup:home:backups")
drive.uploadLatest()
```

## Full Example

### Thing Configuration

```java
Bridge googledrive:account:home "Google Drive" [ clientId="xxx.apps.googleusercontent.com", clientSecret="xxx" ] {
    Thing backup backups "openHAB Backups" [ targetFolder="openHAB Backups", retentionCount=10 ]
}
```

### Item Configuration

```java
String   GDrive_Backup_Status      "Backup Status [%s]"        { channel="googledrive:backup:home:backups:status" }
DateTime GDrive_Backup_LastUpload  "Last Upload [%1$tF %1$tR]" { channel="googledrive:backup:home:backups:lastUpload" }
Switch   GDrive_Backup_Upload      "Upload Now"                { channel="googledrive:backup:home:backups:upload" }
```

### Rule Configuration

```java
rule "Weekly openHAB backup"
when
    Time cron "0 0 3 ? * SUN"
then
    // Docker image; for openHABian / package installs use: "sudo", "openhab-cli", "backup"
    executeCommandLine(Duration.ofMinutes(10), "/openhab/runtime/bin/backup")
    // The binding uploads the new archive automatically once it is complete
end

rule "Notify when a backup upload failed"
when
    Channel "googledrive:backup:home:backups:event" triggered UPLOAD_FAILED
then
    logWarn("backup", "Google Drive backup upload failed")
end
```
