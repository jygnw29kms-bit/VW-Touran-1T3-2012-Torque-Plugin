#!/usr/bin/env python3
"""Collect text exports from VCDS into a new, reviewable folder. No ECU access."""
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

VIN = re.compile(r"(?<![A-Z0-9])(?=[A-HJ-NPR-Z0-9]{0,16}[0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])", re.I)
PRIVATE_LINE = re.compile(r"^.*(?:\bVCID\b|\bVINID\b|Seriennummer|Serial number|Betriebsnr|Werkstattcode|\bWSC\b|Kennzeichen|Reparaturauftrag).*$", re.I | re.M)
ADDRESS = re.compile(r"^(?:Address|Adresse)\s+([0-9A-F]{2})\s*:", re.I | re.M)
EXTENSIONS = {".txt", ".csv", ".log"}


def decode(data):
    if data.startswith((b"\xff\xfe", b"\xfe\xff")):
        return data.decode("utf-16")
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        return data.decode("cp1252")


def collect(source, output, version):
    source, output = source.resolve(), output.resolve()
    if not source.is_dir():
        raise ValueError("Quellordner existiert nicht.")
    if output == source or source in output.parents:
        raise ValueError("Ausgabe muss außerhalb des Quellordners liegen.")
    files = sorted(p for p in source.rglob("*")
                   if p.is_file() and not p.is_symlink() and p.suffix.lower() in EXTENSIONS)
    if not files:
        raise ValueError("Keine TXT-, CSV- oder LOG-Exporte gefunden.")
    # Validate and redact everything before creating the output directory.
    prepared = []
    for path in files:
        if source not in path.resolve().parents:
            raise ValueError("Datei liegt außerhalb des Quellordners.")
        if path.stat().st_size > 32 * 1024 * 1024:
            raise ValueError("Datei größer als 32 MiB: " + path.name)
        raw = path.read_bytes()
        content = decode(raw)
        if "\x00" in content:
            raise ValueError("Binärdatei statt Textexport: " + path.name)
        redacted, replacements = VIN.subn("[VIN_REDACTED]", content)
        redacted, private_lines = PRIVATE_LINE.subn("[PRIVATE_FIELD_REDACTED]", redacted)
        relative = path.relative_to(source).as_posix()
        prepared.append((relative, redacted.encode("utf-8"), {
            "source_relative_path": VIN.sub("[VIN_REDACTED]", relative),
            "original_sha256": hashlib.sha256(raw).hexdigest(),
            "vin_like_tokens_redacted": replacements,
            "private_lines_redacted": private_lines,
            "ecu_addresses": sorted(set(a.upper() for a in ADDRESS.findall(content))),
        }))
    output.mkdir(parents=True, exist_ok=False)
    entries = []
    for index, (relative, data, entry) in enumerate(prepared, 1):
        # Neutral names avoid publishing a VIN or owner name from filenames.
        name = f"export-{index:03d}{Path(relative).suffix.lower()}"
        (output / name).write_bytes(data)
        entry.update(file=name, sha256=hashlib.sha256(data).hexdigest())
        entries.append(entry)
    manifest = {
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "vcds_version_user_supplied": version,
        "files": entries,
        "ecu_addresses_seen": sorted({a for e in entries for a in e["ecu_addresses"]}),
        "diagnostic_completeness": "not_verified",
        "note": "VIN-Muster automatisch ersetzt; Namen, Kennzeichen, Seriennummern und Pfade vor Git-Upload manuell prüfen. Keine Aussage über Kommunikationsprotokoll oder CAN-IDs.",
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{len(entries)} Exporte gesammelt: {output}")
    print("Adressen im Scan: " + (", ".join(manifest["ecu_addresses_seen"]) or "keine erkannt"))
    print("Vor Git-Upload alle Dateien und manifest.json auf persönliche Daten prüfen.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--vcds-version", default="unbekannt")
    args = parser.parse_args()
    try:
        collect(args.source, args.output, args.vcds_version)
    except (ValueError, OSError, UnicodeError) as error:
        parser.exit(1, f"Fehler: {error}\n")


if __name__ == "__main__":
    main()
