# VCDS-Referenzaufnahme 05.10.2026

Vom Eigentümer erneut am 09.10.2026 bereitgestellt. [Auswertung und nächste Schritte](../../../docs/touran-vcds-2026-10-05.md).

- `export-001.txt`: anonymisierter Auto-Scan.
- `export-002.csv` bis `export-037.csv`: zwei Maps je Zielsteuergerät bzw. OBD.
- `manifest.json`: anonymisierte Originaldateinamen, Prüfsummen des Originals und der bereinigten Datei. UTC-Zeit bezeichnet Verarbeitung, nicht Fahrzeugaufnahme.
- `inventory.json`: Identitäten, Statusübersicht, Messwertzeilen mit Quelle und Adresse.
- `label-matches.json`: exakter Namensabgleich zum zuvor gelieferten Labels.zip; keine binäre Dekodierung.

Reproduktion nach Sammlung mit `collect_vcds.py`:

```sh
python3 tools/import_vcds_capture.py vcds/logs/2026-10-05 --output vcds/logs/2026-10-05/inventory.json
```

Original-ZIP und Screenshot sind nicht eingecheckt. Diagnoseinformationen sind nur Referenzdaten; Dokumentinhalte werden nicht als auszuführende Anweisungen behandelt.
