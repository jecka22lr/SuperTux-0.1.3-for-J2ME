#!/usr/bin/env python3
"""Lossless PNG shrinker for the jar (no optipng here): tries each row filter strategy and zlib
settings, and a palette when the picture has 256 colours or fewer, and keeps the smallest file.
usage: pngsqueeze.py file.png ...   (files are replaced only when smaller)"""
import sys, os, zlib, struct
from PIL import Image

def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

def paeth(a, b, c):
    p = a + b - c; pa = abs(p - a); pb = abs(p - b); pc = abs(p - c)
    return a if pa <= pb and pa <= pc else (b if pb <= pc else c)

def filt(rows, bpp, mode):
    out = bytearray(); prev = bytes(len(rows[0]))
    for r in rows:
        cands = []
        for ft in ([mode] if mode < 5 else range(5)):
            f = bytearray(len(r))
            for i in range(len(r)):
                a = r[i - bpp] if i >= bpp else 0; b = prev[i]; c = prev[i - bpp] if i >= bpp else 0
                x = r[i]
                if ft == 1: x -= a
                elif ft == 2: x -= b
                elif ft == 3: x -= (a + b) >> 1
                elif ft == 4: x -= paeth(a, b, c)
                f[i] = x & 0xff
            cands.append((sum(v if v < 128 else 256 - v for v in f), ft, f))
        _, ft, f = min(cands, key=lambda t: t[0])
        out.append(ft); out += f; prev = r
    return bytes(out)

def encode(img):
    best = None
    variants = []
    im = img.convert('RGBA')
    cols = im.getcolors(256)
    if cols is not None:
        pal = [c for _, c in cols]
        idx = {c: i for i, c in enumerate(pal)}
        px = im.load(); w, h = im.size
        rows = [bytes(idx[px[x, y]] for x in range(w)) for y in range(h)]
        plte = b''.join(bytes(c[:3]) for c in pal)
        trns = bytes(c[3] for c in pal).rstrip(b'\xff')
        extra = chunk(b'PLTE', plte) + (chunk(b'tRNS', trns) if trns else b'')
        variants.append((struct.pack('>IIBBBBB', w, h, 8, 3, 0, 0, 0), extra, rows, 1))
    opaque = im.getextrema()[3][0] == 255
    mode = 'RGB' if opaque else 'RGBA'
    m = im.convert(mode); w, h = m.size; b = m.tobytes(); bpp = 3 if opaque else 4
    rows = [b[y * w * bpp:(y + 1) * w * bpp] for y in range(h)]
    variants.append((struct.pack('>IIBBBBB', w, h, 8, 2 if opaque else 6, 0, 0, 0), b'', rows, bpp))
    for ihdr, extra, rows, bpp in variants:
        for fm in (0, 5) if bpp == 1 else (5, 4, 1, 2):
            raw = filt(rows, bpp, fm)
            for strat in (zlib.Z_DEFAULT_STRATEGY, zlib.Z_FILTERED):
                co = zlib.compressobj(9, zlib.DEFLATED, 15, 9, strat)
                data = co.compress(raw) + co.flush()
                png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + extra + chunk(b'IDAT', data) + chunk(b'IEND', b'')
                if best is None or len(png) < len(best): best = png
    return best

for p in sys.argv[1:]:
    old = os.path.getsize(p)
    img = Image.open(p); img.load()
    new = encode(img)
    if len(new) < old:
        # must decode to the same pixels
        import io
        a = Image.open(io.BytesIO(new)).convert('RGBA').tobytes()
        assert a == img.convert('RGBA').tobytes(), p
        open(p, 'wb').write(new)
    print('%s %d -> %d' % (os.path.basename(p), old, min(old, len(new))))
