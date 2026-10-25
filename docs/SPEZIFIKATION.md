# Google Drive Binding für openHAB – Spezifikation

| | |
|---|---|
| Status | Entwurf v0.1 – zur Prüfung |
| Datum | 2026-10-25 |
| Ziel-Version | openHAB 5.2.x (Testsystem: offizielles Docker-Image, openHAB 5.2.1, `192.168.1.201:7070`) |
| Add-on-ID | `googledrive` |
| Paket | `org.openhab.binding.googledrive` |
| Lizenz | EPL-2.0 (Pflicht für openhab-addons) |

---

## 1. Ziel

Ein Binding, das
1. **(v1, Kernfunktion)** die von openHAB erzeugten Backups aus `userdata/backup` automatisch in einen Google-Drive-Ordner hochlädt und dort alte Backups aufräumt,
2. **(v1.2)** einen **Bilderrahmen** bereitstellt, der Bilder aus einem oder mehreren freigegebenen Drive-Ordnern anzeigt.

Alles wird über die openHAB-UI eingerichtet – keine Shell, kein rclone, kein sudo.

### Nicht-Ziele (bewusst ausgeschlossen)
- Das Binding **erstellt keine Backups**. Das übernimmt weiterhin `openhab-cli backup` (z. B. per Rule).
- Kein allgemeiner Drive-Sync (keine Zwei-Wege-Synchronisation, kein Download von Backups).
- Kein Wiederherstellen (Restore) aus dem Binding heraus.
- Keine Google-Photos-Anbindung (Library API seit 2025 auf app-eigene Inhalte beschränkt).
- Kein Zugriff auf private Bilderordner per Login in v1 (siehe §7.6).

---

## 2. Anforderungen für offizielle Aufnahme & Marketplace

