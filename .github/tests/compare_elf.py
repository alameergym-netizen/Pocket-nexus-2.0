"""Diagnostic only: compare file-backed ELF64 little-endian sections, including build-ID notes."""
import hashlib
import json
from pathlib import Path
import struct
import sys

def sections(path):
    data = Path(path).read_bytes()
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected ELF64 little endian")
    offset = struct.unpack_from("<Q", data, 40)[0]
    size, count, names_index = struct.unpack_from("<HHH", data, 58)
    if size != 64 or not count or names_index >= count:
        raise ValueError("Unsupported section table")
    headers = [struct.unpack_from("<IIQQQQIIQQ", data, offset + i * size) for i in range(count)]
    names_header = headers[names_index]
    names = data[names_header[4]:names_header[4]+names_header[5]]
    out = {}
    for i, h in enumerate(headers):
        end = names.find(b"\x00", h[0])
        name = names[h[0]:end].decode("utf-8", "replace")
        if h[1] in (0, 8):
            continue
        section = data[h[4]:h[4]+h[5]]
        if len(section) != h[5]:
            raise ValueError("Truncated section")
        out[str(i)+":"+name] = {"size": len(section), "sha256": hashlib.sha256(section).hexdigest()}
    return out

if __name__ == "__main__":
    a,b = sections(sys.argv[1]), sections(sys.argv[2])
    changed = sorted(k for k in a.keys() | b.keys() if a.get(k) != b.get(k))
    Path(sys.argv[3]).write_text(json.dumps({"changed":changed,"first":a,"second":b},indent=2)+"\n")
    print("ELF sections differing:", changed)
