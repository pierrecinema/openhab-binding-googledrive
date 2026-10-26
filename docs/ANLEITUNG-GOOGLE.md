# Google-Einrichtung für das Google Drive Binding

Die Einrichtung dauert etwa 15 Minuten und ist nur einmal nötig.
Am Ende hast du eine **Client-ID** und einen **Client-Secret**, die du in openHAB einträgst.

> **Hinweis:** Google ändert die Bezeichnungen in der Console gelegentlich. Wenn ein Menüpunkt etwas anders heißt, such nach dem englischen Begriff in Klammern.

**Übersicht**

| Teil | Inhalt | Dauer |
|---|---|---|
| 1 | Projekt anlegen | 2 min |
| 2 | Google Drive API aktivieren | 1 min |
| 3 | Zustimmungsbildschirm einrichten | 3 min |
| 4 | Branding vollständig ausfüllen | 3 min |
| 5 | App veröffentlichen | 1 min |
| 6 | Client-ID erstellen | 2 min |
| 7 | Binding in openHAB installieren | 2 min |
| 8 | Konto in openHAB verbinden | 2 min |

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

## Teil 4: Branding vollständig ausfüllen

Google lässt die App erst veröffentlichen (Teil 5), wenn die Seite „Branding“ vollständig ist. Deshalb füllst du sie jetzt komplett aus.

16. Klicke links auf **„Branding“**.
17. Prüfe bzw. trage ein:

    | Feld | Eintrag |
    |---|---|
    | App-Name | `openHAB` |
    | E-Mail-Adresse für Nutzersupport | deine E-Mail aus der Auswahlliste |
    | App-Logo | **leer lassen** (mit Logo verlangt Google eine Prüfung der App) |
    | Startseite der Anwendung | `https://github.com/pierrecinema/openhab-binding-googledrive` |
    | Link zur Datenschutzerklärung | `https://github.com/pierrecinema/openhab-binding-googledrive` |
    | Link zu den Nutzungsbedingungen | `https://github.com/pierrecinema/openhab-binding-googledrive` |
    | Autorisierte Domains → „Domain hinzufügen“ | `github.com` |
    | Kontaktdaten des Entwicklers (ganz unten) | deine E-Mail-Adresse |

    > Die Links müssen nur eingetragen sein. Google prüft sie bei einer App für den Eigengebrauch nicht. Das Feld **„Kontaktdaten des Entwicklers“** ganz unten wird am häufigsten übersehen.

18. Klicke ganz unten auf **„Speichern“** (*Save*).

## Teil 5: App veröffentlichen ⚠️ wichtig

19. Klicke links auf **„Zielgruppe“** (*Audience*).
20. Beim Veröffentlichungsstatus steht **„Test“** (*Testing*). Klicke auf **„App veröffentlichen“** (*Publish app*) und bestätige.
21. Danach muss dort **„In Produktion“** (*In production*) stehen.

> Wenn du diesen Teil auslässt, verfällt die Anmeldung nach 7 Tagen und das Binding verlangt dann einen neuen Login. Eine Prüfung durch Google ist für den Eigengebrauch nicht nötig. Falls ein Hinweis wie „Bestätigung erforderlich“ erscheint, kannst du ihn ignorieren.

**„App veröffentlichen“ ist nicht anklickbar?** Dann steht dort *„… müssen Sie die Konfiguration auf der Seite ‚Branding‘ abschließen“*. Geh zurück zu Teil 4 und prüfe jedes Feld der Tabelle, vor allem die **Kontaktdaten des Entwicklers** und die **autorisierte Domain** `github.com`. Speichern nicht vergessen.

## Teil 6: Client-ID erstellen

22. Klicke links auf **„Clients“** und dann auf **„Client erstellen“** (*Create client*).
23. Wähle als Anwendungstyp **„Fernseher und Geräte mit begrenzter Eingabe“** (*TVs and Limited Input devices*).
    > Nur dieser Typ funktioniert mit dem Code-Login. Die Typen „Webanwendung“ oder „Desktop“ funktionieren nicht.
24. Gib als Name `openHAB Google Drive Binding` ein und klicke auf **„Erstellen“**.
25. Es erscheinen die **Client-ID** (endet auf `.apps.googleusercontent.com`) und der **Clientschlüssel** (*Client secret*, beginnt meist mit `GOCSPX-`).
    > ⚠️ **Kopiere beide sofort**, zum Beispiel in einen Passwort-Manager, oder lade die JSON-Datei herunter. Google zeigt den Clientschlüssel nur bei der Erstellung an und später nicht mehr. Hast du ihn verloren, legst du einfach einen neuen Client an.
    >
    > Gib Client-ID und Clientschlüssel nicht weiter und zeig sie nicht auf Screenshots.

