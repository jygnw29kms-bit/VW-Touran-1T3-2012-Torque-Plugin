# 135er Touran ESP32 Gateway Protocol v1

Transport-neutral NDJSON. One JSON object per line.

## Discovery / hello

ESP32 -> client:

```json
{"type":"hello","proto":1,"device":"135er-touran-gateway","board":"ESP32-S3-CAN-2CH-U","fw":"0.1.0","readonly":true}
```

## Raw CAN frame

```json
{"type":"can","ch":1,"ts_us":123456789,"id":512,"ext":false,"data":"01A2030405060708"}
```

- `ch`: 1 or 2
- `ts_us`: monotonic ESP timer timestamp
- `id`: numeric CAN identifier
- `ext`: true for 29-bit extended identifier
- `data`: uppercase hex, 0..8 bytes

## Vehicle values

```json
{"type":"value","name":"rpm","value":2184.0,"unit":"rpm","ts_us":123456789,"source":"can1"}
```

Known names:

- rpm
- speed
- map
- boost
- coolant
- oil_temp
- intake_temp
- throttle
- pedal
- engine_load
- ignition
- fuel_pressure
- lambda
- ecu_voltage

Unknown or unverified signals are never fabricated.

## Gateway health

```json
{"type":"health","uptime_ms":123456,"can1_rate":500,"can2_rate":100,"readonly":true,"can1_rx":1234,"can2_rx":456,"drops":0}
```

## Commands

Client -> ESP32:

```json
{"cmd":"hello"}
{"cmd":"capture","enable":true}
{"cmd":"health"}
{"cmd":"filter","ch":1,"ids":[512,640,1024]}
```

Any transmit/diagnostic command is rejected while `readonly=true`.

## Future controlled commands

These are reserved but disabled until real Touran traces have been verified:

- `diag_request` for ISO-TP diagnostic traffic
- `mfa_send` for validated BAP/MFA messages
- `set_mode` to leave read-only state
- `ota` for ESP32 firmware update

## Planned transport priority in Android app

1. USB CDC when physically connected
2. Wi-Fi TCP (`192.168.4.1:13569` in AP mode)
3. BLE service fallback

All transports carry the same NDJSON messages.

## Android v0.7.1 validation contract

Current transport: Wi-Fi TCP only. A connection is ready only after protocol 1, device `135er-touran-gateway`, board `ESP32-S3-CAN-2CH-U` and `readonly=true` have been confirmed. Active-mode hello/health messages disconnect the client. Lines exceeding 4096 characters are discarded through the following newline.

CAN identifiers must fit 11 bits (`ext=false`) or 29 bits (`ext=true`). Channels are 1/2, timestamps are nonnegative integer microseconds, and data is an even-length hex string of at most eight bytes. Invalid input is rejected, never repaired.

Registered `value` units:

| Name | Unit |
| --- | --- |
| rpm | rpm |
| speed | km/h |
| map | kPa (absolute manifold pressure) |
| boost | bar (relative boost; verified decoder required) |
| coolant, oil_temp, intake_temp | °C |
| throttle, pedal, engine_load | % |
| ignition | ° |
| fuel_pressure | bar |
| lambda | λ |
| ecu_voltage | V |

Sources are `can1`/`can2`; values must be finite. A source tag does not itself verify a decoder. The current firmware emits no decoded vehicle values. Signal mapping requires the actual vehicle evidence described in the source audit.

For each signal, timestamps must increase within a gateway connection. Duplicate or older samples do not refresh the display. Freshness expires 4.5 seconds after Android receipt; ESP and Android clocks are not compared. At most the latest sample per signal in each 33-ms window reaches the UI and value log.

The client requests health every five seconds and disconnects after 15 seconds without a valid message. Handshake timeout is five seconds. Recognized firmware rejection:

```json
{"type":"error","code":"READ_ONLY","message":"active CAN transmit disabled"}
```

`capture` is sent but has no acknowledgement in the current firmware. `filter` is documented as a future command and is not implemented by the current firmware. The protocol currently does not preserve RTR/error-frame flags separately.