Das Binding wird von Anfang an so gebaut, dass es 1:1 als Pull Request in
[openhab/openhab-addons](https://github.com/openhab/openhab-addons) eingereicht werden kann.

| Bereich | Umsetzung |
|---|---|
| Struktur | Maven-Modul `bundles/org.openhab.binding.googledrive`, Parent `org.openhab.addons.reactor.bundles` 5.2.x |
| Metadaten | `src/main/resources/OH-INF/addon/addon.xml`, Thing-Typen in `OH-INF/thing/*.xml`, Konfiguration in `OH-INF/config/*.xml` |
| i18n | `OH-INF/i18n/googledrive.properties` (Englisch, Basis) + `googledrive_de.properties` |
| Code-Stil | EPL-2.0-Header, `@NonNullByDefault`, Spotless-Formatierung, keine Warnungen aus SAT (Checkstyle, PMD, SpotBugs) |
| Abhängigkeiten | **Keine Fremdbibliotheken.** Nur was openHAB Core mitbringt: Jetty `HttpClient` (`HttpClientFactory`), Gson, `OAuthFactory`/`OAuthClientService` |
| Logging | SLF4J, keine Tokens/Keys/Secrets in Logs |
| Threads | Nur `ThingHandler.scheduler` bzw. `ThreadPoolManager`, keine eigenen Threads |
| Doku | `README.md` nach openHAB-Vorlage (Supported Things, Discovery, Configuration, Channels, Full Example) inkl. Anleitung Google Cloud Console |
| Tests | JUnit 5 + Mockito für Logik ohne Netzwerk (Link-Parsing, Retention, API-Antworten) |
| Build | `mvn clean install` inkl. SAT grün; `mvn spotless:check` grün |
| Beitrag | DCO-Sign-off in allen Commits, Eintrag in `CODEOWNERS`, Eintrag in `bom/openhab-addons/pom.xml` |
| Marketplace (vorab) | Community-Beitrag in Kategorie *Add-on Marketplace* mit JAR-Link und Versionsbereich `[5.2.0;6.0.0)` |

---

## 3. Architektur

```
googledrive:account   (Bridge)   – Google-Login (OAuth2 Gerätecode), Token-Refresh, Speicher-Kontingent
 └── googledrive:backup (Thing)  – Ordner überwachen, hochladen, aufräumen

googledrive:photoframe (Thing, eigenständig, ohne Bridge)
                                  – Freigabe-Links + API-Key, Bilder-Servlet
```

Interne Klassen (Vorschlag):

| Klasse | Aufgabe |
|---|---|
| `GoogleDriveHandlerFactory` | erzeugt Handler, registriert OAuth-Clients und Servlet |
| `GoogleDriveAccountHandler` | Bridge: Login-Ablauf, Tokens, Quota, stellt `DriveApi` bereit |
| `GoogleDriveBackupHandler` | Backup-Thing |
| `GoogleDrivePhotoFrameHandler` | Bilderrahmen-Thing |
| `DriveApi` | schlanker REST-Client (Liste, Ordner anlegen, Upload, Löschen, Quota) |
| `ResumableUpload` | Upload in 8-MiB-Stücken mit Wiederaufnahme |
| `RetentionPolicy` | entscheidet, welche Remote-Backups gelöscht werden (rein, gut testbar) |
| `FolderLinkParser` | extrahiert Ordner-ID + Resource-Key aus Freigabe-Links |
| `PhotoFrameServlet` | liefert aktuelles Bild per HTTP |
| `dto/*` | Gson-DTOs für Drive-API-Antworten |

---

## 4. Bridge `googledrive:account`

### 4.1 Login (OAuth 2.0 „Device Authorization Grant“)
Gewählt, weil openHABian meist ohne HTTPS und ohne Portfreigabe läuft – Google akzeptiert Redirect-Logins nur über `https` oder `localhost`.

Ablauf:
1. Nutzer legt in der Google Cloud Console ein Projekt an, aktiviert die **Google Drive API** und erstellt eine **OAuth-Client-ID vom Typ „Fernseher und Geräte mit begrenzter Eingabe“**. Client-ID + Secret kommen in die Bridge-Konfiguration.
2. Bridge fordert bei `https://oauth2.googleapis.com/device/code` einen Code an (Scope `https://www.googleapis.com/auth/drive.file`).
3. Bridge geht auf `OFFLINE / CONFIGURATION_PENDING` mit Statustext:
   *„Öffne https://www.google.com/device und gib den Code **ABCD-EFGH** ein (gültig 30 Min.)“*
   Der Code steht zusätzlich im Log (INFO) und im Channel `loginCode`.
4. Bridge fragt im von Google vorgegebenen Intervall `https://oauth2.googleapis.com/token` ab, bis der Nutzer zugestimmt hat.
5. Tokens werden über den openHAB-`OAuthClientService` gespeichert und automatisch erneuert. Den Gerätecode-Ablauf selbst (Schritte 2–4) setzt das Binding in `DeviceCodeLogin` um: Die Core-Variante (`getDeviceCodeResponse()`) schickt beim Token-Abruf kein `client_secret` mit, Google verlangt es aber. Die fertigen Tokens werden per `importAccessTokenResponse()` an den Core übergeben. und automatisch erneuert → Bridge `ONLINE`.
6. Läuft der Code ab, wird automatisch ein neuer angefordert.

**Warum Scope `drive.file`:** Das Binding sieht nur Dateien, die es selbst angelegt hat. Das reicht für Backups, ist das sicherste Recht und das einzige brauchbare Drive-Recht, das Google für den Gerätecode-Login erlaubt.

> ⚠️ **Wichtig für die Doku:** Das Google-Cloud-Projekt muss unter *OAuth-Zustimmungsbildschirm → Veröffentlichungsstatus* auf **„In Produktion“** stehen. Im Status „Test“ laufen Refresh-Tokens nach 7 Tagen ab. Eine Google-Verifizierung ist für den Eigengebrauch nicht nötig (es erscheint nur ein Warnhinweis beim Login).

### 4.2 Konfiguration
| Parameter | Typ | Pflicht | Standard | Beschreibung |
|---|---|---|---|---|
| `clientId` | Text | ja | – | OAuth-Client-ID |
| `clientSecret` | Text (password) | ja | – | OAuth-Client-Secret |
| `quotaRefresh` | Zahl (Min.) | nein | 60 | Intervall für Abfrage des Speicherplatzes, 0 = aus |

### 4.3 Channels
| Channel | Item-Typ | Beschreibung |
|---|---|---|
| `quotaUsed` | `Number:DataAmount` | belegter Speicher im Google-Konto |
| `quotaTotal` | `Number:DataAmount` | Gesamtspeicher (leer bei unbegrenzt) |
| `loginCode` | `String` | aktueller Gerätecode, leer wenn eingeloggt |

### 4.4 Thing-Aktion
- `logout()` – Tokens löschen, neuen Login starten (Rule-Action + Hinweis in der Doku).

---

## 5. Thing `googledrive:backup`

### 5.1 Verhalten
1. Beim Start: Zielordner in Drive suchen (nur vom Binding angelegte sichtbar), sonst anlegen. Ordner-ID wird in den Thing-Properties gemerkt.
2. **Ordner überwachen:** `WatchService` von openHAB Core auf `sourceFolder`. Neue Datei, die zu `filePattern` passt → Kandidat.
3. **Fertig-geschrieben-Prüfung:** Upload erst, wenn Dateigröße `stableSeconds` lang unverändert ist (das Backup-Skript schreibt das ZIP schrittweise).
4. **Duplikat-Prüfung:** Ist eine Datei gleichen Namens und gleicher MD5-Prüfsumme (`md5Checksum` von Drive) bereits im Zielordner → überspringen.
5. **Upload** per Resumable Upload in 8-MiB-Stücken; bei Abbruch Wiederaufnahme ab letztem bestätigten Byte (max. 5 Versuche mit exponentiellem Backoff).
6. **Aufräumen** nach erfolgreichem Upload gemäß `retentionCount` (älteste zuerst, nach `createdTime`). Es werden nur Dateien betrachtet, die zu `filePattern` passen.
7. Optional lokale Datei nach erfolgreichem Upload löschen (`deleteLocalAfterUpload`).
8. Beim Start optional alle lokal vorhandenen, noch nicht hochgeladenen Backups nachholen (`uploadExistingOnStart`).
9. Es läuft immer nur **ein** Upload gleichzeitig; weitere Dateien werden in einer Warteschlange abgearbeitet.

### 5.2 Konfiguration
| Parameter | Typ | Standard | Beschreibung |
|---|---|---|---|
| `sourceFolder` | Text | `${OPENHAB_USERDATA}/backup` | Lokaler Backup-Ordner |
| `filePattern` | Text | `*.zip` | Glob-Muster für Backup-Dateien |
| `targetFolder` | Text | `openHAB Backups` | Ordnername in Drive (wird angelegt) |
| `retentionCount` | Zahl | 10 | Anzahl Backups in Drive behalten, 0 = unbegrenzt – frei vom Nutzer einstellbar |
| `retentionMode` | Auswahl `TRASH` / `DELETE` | *offen* | Alte Backups in den Papierkorb oder endgültig löschen (Standardwert noch festzulegen) |
| `autoUpload` | Schalter | an | Neue Dateien automatisch hochladen; aus = nur manuell |
| `stableSeconds` | Zahl | 30 | Wartezeit ohne Größenänderung vor Upload |
| `uploadExistingOnStart` | Schalter | aus | Fehlende lokale Backups beim Start nachladen |
| `deleteLocalAfterUpload` | Schalter | aus | Lokale Datei erst nach bestätigtem Upload (MD5 stimmt überein) löschen |

### 5.3 Channels
| Channel | Item-Typ | Beschreibung |
|---|---|---|
| `upload` | `Switch` | `ON` = neuestes lokales Backup jetzt hochladen (springt danach auf `OFF`) |
| `status` | `String` | `IDLE`, `WAITING`, `UPLOADING`, `OK`, `ERROR` |
| `progress` | `Number:Dimensionless` | Fortschritt des laufenden Uploads in % |
| `lastUpload` | `DateTime` | Zeitpunkt des letzten erfolgreichen Uploads |
| `lastFile` | `String` | Name der zuletzt hochgeladenen Datei |
| `lastSize` | `Number:DataAmount` | Größe der zuletzt hochgeladenen Datei |
| `remoteCount` | `Number` | Anzahl Backups im Drive-Ordner |
| `lastError` | `String` | letzte Fehlermeldung (leer bei Erfolg) |
| `event` | Trigger | `UPLOAD_STARTED`, `UPLOAD_SUCCESS`, `UPLOAD_FAILED` – für Rules/Benachrichtigungen |

### 5.4 Thing-Aktionen (für Rules)
- `uploadLatest()` – neuestes lokales Backup hochladen
- `uploadFile(String fileName)` – bestimmte Datei hochladen
- `cleanup()` – Aufräumen jetzt ausführen

### 5.5 Beispiel-Rule (für README)
```java
rule "Wöchentliches Backup"
when Time cron "0 0 3 ? * SUN"
then
    // openHABian / Paketinstallation:
    executeCommandLine(Duration.ofMinutes(5), "sudo", "openhab-cli", "backup")
    // Docker (offizielles Image, im Container kein sudo/openhab-cli):
    // executeCommandLine(Duration.ofMinutes(5), "/openhab/runtime/bin/backup")
    // Upload passiert automatisch, sobald die ZIP-Datei fertig ist
end

rule "Backup-Fehler melden"
when Channel "googledrive:backup:konto:backup:event" triggered UPLOAD_FAILED
then
    // z. B. Pushover/Telegram
end
```

---

## 6. Thing `googledrive:photoframe` (v1.2)

### 6.1 Verhalten
- Liest die Bilder aus einem oder mehreren **öffentlich freigegebenen** Ordnern („Jeder mit dem Link“) mit einem **API-Key** – ohne Login, ohne Bridge.
- Mehrere Ordner: mehrere Links ins selbe Feld (Mehrfachwert); die Bilder werden zu einer Liste zusammengeführt.
- Unterstützte Link-Formen:
  `https://drive.google.com/drive/folders/<ID>?usp=sharing`,
  `https://drive.google.com/drive/u/0/folders/<ID>`,
  `…?resourcekey=<KEY>` (Resource-Key wird als Header `X-Goog-Drive-Resource-Keys` mitgeschickt),
  sowie die reine Ordner-ID.
- Berücksichtigt nur Bilddateien (`mimeType contains 'image/'`), optional rekursiv in Unterordnern.
- Bilder werden in der gewünschten Größe über `thumbnailLink` (`=s<maxSize>`) geladen, sonst Original per `alt=media`. Lokaler Cache in `userdata/cache/googledrive/` (Größe begrenzt).
- **Servlet** `http://<openhab>:8080/googledrive/photoframe/<thingId>/current.jpg` liefert das aktuelle Bild. Damit wird der Bilderrahmen in MainUI per `oh-image`-Widget (Vollbild-Seite, Auto-Refresh) gebaut, ohne große Bilder über den Event-Bus zu schicken.

### 6.2 Konfiguration
| Parameter | Typ | Standard | Beschreibung |
|---|---|---|---|
| `apiKey` | Text (password) | – | Google-API-Key (Drive API aktiviert) |
| `folderLinks` | Text, mehrfach | – | Ein oder mehrere Freigabe-Links |
| `interval` | Zahl (s) | 30 | Bildwechsel-Intervall |
| `order` | Auswahl `RANDOM` / `NAME` / `DATE` | `RANDOM` | Reihenfolge |
| `maxSize` | Zahl (px) | 1920 | längste Bildkante |
| `recursive` | Schalter | aus | Unterordner einbeziehen |
| `listRefresh` | Zahl (Min.) | 60 | Ordnerinhalt neu einlesen |
| `publishImage` | Schalter | aus | Bild zusätzlich in Channel `image` schreiben (kleinere Auflösung empfohlen) |

### 6.3 Channels
| Channel | Item-Typ | Beschreibung |
|---|---|---|
| `control` | `Player` | `PLAY`/`PAUSE`/`NEXT`/`PREVIOUS` |
| `imageUrl` | `String` | URL des Servlets inkl. Zeitstempel (für Widgets) |
| `image` | `Image` | aktuelles Bild (nur wenn `publishImage`) |
| `fileName` | `String` | Dateiname |
| `dateTaken` | `DateTime` | Aufnahmedatum (aus Drive-Bildmetadaten, falls vorhanden) |
| `imageCount` | `Number` | Anzahl Bilder insgesamt |

---

## 7. Querschnitt

### 7.1 Fehlerbehandlung
| Fall | Reaktion |
|---|---|
| 401 Unauthorized | Token erneuern; scheitert das → Bridge `OFFLINE`, neuer Login nötig |
| 403 `rateLimitExceeded` / 429 | Exponentieller Backoff (1 s … 64 s) |
| 403 `storageQuotaExceeded` | `status=ERROR`, `lastError` gesetzt, Event `UPLOAD_FAILED`, kein Retry |
| 404 Zielordner | Ordner neu anlegen, Upload wiederholen |
| Netzwerk weg | Thing `OFFLINE / COMMUNICATION_ERROR`, Retry alle 5 Min.; Warteschlange bleibt erhalten |
| Quellordner fehlt | Thing `OFFLINE / CONFIGURATION_ERROR` |
| Freigabe-Link ungültig/nicht öffentlich | Bilderrahmen `OFFLINE / CONFIGURATION_ERROR` mit Hinweis welcher Link |

### 7.2 Sicherheit
- `clientSecret` und `apiKey` als `context="password"`.
- Tokens nur im openHAB-OAuth-Speicher, nie im Log.
- Recht `drive.file` = minimaler Zugriff.
- Servlet liefert nur Bilder aus konfigurierten Ordnern, keine beliebigen IDs.

### 7.3 Ressourcen
- Upload streamt aus der Datei (kein Laden ins RAM) – wichtig für Raspberry Pi.
- Ein Upload gleichzeitig; Bilder-Cache begrenzt (Standard 200 MB).

### 7.4 Verwendete Google-Endpunkte
| Zweck | Endpunkt |
|---|---|
| Gerätecode | `POST https://oauth2.googleapis.com/device/code` |
| Token | `POST https://oauth2.googleapis.com/token` |
| Ordner suchen / Dateien listen | `GET https://www.googleapis.com/drive/v3/files?q=…` |
| Ordner anlegen | `POST https://www.googleapis.com/drive/v3/files` (`mimeType=application/vnd.google-apps.folder`) |
| Upload | `POST https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable` + `PUT` Stücke |
| Papierkorb | `PATCH …/files/{id}` mit `{"trashed":true}` |
| Löschen | `DELETE …/files/{id}` |
| Quota | `GET …/about?fields=storageQuota` |
| Bild laden | `thumbnailLink` bzw. `GET …/files/{id}?alt=media&key=…` |

### 7.5 Tests
- `FolderLinkParserTest` – alle Link-Formen, ungültige Links
- `RetentionPolicyTest` – 0 = unbegrenzt, Sortierung, nur passende Dateien
- `DriveApiTest` – Parsen von Beispielantworten (JSON-Fixtures), Fehlercodes
- `ResumableUploadTest` – Stückgrößen, Wiederaufnahme nach `308 Resume Incomplete`
- Manuell: offizielles Docker-Image openHAB 5.2.1 (`192.168.1.201:7070`), echtes Google-Konto, Backup ~50–300 MB

### 7.5a Hinweise Docker
- Das Binding selbst verhält sich gleich – es nutzt nur `${OPENHAB_USERDATA}` (im Container `/openhab/userdata`) und ausgehendes HTTPS.
- Backups landen in `/openhab/userdata/backup`. Liegt `userdata` auf einem Volume, bleiben sie auch nach Container-Updates erhalten.
- Im Container gibt es kein `sudo` und meist kein `openhab-cli`; das Backup wird über `/openhab/runtime/bin/backup` erzeugt (vor M3 im Container prüfen).
- Installation der Test-JAR: in den `addons`-Ordner des Containers (`/openhab/addons`) kopieren.

### 7.6 Später (nicht v1)
- Private Bilderordner per Login (bräuchte Scope `drive.readonly` + Redirect-Login über HTTPS/myopenHAB).
- Discovery (nicht sinnvoll – Cloud-Dienst; Bridge wird manuell angelegt).

---

## 8. Meilensteine

| # | Inhalt | Ergebnis |
|---|---|---|
| M1 | Projektgerüst, `addon.xml`, leere Handler, Build + SAT grün | installierbare, leere JAR |
| M2 | Bridge mit Gerätecode-Login, Token-Refresh, Quota | Bridge geht `ONLINE` |
| M3 | Backup-Thing: Watch, Upload, Retention, Channels, Actions | **Backup-Version nutzbar** |
| M4 | README, i18n DE/EN, Tests | **Marketplace-Release v1.0** |
| M5 | Bilderrahmen-Thing + Servlet + Widget-Beispiel | v1.2 |
| M6 | PR an openhab-addons | offizielles Review |

---

## 9. Entscheidungen & offene Punkte

| # | Thema | Stand |
|---|---|---|
| 1 | Alte Backups: Papierkorb oder endgültig löschen (Standardwert) | **offen** – beide Optionen werden gebaut, nur der Standard fehlt |
| 2 | Anzahl behaltener Backups | **entschieden:** frei einstellbar (`retentionCount`) |
| 3 | Lokale Backups nach Upload löschen | **entschieden:** Option kommt, Standard aus |
| 4 | Bilderrahmen | **entschieden:** v1.2 |
| 5 | Code-Owner / GitHub-Konto | **offen** – GitHub-Konto wird noch angelegt (nötig erst für M6) |
| 6 | Gerätecode-Login im openHAB Core | **geklärt (M2):** Core sendet kein `client_secret` beim Token-Abruf → Login-Ablauf im Binding, Speicherung/Erneuerung weiter über den Core |
