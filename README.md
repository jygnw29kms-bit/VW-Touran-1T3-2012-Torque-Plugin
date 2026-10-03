# VW Touran 1T3 2012 – Torque / OBD Live Data Plugin

Target vehicle:
- Volkswagen Touran 1T3
- Model year: 2012
- HSN/TSN: 0603 / AIH
- Engine: 1.4 TSI, 103 kW / 140 PS
- Engine code target: CAVC
- Fuel: petrol / direct injection

This repository provides a practical live-data pack for Torque-compatible OBD adapters plus tools to inventory the vehicle-specific VW/VAG measuring values actually exposed by the ECU.

## What is included

- Torque extended PID CSV for useful SAE/OBD-II live data.
- OBD support scanner that queries the ECU before assuming a PID exists.
- VCDS controller-map importer for cataloguing VW-specific measuring values.
- Capture folder for real vehicle exports and validation.
- Documentation for dashboard fields and validation.

## Important design rule

No undocumented VW-specific PID is treated as guaranteed. SAE/OBD-II PIDs are standardized, but manufacturer-specific values depend on ECU software, protocol, control module and dataset. The VCDS map workflow is therefore the source of truth for the exact car.

## Quick start

1. Copy `torque/VW_Touran_1T3_2012_0603_AIH_CAVC.csv` to the Torque extended PID folder or import it through Torque.
2. Connect the OBD adapter.
3. Run `python tools/obd_probe.py --port AUTO` on a laptop/Raspberry Pi with python-obd to discover supported standard PIDs.
4. In VCDS create a controller channel map for address 01-Engine and place the CSV in `captures/`.
5. Run `python tools/vcds_map_import.py captures/<file>.csv`.
6. Commit the generated inventory so the repository can be refined against the real ECU.

## Safety

Do not watch live data while driving. Use a passenger or log data for later review.
