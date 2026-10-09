# Labels.zip: Diagnosebasis für den Touran

Prüfung: 09.10.2026. Ziel: VW Touran 1T3, Modelljahr 2012, CAVC, KWB-Handschaltgetriebe und die bereits im Projekt dokumentierte PR-Ausstattung.

## Ergebnis

Das Archiv liefert wertvolle Steuergeräte-Labelkandidaten, Weiterleitungen nach Teilenummer, Messwertbezeichnungen und Statusbeschreibungen. Es ist keine vollständige, direkt ausführbare Diagnosebeschreibung. Insbesondere fehlen der App weiterhin Diagnose-Transport und Steuergeräte-Sitzungen im ESP32-Gateway; Labels allein schließen diese Implementierungslücke nicht.

Nachtrag vom 09.10.2026: Der Nutzer hat den bereits früher bereitgestellten Scan mit `Touran.zip` erneut zugänglich gemacht. Der Scan und 36 Messwert-Exporte liegen nun anonymisiert in [vcds/logs/2026-10-05](../vcds/logs/2026-10-05/README.md). Die [fahrzeugspezifische Auswertung](touran-vcds-2026-10-05.md) und `label-matches.json` dort ersetzen die offenen Zuordnungen der folgenden ursprünglichen Kandidatentabelle. Insbesondere ist das tatsächliche Kombiinstrument UDS-basiert; Motoröltemperatur ist als IDE00196 exportiert, nicht als bestätigter klassischer MWB 003.

Die Labeldateien sind Datenquellen. Darin enthaltene Hinweise zu VCDS, Grundeinstellungen, Codierung, Anpassung oder Security Access wurden nicht als Nutzeranweisungen behandelt und nicht ausgeführt.

## Vollständige Archivprüfung

- 4374 Dateien in 4377 ZIP-Einträgen, rund 24,6 MB entpackte Daten.
- 1620 lesbare `.lbl`, 2694 binäre `.clb`, 50 `.crd`, 6 `.xpl`, 4 `.txt`.
- Alle Dateien wurden gelesen und dabei die ZIP-CRC geprüft. Das bestätigt die Archivintegrität, nicht die Herstellerherkunft.
- ZIP-SHA-256: `9839e2e69b53b65ee24325dfaf1a9a6eed58468d0da3e957e70f6837649163a8`.
- Dateiköpfe der untersuchten Klartextlabels nennen Ross-Tech. Das hochgeladene Archiv ist hier die Quelle; eine unabhängige Hersteller-Signaturprüfung liegt nicht vor.
- 198 Plattform-/Motorfamilien-Kandidaten einschließlich lesbarer Weiterleitungsziele ausgewählt, davon 82 Klartextdateien. `DRV`-Sprachvarianten bleiben getrennt.
- `.clb` und `.crd` wurden inventarisiert und gehasht, nicht als vermeintlicher Klartext dekodiert. ASAM-/ODX-/ROD-Dateien sind in diesem Archiv nicht enthalten.

Reproduzierbare Prüfung ohne Ausführen von Dateiinhalten:

```sh
python3 tools/index_vcds_labels.py /pfad/Labels.zip --output /pfad/labels-index.json
```

Der Index enthält Dateihashes, Quellenzeilen, Labelköpfe, Weiterleitungen und nummerierte Messwertbeschreibungen. Er exportiert keine Login-, Codierungs-, Anpassungs- oder Grundeinstellungsbefehle. Nummerierte Messwertbeschreibungen sind noch keine Erlaubnis, eine betreffende Funktion am Fahrzeug auszuführen.

## Steuergeräte und relevante Labelkandidaten

Adressen sind logische VCDS-Diagnoseadressen, keine CAN-Identifier. Die Tabelle beschreibt Suchkandidaten im Paket und keine bestätigte Liste eingebauter Steuergeräte. Auswahl erfolgt später mit SW-/HW-Teilenummer, Versionsstand, Komponente, tatsächlich verwendetem Label und gegebenenfalls ASAM-ID aus dem vorhandenen Scan.

