#!/usr/bin/env python3
"""Turns the jars build.sh makes along the way into the released Editions: each gets its own file name
and its own MIDlet name ("SuperTux F", "SuperTux N"...), so the Editions install next to each other on a
phone instead of replacing one another (each keeps its own saves and settings).
usage: edition.py <dist dir> <from.jar> <to name, e.g. SuperTux-Full> <MIDlet name> <description> [extra manifest lines]
Writes <to>.jar and <to>.jad and removes <from>.jar / .jad. Extra lines ("Name: value") are added
to the manifest and the jad (the nHD jars' Nokia scaling attributes)."""
import sys, os, zipfile, zlib

dist, src, dst, name, desc = sys.argv[1:6]
extra = sys.argv[6:]
src_jar = os.path.join(dist, src)
z = zipfile.ZipFile(src_jar)
entries = [(i, z.read(i.filename)) for i in z.infolist()]
z.close()

def unfold(text):
    """manifest lines, with the continuation lines (starting with a space) joined back on"""
    out = []
    for line in text.replace('\r\n', '\n').split('\n'):
        if line.startswith(' ') and out: out[-1] += line[1:]
        else: out.append(line)
    return out

def fold(line):
    """a manifest line cut at 72 bytes, the rest on continuation lines (the jar spec; old phones check)"""
    b = line.encode('latin-1'); parts = [b[:72]]; b = b[72:]
    while b: parts.append(b' ' + b[:71]); b = b[71:]
    return b'\r\n'.join(parts).decode('latin-1')

def manifest(text):
    out = []
    for line in unfold(text):
        if line.startswith('MIDlet-1:'):
            parts = line[len('MIDlet-1:'):].strip().split(',')
            parts[0] = name
            line = 'MIDlet-1: ' + ','.join(parts)
        elif line.startswith('MIDlet-Name:'): line = 'MIDlet-Name: ' + name
        elif line.startswith('MIDlet-Description:'): line = 'MIDlet-Description: ' + desc
        out.append(line)
    while out and not out[-1].strip(): out.pop()
    return out + extra + ['', '']

mf = None
dst_jar = os.path.join(dist, dst + '.jar')
out = zipfile.ZipFile(dst_jar + '.tmp', 'w')
for info, data in entries:              # same order and compression as repack.py left them
    if info.filename.upper() == 'META-INF/MANIFEST.MF':
        mf = manifest(data.decode('latin-1'))
        data = '\r\n'.join(fold(l) if l else l for l in mf).encode('latin-1')
    ni = zipfile.ZipInfo(info.filename, info.date_time)
    ni.external_attr = info.external_attr
    ni.compress_type = info.compress_type
    out.writestr(ni, data, compresslevel=9 if info.compress_type == zipfile.ZIP_DEFLATED else None)
out.close()
os.replace(dst_jar + '.tmp', dst_jar)
assert mf is not None, 'no manifest in ' + src

size = os.path.getsize(dst_jar)
lines = [l for l in mf if l.strip() and not l.startswith('Manifest-Version') and not l.startswith('Created-By')]
jad = '\n'.join(lines + ['MIDlet-Jar-URL: ' + dst + '.jar', 'MIDlet-Jar-Size: %d' % size]) + '\n'
open(os.path.join(dist, dst + '.jad'), 'w').write(jad)
if os.path.abspath(src_jar) != os.path.abspath(dst_jar):
    os.remove(src_jar)
    old_jad = src_jar[:-4] + '.jad'
    if os.path.exists(old_jad): os.remove(old_jad)
print('%s.jar  %s  %d bytes' % (dst, name, size))
