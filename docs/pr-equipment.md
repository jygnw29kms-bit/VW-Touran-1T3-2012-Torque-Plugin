# Vehicle PR equipment master

Source: owner's VW PR equipment list for the project vehicle. This file is the equipment truth layer beside VCDS/OBD captures. It describes factory equipment; it does not by itself prove a live data channel is readable.

## Identity (non-secret build facts)

- VW Touran 1T3, model year 2012
- Production date: 20.09.2011
- Sales type: 1T33B1
- Engine: CAVC, 1.4 TSI 103 kW / 140 PS Twincharger
- Gearbox: KWB, 6-speed manual
- Front-wheel drive
- Paint: 2K / A3T
- STYLE / Sport-Komfort equipment

The full VIN is intentionally not embedded in the public app/repository.

## App-relevant factory equipment present

- 1AT ESP
- 1N3 speed-sensitive Servotronic
- 2PP leather multifunction steering wheel
- 4A3 front seat heating
- 4L6 auto-dimming interior mirror
- 4LC radio/telephone steering-wheel controls
- 6XQ electrically folding/heated mirrors
- 7X5 Park Assist
- 8G1 high-beam assist
- 8N3 rain/light sensor
- 8T2 cruise control (GRA)
- 8W1 washer-fluid level display
- 8WH fog/cornering lamps
- 9AK Climatronic
- 9Q5 MFA+
- 9Y1 outside-temperature display
- PG1 seven-seat configuration
- UG1 hill-start assist

## Factory equipment absent

- 1D0 no tow bar
- 7K0 no tyre-pressure monitoring
- 7L3 no start/stop and no recuperation
- 7Q0 no factory navigation
- 7Y0 no lane-change assistant
- 9M0 no auxiliary/parking heater
- 9W0 no factory telephone preparation
- QK0 no camera/distance-sensor driver-assistance system
- UF0 no external electrical interface

## App rule

PR presence only enables a feature to be considered plausible. A value is shown live only after a real VAG/VCDS or confirmed standard-OBD response. PR absence prevents the app from advertising a factory feature that the car does not have.
