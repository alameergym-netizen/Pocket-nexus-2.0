"""Build a deterministic development ZIP, not a signed installer."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
FILES = (
    "pc-setup/PocketNexus-PC.bat",
    "pc-setup/PocketNexus-PC.ps1",
    "pc-setup/NexusSecureHost.cs",
    "pc-setup/NexusVibepollo.cs",
    "pc-setup/README.txt",
    "pc-setup/Test-NexusUpgrade.ps1",
    "pc-setup/playnite/extension.yaml",
    "pc-setup/playnite/PocketNexusCatalog.psm1",
    "pc-setup/other-handhelds/ArtemisPocket-USB-Setup.bat",
    "pc-setup/other-handhelds/ArtemisPocket-USB-Setup.ps1",
    "LICENSE.txt",
    "docs/HORIZON_HOST.md",
    "docs/HORIZON_WINDOWS_DELIVERY.md",
)

def build(output):
    entries = {}
    for name in FILES:
        path = ROOT / name
        target = name.removeprefix("pc-setup/")
        entries[target] = path.read_bytes()
    manifest = {"format": 1, "channel": "development", "files": {
        k: hashlib.sha256(v).hexdigest() for k, v in sorted(entries.items())
    }}
    entries["SHA256-MANIFEST.json"] = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data, compresslevel=9)
    with zipfile.ZipFile(output) as z:
        assert z.testzip() is None
        assert set(z.namelist()) == set(entries)
        for name, sha in manifest["files"].items():
            assert hashlib.sha256(z.read(name)).hexdigest() == sha
    return hashlib.sha256(output.read_bytes()).hexdigest()

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: package_host.py OUTPUT.zip")
    path = Path(sys.argv[1])
    first = build(path)
    second = build(path)
    if first != second:
        raise SystemExit("Non-deterministic ZIP")
    path.with_suffix(".zip.sha256").write_text(first + "  " + path.name + "\n", encoding="ascii")
    print("PASS: verified deterministic development archive", first)
