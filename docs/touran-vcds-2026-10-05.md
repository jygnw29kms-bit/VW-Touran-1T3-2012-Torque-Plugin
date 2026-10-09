# Touran: Auswertung des VCDS-Pakets vom 05.10.2026

Ausgewertet am 09.10.2026. Quelle: erneut vom Eigentümer hochgeladenes `Touran.zip`, SHA-256 `3013e639c7331fbd892931cf4bb9c8930c8e5400d7e31d2174f89a89548ab1c3`.

## Vorhandene Daten

- 38 Archivdateien: ein vollständiger Auto-Scan, 36 CSV-Blockmaps und ein Screenshot der Motorsteuergeräteansicht. ZIP-CRC aller Dateien geprüft; keine Dateiinhalte ausgeführt.
- 17 erreichbare Hauptmodule mit Identifikation, drei weitere Scanabschnitte nicht erreichbar (37, 56, 77). 04 und 46 stehen nur in der Statusübersicht; daraus werden keine zusätzlichen eigenständigen ECU-Identitäten erfunden.
- Zwei Aufnahmezeitpunkte je Ziel: 17 Hauptmodule plus OBD. Das sind Momentaufnahmen, keine kontinuierlichen Fahrlogs. Die erste Motor-Map zeigt 0/min, die zweite ungefähr 680/min. Werte verschiedener Module wurden zeitversetzt gelesen und sind nicht synchron.
- Die fünf UDS-Maps mit benannten Zeilen (08, 10, 16, 17, 25) enthalten zusammen 865 Zeilen pro Durchlauf. Klassische Blockmaps enthalten 412 Gruppenzeilen; OBD enthält 33 PID-Zeilen. Insgesamt 2620 Messwertzeilen in beiden Durchläufen, einschließlich wiederholter Schlüssel.
- Auto-Scan: PCI 23.11.0.1, HEX-V2, Datenstand 20240306 DS351.0. Maps/Screenshot: PCI 23.11.0. Scanheader: Windows 7 x64; aktuelle Nutzerangabe: Windows 10. Keine erneute Versionsauskunft für diese Aufnahme nötig.

Anonymisierte Dateien: [Datenpaket](../vcds/logs/2026-10-05/README.md). `inventory.json` bewahrt Messwertschlüssel, Zellen und Quellenzeilen; `manifest.json` ordnet neutrale Exportnamen den anonymisierten Originalnamen zu. VIN, VINID, VCID, Seriennummern und Werkstatt-/Auftragszeilen wurden entfernt. Originalarchiv und Screenshot bleiben außerhalb des Repositories. Der Screenshot bestätigt die Motoridentität und die VCDS-Anzeige „Protokoll: CAN“; das beweist keine bestimmte Transportvariante, CAN-ID oder Rohskalierung.

## Tatsächliche Steuergeräte