**Die Arbeit in der Google Console ist damit erledigt.**

---

## Teil 7: Binding in openHAB installieren

26. Lade die JAR-Datei aus dem neuesten Release herunter: [Releases](https://github.com/pierrecinema/openhab-binding-googledrive/releases) → `org.openhab.binding.googledrive-<version>.jar`.
27. Kopiere sie in den `addons`-Ordner deiner openHAB-Installation:

    | Installation | Ordner |
    |---|---|
    | Docker (offizielles Image) | `/openhab/addons` im Container, meist ein Volume auf dem Docker-Host |
    | openHABian / Paketinstallation | `/usr/share/openhab/addons` |
    | Manuell (zip) | `<openhab>/addons` |

    Beispiel Docker (ersetze `openhab` durch den Namen deines Containers):
    ```bash
    docker cp org.openhab.binding.googledrive-<version>.jar openhab:/openhab/addons/
    ```
28. Nach etwa 10–30 Sekunden ist das Binding geladen. Ein Neustart ist nicht nötig.

**Update auf eine neue Version:** alte JAR aus dem `addons`-Ordner löschen, neue hineinkopieren. Things, Items und die Google-Anmeldung bleiben erhalten.

## Teil 8: Konto in openHAB verbinden

29. Öffne openHAB und gehe zu **Einstellungen → Things → „+“ → Google Drive Binding**.
30. Wähle **„Google-Drive-Konto“** (mit dem Label *Bridge*).
31. Trag **Client-ID** und **Client-Secret** aus Teil 6 ein. Achte darauf, dass vorne und hinten kein Leerzeichen mitkopiert wird. Klicke auf **„Erstellen“**.
32. Öffne das neue Thing. Der Status ist **OFFLINE** mit der Meldung *„Öffne https://www.google.com/device und gib den Code ABCD-EFGH ein“*.
33. Öffne **https://www.google.com/device** auf Handy oder PC und gib den Code ein (Groß-/Kleinschreibung egal).
34. Wähle das Google-Konto, in dessen Drive die Backups landen sollen.
35. Erscheint *„Google hat diese App nicht überprüft“*: **„Erweitert“** → **„Weiter zu openHAB (unsicher)“**. Das ist deine eigene App, also unbedenklich.
36. Klicke auf **„Zulassen“**. Nach wenigen Sekunden geht das Konto auf **ONLINE**, und „Speicher belegt“ / „Speicher gesamt“ zeigen Werte.
37. Lege jetzt das Thing **„Backup-Upload“** an (ebenfalls unter **„+“ → Google Drive Binding**) und wähle das Konto als Bridge.

Der Code ist 30 Minuten gültig. Läuft er ab, zeigt openHAB automatisch einen neuen an.

---

## Wenn etwas nicht klappt

| Meldung / Problem | Ursache | Lösung |
|---|---|---|
| „App veröffentlichen“ nicht anklickbar | Branding unvollständig | Teil 4 nochmal prüfen, vor allem Entwickler-Kontakt und Domain `github.com` |
| „Google hat den OAuth-Client abgelehnt“ | Falsche Client-ID oder falscher Clientschlüssel, oder falscher Client-Typ | Teil 6 wiederholen, Typ „Fernseher und Geräte mit begrenzter Eingabe“ wählen |
| „Der Zugriff wurde abgelehnt“ | Auf der Google-Seite wurde „Abbrechen“ gewählt | Thing deaktivieren und wieder aktivieren, dann erscheint ein neuer Code |
| Nach 7 Tagen wieder OFFLINE mit neuem Code | App steht noch auf „Test“ | Teil 5 erledigen, dann einmal neu anmelden |
| Thing bleibt UNKNOWN, kein Code | Keine Internetverbindung aus openHAB / dem Container | Im openHAB-Log nach `googledrive` suchen |
| Zweiter Ordner „openHAB Backups“ in Drive | Der Ordner wurde von Hand angelegt; das Binding sieht nur eigene Ordner | Den leeren, selbst angelegten Ordner löschen |

Fragen und Rückmeldungen gerne im [Forumsthread](https://community.openhab.org/t/google-drive-binding-automatic-openhab-backups-to-google-drive/170779) oder als [GitHub-Issue](https://github.com/pierrecinema/openhab-binding-googledrive/issues).
