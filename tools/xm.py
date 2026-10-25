# Minimal FastTracker 2 (.xm) reader: header, order list, patterns and instrument names.
import struct

class XM:
    def __init__(self, path):
        d = open(path, 'rb').read()
        assert d[:17] == b'Extended Module: ', path
        self.name = d[17:37].split(b'\0')[0].decode('latin-1').strip()
        hsize = struct.unpack_from('<I', d, 60)[0]
        (self.length, self.restart, self.channels, npat, nins, self.flags,
         self.tempo, self.bpm) = struct.unpack_from('<8H', d, 64)
        self.order = list(d[80:80 + self.length])
        p = 60 + hsize
        self.patterns = []
        for _ in range(npat):
            plen, ptype, rows, psize = struct.unpack_from('<IBHH', d, p)
            p += plen
            data = d[p:p + psize]; p += psize
            pat = []
            q = 0
            for r in range(rows):
                row = []
                for c in range(self.channels):
                    if psize == 0:
                        row.append((0, 0, 0, 0, 0)); continue
                    b = data[q]; q += 1
                    if b & 0x80:
                        n = i = v = e = a = 0
                        if b & 1: n = data[q]; q += 1
                        if b & 2: i = data[q]; q += 1
                        if b & 4: v = data[q]; q += 1
                        if b & 8: e = data[q]; q += 1
                        if b & 16: a = data[q]; q += 1
                    else:
                        n = b; i, v, e, a = data[q:q + 4]; q += 4
                    row.append((n, i, v, e, a))
                pat.append(row)
            self.patterns.append(pat)
        self.instruments = []
        for _ in range(nins):
            isize = struct.unpack_from('<I', d, p)[0]
            name = d[p + 4:p + 26].split(b'\0')[0].decode('latin-1').strip()
            ns = struct.unpack_from('<H', d, p + 27)[0]
            samples = []
            if ns:
                keymap = list(d[p + 33:p + 33 + 96])
                q = p + isize
                total = 0
                for s in range(ns):
                    slen, ls, ll, vol, fine, typ, pan, rel = struct.unpack_from('<IIIBbBBb', d, q)
                    sname = d[q + 18:q + 40].split(b'\0')[0].decode('latin-1').strip()
                    samples.append(dict(len=slen, vol=vol, rel=rel, fine=fine, loop=typ & 3, bits16=bool(typ & 16), name=sname,
                                        lstart=ls // (2 if typ & 16 else 1), llen=ll // (2 if typ & 16 else 1)))
                    q += 40; total += slen
                # delta-coded sample data
                for s in samples:
                    raw = d[q:q + s['len']]; q += s['len']
                    out = []; acc = 0
                    if s['bits16']:
                        for k in range(0, len(raw) - 1, 2):
                            acc = (acc + struct.unpack_from('<h', raw, k)[0]) & 0xffff
                            out.append((acc - 65536 if acc > 32767 else acc) / 32768.0)
                    else:
                        for v in raw:
                            acc = (acc + v) & 0xff
                            out.append((acc - 256 if acc > 127 else acc) / 128.0)
                    s['data'] = out
                p = q
            else:
                keymap = [0] * 96
                p += isize
            self.instruments.append(dict(name=name, samples=samples, keymap=keymap))

if __name__ == '__main__':
    import sys
    for f in sys.argv[1:]:
        x = XM(f)
        print(f.split('/')[-1], repr(x.name), 'ch', x.channels, 'len', x.length, 'pats', len(x.patterns), 'tempo', x.tempo, 'bpm', x.bpm)
        for i, ins in enumerate(x.instruments):
            if ins['samples']:
                s = ins['samples'][0]
                print('   %2d %-22s %-22s rel %3d len %6d loop %d n %d' % (i + 1, ins['name'], s['name'], s['rel'], s['len'], s['loop'], len(ins['samples'])))
