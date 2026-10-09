# VCDS: Datensammlung für den Touran

Ziel: Steuergeräte und Softwarestände exakt zuordnen, Fehler samt Umgebungsdaten erhalten und verfügbare Messwerte für die App dokumentieren. Ein Auto-Scan ist der erste Schritt; Messwertdefinitionen stehen meist in separaten Exporten.

`tools/collect_vcds.py` sammelt gespeicherte Exporte. Es steuert VCDS nicht und greift nicht auf das Fahrzeug zu. Eine automatische Übernahme der VCDS-Oberfläche ist derzeit nicht implementiert. Der erneut gelieferte Scan nennt **PCI 23.11.0.1**, die Maps **PCI 23.11.0**, Datenstand **20240306 DS351.0**; das ist die nun belegte Version der Aufnahme. Der Nutzer verwendet aktuell Windows 10, während der Scanheader Windows 7 x64 nennt. Die vorhandene Aufnahme enthält bereits den Auto-Scan und zwei Maps je exportiertem Ziel: [Auswertung](touran-vcds-2026-10-05.md). Fehlende Menüs bitte dokumentieren, statt andere Diagnosefunktionen auszuprobieren.

## 1. Arbeitsplatz vorbereiten

1. Windows-Laptop und Diagnoseinterface anschließen. Fahrzeug steht sicher, Zündung eingeschaltet, Motor zunächst aus. Für längere Sitzungen eine geeignete Spannungsversorgung verwenden.
2. VCDS starten. Unter **Über/About** genaue Programm- und Datenversion notieren; Interface-Typ ergänzen. Keine Lizenz- oder vollständigen Interface-Seriennummern veröffentlichen.
3. Einen eigenen Ordner `C:\Touran-VCDS-Rohdaten` erstellen. Dort ausschließlich die Exporte dieses Tourans sammeln. VCDS kann zunächst in seinem eigenen Ordner `Logs` speichern; diese Dateien anschließend in den Sammelordner kopieren.
4. Notiz `sitzung.txt` erstellen: Datum, VCDS-Version, Interface-Typ, Zündung/Motorzustand, ungefährer Kühlmitteltemperaturbereich und bekannte Nachrüstungen. VIN und Kennzeichen sind für das öffentliche Projekt nicht nötig.

## 2. Vollständigen Auto-Scan speichern

1. **Auto-Scan** öffnen. Die zur Touran-Plattform passende Auswahl verwenden; falls unterstützt, automatische Erkennung über CAN nutzen.
2. Scan bis zum Ende laufen lassen und über **Speichern/Save** vollständig sichern. Die gesamte TXT-Datei übernehmen, nicht nur die Fehlerliste.
3. **Keine Fehler löschen.** Vorhandene Fehler, Häufigkeiten und Umgebungsdaten gehören zur Aufnahme.
4. Wichtig sind für jedes Steuergerät: Adresse, Labeldatei, Teilenummer SW/HW, Komponente, Softwarestand, Codierung, ASAM-Datensatz/ROD-Zuordnung, Subsysteme und Fehler einschließlich Status. Nicht verfügbare Angaben müssen nicht ergänzt oder geraten werden.

## 3. Gateway und Steuergeräteidentifikation ergänzen

1. **Steuergerät auswählen → 19 CAN-Gateway → Verbauliste/Installation List**, sofern vorhanden. Liste als Text sichern oder einen Screenshot lokal speichern; unterscheiden zwischen verbaut, erreichbar und fehlerhaft.
2. Bei jedem im Scan tatsächlich vorhandenen Steuergerät die Identifikation prüfen. Falls **Erweiterte Identifikation/Advanced ID** verfügbar ist, Ergebnisse speichern oder als TXT abschreiben, insbesondere SW/HW und Datensatzkennung.
3. Priorität: **01 Motor**, **19 Gateway**, **17 Kombiinstrument**, **03 ABS**, **08 Klima**, **09 Zentralelektrik**, **44 Lenkhilfe**, **16 Lenkradelektronik**, danach übrige tatsächlich verbaute Module und Subsysteme. Keine Steuergeräte allein wegen einer theoretischen Ausstattung voraussetzen.

## 4. Messwert-Kanalmaps exportieren

1. Zur VCDS-Hauptansicht zurückkehren. Unter **Anwendungen/Applications → Steuergeräte-Kanalmapping/Controller Channels Map** die Adresse eines vorhandenen Steuergeräts eingeben.
2. Als Funktion **Messwerte/Measuring Values** und als Ausgabe **CSV** auswählen, soweit deine Version dies anbietet. Sicherheitszugang/Login leer lassen.
3. Zunächst **01**, **17**, **19**, **03**, **08** exportieren; danach die übrigen im Scan vorhandenen Steuergeräte. Jeden Export speichern und Adresse sowie Motorzustand in `sitzung.txt` zuordnen.
4. Unterstützt die Version mehrere Messwertarten, dokumentieren, ob klassische Messwertblöcke oder erweiterte Messwerte gelesen wurden. Vorhandene Namen, Gruppen-/IDE-Kennungen, Einheiten und Werte vollständig erhalten.
5. Meldet VCDS fehlende ROD-/Labeldaten, nicht unterstützte Funktion oder Zugriffsfehler, den genauen Meldungstext notieren. Ein fehlgeschlagener Export zählt nicht als „keine Messwerte vorhanden“.

