# Confirmed CAVC OBD/VCDS mapping

This project uses the owner's 05.10.2026 VCDS blockmap as the vehicle-specific truth layer.

## Confirmed standard OBD Mode-01 PIDs

`0104 0105 0106 0107 010B 010C 010D 010E 010F 0111 011F 0121 0123 012E 0130 0131 0133 0134 013C 0142 0143 0144 0145 0146 0147 0149 014A 014C 0156`

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
- 134: project oil-temperature candidate from the captured blockmap
- 210: MAF
- 106: rail pressure
- 031/032: lambda/adaptation
- 020: knock retard per cylinder
- 090/091/093: camshaft data
- 015/016: misfire counters

A failure of one optional VAG block must not kill the complete VAG session. Only repeated failure of the realtime core block triggers a full OBD fallback.
