# Google Drive Binding

This binding uploads openHAB backups to [Google Drive](https://drive.google.com) and removes old backups there.

openHAB already creates backups in `userdata/backup` (for example with a rule that runs the backup script).
The binding watches that folder and uploads every new backup file to a folder in your Google Drive.

The binding requires openHAB 5.0 or later.

> **Status:** under development. The login works, the upload (M3) is not implemented yet.

## Google Cloud Setup

The binding needs your own OAuth client from Google. This is a one-time setup of about 15 minutes.

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