**Nicht ausführen:** Codierung, Anpassung, Grundeinstellung, Stellglieddiagnose, Login/Sicherheitszugang oder Fehlerlöschen. Sie werden für diese erste Datensammlung nicht benötigt. Auch eine reine Messwertabfrage erzeugt Diagnoseverkehr; sie ist kein passives CAN-Mithören.

## 5. Gezielt Livewerte protokollieren

Nach den Identifikationen und Maps lassen sich passende Kanäle auswählen. Für einen ersten Log nur Werte wählen, die VCDS mit eindeutiger Bezeichnung tatsächlich anbietet; keine fremden Motor-Messwertblöcke übernehmen.

- Motor: Drehzahl, Kühlmitteltemperatur, Ansauglufttemperatur, Öltemperatur falls verfügbar, Bordspannung, Saugrohr-/Ladedruck Soll und Ist, Kraftstoffdruck, Lambda und Fehlzündungszähler.
- Kombiinstrument: Öltemperatur, Kühlmitteltemperatur, Ölstand und Spannung, soweit angeboten. Öltemperatur in Gruppe 003/Feld 3 ist nur ein Hinweis aus einem Familienlabel, noch keine Bestätigung für dein Steuergerät.
- Klima: Temperaturen, Druck und Kompressorstatus, soweit angeboten.

Im Freien im Stand etwa 60 Sekunden Leerlauf protokollieren. Anzahl der Werte so begrenzen, dass eine brauchbare Abtastrate bleibt; gegebenenfalls mehrere kurze Logs aufnehmen. Betriebszustand und ausgewählte Kanäle notieren. Eine Fahrtaufnahme ist für das erste Datenpaket nicht erforderlich.

Diese Exporte zeigen VCDS-Bezeichnungen und Werte. Sie liefern nicht automatisch die CAN-Routingadressen, Diagnose-DIDs, Rohbytes oder Skalierungsformeln für unsere eigene App. Solche Zuordnungen müssen anschließend separat belegt und am Fahrzeug geprüft werden.

## 6. Paket aufbereiten

Python 3 auf Windows verwenden. Im Repository-Ordner in PowerShell ausführen:

```powershell
py -3 tools\collect_vcds.py --source "C:\Touran-VCDS-Rohdaten" --output "captures\touran-vcds-aufnahme-01" --vcds-version "HIER genaue Version eintragen"
```

Der Ausgabeordner darf noch nicht existieren und muss außerhalb des Quellordners liegen. Das Werkzeug übernimmt TXT-, CSV- und LOG-Dateien rekursiv, liest UTF-8, UTF-16 oder Windows-1252, ersetzt VIN-artige 17-stellige Zeichenfolgen und erstellt ein Manifest mit Prüfsummen und gefundenen Steuergeräteadressen. Originale bleiben erhalten. Bilder, PDFs, Programme und VCDS-Labeldateien werden nicht übernommen.

**Vor dem Git-Upload alle Ausgabedateien einschließlich `manifest.json` prüfen:** Die automatische Ersetzung garantiert keine vollständige Anonymisierung und kann andere 17-stellige Kennungen mit ersetzen. Namen, Kennzeichen, Seriennummern und ursprüngliche relative Dateinamen können weiterhin enthalten sein. Nur den geprüften Ausgabeordner hochladen, nicht den Rohdatenordner oder die gesamte VCDS-Installation.

## 7. Abschlusscheck

- Vollständiger Auto-Scan vorhanden, genaue VCDS-Version dokumentiert.
- Gateway-Verbauliste und Identifikation vorhandener Module dokumentiert, soweit unterstützt.
- Messwert-Maps mindestens für Motor und Kombiinstrument, möglichst für alle erreichbaren Module.
- Fehlende Exporte und VCDS-Meldungen ausdrücklich notiert.
- Live-Logs mit Betriebszustand, falls aufgenommen.
- Persönliche Daten vor dem Upload geprüft.

Das Paket ist eine Grundlage für die Erweiterung der App, kein Nachweis einer vollständigen Diagnose oder der Fehlerfreiheit des Fahrzeugs. Aus dem Scan werden zunächst die Labels eindeutig zugeordnet; anschließend werden Transport, Messwerte und Herstellerfehler für jedes Modul validiert.
