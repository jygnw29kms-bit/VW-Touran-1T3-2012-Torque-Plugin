#!/usr/bin/env python3
import argparse
import json
import sys
from datetime import datetime, timezone

try:
    import obd
except ImportError:
    print("Missing dependency: python-obd. Install with: pip install obd", file=sys.stderr)
    raise

RANGES = [0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0]

def bitmap_to_pids(base, value):
    supported = []
    for bit in range(32):
        if value & (1 << (31 - bit)):
            supported.append(base + bit + 1)
    return supported

def query_raw(connection, pid):
    cmd = obd.OBDCommand(
        name=f"PID_{pid:02X}",
        desc="Support bitmap",
        command=bytes(f"01{pid:02X}", "ascii"),
        bytes_returned=4,
        decoder=lambda messages: messages
    )
    return connection.query(cmd)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", default=None, help="Serial port. Omit for auto-detect.")
    ap.add_argument("--out", default="captures/obd_supported_pids.json")
    args = ap.parse_args()

    connection = obd.OBD(portstr=args.port, fast=False)
    if not connection.is_connected():
        raise SystemExit("No OBD connection.")

    supported = set()
    for base in RANGES:
        cmd_name = f"PIDS_{base+1:02X}_{base+0x20:02X}"
        cmd = getattr(obd.commands, cmd_name, None)
        if cmd is None:
            continue
        response = connection.query(cmd)
        if response.is_null():
            continue
        raw = int(response.value)
        supported.update(bitmap_to_pids(base, raw))

    payload = {
        "captured_at": datetime.now(timezone.utc).isoformat(),
        "port": str(connection.port_name()),
        "supported_mode_01_pids": [f"01{pid:02X}" for pid in sorted(supported)]
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
    print(json.dumps(payload, indent=2))

if __name__ == "__main__":
    main()
