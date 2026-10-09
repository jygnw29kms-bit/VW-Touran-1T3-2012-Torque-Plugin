# Confirmed CAVC OBD/VCDS mapping

The owner re-supplied the 05.10.2026 Auto-Scan and blockmaps as `Touran.zip` on 09.10.2026. Sanitized exports and their inventory are now in [vcds/logs/2026-10-05](../vcds/logs/2026-10-05/README.md). The engine is SW `03C 906 027 CR`, HW `03C 907 309 B`, MED17.5.5 G01 1669. See [capture analysis](touran-vcds-2026-10-05.md).

## Confirmed standard OBD Mode-01 PIDs

`0103 0104 0105 0106 0107 010B 010C 010D 010E 010F 0111 0113 0115 011C 011F 0121 0123 012E 0130 0131 0133 0134 013C 0142 0143 0144 0145 0146 0147 0149 014A 014C 0156`

These 33 PIDs have actual rows in both supplied OBD snapshots. No support-bitmap/raw-response export is included; absence of a PID here is not proof that the ECU rejects it.

Important confirmed fallbacks used by the master UI:
- RPM: 010C
- speed: 010D
- coolant: 0105, formula A-40 °C
- intake air: 010F, formula A-40 °C
- ambient air: 0146, formula A-40 °C
- ECU voltage: 0142
- calculated load: 0104
- throttle: 0111
- accelerator pedal: 0149
- ignition advance: 010E
- rail pressure: 0123
- lambda actual: 0134
- commanded lambda: 0144
- STFT/LTFT: 0106/0107
- catalyst temperature: 013C

## Explicitly not confirmed as standard OBD on this car

- 0110 MAF
- 015C oil temperature
- 015E
- 0162
- 0163

The app must not manufacture values for those PIDs. MAF and oil temperature therefore require the verified VAG path or remain unavailable.

## VAG measuring blocks used

- 005: RPM, engine load, vehicle speed — realtime core
- 004: RPM, voltage, coolant, intake temperature
- 003: RPM, manifold pressure, throttle-related field, ignition angle
- 115: RPM, engine load, requested/actual boost absolute
- 113: ambient pressure
- 134: field 1 is a temperature (26/27 °C); oil-temperature semantics remain unconfirmed in the unnamed engine map
- 210: field 3 has air-mass-flow units (0.00/2.67 g/s), now directly evidenced; raw response scaling remains unverified
- 106: rail pressure
- 031/032: lambda/adaptation
- 020: knock retard per cylinder
- 090/091/093: camshaft data
- 015/016: misfire counters

A failure of one optional VAG block must not kill the complete VAG session. Only repeated failure of the realtime core block triggers a full OBD fallback.

## Confirmed named oil-temperature source

Controller **17**, SW/HW `1T0 920 875`, ASAM `EV_Kombi_UDS_VDD_RM09 A04114`: both named maps contain **IDE00196, Motoröltemperatur, 25.0 °C**. This supersedes the generic cluster-family MWB 003/field 3 hint for this vehicle. `IDE00196` is a VCDS data identifier, not an established wire-level UDS DID. No generic OBD 015C or passive CAN decoder follows from this capture.