| Adresse | Funktion | Kandidaten im Archiv | Einordnung |
| --- | --- | --- | --- |
| 01 | Motor CAVC | `03C-906-027-CAV.clb`; `DRV/03C-906-027-CAV.clb`; `DRV/03C-906-027-CAV2.clb` | Namentlich passende CAV-Familie, binär; genaue ECU-/Softwarezuordnung offen. |
| 03 | ABS/ESP | `1K-03.clb`, `1K0-907-379-EC1-V1` bis `V4.clb` sowie MK60-/MK70-Varianten | ESP ist laut PR-Profil vorhanden; Controllergeneration nicht aus dem Baujahr raten. |
| 08 | Climatronic | `1T-08.lbl` → `1K0-907-044.lbl`; zusätzlich `5K0-907-044.clb` | Alte und neuere Varianten im Paket; genaue Teilenummer entscheidend. |
| 09 | Bordnetz/BCM | `1T-09.lbl`, `5K-09.lbl` → `1K0-937-08x-09.clb` | `5K-09` ordnet 084/085/086/087-Familien verschiedenen BCM-Ausführungen zu. Keine Codierung übernehmen. |
| 10 | Einparkhilfe/Parklenkassistent | `1T0-919-475.clb`, `1K0-919-475.clb`, `3AA-919-475.clb`, `3C8-919-475.clb`, `5K-10.lbl` | PR 7X5 ist bekannt; daraus folgt nicht die konkrete ECU-Variante. |
| 15 | Airbag | `1T0-909-605.lbl`, `5K0-959-655.clb` | Alte/neue Familien; nur Identität, Fehler und passende Messwerte betrachten. |
| 16 | Lenksäule/MFL/GRA | `5K-16.lbl` → `5K0-953-569.clb`; `1K0-953-549-MY8/MY9.clb` | Weiterleitung kann ausdrücklich die HW-Teilenummer verlangen. |
| 17 | Kombiinstrument/MFA | `1T-17.clb`, `5K-17.clb`, `1K0-920-xxx-17.lbl`, `5K0-920-xxx-17.clb` | Ein möglicher zusätzlicher Datenweg für Öltemperatur und Buszustände; noch kein bestätigter Kanal des Fahrzeugs. |
| 19 | CAN-Gateway/J533 | `1K-19.lbl`, `1K0-907-530-V1` bis `V4.clb`, `7N0-907-530-V1/V2.clb` | Zentrale Quelle für tatsächlich registrierte Module. Nicht pauschal die ältere 1K0-Familie annehmen. |
| 20 | Fernlichtassistent | `1K-20.lbl`, `5K-20.lbl`, `7N-20.lbl` | Wegen PR 8G1 relevant; Teilenummer und tatsächliche Adresse im Scan bestätigen. |
| 25 | Wegfahrsperre/Identität | `1T-25.lbl` → Familie `1K0-920-xxx-25` | Nur lesende Identifikation; keine Schlüsselanpassung, Logins oder PIN-Funktionen ableiten. |
| 42/52 | Türen vorn | `1T-42.lbl`, `1T-52.lbl`; MIN/MAX GEN1–3 und GEN4-Weiterleitungen | Späte Generationen können weitere Module als Subsysteme führen. |
| 62/72 | Türen hinten | `1T-62.lbl`, `1T-72.lbl`, Generationen der `1K0-959-703/704`-Familien | Ob getrennte Adressen oder Subsysteme: Scan entscheidet. |
| 44 | Lenkhilfe/Servotronic | `1K-44.clb`, `1K0-909-14x-GEN3.clb`, `1Kx-909-143/144`-Varianten | Passend zu PR 1N3 als Suchbereich, nicht als verifizierte Hardwareliste. |
| 46 | Komfortsystem | `1K-46.clb`, `1K0-959-433-MIN/MAX.clb` | Eigenständiges Komfortsteuergerät nur berücksichtigen, wenn tatsächlich vorhanden; BCM-Integration möglich. |
| 55/56 | Leuchtweitenregelung / Werksradio | Touran-55-Subsystemlabels, `1T-56.lbl`, `1T0-035-095.lbl` und weitere | Nur bei tatsächlichem Einbau. Das Erisin ersetzt nicht automatisch ein weiterhin diagnostizierbares Werksradio. |

Für DSG/Automatik (02), Werksnavigation, Werks-Telefon, RDK, Standheizung und Anhängersteuergerät wird nach dem bekannten Profil kein Standardumfang vorausgesetzt. Nachrüstungen und die tatsächliche Gateway-Verbauliste haben Vorrang vor dem Werksprofil.

