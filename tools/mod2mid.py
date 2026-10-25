# XM (0.1.3's ".mod" music) -> General MIDI type 0, for J2ME phones.
# One MIDI tick = one tracker tick (division 24, MIDI tempo = tracker BPM), so speed/BPM
# changes carry over exactly. Instruments become GM programs or drums by name, or by sound
# analysis for unnamed ones; their real pitch is measured from the sample data.
import math, struct, sys
from xm import XM

NAMED = [  # keyword in instrument/sample name -> ('prog', n) or ('drum', key)
    ('bass drum', ('drum', 36)), ('kick', ('drum', 36)), ('claps', ('drum', 39)), ('clap', ('drum', 39)),
    ('high hat', ('drum', 42)), ('hihat', ('drum', 42)), ('glass', ('drum', 49)), ('snare', ('drum', 38)),
    ('trumpet', ('prog', 56)), ('tuba', ('prog', 58)), ('cello', ('prog', 42)), ('harp', ('prog', 46)),
    ('strings', ('prog', 48)), ('choir', ('prog', 52)), ('voice', ('prog', 53)), ('wurlitzer', ('prog', 4)),
    ('acoustic bass', ('prog', 32)), ('bass harm', ('prog', 38)), ('coolbass', ('prog', 38)),
    ('ibanez', ('prog', 29)), ('guitar', ('prog', 27)), ('clean', ('prog', 27)),
    ('cavern', ('prog', 99)), ('scifi', ('prog', 98)),
]