| Adresse | SW-Teilenummer | HW-Teilenummer | ASAM | Ergebnis |
| --- | --- | --- | --- | --- |
| 01 | 03C 906 027 CR | 03C 907 309 B | nicht im Scan angegeben | 0 Fehler |
| 03 | 1K0 907 379 BJ | 1K0 907 379 BJ | nicht im Scan angegeben | 0 Fehler |
| 08 | 5K0 907 044 ES | 5K0 907 044 ES | EV_Climatronic A01010 | 0 Fehler |
| 09 | 5K0 937 086 J | 5K0 937 086 J | nicht im Scan angegeben | 3 Fehler |
| 10 | 3AA 919 475 L | 4H0 919 475 E | EV_EPHVA2C000000000 002008 | 0 Fehler |
| 15 | 1T0 909 605 E | 1T0 909 605 E | nicht im Scan angegeben | 0 Fehler |
| 16 | 5K0 953 501 BH | 5K0 953 569 L | EV_VW360SteerWheelUDS A03004 | 1 Fehler |
| 17 | 1T0 920 875 | 1T0 920 875 | EV_Kombi_UDS_VDD_RM09 A04114 | 0 Fehler |
| 19 | 7N0 907 530 J | 1K0 907 951 | nicht im Scan angegeben | 3 Fehler |
| 20 | 5K0 857 511 B | 8P0 857 511 C | nicht im Scan angegeben | 0 Fehler |
| 25 | 5K0 953 234 | 5K0 953 234 | EV_Immo_UDS_VDD_RM09 A03009 | 0 Fehler |
| 37 | — | — | nicht im Scan angegeben | nicht erreichbar |
| 42 | 1T0 959 701 AA | 1T0 959 701 AA | nicht im Scan angegeben | 0 Fehler |
| 44 | 5N1 909 144 M | 5N1 909 148 G | nicht im Scan angegeben | 0 Fehler |
| 52 | 1T0 959 702 S | 1T0 959 702 S | nicht im Scan angegeben | 0 Fehler |
| 56 | — | — | nicht im Scan angegeben | nicht erreichbar |
| 61 | 7N0 907 534 | 1K0 907 951 | nicht im Scan angegeben | 0 Fehler |
| 62 | 5K0 959 703 A | 5K0 959 703 A | nicht im Scan angegeben | 0 Fehler |
| 72 | 5K0 959 704 A | 5K0 959 704 A | nicht im Scan angegeben | 0 Fehler |
| 77 | — | — | nicht im Scan angegeben | nicht erreichbar |

Der Scan nennt beim BCM zusätzlich Wischer `1T1 955 119 A` und Regen-/Lichtsensor `1K0 955 559 AH`, bei der Lenkradelektronik das MFL-Modul `5K0 959 542 A`. Diese Subsysteme bleiben dem jeweiligen Hauptmodul zugeordnet. Die vollständigen Software-/Komponentenangaben und ROD-Zuordnungen stehen im Scan und im Inventar.

## Abgleich mit Labels.zip

16 von 17 gemeldeten Hauptmodul-Labelnamen sind im zuvor gelieferten Archiv vorhanden; 15 davon sind binär und nur das Airbaglabel ist lesbar. Beim Kombiinstrument wurde der VCDS-Zusatz `-SRI1` für die Dateisuche getrennt; der originale Name bleibt im Inventar erhalten. **Das exakte ABS-Label `DRV/1K0-907-379-60EC1F.clb` fehlt.** Ähnliche MK60EC1-Dateien sind kein belegter Ersatz. Die Ergebnisse mit Dateihashes stehen in `label-matches.json`.

Gleiche Dateinamen belegen nicht, dass das frühere Labels.zip exakt den Datenstand der VCDS-Aufnahme enthält. Die im Scan genannten ROD-Dateien sind weder in Touran.zip noch im Labels.zip enthalten. Die UDS-Maps liefern bereits Namen und Einheiten; Rohdiagnosedefinitionen fehlen weiterhin.

## Neue belastbare Messwertquellen

- **Motoröltemperatur:** Adresse 17, SW/HW `1T0 920 875`, ASAM `EV_Kombi_UDS_VDD_RM09 A04114`, **IDE00196 / Motoröltemperatur / 25.0 °C** in beiden Maps. Außerdem IDE00151 Ölfüllstand und IDE00302 Öldruckschalterstatus. Ein Schalterstatus ist keine kontinuierliche Öldruckmessung. Der alte Familienlabel-Hinweis MWB 003/Feld 3 wird hier nicht verwendet.
- **Luftmassenstrom:** Motor-MWB 210/Feld 3 enthält **0.00 und 2.67 g/s**. Die Einheit ist am tatsächlichen Motor belegt, die Rohantwort-Skalierung noch nicht. Die anderen Felder der Gruppe werden nicht mit erfundenen Bedeutungen versehen.
- **Motor-MWB 134:** vier Temperaturfelder; Feld 1 zeigt **26/27 °C**, das Kombiinstrument zeitversetzt 25 °C. Das macht Feld 1 plausibel als Kandidat, beweist aber ohne CAV-Labelbeschreibung nicht die Bedeutung Öltemperatur.
- **OBD:** 33 PID-Zeilen in beiden Exports, einschließlich der bisher in der Liste fehlenden 03, 13, 15 und 1C. Keine Zeile für 10 oder 5C; ohne Supportbitmap ist das kein definitiver Nachweis fehlender Unterstützung.
- **Klima/Parkhilfe/Lenkrad:** benannte UDS-Listen für Kompressor, Temperaturen, Klappen, Parksystem und Eingangssignale. Es gibt nun fahrzeugspezifische Messwertverzeichnisse statt allgemeiner Ausstattungsvermutungen.

