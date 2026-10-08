# 135er Touran – ESP32 CAN Gateway + Android App

Zielsystem:
- Volkswagen Touran 1T3, Modelljahr 2012
- HSN/TSN 0603 / AIH
- 1.4 TSI CAVC, 103 kW / 140 PS
- Erisin Android-Radio
- Waveshare ESP32-S3-CAN-2CH-U als einzige Fahrzeug-Schnittstelle

## Architektur ab App 0.7.0

Die 135er-Touran-App verwendet keinen ELM327-/ES359-Adapter mehr. Fahrzeugdaten gelangen ausschließlich über das ESP32-S3-CAN-2CH-U in die App.

- CAN1: 500 kbit/s, zunächst Listen-Only; Antrieb/Diagnose
- CAN2: 100 kbit/s, zunächst Listen-Only; Komfort/Infotainment/MFA-Analyse
- ESP32 -> Erisin: gemeinsames NDJSON-Gateway-Protokoll
- erster implementierter Transport: Wi-Fi/TCP Port 13569
- vorgesehen: USB CDC als bevorzugter Festeinbau-Transport, BLE als Fallback
- Raw-CAN-Logging beider Kanäle
- Gateway-Health, Fehlerzähler und Watchdog-Grundlage
- spätere kontrollierte ISO-TP-Diagnose
- spätere MFA/BAP-Funktionen nach verifizierter Busanalyse

Aktives CAN-Senden ist im aktuellen Firmwarestand gesperrt. Der erste Fahrzeugeinsatz erfolgt rein passiv.

## Verzeichnisse

- `android-app/` – 135er-Touran Android-App
- `esp32-firmware/` – Firmware für ESP32-S3-CAN-2CH-U
- `docs/ESP32_S3_CAN_2CH_ARCHITECTURE.md` – Systemarchitektur
- `docs/ESP32_GATEWAY_PROTOCOL.md` – App-/Gateway-Protokoll
- `captures/` – reale Fahrzeug-/VCDS-/CAN-Logs zur Verifikation
- `tools/` und `torque/` – historische Analysewerkzeuge; nicht mehr der Laufzeitpfad der App

## Grundregel für Fahrzeugwerte

Es werden keine Werte simuliert oder erfunden. Ein Dashboardwert wird erst verwendet, wenn seine Quelle für diesen CAVC/Touran durch reale CAN-/Diagnosedaten oder vorhandene VCDS-Messungen verifiziert wurde.

## Erster Fahrzeugeinsatz

1. Firmware auf das ESP32-S3-CAN-2CH-U flashen.
2. CAN zunächst nur im Listen-Only-Modus anschließen.
3. Mit der 135er-Touran-App verbinden.
4. Rohdaten-Capture bei Zündung, Leerlauf und gezielten Bedienaktionen erstellen.
5. CAN-Signale gegen bekannte VCDS-Werte verifizieren.
6. Erst danach gezielte aktive Diagnose und MFA/BAP-Kommunikation freigeben.