## Konkrete neue Messwert-Hinweise

### Motor: Vergleichsquelle BLG, ausdrücklich kein CAVC-Nachweis

`Labels/03C-906-032-BLG.lbl` benennt im Kopf den 170-PS-BLG. Die Datei enthält 401 nummerierte Messwertzeilen zu 90 Gruppennummern; sie darf nicht automatisch den CAVC-Dekoder bestimmen.

| Messwertblock | In dieser Vergleichsdatei beschrieben | Nutzen für den Abgleich |
| --- | --- | --- |
| 014–016 | Aussetzer gesamt und je Zylinder, Erkennung aktiv/deaktiviert | Aussetzerdiagnose; Betriebszustand berücksichtigen. |
| 020, 022/023, 026 | Zündwinkelrücknahme und Klopfkontrollwerte | Verbrennungs-/Klopfkorrektur vergleichen. |
| 030–033, 041 | Lambdastatus, Ist/Soll, Gemischadaption, Sondenheizung | Gemischdiagnose; Status und Regelwerte auseinanderhalten. |
| 090/091/093 | Nockenwellensteuerung und Phasenlage | Ist/Soll und Adaption getrennt behandeln. |
| 106 | Kraftstoff-Raildruck tatsächlich; Pumpenstatus | Istwert allein liefert noch keinen Soll/Ist-Regelvergleich. |
| 113 Feld 4 | Atmosphärischer Druck, mbar | Druckreferenz, kein fester Ersatzwert. |
| 115 Feld 3/4 | Ladedruck Soll/Ist | Erst nach Bestätigung von Einheit und absolut/relativ ableiten. |
| 125–129 | Antriebs-CAN-Kommunikation | Zusätzlicher Diagnosebereich neben Motor-Livewerten. |
| 130–137 | Kühlung, Temperaturen, Lüfter und Klimaanforderungen | Neuer Suchbereich für das CAVC-Messwertinventar. |
| 134 Feld 1/2/3/4 | Öltemperatur / Umgebung / Ansaugluft / Motorausgang | Bestätigt, dass die BLG-Datei diese Bedeutung verwendet; bestätigt nicht den CAVC-Ölkanal. |
| 066 | GRA-Schalter-/Statusbereich | Relevanter Suchbereich für die vorhandene GRA. |

MWB 004 ist in der Datei doppelt beschrieben, teilweise mit verschiedenen Referenzbereichen. Das zeigt, weshalb blindes Einlesen und „letzter Eintrag gewinnt“ keine geprüfte Diagnose liefern. MWB 210 ist in dieser BLG-Datei nicht beschrieben; der bereits im Projekt genannte CAVC-Luftmassenkanal wird dadurch weder bestätigt noch widerlegt.

### Kombiinstrument: zusätzlicher Öltemperatur-Kandidat

`Labels/1K0-920-xxx-17.lbl` beschreibt:

- MWB 003 Feld 1: Kühlmitteltemperatur; Feld 2: Ölstand; Feld 3: Öltemperatur.
- MWB 004 Feld 1: Versorgungsspannung.
- MWB 125/126: Kommunikationszustände von Motor, Bremse, Gateway, Airbag und Lenkhilfe.

Damit existiert ein zusätzlicher sinnvoller Prüfpfad für Öltemperatur. Voraussetzung ist, dass das tatsächlich verbaute Kombiinstrument diese Labelfamilie und den Messwert unterstützt. Dies ist keine CAN-Bitzuordnung und kein Nachweis für PID 015C.

### Climatronic

`1K0-907-044.lbl` beschreibt Kompressorstatus, Temperaturen, Luftauslass-/Fußraumtemperaturen sowie Positions-/Potentiometerwerte mehrerer Klappen. Diese Informationen können nach passender ECU-Zuordnung die Fehlersuche deutlich erweitern. Die ebenfalls enthaltenen Grundeinstellungen werden nicht automatisch übernommen.

## Was für eine steuergeräteübergreifende Diagnose noch benötigt wird