IDE-/ENG-/MAS-Bezeichnungen sind VCDS-Datenschlüssel und dürfen nicht direkt als numerische UDS-DIDs gesendet werden. Eine Beobachtung eines physikalischen Wertes liefert noch keine Zuordnung zu passiven CAN-Frames.

## Gespeicherte Fehler: sieben Einträge

| Modul | Code | VCDS-Befund |
| --- | --- | --- |
| 09 | 00978 / 010 | Abblendlicht links: Unterbrechung/Kurzschluss nach Plus |
| 09 | 01134 / 012 | Alarmhorn: elektrischer Fehler im Stromkreis |
| 09 | 02394 / 010 | Standlicht vorne links: Unterbrechung/Kurzschluss nach Plus, sporadisch |
| 16 | 226057 / B1147 F1 | Zündanlassschalter/Klemme S: Unterbrechung/Kurzschluss nach Masse, sporadisch, bestätigt |
| 19 | 01305 / 004 | Infotainment-Datenbus: keine Kommunikation, sporadisch |
| 19 | 01303 / 004 | Telefonmodul: keine Kommunikation |
| 19 | 01044 / 000 | Steuergerät falsch codiert |

37 Navigation, 56 Radio und 77 Telefon sind nicht erreichbar; die Übersichtsstatus unterscheiden sich teils davon (37 nicht angemeldet, 56 Fehler). Beide Beobachtungen bleiben erhalten. Beim Radio-Umbau sind Gateway-Anmeldung und tatsächliche Ausstattung zu vergleichen, ohne automatisch Codierungen zu ändern. Auch Adresse 61 Batterieregelung ist tatsächlich erreichbar, unabhängig von der bisherigen PR-Interpretation.

Die Angaben sind gespeicherte Scanbefunde, keine Ferndiagnose eines defekten Bauteils. Mehrere Umgebungsdaten tragen Datum 2021.02.25, während der Scan vom 05.10.2026 stammt; aus diesen Zeitstempeln werden keine verlässlichen aktuellen Fehlerzeitpunkte abgeleitet. Fehlerfreiheit des Motors/ABS/Airbags im Scan bestätigt keine mechanische Fehlerfreiheit.

## Nächste Daten für die App-Implementierung

Ein erneuter allgemeiner Scan ist für die Identifikation nicht erforderlich. Gezielt ergänzen:

1. Gateway-Verbauliste als separate Aufnahme, besonders 37/46/56/61/77 und Radio-Nachrüstung.
2. Erweiterte Identifikation und, falls VCDS dies unterstützt, benannte Messwertansicht des Motors für 134 und 210. Dafür weder Login noch Grundeinstellung ausführen.
3. Korrektes ABS-Label aus einer nachvollziehbaren Datenquelle bzw. exportierte benannte ABS-Messwertinformationen. Die übrigen ROD-Zuordnungen sind bekannt, ihre transportrelevanten Definitionen noch nicht verfügbar.
4. Kontrollierte Referenzaufnahme für Rohdiagnoseverkehr/Transport und Skalierung je Modul. Die vorliegenden CSVs allein reichen dafür nicht. Exakte Rahmenbedingungen werden vor einer solchen Aufnahme festgelegt; kein blindes Senden vermuteter IDs.
5. Nach implementiertem Transport die App-Fehler und Messwerte gegen VCDS vergleichen. Bis dahin sind diese Dateien Referenzdaten, keine neu aktivierte Fahrzeugdiagnose.
