# 135er Touran – ESP32-S3-CAN-2CH-U Architektur

## Verbindliche Hardware

- Fahrzeug: VW Touran 1T3, Baujahr 2012, 1.4 TSI CAVC
- Radio: Erisin Android
- Gateway: Waveshare ESP32-S3-CAN-2CH-U
- Alte ELM327-/ES359-Kommunikation wird nicht weiterentwickelt.

## Ziel

Das ESP32-S3-CAN-2CH-U ist die einzige Fahrzeugschnittstelle der 135er-Touran-App. Die Android-App spricht niemals direkt OBD/ELM, sondern nur mit dem Gateway.

## Rollen

### CAN 1
- primär Antriebs-/Diagnose-CAN
- passives Mitschneiden zuerst
- später ISO-TP/UDS/KWP/TP2.0 nur gezielt und kontrolliert

### CAN 2
- Infotainment-/Komfort-CAN
- MFA/BAP-Analyse und spätere Ausgabe
- zunächst ausschließlich Listen-Only/Passive Capture

## Transport ESP32 -> Erisin

Ein einheitliches NDJSON-Protokoll wird über mehrere Transportwege angeboten:

1. USB CDC – bevorzugt für festen Einbau
2. Wi-Fi TCP – Diagnose/Entwicklung/Fallback
3. Bluetooth LE – Service/Fallback

Die Nutzdaten sind transportneutral. Aktuell ist ausschließlich Wi-Fi/TCP implementiert. USB CDC, BLE und eine automatische Transportpriorisierung sind Entwicklungsziele. Ein natives USB-Interface am Board bedeutet noch keine implementierte App-Kommunikation.

## Sicherheitsmodell

- Boot standardmäßig read-only
- beide CAN-Kanäle starten ohne aktive Sendefunktion
- CAN-TX erst nach expliziter Freigabe in Firmware und App
- keine automatische DTC-Löschung
- keine Codierung/Anpassung von Steuergeräten
- MFA/BAP-Senden erst nach validierter Busanalyse

## Datenprioritäten

Fast: 20–50 Hz
- Drehzahl
- Geschwindigkeit
- Saugrohrdruck/Ladedruck
- Pedalstellung
- Drosselklappe

Medium: 5–10 Hz
- Motorlast
- Zündwinkel
- Lambda
- Kraftstoffdruck

Slow: 1–2 Hz
- Kühlmitteltemperatur
- Öltemperatur
- ECU-Spannung
- Umgebung/Ansaugluft

Event-basiert
- DTC
- CAN-Status
- Bus-Off/Error-Passive
- Gateway-Reconnect
- MFA/BAP-Telegramme

## Bekannte App-Funktionen, die erhalten bleiben

- Master-GUI
- Livewerte
- Logger + USB-Export
- Server-Logupload
- Diagnoseansicht
- Fahrzeugprofil
- In-App-Update
- Erisin App-Launcher-Funktionen

## Neue Gateway-Funktionen

- Raw-CAN-Logging beider Kanäle
- Zeitstempel pro Frame
- CAN-ID/DLC/Data-Rohdaten
- Buslast/Fehlerzähler
- Filterlisten
- Capture Start/Stop
- Diagnose-Request-Queue
- ISO-TP Layer
- Fahrzeugwert-Aggregator
- OTA-Firmware-Update des ESP32
- RTC-Zeitstempel
- Watchdog
- Health-/Self-Test
- später BAP/MFA-Ausgabe

## Entwicklungsphasen

1. Board-Treiber + Self-Test
2. CAN1/CAN2 Listen-Only + Raw Logger
3. USB/Wi-Fi/BLE Gateway-Protokoll
4. Android-App auf ESP32-Gateway umstellen
5. CAVC-Livewerte aus realen Logs verifizieren
6. ISO-TP/Diagnose
7. MFA/BAP-Analyse
8. gezielte MFA-Ausgabe

## Quellenstand und Boardvarianten

Siehe [Quellenprüfung 09.10.2026](source-audit-2026-10-09.md). Die aktuelle allgemeine Waveshare-Hardwarereferenz nennt 16 MB Flash; das Projekt ist auf 8 MB eingestellt. Die tatsächliche `-U`-Boardvariante muss vor einer Änderung geprüft werden. Pinbelegung und CAN-Bitraten beweisen keine Touran-spezifischen Signaldekoder.
