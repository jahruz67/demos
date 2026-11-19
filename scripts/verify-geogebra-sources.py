#!/usr/bin/env python3
"""Verify the pinned archive, each member, and optionally an upstream checkout."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--upstream", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1] / "third_party/geogebra"
manifest = json.loads((root / "manifest.json").read_text())
archive = root / f"geogebra-{manifest['version']}-sources.zip"
digest = lambda data: hashlib.sha256(data).hexdigest()
assert digest(archive.read_bytes()) == manifest["sha256"], "Archive checksum mismatch"
with ZipFile(archive) as source:
    assert set(source.namelist()) == set(manifest["files"]), "Archive membership mismatch"
    for name, expected in manifest["files"].items():
        assert digest(source.read(name)) == expected, f"Archive member mismatch: {name}"
        if args.upstream:
            assert digest((args.upstream / name).read_bytes()) == expected, f"Upstream mismatch: {name}"
print(f"Verified {len(manifest['files'])} original files at {manifest['commit']}")
