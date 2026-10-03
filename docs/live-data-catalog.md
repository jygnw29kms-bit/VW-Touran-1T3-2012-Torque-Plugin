# Live-data catalogue

## Standard OBD-II data

The following classes are expected to be discoverable through standard Mode 01 where supported by the ECU:

- engine RPM
- vehicle speed
- calculated engine load
- coolant temperature
- intake air temperature
- intake manifold absolute pressure
- mass air flow
- throttle position
- ignition timing advance
- short- and long-term fuel trim
- oxygen/lambda related values where exposed
- fuel level
- barometric pressure
- control module voltage
- ambient air temperature
- accelerator pedal position
- commanded throttle actuator
- fuel rail pressure when supported
- commanded equivalence ratio when supported

The ECU support bitmap is authoritative; unsupported PIDs must not be presented as live values.

## VW/VAG-specific data to inventory

The project is structured to capture and classify manufacturer-specific values including, where the ECU exposes them:

### Engine / combustion
- cylinder misfire counters
- knock correction / retard per cylinder
- ignition angle
- requested vs actual engine torque
- load request / actual load
- engine operating state

### Boost / air path
- requested boost pressure
- actual boost pressure
- ambient pressure
- intake pressure
- throttle angle requested/actual
- intake air temperature
- air mass requested/actual

### Fuel system
- high-pressure fuel requested/actual
- low-pressure fuel value if exposed
- injection time
- lambda requested/actual
- fuel trims / adaptation values
- fuel system status

### Cooling / temperatures
- engine coolant temperature
- intake air temperature
- ambient temperature
- catalyst temperature where exposed
- oil temperature where exposed

### Camshaft / timing
- camshaft adaptation
- specified/actual cam angle
- camshaft control status

### Electrical
- battery/control-module voltage
- alternator/load values where exposed

### Emissions
- catalyst state
- oxygen sensor/lambda values
- EVAP status
- readiness related values

### Vehicle / drivetrain
- vehicle speed
- clutch/brake status where exposed to the engine ECU
- cruise-control state where exposed

## Why discovery is required

VW measuring groups and advanced measuring values vary by controller and software. A group number alone is not a universal semantic identifier. The VCDS export from the real vehicle is therefore stored as a capture and transformed into an inventory rather than guessed.