1. **Verbindliche Zuordnung des bereits gelieferten Scans.** Pro Hauptmodul und Subsystem: logische Adresse, SW- und HW-Teilenummer, Komponente, Softwareversion, verwendeter Labelname, bei UDS ASAM-ID und Datensatzversion. Gateway-Verbauliste und vorhandene Fehler inklusive Status/Umgebungsdaten übernehmen; VIN bei exportierten Berichten redigieren.
2. **Diagnosekommunikation im ESP32-Gateway.** Der aktive App-Pfad verwendet NDJSON über TCP. Die Firmware sperrt momentan `diag_request`; sie besitzt dort keine fertige steuergeräteübergreifende Sitzung. Lesende Diagnose sendet Anfragen auf CAN und ist daher mit echter Listen-Only-Busüberwachung allein nicht möglich.
3. **Passender Transport je Steuergerät.** CAN-/Gateway-Routing und Zieladressen anhand realer Daten verifizieren. ISO-TP sowie gegebenenfalls VW TP2.0/KWP2000 unterstützen; bei UDS die passenden Dienste/Datensätze verwenden. Nicht aus „MED17“, Fahrzeugjahr oder einer Gruppennummer das Protokoll ableiten. Das alte `VagTp20.java` spricht einen ELM327-Pfad und nur Adresse 01; es ist kein fertiger ESP32-Multisteuergeräte-Treiber.
4. **Robuste Sitzungen.** Identifikation, Timeouts, Segmentierung/Flow-Control, negative Antworten, erforderliches Session-Management, kontrollierte Wiederholungen und Abbruch bei Busproblemen. Sitzungen koordinieren statt konkurrierende Tester unkontrolliert auf den Bus zu lassen.
5. **Diagnosedatensätze.** Messwertnummern/DIDs, Datentyp, Länge, Skalierung, Einheit, Bit-/Statusbedeutungen und Verfügbarkeit je ECU-/Softwarestand. Klartextlabels liefern häufig Beschreibungen, aber nicht alle Protokolldetails. Bei UDS fehlen in `Labels.zip` die separat benötigten ASAM-/ODX-/ROD-Daten bzw. daraus exportierte passende Messwertinformationen.
6. **Fehlerspeicher mit Kontext.** Herstellerfehlercode und Text, aktuell/sporadisch, Statusbits, Häufigkeit und verfügbare Umgebungsbedingungen korrekt lesen. OBD Mode 03/07/0A allein deckt ABS, Airbag, Komfort und herstellerspezifische UDS-Statusdaten nicht vollständig ab. Dieses Archiv ist keine nachgewiesen vollständige DTC-/Ursachenbibliothek.
7. **App-Erweiterung.** Modulübersicht, Scanfortschritt, ECU-Identität, Teilfehler/unerreichbare Module, Messwertlisten mit Quelle/Einheit, Fehlerdetails und vollständiger Export. Ein einzelnes nicht erreichbares Modul darf den übrigen Scan nicht abbrechen. „Nicht geprüft“ bleibt von „kein Fehler“ getrennt.
8. **Fahrzeugvalidierung.** Ergebnisse gegen den bereits vorhandenen VCDS-Scan und neue gezielte lesende Referenzabfragen vergleichen. Mechanische Ursachen werden nicht allein durch ein fehlerfreies Steuergerät oder einen Label-Sollbereich ausgeschlossen.

Codierung, Anpassung, Grundeinstellungen, Stellgliedtests, Wegfahrsperren-/Schlüsselarbeiten und Fehlerlöschung sind ein anderer Funktionsumfang. Deren bloßes Vorkommen in den Labels autorisiert oder implementiert sie nicht. Für den hier betrachteten Diagnoseumfang bleibt die Planung zunächst bei Identifikation, Fehlerspeicher und Messwerten.

## Erzeugte Arbeitsdateien

Außerhalb des Checkouts liegen:

- `/workspace/research/touran-diagnostics/labels-index.json`: vollständiger Archivindex mit Einzeldateihashes und Quellenzeilen.
- `/workspace/research/touran-diagnostics/touran-label-candidates.json`: 198 Kandidaten und weitergeleitete Zielvarianten; Zuordnungsstatus ausdrücklich unbestätigt.

Die Quelldateien wurden nicht in die APK eingebaut. Es wurden keine CAVC-Zuordnungen geraten, keine Steuergerätekennung ersetzt und keine aktiven CAN-Befehle freigeschaltet.
