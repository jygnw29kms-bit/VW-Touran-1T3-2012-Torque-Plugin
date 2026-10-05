# UI Master and remote diagnostics

Current UI master: **Master Image 1 / OEM performance cluster**, radio target **1024x600 landscape**.

Data priority: VCDS/VAG live data -> standard OBD/ELM327 fallback -> unavailable (`—`). No dummy values.
The visual identity always uses the red 2012 VW Touran 1T3 / CAVC and B-JU 6969.

## Server log / adapter capability scan

Version 0.5.1 adds explicit user actions in Logger:
- `LOG AN SERVER`
- `ADAPTER SCAN + SEND`

The scan is read-only. In VAG mode it probes the known CAVC/MED17.5.5 measuring blocks from the user's VCDS maps. In OBD fallback it records ELM identity/protocol/voltage plus OBD PID support bitmaps. The report is uploaded over HTTPS to `https://www.dezender.de/touran/api/upload-log.php` and contains no VIN by default.
