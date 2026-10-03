#!/usr/bin/env python3
import argparse
import csv
import json
from pathlib import Path

def sniff(path):
    sample = Path(path).read_text(encoding="utf-8-sig", errors="replace")[:8192]
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters=";,	")
    except csv.Error:
        dialect = csv.excel
        dialect.delimiter = ";"
    return dialect

def main():
    ap = argparse.ArgumentParser(description="Import a VCDS controller channel/map CSV into a normalized inventory.")
    ap.add_argument("input")
    ap.add_argument("--output", default=None)
    args = ap.parse_args()

    path = Path(args.input)
    out = Path(args.output) if args.output else path.with_suffix(".inventory.json")
    dialect = sniff(path)

    rows = []
    with path.open("r", encoding="utf-8-sig", errors="replace", newline="") as f:
        reader = csv.reader(f, dialect)
        for row in reader:
            cleaned = [cell.strip() for cell in row]
            if any(cleaned):
                rows.append(cleaned)

    inventory = {
        "source": str(path),
        "row_count": len(rows),
        "rows": rows,
        "note": "Raw normalized inventory. Semantic mapping must be validated against ECU identity and the labels shown by VCDS."
    }
    out.write_text(json.dumps(inventory, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {out} ({len(rows)} rows)")

if __name__ == "__main__":
    main()
