# Protracker .mod and Impulse Tracker .it readers for mod2mid, giving the same shape as xm.XM:
# order list, patterns of rows of (note, instrument, volume column, effect, parameter) cells in
# FastTracker 2 terms (note 1 = C-0, 49 = C-4, 97 = note off; volume column 0x10 + volume;
# effects 0x0..0xF, 0x14), and instruments with their sample data as floats.
import math, struct

def load(path):
    """the right reader for the file's contents (ATTNW names MODs, XMs and ITs .mod alike)"""
    d = open(path, 'rb').read(64)
    if d[:17] == b'Extended Module: ':
        from xm import XM
        return XM(path)
    if d[:4] == b'IMPM': return IT(path)
    return MOD(path)

def _sample(name, raw, signed, vol, rate, loop, lstart, llen):
    data = [((b - 256 if b > 127 else b) if signed else b - 128) / 128.0 for b in raw]
    # where XM would put it: rel + fine/128 semitones from C-4 at 8363 Hz
    semis = 12 * math.log(rate / 8363.0, 2) if rate > 0 else 0
    rel = int(math.floor(semis))
    return dict(len=len(data), vol=vol, rel=rel, fine=int(round((semis - rel) * 128)), loop=1 if loop else 0,
                bits16=False, name=name, lstart=lstart, llen=llen, data=data)


class MOD:
    """Protracker / 4-32 channel .mod (M.K., xCHN, xxCH tags)"""
    def __init__(self, path):
        d = open(path, 'rb').read()
        tag = d[1080:1084]
        if tag in (b'M.K.', b'M!K!', b'FLT4', b'4CHN'): ch = 4
        elif tag[1:4] == b'CHN' and tag[:1].isdigit(): ch = int(tag[:1])
        elif tag[2:4] in (b'CH', b'CN') and tag[:2].isdigit(): ch = int(tag[:2])
        else: raise ValueError('unknown MOD type %r' % tag)
        self.name = d[:20].split(b'\0')[0].decode('latin-1').strip()
        self.channels = ch
        self.length = d[950]
        self.restart = 0
        self.order = list(d[952:952 + self.length])
        self.tempo, self.bpm = 6, 125
        npat = max(d[952:952 + 128]) + 1
        p = 1084
        self.patterns = []
        for _ in range(npat):
            pat = []
            for r in range(64):
                row = []
                for c in range(ch):
                    b0, b1, b2, b3 = d[p:p + 4]; p += 4
                    ins = (b0 & 0xf0) | (b2 >> 4)
                    period = ((b0 & 15) << 8) | b1
                    # period 428 plays a sample at 8363 Hz, which is XM's C-4 (49)
                    n = int(round(49 + 12 * math.log(428.0 / period, 2))) if period else 0
                    if n: n = max(1, min(96, n))
                    e, a = b2 & 15, b3
                    if e == 0x0C: row.append((n, ins, 0x10 + min(64, a), 0, 0)); continue
                    row.append((n, ins, 0, e, a))
                pat.append(row)
            self.patterns.append(pat)
        self.instruments = []
        for i in range(31):
            h = 20 + i * 30
            name = d[h:h + 22].split(b'\0')[0].decode('latin-1').strip()
            slen, fine, vol, ls, ll = struct.unpack_from('>HbBHH', d, h + 22)
            slen *= 2; ls *= 2; ll *= 2
            fine = ((fine & 15) ^ 8) - 8          # -8..7 eighths of a semitone
            raw = d[p:p + slen]; p += slen
            rate = 8363 * 2 ** (fine / 96.0)
            s = _sample(name, raw, True, min(64, vol), rate, ll > 2, ls, ll)
            self.instruments.append(dict(name=name, samples=[s] if raw else [], keymap=[0] * 96))


