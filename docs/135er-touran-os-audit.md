# 135er Touran OS ? Radio Audit

TouranLive 0.5.2 adds a user-triggered, read-only radio system audit.

Collected (no IMEI/serial/MAC/Android ID): Android/build/SOC/board/hardware/bootloader, ABI, kernel-exposed CPU/RAM/storage/partitions/mounts, 1024x600 display metrics/density/refresh, relevant system properties, USB/Bluetooth/Wi-Fi/GPS/touch capabilities, and only head-unit-relevant packages (radio/MCU/CAN/CarPlay/launcher/DSP/etc.).

The report is uploaded only after the user presses `RADIO SYSTEMSCAN + SEND` to the existing private Touran report endpoint. It is intended to determine whether a safe custom 135er Touran OS/ROM is feasible and what vendor components must be preserved.

Before any ROM flashing, we still require verified bootloader/partition/recovery/MCU information and a complete backup of current boot/vendor/system partitions where the platform permits it.
