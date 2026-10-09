#!/usr/bin/env python3
"""Index sanitized VCDS exports; IDE names are not wire-level diagnostic DIDs."""
import argparse
import csv
import json
import re
from pathlib import Path


def index(folder):
    ecus, maps, statuses = [], [], []
    manifest_path = folder / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    sources = {f["file"]: f["source_relative_path"] for f in manifest.get("files", [])}
    for path in sorted(folder.iterdir()):
        if path.suffix.lower() not in {".txt", ".csv", ".log"}:
            continue
        text = path.read_text(encoding="utf-8")
        if path.suffix.lower() == ".csv":
            rows = list(csv.reader(text.splitlines()))
            identity = re.search(r";SW:(.*?)\s+HW:(.*?)\s+---\s*(.*)", text)
            header = next((r for r in rows if r and r[0] == "Block"), None)
            obd = ";33-OBD2" in text
            original_name = sources.get(path.name, path.name)
            address = re.search(r"blockmap-([0-9A-F]{2}|OBD)[-_]", original_name, re.I)
            values = []
            for line, row in enumerate(rows, 1):
                if row and (re.fullmatch(r"\d+", row[0]) or re.match(r"(?:IDE|ENG)\d+", row[0])
                            or (obd and re.fullmatch(r"[0-9A-F]{2}", row[0]))):
                    values.append({"line": line, "key": row[0], "cells": row[1:]})
            maps.append({"source": path.name, "address": address[1].upper() if address else None,
                         "software_part": identity[1].strip() if identity else None,
                         "hardware_part": identity[2].strip() if identity else None,
                         "format": "obd_snapshot" if obd else "named_measurements" if header and "Beschreibung" in header else "numbered_block_snapshot",
                         "header": header, "measurements": values,
                         "semantics": "Named rows retain VCDS names; numbered block fields require matching labels. Neither supplies CAN IDs, raw payloads or scaling."})
        else:
            for match in re.finditer(r"^([0-9A-F]{2})-(.*?) -- Status: (.*)$", text, re.M):
                statuses.append({"address": match[1], "name": match[2], "status": match[3].strip(), "source": path.name})
            sections = list(re.finditer(r"^Adresse ([0-9A-F]{2}):\s*(.*)$", text, re.M))
            for n, match in enumerate(sections):
                body = text[match.end():sections[n+1].start() if n+1 < len(sections) else len(text)]
                identity = re.search(r"Teilenummer SW:\s*(.*?)\s+HW:\s*(.*)", body)
                def field(pattern):
                    found = re.search(pattern, body, re.M)
                    return found[1].strip() if found else None
                label = re.search(r"Labeldatei:[.| ]*([^\r\n]+)", match[2])
                ecus.append({"address": match[1], "source": path.name,
                             "source_line": text[:match.start()].count('\n')+1,
                             "name": match[2].split("Labeldatei:")[0].strip(),
                             "label": label[1].strip() if label else None,
                             "software_part": identity[1].strip() if identity else None,
                             "hardware_part": identity[2].strip() if identity else None,
                             "component": field(r"^\s*Bauteil:\s*(.*)$"),
                             "asam": field(r"^\s*ASAM Datensatz:\s*(.*)$"),
                             "rod": field(r"^\s*ROD:\s*(.*)$"),
                             "reachable": "nicht erreichbar" not in body,
                             "dtc_count": int(field(r"^(\d+) Fehler(?:codes gefunden| gefunden)" ) or 0) if "Kein(e) Fehlercode(s) gefunden." in body or re.search(r"^\d+ Fehler",body,re.M) else None})
    return {"ecus": ecus, "scan_statuses": statuses, "maps": maps,
            "completeness": "capture_inventory_only_not_complete_diagnosis"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = index(args.folder)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{len(result['ecus'])} scan sections, {len(result['maps'])} maps indexed.")


if __name__ == "__main__":
    main()
