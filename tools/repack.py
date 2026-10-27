#!/usr/bin/env python3
"""Rewrites jars smaller and the same inside: everything deflated at the strongest zlib setting, and
entries that don't shrink (PNGs, MP3s) stored. Manifest first, no data descriptors (old phones).
usage: repack.py file.jar ..."""
import sys, os, zipfile, zlib
for path in sys.argv[1:]:
    z = zipfile.ZipFile(path)
    names = z.namelist()
    meta = [n for n in names if n.upper().startswith('META-INF/')]
    meta.sort(key=lambda n: (n.upper() != 'META-INF/', n.upper() != 'META-INF/MANIFEST.MF', n))
    order = meta + [n for n in names if not n.upper().startswith('META-INF/')]
    data = [(n, z.read(n)) for n in order]
    z.close()
    tmp = path + '.tmp'
    out = zipfile.ZipFile(tmp, 'w')
    for n, d in data:
        info = zipfile.ZipInfo(n, (2026, 1, 1, 0, 0, 0))
        info.external_attr = (0o40755 << 16) | 0x10 if n.endswith('/') else 0o644 << 16
        c = zlib.compressobj(9, zlib.DEFLATED, -15, 9)
        packed = c.compress(d) + c.flush()
        if n.endswith('/') or len(packed) >= len(d):
            info.compress_type = zipfile.ZIP_STORED
            out.writestr(info, d)
        else:
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, d, compresslevel=9)
    out.close()
    old = os.path.getsize(path)
    os.replace(tmp, path)
    print('%s %d -> %d' % (os.path.basename(path), old, os.path.getsize(path)))