class IT:
    """Impulse Tracker .it (uncompressed 8/16-bit samples; instrument or sample mode)"""
    def __init__(self, path):
        d = open(path, 'rb').read()
        self.name = d[4:30].split(b'\0')[0].decode('latin-1').strip()
        ordn, insn, smpn, patn, cwt, cmwt, flags, special = struct.unpack_from('<8H', d, 0x20)
        self.tempo, self.bpm = d[0x32] or 6, d[0x33] or 125
        order = []
        for o in d[0xC0:0xC0 + ordn]:
            if o == 255: break
            order.append(o)                        # 254 (skip) is past the patterns: mod2mid skips it
        self.order = order; self.length = len(order); self.restart = 0
        q = 0xC0 + ordn
        ins_off = struct.unpack_from('<%dI' % insn, d, q); q += insn * 4
        smp_off = struct.unpack_from('<%dI' % smpn, d, q); q += smpn * 4
        pat_off = struct.unpack_from('<%dI' % patn, d, q)
        samples = []
        for so in smp_off:
            name = d[so + 0x14:so + 0x2E].split(b'\0')[0].decode('latin-1').strip()
            flg, vol = d[so + 0x12], d[so + 0x13]
            cvt = d[so + 0x2E]
            length, lb, le, c5 = struct.unpack_from('<4I', d, so + 0x30)
            ptr = struct.unpack_from('<I', d, so + 0x48)[0]
            raw = b''
            if flg & 1 and not flg & 8:            # present, not compressed
                if flg & 2:                        # 16-bit: keep the high bytes
                    raw = bytes(d[ptr + 1:ptr + 2 * length:2])
                else:
                    raw = d[ptr:ptr + length]
            # C-5 at c5 Hz; XM's C-4 at 8363 Hz is the same note number after the -11 shift below
            samples.append(_sample(name, raw, bool(cvt & 1), min(64, vol), c5, flg & 16, lb, max(0, le - lb)))
        self.instruments = []
        if flags & 4:                              # instrument mode: the samples its keyboard uses
            for io in ins_off:
                name = d[io + 0x20:io + 0x3A].split(b'\0')[0].decode('latin-1').strip()
                kb = d[io + 0x40:io + 0x40 + 240]
                used = []
                for k in range(120):
                    s = kb[2 * k + 1]
                    if s and s - 1 < len(samples) and s - 1 not in used: used.append(s - 1)
                ss = [samples[s] for s in used if samples[s]['data']]
                self.instruments.append(dict(name=name, samples=ss, keymap=[0] * 96))
        else:
            for s in samples:
                self.instruments.append(dict(name=s['name'], samples=[s] if s['data'] else [], keymap=[0] * 96))
        # patterns: IT's packed rows, up to 64 channels
        self.patterns = []
        maxch = 0
        raw_pats = []
        for po in pat_off:
            if po == 0: raw_pats.append([{} for _ in range(64)]); continue   # empty 64-row pattern
            plen, rows = struct.unpack_from('<HH', d, po)
            p = po + 8; end = p + plen
            mask = [0] * 64; last = [[0, 0, 255, 0, 0] for _ in range(64)]
            pat = []
            for r in range(rows):
                cells = {}
                while p < end:
                    cv = d[p]; p += 1
                    if cv == 0: break
                    c = (cv - 1) & 63
                    if cv & 128: mask[c] = d[p]; p += 1
                    m = mask[c]
                    note, ins, vol, cmd, val = 0, 0, 255, 0, 0
                    if m & 1: note = d[p]; p += 1; last[c][0] = note
                    if m & 2: ins = d[p]; p += 1; last[c][1] = ins
                    if m & 4: vol = d[p]; p += 1; last[c][2] = vol
                    if m & 8: cmd, val = d[p], d[p + 1]; p += 2; last[c][3:5] = [cmd, val]
                    if m & 16: note = last[c][0]
                    if m & 32: ins = last[c][1]
                    if m & 64: vol = last[c][2]
                    if m & 128: cmd, val = last[c][3], last[c][4]
                    cells[c] = (note if m & 17 else None, ins, vol, cmd, val)
                    maxch = max(maxch, c + 1)
                pat.append(cells)
            raw_pats.append(pat)
        self.channels = max(1, maxch)
        for pat in raw_pats:
            out = []
            for cells in pat:
                row = []
                for c in range(self.channels):
                    cell = cells.get(c) if isinstance(cells, dict) else None
                    row.append(self._cell(cell) if cell else (0, 0, 0, 0, 0))
                out.append(row)
            self.patterns.append(out or [[(0, 0, 0, 0, 0)] * self.channels])

    @staticmethod
    def _cell(cell):
        note, ins, vol, cmd, val = cell
        n = 0
        if note is not None:
            if note >= 253: n = 97                 # off, cut, fade
            elif note <= 119: n = max(1, min(96, note - 11))   # IT C-5 (60) = XM C-4 (49)
        v = 0x10 + vol if vol <= 64 else 0
        e, a = 0, 0
        L = chr(64 + cmd) if 1 <= cmd <= 26 else ''
        if L == 'A' and val: e, a = 0x0F, min(31, val)                # speed
        elif L == 'T' and val >= 32: e, a = 0x0F, val                 # tempo
        elif L == 'B': e, a = 0x0B, val                               # order jump
        elif L == 'C': e, a = 0x0D, ((min(val, 99) // 10) << 4) | (min(val, 99) % 10)   # row (XM: BCD)
        elif L == 'G': e, a = 0x03, val                               # tone portamento
        elif L == 'J': e, a = 0x00, val                               # arpeggio
        elif L == 'S' and (val >> 4) in (0xC, 0xD, 0xE): e, a = 0x0E, val   # note cut / delay / pattern delay
        return (n, ins, v, e, a)