def classify(ins, notes):
    names = (ins['name'] + ' ' + ' '.join(s['name'] for s in ins['samples'])).lower()
    for k, v in NAMED:
        if k in names: return v + (True,)
    s = max(ins['samples'], key=lambda s: s['len'])
    d = s['data']
    zc = sum(1 for k in range(1, len(d)) if (d[k - 1] < 0) != (d[k] < 0)) / max(1, len(d))
    q = max(1, len(d) // 4)
    r1 = math.sqrt(sum(v * v for v in d[:q]) / q); r4 = math.sqrt(sum(v * v for v in d[-q:]) / q)
    decay = r4 / (r1 + 1e-9)
    distinct = len(set(notes))
    if zc > 0.25: return ('drum', 42 if len(d) < 1500 else 46)       # noise: hi-hats
    if distinct <= 2 and not s['loop'] and len(d) < 9000:
        if zc < 0.04: return ('drum', 36)                              # low thud: kick
        return ('drum', 38 if zc < 0.15 else 39)                       # snare / clap
    if s['loop'] and len(d) < 400: return ('prog', 80)                 # chip waveform: square lead
    if decay < 0.5: return ('prog', 4)                                 # plucked / decaying: e-piano
    return ('prog', 48)                                                # sustained: strings

def pitch_offset(ins, rep_note, named):
    """semitones (whole octaves) to add to (note - 1 + 12) so the MIDI note sounds where the sample does"""
    s = max(ins['samples'], key=lambda s: s['len'])
    if named and s['rel'] != 0: return 0       # tuned in the tracker: trust the note names
    d = s['data']
    if len(d) < 32: return 0
    if s['loop'] and 8 <= s['llen'] <= 600:
        period = float(s['llen'])               # single-cycle chip waveform
    else:
        # YIN: first dip of the cumulative-mean-normalised difference function
        seg = d[min(len(d) // 8, 2000):][:3000]
        n = len(seg)
        if n < 200: return 0
        W = n // 2
        dsum = 0.0; period = 0
        prev = None
        for tau in range(1, min(W, 1200)):
            diff = 0.0
            for k in range(0, W, 2):
                v = seg[k] - seg[k + tau]; diff += v * v
            dsum += diff
            dn = diff * tau / dsum if dsum else 1
            if dn < 0.15 and tau > 4:
                period = tau; break
        if not period: return 0
    real = rep_note + s['rel'] + s['fine'] / 128.0
    rate = 8363 * 2 ** ((real - 49) / 12.0)
    f = rate / period
    midi = 69 + 12 * math.log(f / 440.0, 2)
    off = midi - (rep_note - 1 + 12)
    off = int(round(off / 12.0)) * 12
    return max(-24, min(24, off))

def vlq(n):
    out = [n & 0x7f]; n >>= 7
    while n: out.append((n & 0x7f) | 0x80); n >>= 7
    return bytes(reversed(out))

def convert(path, out, loop_once=True):
    x = XM(path)
    # notes used per instrument
    used = {}
    for o in x.order:
        if o >= len(x.patterns): continue
        for row in x.patterns[o]:
            for (n, i, v, e, a) in row:
                if i and 0 < n < 97: used.setdefault(i, []).append(n)
    imap = {}
    progs = {}
    for i, ins in enumerate(x.instruments, 1):
        if i not in used or not ins['samples'] or not any(s['data'] for s in ins['samples']): continue
        kind = classify(ins, used[i])
        named = len(kind) > 2
        kind = kind[:2]
        notes = sorted(used[i]); rep = notes[len(notes) // 2]
        off = pitch_offset(ins, rep, named) if kind[0] == 'prog' else 0
        vol = max(s['vol'] for s in ins['samples'])
        imap[i] = (kind, off, vol)
    # MIDI channel per GM program (drums on 9)
    chan = {}
    free = [c for c in range(16) if c != 9]
    for i, (kind, off, vol) in sorted(imap.items()):
        if kind[0] == 'prog' and kind[1] not in chan:
            chan[kind[1]] = free.pop(0) if free else 0

    events = []   # (tick, order, bytes)
    def ev(t, b, o=1): events.append((t, o, bytes(b)))
    for p, c in chan.items(): ev(0, [0xC0 | c, p], 0)
    for c in list(chan.values()) + [9]: ev(0, [0xB0 | c, 7, 110], 0)

    speed, bpm = x.tempo or 6, x.bpm or 125
    ev(0, [0xFF, 0x51, 3] + list(struct.pack('>I', int(60000000 / bpm))[1:]), 0)
    t = 0
    playing = [None] * x.channels     # (midi channel, note)
    last_ins = [0] * x.channels
    last_note = [0] * x.channels
    vols = [64] * x.channels
    seen = set()
    order, row = 0, 0
    while order < len(x.order):
        pat_no = x.order[order]
        if pat_no >= len(x.patterns): order += 1; row = 0; continue
        pat = x.patterns[pat_no]
        if (order, row) in seen: break
        seen.add((order, row))
        if row >= len(pat): order += 1; row = 0; continue
        cells = pat[row]
        jump = brk = None
        delay_rows = 0
        # speed/tempo first (they apply to this row)
        for (n, i, v, e, a) in cells:
            if e == 0x0F and a:
                if a < 32: speed = a
                else:
                    bpm = a
                    ev(t, [0xFF, 0x51, 3] + list(struct.pack('>I', int(60000000 / bpm))[1:]), 0)
            if e == 0x0E and (a >> 4) == 0x0E: delay_rows = a & 15
        for c, (n, i, v, e, a) in enumerate(cells):
            if e == 0x0B: jump = a
            if e == 0x0D: brk = (a >> 4) * 10 + (a & 15)
            if i: last_ins[c] = i
            ins = last_ins[c]
            # volume: column 0x10..0x50, Cxx, or the instrument default on a new note
            if i and ins in imap: vols[c] = imap[ins][2]
            if 0x10 <= v <= 0x50: vols[c] = v - 0x10
            if e == 0x0C: vols[c] = min(64, a)
            start = t
            if e == 0x0E and (a >> 4) == 0x0D: start = t + min(a & 15, speed - 1)   # note delay
            if n == 97 or e == 0x14:                                                   # note off
                if playing[c]: ev(start, [0x80 | playing[c][0], playing[c][1], 0]); playing[c] = None
                continue
            if 0 < n < 97 and ins in imap and e != 0x03 and e != 0x05:
                if playing[c]: ev(start, [0x80 | playing[c][0], playing[c][1], 0]); playing[c] = None
                kind, off, _ = imap[ins]
                vel = max(1, min(127, vols[c] * 2))
                if kind[0] == 'drum':
                    ev(start, [0x99, kind[1], vel]); ev(start + 2, [0x89, kind[1], 0])
                else:
                    ch = chan[kind[1]]
                    note = max(0, min(127, n - 1 + 12 + off))
                    ev(start, [0x90 | ch, note, vel])
                    playing[c] = (ch, note)
                    last_note[c] = note
            elif 0 < n < 97 and ins in imap and (e == 0x03 or e == 0x05) and playing[c]:
                # tone portamento: jump to the target note at the end of the row
                kind, off, _ = imap[ins]
                note = max(0, min(127, n - 1 + 12 + off))
                ch = playing[c][0]
                ev(t + speed - 1, [0x80 | ch, playing[c][1], 0]); ev(t + speed - 1, [0x90 | ch, note, max(1, vols[c] * 2)])
                playing[c] = (ch, note)
            # arpeggio 0xy on a sounding note: retrigger x/y semitones on the ticks
            if e == 0 and a and playing[c] and speed > 1:
                ch, base = playing[c][0], last_note[c]
                seq = [0, a >> 4, a & 15]
                cur = playing[c][1]
                for k in range(1, speed):
                    nn = max(0, min(127, base + seq[k % 3]))
                    if nn != cur:
                        ev(t + k, [0x80 | ch, cur, 0]); ev(t + k, [0x90 | ch, nn, max(1, vols[c] * 2)])
                        cur = nn
                playing[c] = (ch, cur)
            if e == 0x0E and (a >> 4) == 0x0C and playing[c]:                        # note cut
                ev(t + min(a & 15, speed - 1), [0x80 | playing[c][0], playing[c][1], 0]); playing[c] = None
        t += speed * (1 + delay_rows)
        if jump is not None:
            order, row = jump, brk or 0
            if order >= len(x.order): break
        elif brk is not None:
            order += 1; row = brk
        else:
            row += 1
            if row >= len(pat): order += 1; row = 0
    for c in range(x.channels):
        if playing[c]: ev(t, [0x80 | playing[c][0], playing[c][1], 0])
    events.sort(key=lambda e: (e[0], e[1]))
    trk = bytearray(); last = 0
    for (tk, o, b) in events:
        trk += vlq(tk - last) + b; last = tk
    trk += vlq(0) + bytes([0xFF, 0x2F, 0])
    data = b'MThd' + struct.pack('>IHHH', 6, 0, 1, 24) + b'MTrk' + struct.pack('>I', len(trk)) + trk
    open(out, 'wb').write(data)
    return x, imap, t

if __name__ == '__main__':
    x, imap, t = convert(sys.argv[1], sys.argv[2])
    print(sys.argv[1].split('/')[-1], 'ticks', t, 'instruments', {k: (v[0], v[1]) for k, v in imap.items()})
