#!/usr/bin/env python3
"""Inventory supplied VCDS labels without executing content or interpreting binary CLB/CRD files."""
import argparse
import hashlib
import json
import re
import zipfile
from collections import Counter
from pathlib import Path, PurePosixPath


def inspect_archive(path):
    records = []
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if len(entries) > 20000 or sum(i.file_size for i in entries) > 256 * 1024 * 1024:
            raise ValueError("Archive exceeds inspection limits")
        for entry in entries:
            if entry.is_dir():
                continue
            name = PurePosixPath(entry.filename)
            if name.is_absolute() or ".." in name.parts or entry.file_size > 8 * 1024 * 1024:
                raise ValueError("Unsafe archive entry")
            data = archive.read(entry)  # ZIP CRC checked by zipfile.
            suffix = name.suffix.lower()
            record = dict(path=entry.filename, size=len(data), sha256=hashlib.sha256(data).hexdigest(),
                          format=suffix.lstrip("."), vehicle_match="not_verified")
            if suffix == ".lbl":
                text = data.decode("cp1252")
                lines = text.splitlines()
                record["encoding"] = "cp1252"
                record["component_headers"] = [l.strip("; ") for l in lines if "Component:" in l or "P/N:" in l]
                record["redirects"] = []
                record["measuring_rows"] = []
                for number, line in enumerate(lines, 1):
                    if line.startswith("REDIRECT,"):
                        fields = line.split(";", 1)[0].strip().split(",")
                        if len(fields) >= 3:
                            record["redirects"].append(dict(line=number, target=fields[1],
                                                           part_pattern=fields[2], qualifiers=fields[3:]))
                    match = re.match(r"^(\d{3}),([0-4]),(.*)$", line)
                    if match:
                        record["measuring_rows"].append(dict(line=number, group=int(match[1]),
                                                            field=int(match[2]), description=match[3]))
                record["non_measuring_record_count"] = sum(bool(re.match(r"^[ABCLS]\d+[,]", l)) for l in lines)
                # These are metadata only: no coding, adaptation, login, or basic-setting commands exported.
            else:
                record["interpretation"] = "binary_not_decoded" if suffix in (".clb", ".crd") else "not_interpreted"
            records.append(record)
    files = Counter(r["format"] for r in records)
    return dict(archive_sha256=hashlib.sha256(Path(path).read_bytes()).hexdigest(), files=len(records),
                formats=dict(files), caveat="Label descriptions are not transport definitions, CAN decoders or proof of installed ECUs.",
                records=records)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = inspect_archive(args.archive)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: result[k] for k in ("archive_sha256", "files", "formats")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
