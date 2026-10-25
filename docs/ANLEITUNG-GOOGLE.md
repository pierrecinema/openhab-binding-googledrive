# Google-Einrichtung für das Google Drive Binding

Die Einrichtung dauert etwa 15 Minuten und ist nur einmal nötig.

> **Hinweis:** Google ändert die Bezeichnungen in der Console gelegentlich. Wenn ein Menüpunkt etwas anders heißt, such nach dem englischen Begriff in Klammern.

---

## Teil 1: Projekt anlegen

1. Öffne **https://console.cloud.google.com/**.
2. Melde dich mit deinem Google-Konto an. Beim ersten Mal musst du den Nutzungsbedingungen zustimmen.
3. Klicke oben links neben dem Google-Cloud-Logo auf die **Projektauswahl**. Dort steht „Projekt auswählen“ oder ein bestehender Projektname.
4. Klicke auf **„Neues Projekt“** (*New Project*).
5. Gib als Projektname `openHAB` ein. Den Speicherort lässt du auf „Keine Organisation“.
6. Klicke auf **„Erstellen“** (*Create*).
7. Wähle danach über die Projektauswahl oben das neue Projekt **openHAB** aus. Alle weiteren Schritte müssen in diesem Projekt passieren.

## Teil 2: Google Drive API aktivieren

8. Öffne oben links das Menü **☰** und dann **„APIs und Dienste“ → „Bibliothek“** (*APIs & Services → Library*).
9. Suche nach **`Google Drive API`** und klicke auf das Ergebnis.
10. Klicke auf **„Aktivieren“** (*Enable*).

## Teil 3: Zustimmungsbildschirm einrichten

11. Öffne **☰ → „APIs und Dienste“ → „OAuth-Zustimmungsbildschirm“** (*OAuth consent screen*). Es öffnet sich die „Google Auth Platform“.
12. Klicke auf **„Jetzt starten“** (*Get started*) und fülle die Schritte aus:
    - **App-Informationen:** App-Name `openHAB`, als Support-E-Mail deine E-Mail-Adresse.
    - **Zielgruppe:** **„Extern“** (*External*).
    - **Kontaktdaten:** deine E-Mail-Adresse.
    - **Abschließen:** den Richtlinien zustimmen, dann **„Erstellen“**.
13. Klicke links auf **„Datenzugriff“** (*Data Access*), dann auf **„Bereiche hinzufügen oder entfernen“** (*Add or remove scopes*).
14. Gib im Filter `drive.file` ein. Setze den Haken bei **`.../auth/drive.file`** („Nur die Dateien ansehen, bearbeiten, erstellen und löschen, die mit dieser App verwendet werden“).
15. Klicke auf **„Aktualisieren“** (*Update*) und dann unten auf **„Speichern“** (*Save*).

## Teil 4: App veröffentlichen ⚠️ wichtig

16. Klicke links auf **„Zielgruppe“** (*Audience*).
17. Beim Veröffentlichungsstatus steht **„Test“** (*Testing*). Klicke auf **„App veröffentlichen“** (*Publish app*) und bestätige.
18. Danach muss dort **„In Produktion“** (*In production*) stehen.

> Wenn du diesen Teil auslässt, verfällt die Anmeldung nach 7 Tagen und das Binding verlangt dann einen neuen Login. Eine Prüfung durch Google ist für den Eigengebrauch nicht nötig.

### „App veröffentlichen“ ist nicht anklickbar?

Steht dort *„Damit Sie Ihre Anwendung veröffentlichen können, müssen Sie die Konfiguration auf der Seite ‚Branding‘ abschließen“*, ist die Seite **„Branding“** noch nicht vollständig ausgefüllt.

**Branding vervollständigen:**

1. Klicke links in der Google Auth Platform auf **„Branding“**.
2. Prüfe die Pflichtfelder, sie sind mit * markiert:
   - **App-Name:** `openHAB`
   - **E-Mail-Adresse für Nutzersupport:** deine E-Mail aus der Auswahlliste
   - **Kontaktdaten des Entwicklers:** ganz unten auf der Seite, deine E-Mail-Adresse. Dieses Feld wird am häufigsten übersehen.
3. Klicke ganz unten auf **„Speichern“**.
4. Geh zurück zu **„Zielgruppe“**. Ist **„App veröffentlichen“** jetzt anklickbar, bist du fertig.

**Falls der Button weiter gesperrt ist**, verlangt Google zusätzlich die Links unter **„App-Domain“**. Trag auf der Seite „Branding“ Folgendes ein:

| Feld | Eintrag |
|---|---|
| Startseite der Anwendung | `https://github.com/pierrecinema/openhab-binding-googledrive` |
| Link zur Datenschutzerklärung | `https://github.com/pierrecinema/openhab-binding-googledrive` |
| Link zu den Nutzungsbedingungen | `https://github.com/pierrecinema/openhab-binding-googledrive` |
| **Autorisierte Domains** → „Domain hinzufügen“ | `github.com` |

Danach wieder **„Speichern“** und den Button bei **„Zielgruppe“** nochmal versuchen.

Google prüft diese Links bei einer App für den Eigengebrauch nicht. Es reicht, dass sie eingetragen sind.

**Wichtig:**
- **Kein Logo hochladen.** Mit Logo verlangt Google eine Prüfung der App, und die Veröffentlichung kann sich dann verzögern.
- Falls nach dem Veröffentlichen ein Hinweis wie „Bestätigung erforderlich“ erscheint, kannst du ihn ignorieren. Für den Bereich `drive.file` und den Eigengebrauch ist keine Prüfung nötig.

## Teil 5: Client-ID erstellen

19. Klicke links auf **„Clients“** und dann auf **„Client erstellen“** (*Create client*).
20. Wähle als Anwendungstyp **„Fernseher und Geräte mit begrenzter Eingabe“** (*TVs and Limited Input devices*).
    > Nur dieser Typ funktioniert mit dem Code-Login. Die Typen „Webanwendung“ oder „Desktop“ funktionieren nicht.
21. Gib als Name `openHAB Google Drive Binding` ein und klicke auf **„Erstellen“**.
22. Es erscheinen die **Client-ID** (endet auf `.apps.googleusercontent.com`) und der **Clientschlüssel** (*Client secret*).
    > ⚠️ **Kopiere beide sofort**, zum Beispiel in einen Passwort-Manager, oder lade die JSON-Datei herunter. Google zeigt den Clientschlüssel nur bei der Erstellung an und später nicht mehr. Hast du ihn verloren, legst du einfach einen neuen Client an.

**Die Arbeit in der Google Console ist damit erledigt.**

---

## Teil 6: Binding in openHAB installieren

23. Lade die JAR-Datei aus dem neuesten Release herunter: [Releases](https://github.com/pierrecinema/openhab-binding-googledrive/releases) → `org.openhab.binding.googledrive-<version>.jar`.
24. Kopiere sie in den `addons`-Ordner deiner openHAB-Installation. Dafür gibt es zwei Wege:
    - **Über das Volume auf dem Docker-Host:** in den Ordner, der im Container als `/openhab/addons` eingebunden ist.
    - **Per `docker cp`** auf dem Docker-Server. Ersetze dabei `openhab` durch den Namen deines Containers:
      ```bash
      docker cp org.openhab.binding.googledrive-<version>.jar openhab:/openhab/addons/
      ```

    Bei einer Paketinstallation oder openHABian ist der Ordner `/usr/share/openhab/addons`.
25. Nach etwa 10–30 Sekunden ist das Binding geladen. Ein Neustart ist nicht nötig.
