"""Compare two same-input APK builds without treating ZIP metadata as payload.
Report both full SHA-256 and per-entry SHA-256; only exact payload agreement passes.
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

def digest(data):
    return hashlib.sha256(data).hexdigest()

def inspect(path):
    path = Path(path)
    with zipfile.ZipFile(path) as apk:
        if apk.testzip() is not None:
            raise ValueError("Corrupt APK")
        names = [i.filename for i in apk.infolist() if not i.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate APK entries")
        # APK v1 signer files are excluded; v2/v3 signing blocks are outside ZIP entries.
        payload = {n: digest(apk.read(n)) for n in names
                   if not (n.startswith("META-INF/") and
                           (n.endswith((".SF", ".RSA", ".DSA", ".EC")) or n == "META-INF/MANIFEST.MF"))}
        if "AndroidManifest.xml" not in payload or "classes.dex" not in payload:
            raise ValueError("Missing APK core")
    return {"sha256": digest(path.read_bytes()), "payload": payload}

def compare(a, b):
    left, right = inspect(a), inspect(b)
    changed = sorted(k for k in left["payload"].keys() | right["payload"].keys()
                     if left["payload"].get(k) != right["payload"].get(k))
    return {"first": left, "second": right,
            "byte_identical": left["sha256"] == right["sha256"],
            "payload_identical": not changed, "changed_entries": changed}

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: compare_apks.py FIRST SECOND REPORT.json")
    result = compare(sys.argv[1], sys.argv[2])
    Path(sys.argv[3]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print("Byte-identical:", result["byte_identical"])
    print("Payload-identical:", result["payload_identical"])
    if not result["payload_identical"]:
        print("Changed entries:", result["changed_entries"][:50])
        raise SystemExit(1)
    print("PASS: identical APK payload after two clean builds")
