# Quellen- und App-Prüfung vom 09.10.2026

## Ergebnis und Geltungsbereich

TouranLive 0.7.1 verbessert den vorhandenen Wi-Fi/TCP-Gateway-Pfad, die Darstellung tatsächlicher Fahrzeugwerte, Capture-Bedienung, Upload-Zuverlässigkeit und Update-Prüfung. Es werden keine neuen CAN-Signale dekodiert und keine aktiven Diagnose-/MFA-Befehle freigeschaltet.

Die Recherche berücksichtigt die zum Abrufzeitpunkt erreichbaren Primärquellen und die für das Projekt verwendeten API-/Firmware-Versionen. Sie ist kein Nachweis, dass sämtliche Informationen zum Fahrzeug oder zur neuesten Android-Version vollständig erfasst sind.

## Neu ausgewertete Primärquellen

| Quelle | Prüfbefund | Umsetzung / Grenze |
| --- | --- | --- |
| Offizielle Android-SDK-Quellen, `sources;android-35`, Revision 1, API 35 / Extension 13; `android/net/ConnectivityManager.java` | Netzwerkzustand benötigt `ACCESS_NETWORK_STATE`. | Manifest ergänzt; vorhandene Fehler im Netzcheck behoben. |
| Offizielle Android-SDK-Quellen, `android/net/NetworkCapabilities.java` | `INTERNET` bedeutet vorgesehene Internetanbindung; `VALIDATED` bedeutet beim letzten Systemtest bestätigte Erreichbarkeit. WLAN allein beweist keines davon. | Lokales Gateway-WLAN wird nicht länger pauschal als Internet gewertet. Unvalidiertes Internet bleibt ein Versuch; Erfolg wird am Server geprüft. |
| Offizielle Android-SDK-Quellen, `android/net/Network.java`, `getSocketFactory()` | Einzelne Sockets können an ein bestimmtes Netzwerk gebunden werden. | Gateway-Socket wählt ein vorhandenes WLAN mit passender lokaler Route. Kein globales Umleiten der Upload-/Update-Verbindungen. |
| [Android-Manifest, AOSP Android 15](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/res/AndroidManifest.xml) und SDK-Paketmanagerquellen | Paket-Sichtbarkeit und Signaturinformationen sind explizite Plattformregeln. | Launcher-Intent-Abfrage ersetzt die pauschale Paketabfrage für den App-Launcher. Updates prüfen Paketname, Version und aktuelle Signaturen zusätzlich zur SHA-256. |
| [Waveshare-Hardwarereferenz](https://github.com/waveshareteam/ESP32-S3-CAN-2CH/blob/d72279de7161ec95ea5ede89fd14cb515515a6d5/HARDWARE_REFERENCE.md), Stand `d72279de7161ec95ea5ede89fd14cb515515a6d5` | CAN1: TWAI, GPIO15/16; CAN2: XL2515QF20, GPIO17/18/21/41/42; RTC: PCF85063AT, GPIO38/39/40, Adresse 0x51; natives USB: GPIO19/20. | Die vorhandene BSP-Belegung stimmt damit überein. Native USB-Pins sind kein Nachweis eines bereits implementierten USB-Gateway-Transports. |
| Dieselbe Waveshare-Referenz | Allgemeines ESP32-S3-CAN-2CH-Board: 128-Mbit-/16-MB-Flash; vorhandenes Projekt: 8 MB. Terminierungspositionen R23/R30 und getrennte isolierte Masse SGND. | Boardvariante `-U`, tatsächlichen Flashbaustein und Anschlussbelegung am Gerät prüfen. Flashgröße und Fahrzeugverkabelung nicht anhand einer ähnlichen Produktbezeichnung ändern. |
| [ESP-IDF v5.5.2, TWAI-Dokumentation](https://github.com/espressif/esp-idf/blob/30aaf64524299d3bde422ca9a2848090d1bc5d0f/docs/en/api-reference/peripherals/twai.rst) | Listen-Only sendet weder Daten noch dominante ACK-/Error-Bits; reine Filterung ist kein Ersatz dafür. | Bestehende passive Firmware-Grundlage beibehalten. App akzeptiert nur einen Gateway-Handshake und Health-Status mit `readonly=true`. Das ersetzt keine Messung am Bus. |

Die offiziellen Webseiten von Android Developers, Espressif, Waveshare und Ross-Tech waren beim ersten direkten Abruf durch die Netzrichtlinie blockiert. Es wurden deshalb die offiziellen Android-SDK-Quellen, das installierte ESP-IDF-Tag und der Hersteller-GitHub-Upstream verwendet. Nicht gelesene Webseiten dienen nicht als Beleg. Die zusätzlichen Domains sind im Cloud-Konfigurationsentwurf hinterlegt; die SDK-/GitHub-Auswertung hängt davon nicht ab.

## Fahrzeugquellen: nachweisbare Grenzen

- Nachtrag: Mit dem erneut hochgeladenen `Touran.zip` sind der Auto-Scan vom 05.10.2026 und 36 Blockmaps jetzt zugänglich und unter `vcds/logs/2026-10-05` anonymisiert abgelegt. [Auswertung](touran-vcds-2026-10-05.md): 33 OBD-PID-Zeilen tatsächlich beobachtet; Öltemperatur im Kombiinstrument unter IDE00196 benannt. Rohantworten, CAN-Adressen und Skalierungsformeln bleiben unbestätigt.
- MWB-Nummern identifizieren allein keine universell gültigen Signale. Öltemperatur MWB 134 bleibt ein Kandidat. PID 015C ist in diesem Projekt ausdrücklich nicht als Standard-OBD-PID bestätigt; er wurde aus der historischen bestätigten PID-Liste im Code entfernt.
- Hersteller-GPIOs, CAN-Datenraten, Gerätefunktionen oder öffentliche DBCs beweisen keine CAVC-spezifische Bitbelegung. Deshalb gibt es keine erfundene Drehzahl-, Öltemperatur-, Boost- oder MFA-Dekodierung.
- Die aktuelle Firmware sendet `hello`, `health`, rohe `can`-Frames und gegebenenfalls `error`; sie erzeugt noch keine verifizierten `value`-Nachrichten. Eine erfolgreiche Verbindung kann deshalb bei vorhandenen Raw-CAN-Frames weiterhin leere Fahrzeuganzeigen liefern.
- PR-Ausstattung und Fahrzeugprofil bleiben die vom Projekt überlieferten Eigentümerangaben. Neu abgerufene Herstellerquellen bestätigen diese konkrete Fahrzeugausstattung nicht.

Für neue Live-Dekoder fehlen weiterhin: redigierter Auto-Scan mit Steuergeräte-/Softwareidentität, originale Messwert-Blockmap, reale CAN1-/CAN2-Captures und zeitlich zuordenbare Referenzmessungen. Internetquellen allein schließen diese Lücke nicht.

## Implementierte Verbesserungen

- Separate Leser-/Befehlswarteschlangen: Health- und Capture-Kommandos werden auch während des kontinuierlichen CAN-Empfangs geschrieben.
- Verbindung erst nach passendem Protokoll-v1-Handshake; aktive Gateways werden abgewiesen. Fünf Sekunden Handshake-Frist, Health-Anfrage alle fünf Sekunden, Verbindungsabbruch nach 15 Sekunden ohne gültige Antwort. Health kann damit auch auf einem ruhigen CAN-Bus die Verbindung bestätigen.
- Maximal 4096 Zeichen je Nachricht; überlange Nachrichten werden bis zum Zeilenende verworfen. Kanäle, Identifiergrenzen, ganzzahlige Zeitstempel, klassische CAN-Payloads bis acht Bytes, Wertnamen, Quellen, Einheiten und endliche Zahlen werden geprüft. Fehlerantworten sind ein bekannter Nachrichtentyp.
- Livewert-Seite zeigt ausschließlich empfangene Werte. Pro Signal werden ältere/duplizierte Gateway-Zeitstempel verworfen. Android-Empfangszeit steuert die 4,5-Sekunden-Frischefrist; beide monotone Uhren werden nicht miteinander verrechnet. Nach Trennung verschwinden Werte und Min/Max-Zustände.
- Die Anzeige übernimmt spätestens im nächsten 33-ms-Fenster den neuesten Wert je Signal. Gateway-Wertlogs behalten dessen numerische Genauigkeit und Quelle; Persistenz wird je Fenster gebündelt. Das ist keine vollständige Aufzeichnung aller `value`-Nachrichten. Raw-CAN bleibt der separate Rohdatenpfad.
- Dashboard-Animation circa 30 Hz bei vorhandenen Werten und 1 Hz ohne Werte. Datumsformatierer und Fahrzeug-Bitmap-Rechteck werden wiederverwendet; Hexausgabe ohne Formatter je Datenbyte.
- Gateway-Status, Capture-Start/Pause-Anforderung, manuelle Updateprüfung und bewusst ausgelöster Radiosystemscan im Logger. Capture hat im vorhandenen Firmwareprotokoll keine Bestätigung; die Oberfläche spricht deshalb von einer Anforderung.
- Ungeprüfte DTC-Listen melden „nicht ausgelesen“; sie werden nicht als fehlerfreies Fahrzeug dargestellt.
- Speicherbegrenzte CSV-/Raw-CAN-Puffer; Trimmereignisse werden ausgewiesen. Raw-CAN trägt eine Gateway-Sitzung, damit ESP-Neustarts nicht anhand gleicher Zeitstempel vermischt werden. Raw-CAN-Snapshots werden als eigene Berichte unter dem vorhandenen 5-MB-Serverlimit eingereiht.
- Upload-Queue-Einträge werden erst nach abgeschlossenem Schreiben sichtbar. Gleichzeitige Flush-Aufrufe sind serialisiert; nur HTTP-Erfolg mit JSON-`ok: true` bestätigt einen Bericht. Die Queue bleibt grundsätzlich „mindestens einmal“: ein Prozessabbruch zwischen Serverbestätigung und lokaler Löschung kann erneut senden.
- Updates erlauben ausschließlich den vorhandenen HTTPS-Ursprung unter `/touran/`, kontrollierte Weiterleitungen und eine obligatorische SHA-256. Paketname, Versionscode und Signaturen müssen passen; Teil-Dateien werden nicht installiert. Fortsetzung nach der Installationsfreigabe prüft die Datei erneut.
- Android-26-kompatible Styles, engere Launcher-Sichtbarkeit und geschlossene Gateway-Ressourcen bei Activity-Ende. Keine Netzwerkübertragung mehr im UI-Thread von `onDestroy()`.

## Verifikation und offene Punkte

Build, JVM-Regressionstests und Android Lint sind die automatisierten Prüfungen. Die Test-Gateways und HTTP-Server sind ausschließlich lokale Protokollfixtures; ihre Inhalte sind keine Fahrzeugmessungen und werden nicht als solche ausgeliefert.

Die reale Netzwerkbindung bei gleichzeitiger Mobilfunk-/WLAN-Nutzung, Navigation/Touch am Erisin, Android-Installer und Firmware/CAN-Empfang müssen am Gerät geprüft werden. USB CDC, BLE, aktive Diagnose, MFA/BAP, CAN-Filter und OTA bleiben Entwicklungsziele; die Firmware implementiert insbesondere `filter` noch nicht. Das Raw-Protokoll hat zudem keine gesonderten RTR-/Error-Frame-Felder.

Die Serverdatei `server/update.json` zeigt weiterhin auf den vorhandenen Deploymentstand 0.6.1. Sie wird nicht vorab auf eine neue APK/Prüfsumme geändert: die geprüfte APK muss zuerst mit dem vorhandenen Installationsschlüssel signiert und tatsächlich bereitgestellt werden. Ein Cloud-Debugschlüssel ist nicht automatisch mit dem bereits installierten Radio kompatibel. Zertifikatsrotation benötigt eine eigene geprüfte Migrationslösung; die App akzeptiert aktuell nur dieselben aktuellen Signierer.

### Abschließender automatisierter Prüfstand

- Cloud-Build mit Java 21, Gradle 8.9 / AGP 8.7.3: `testDebugUnitTest`, `assembleDebug`, `lintDebug` erfolgreich.
- 23 JVM-Tests ausgeführt; 0 fehlgeschlagen, 0 Fehler, 0 übersprungen.
- Lint: 0 Fehler, 18 Warnungen. Verbleibend sind insbesondere feste Ausrichtung, Backup-Regeln, eingeschränkte Paket-Sichtbarkeit des freiwilligen Audits, Übersetzungs-/Ressourcenhinweise und ältere API-Checks. Keine Fehlerprüfung wurde abgeschaltet.
- APK-Paket `de.growcentral.touranlive`, Version 0.7.1 / Code 25, minSdk 26, targetSdk 35; APK-Signaturprüfung erfolgreich.
- SHA-256 der geprüften Cloud-Debug-APK: `02f2ce59f763e923592d52e6116e25605ed79b8f958920468d51b722c7ee8ce3`.
- GitHub-Android-Workflow führt künftig Tests, Build und Lint aus. Der entfernte CI-Lauf wurde nicht gestartet; seine Java-17-Ausführung wird hier nicht als bereits bestanden ausgegeben.
- Cloud-`start_skill` wurde auf die neue APK und Testanleitung aktualisiert. Zusätzliche Recherche-Domains (`developer.android.com`, `docs.espressif.com`, `www.waveshare.com`, `www.ross-tech.com`) wurden additiv im Umgebungsentwurf gespeichert. Die Umgebung wurde hier nicht veröffentlicht.
