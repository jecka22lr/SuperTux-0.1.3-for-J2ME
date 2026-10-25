# SuperTux 0.1.3 -> J2ME asset converter.
# Reads the half-size (GP2X/Symbian) 0.1.3 data and writes compact resources:
#   res/t.png   tile atlas        res/t.bin  tile table
#   res/s.png   sprite atlas      res/s.bin  sprite table
#   res/l/<set><n>.bin levels     res/b/<n>.png backgrounds (palette PNGs)
#   src/Spr.java   sprite index constants
# World coordinates stay 0.1.3's (32 per tile); the game draws at half scale.
import os, sys, struct, re, glob
from PIL import Image

DATA = sys.argv[1]      # .../supertux-0.1.3/data (half-size images + supertux.strf)
OUT = sys.argv[2]       # project root
IMG = os.path.join(DATA, 'images')
SYM = sys.argv[3] if len(sys.argv) > 3 else DATA   # Jecka's Symbian 0.1.3 data

# ---------- tiny s-expression reader ----------
def tokens(s):
    s = re.sub(r';[^\n]*', '', s)
    for m in re.finditer(r'"((?:[^"\\]|\\.)*)"|([()])|([^\s()"]+)', s):
        if m.group(1) is not None: yield ('s', m.group(1))
        elif m.group(2): yield (m.group(2), None)
        else: yield ('a', m.group(3))
def parse(s):
    stack = [[]]
    for k, v in tokens(s):
        if k == '(': stack.append([])
        elif k == ')': x = stack.pop(); stack[-1].append(x)
        elif k == 's': stack[-1].append(('str', v))
        else:
            if v in ('#t', '#f'): stack[-1].append(v == '#t')
            else:
                try: stack[-1].append(int(v))
                except ValueError:
                    try: stack[-1].append(float(v))
                    except ValueError: stack[-1].append(v)
    return stack[0]
def field(lst, name, default=None):
    for e in lst:
        if isinstance(e, list) and e and e[0] == name:
            return e[1:]
    return default
def one(lst, name, default=None):
    f = field(lst, name)
    if not f: return default
    v = f[0]
    return v[1] if isinstance(v, tuple) else v
def strs(lst, name):
    f = field(lst, name) or []
    return [v[1] for v in f if isinstance(v, tuple)]

# ---------- atlas packer (shelf) ----------
class Atlas:
    def __init__(self, width):
        self.w = width; self.items = []; self.index = {}
    def add(self, path):
        if path in self.index: return self.index[path]
        im = Image.open(path).convert('RGBA')
        self.index[path] = len(self.items); self.items.append(im)
        return self.index[path]
    def pack(self):
        order = sorted(range(len(self.items)), key=lambda i: -self.items[i].size[1])
        x = y = rowh = 0; rects = [None] * len(self.items)
        for i in order:
            w, h = self.items[i].size
            if x + w > self.w: x = 0; y += rowh; rowh = 0
            rects[i] = (x, y, w, h); x += w; rowh = max(rowh, h)
        H = y + rowh
        img = Image.new('RGBA', (self.w, H), (0, 0, 0, 0))
        for i, r in enumerate(rects): img.paste(self.items[i], (r[0], r[1]))
        self.rects = rects
        return img

def save_png(img, path):
    # keep full alpha but let PNG pick the smallest encoding
    img.save(path, optimize=True)

os.makedirs(os.path.join(OUT, 'res', 'l'), exist_ok=True)
os.makedirs(os.path.join(OUT, 'res', 'b'), exist_ok=True)
os.makedirs(os.path.join(OUT, 'src'), exist_ok=True)

# ---------- tiles ----------
ts = parse(open(os.path.join(IMG, 'tilesets', 'supertux.stgt')).read())[0]
tiles = {}
for e in ts[1:]:
    if isinstance(e, list) and e and e[0] == 'tile':
        tid = one(e, 'id')
        tiles[tid] = dict(
            solid=bool(one(e, 'solid', False)), brick=bool(one(e, 'brick', False)),
            ice=bool(one(e, 'ice', False)), water=bool(one(e, 'water', False)),
            fullbox=bool(one(e, 'fullbox', False)), distro=bool(one(e, 'distro', False)),
            goal=bool(one(e, 'goal', False)), data=one(e, 'data', 0) or 0,
            anim=one(e, 'anim-speed', 25) or 25, next=one(e, 'next-tile', 0) or 0,
            images=strs(e, 'images'), editor=strs(e, 'editor-images'))
ids = sorted(tiles)
cidx = {tid: i for i, tid in enumerate(ids)}      # compact index; tile 0 -> 0
assert ids[0] == 0 and len(ids) < 256, len(ids)
ta = Atlas(256)
for tid in ids:
    tiles[tid]['frames'] = []
    for fn in tiles[tid]['images']:
        p = os.path.join(IMG, 'tilesets', fn)
        if fn == 'notile.png' or not os.path.exists(p): continue
        tiles[tid]['frames'].append(ta.add(p))
    ed = [fn for fn in tiles[tid]['editor'] if os.path.exists(os.path.join(IMG, 'tilesets', fn))]
    tiles[tid]['edframe'] = ta.add(os.path.join(IMG, 'tilesets', ed[0])) if ed else -1
save_png(ta.pack(), os.path.join(OUT, 'res', 't.png'))
with open(os.path.join(OUT, 'res', 't.bin'), 'wb') as f:
    f.write(struct.pack('>H', len(ta.rects)))
    for r in ta.rects: f.write(struct.pack('>BBBB', r[0], r[1] & 0xff, r[2], r[3]) + struct.pack('>B', r[1] >> 8))
    f.write(struct.pack('>H', len(ids)))
    for tid in ids:
        t = tiles[tid]
        flags = (t['solid'] << 0) | (t['brick'] << 1) | (t['ice'] << 2) | (t['water'] << 3) \
              | (t['fullbox'] << 4) | (t['distro'] << 5) | (t['goal'] << 6)
        f.write(struct.pack('>BBHHB', flags, t['data'] & 0xff, t['anim'], cidx.get(t['next'], 0), len(t['frames'])))
        for fr in t['frames']: f.write(struct.pack('>H', fr))
# level editor: the editor image of each tile, and the tile groups (sorted by name like 0.1.3's std::set)
groups = []
for e in ts[1:]:
    if isinstance(e, list) and e and e[0] == 'tilegroup':
        groups.append((one(e, 'name'), [cidx[t] for t in (field(e, 'tiles') or []) if isinstance(t, int) and t in cidx]))
groups.sort(key=lambda g: g[0])
with open(os.path.join(OUT, 'res', 'te.bin'), 'wb') as f:
    f.write(struct.pack('>H', len(ids)))
    for tid in ids: f.write(struct.pack('>h', tiles[tid]['edframe']))
    f.write(struct.pack('>B', len(groups)))
    for name, ts_ in groups:
        nb = name.encode('latin-1')
        f.write(struct.pack('>B', len(nb)) + nb + struct.pack('>B', len(ts_)) + bytes(ts_))
print('tiles', len(ids), 'frames', len(ta.rects), 'atlas', ta.pack().size, 'groups', [g[0] for g in groups])

# ---------- sprites ----------
sp = parse(open(os.path.join(DATA, 'supertux.strf')).read())[0]
sa = Atlas(256); sprites = []
seen = set()
for e in sp[1:]:
    if not (isinstance(e, list) and e and e[0] == 'sprite'): continue
    name = one(e, 'name')
    if name in seen: continue
    seen.add(name)
    frames = [sa.add(os.path.join(IMG, fn)) for fn in strs(e, 'images')]
    sprites.append((name, float(one(e, 'fps', 10) or 10), one(e, 'x-hotspot', 0) or 0, one(e, 'y-hotspot', 0) or 0, frames))
# extra single-image sprites the game draws directly (particles, status icon)
for name, fn in (('snow0', 'shared/snow0.png'), ('snow1', 'shared/snow1.png'), ('snow2', 'shared/snow2.png'),
                 ('cloud', 'shared/cloud.png'), ('tux-life', 'shared/tux-life.png'),
                 ('menu-back', 'status/back.png'), ('checkbox', 'status/checkbox.png'),
                 ('checkbox-checked', 'status/checkbox-checked.png'),
                 ('wm-smalltux', 'worldmap/smalltux.png'), ('wm-tux', 'worldmap/tux.png'),
                 ('wm-firetux', 'worldmap/firetux.png'), ('wm-dot-green', 'worldmap/leveldot_green.png'),
                 ('wm-dot-red', 'worldmap/leveldot_red.png'), ('wm-teleporter', 'worldmap/teleporter.png')):
    sprites.append((name, 10.0, 0, 0, [sa.add(os.path.join(IMG, fn))]))
# level editor icons and pointers, from the Symbian data (Jecka's versions)
# the button bar uses 0.1.3's full-size icons (the Symbian data halves them, too small to read on
# a 240 pixel screen); the menu arrows and the select icon stay the Symbian ones
for n in ('up', 'down', 'save', 'exit', 'select-mode1', 'select-mode2', 'rubber', 'tilegroup', 'objects',
          'select-one', 'properties', 'test-level', 'settings', 'bkgrd', 'intact', 'frgrd'):
    sprites.append(('ic-' + n, 10.0, 0, 0, [sa.add(os.path.join(IMG, 'icons', n + '.png'))]))
for n in ('select', 'left', 'right'):
    sprites.append(('ic-' + n, 10.0, 0, 0, [sa.add(os.path.join(SYM, 'images', 'icons', n + '.png'))]))
for n in ('mousecursor', 'select-cursor'):
    sprites.append(('ic-' + n, 10.0, 0, 0, [sa.add(os.path.join(SYM, 'images', 'status', n + '.png'))]))
# world map tiles (antarctica.stwt): one sprite each, numbered from WM_TILES
wt = parse(open(os.path.join(SYM, 'images', 'worldmap', 'antarctica.stwt')).read())[0]
wtiles = {}
for e in wt[1:]:
    if isinstance(e, list) and e and e[0] == 'tile':
        wtiles[one(e, 'id')] = e
WM_TILES = len(sprites)
wtile_sprite = {}
for tid in sorted(wtiles):
    wtile_sprite[tid] = len(sprites)
    sprites.append(('wmt%d' % tid, 10.0, 0, 0, [sa.add(os.path.join(SYM, 'images', 'worldmap', one(wtiles[tid], 'image')))]))
save_png(sa.pack(), os.path.join(OUT, 'res', 's.png'))
# fonts: the 8x9 "small" letter sheets 0.1.3 uses at 320x240, stacked white/gold/blue/black
def status_img(name):
    # the GP2X data lacks some small fonts; the Symbian 0.1.3 data (argv[3]) has them all
    for d in [IMG] + ([os.path.join(sys.argv[3], 'images')] if len(sys.argv) > 3 else []):
        p = os.path.join(d, 'status', name)
        if os.path.exists(p): return Image.open(p).convert('RGBA')
    raise IOError(name)
fonts = [status_img('letters-%s-small.png' % c) for c in ('white', 'gold', 'blue', 'black')]
shadow = Image.new('RGBA', fonts[0].size, (0, 0, 0, 0))
sp_ = shadow.load(); wp = fonts[0].load()
for y in range(fonts[0].size[1]):
    for x in range(fonts[0].size[0]):
        if wp[x, y][3] > 0: sp_[x, y] = (0, 0, 0, 128)
fonts.append(shadow)
fimg = Image.new('RGBA', (128, 54 * 5), (0, 0, 0, 0))
for i, fi in enumerate(fonts): fimg.paste(fi, (0, 54 * i))
save_png(fimg, os.path.join(OUT, 'res', 'f.png'))
with open(os.path.join(OUT, 'res', 's.bin'), 'wb') as f:
    f.write(struct.pack('>H', len(sa.rects)))
    for r in sa.rects: f.write(struct.pack('>HHBB', r[0], r[1], r[2], r[3]))
    f.write(struct.pack('>H', len(sprites)))
    for name, fps, hx, hy, frames in sprites:
        f.write(struct.pack('>Hbb B', int(round(1000 / fps)), hx, hy, len(frames)))
        for fr in frames: f.write(struct.pack('>H', fr))
with open(os.path.join(OUT, 'src', 'Spr.java'), 'w') as f:
    f.write('// Generated by tools/convert.py: sprite numbers in res/s.bin\ninterface Spr {\n')
    for i, s in enumerate(sprites):
        n = s[0].upper().replace('-', '_')
        if n[0].isdigit(): n = {'1UP': 'ONEUP'}.get(n, 'S_' + n)
        if not n.startswith('WMT'): f.write('  int %s = %d;\n' % (n, i))
    f.write('  int WM_TILES = %d;\n' % WM_TILES)
    f.write('}\n')
print('sprites', len(sprites), 'frames', len(sa.rects), 'atlas', sa.pack().size)

# ---------- backgrounds ----------
bgnames = {}
def background(name):
    if not name: return 255
    key = os.path.splitext(name)[0].lower()
    if key in bgnames: return bgnames[key]
    cands = [p for p in glob.glob(os.path.join(IMG, 'background', '*')) if os.path.splitext(os.path.basename(p))[0].lower() == key]
    if not cands: return 255
    n = len(bgnames); bgnames[key] = n
    Image.open(cands[0]).convert('RGB').quantize(128).save(os.path.join(OUT, 'res', 'b', '%d.png' % n), optimize=True)
    return n

# ---------- levels ----------
BADGUYS = ['mriceblock', 'jumpy', 'mrbomb', 'stalactite', 'flame', 'fish', 'bouncingsnowball',
           'flyingsnowball', 'spiky', 'snowball']
ALIASES = {'money': 'jumpy', 'laptop': 'mriceblock', 'bsod': 'snowball'}
musics = {}
def music(name):
    if not name: return 255
    return musics.setdefault(name, len(musics))
sets = [('world1', 'a'), ('bonus1', 'b'), ('bonus2', 'c'), ('misc', 'm')]
level_ids = {}; level_titles = {}
total = 0
for d, letter in sets:
    files = glob.glob(os.path.join(DATA, 'levels', d, '*.stl'))
    if d == 'misc': files = [f for f in files if os.path.basename(f) == 'menu.stl']
    if d in ('world1', 'bonus2'):
        files.sort(key=lambda p: int(re.findall(r'\d+', os.path.basename(p))[-1]))
    else:
        files.sort()
    for n, path in enumerate(files):
        lv = parse(open(path, encoding='latin-1').read())[0][1:]
        width = one(lv, 'width'); assert one(lv, 'version', 0) >= 1, path
        def tm(name):
            v = field(lv, name) or []
            out = bytearray(width * 15)
            for i, t in enumerate(v[:width * 15]): out[i] = cidx.get(t, 0)
            return bytes(out)
        objs = []
        for o in field(lv, 'objects') or []:
            k = ALIASES.get(o[0], o[0])
            if k in BADGUYS:
                objs.append((BADGUYS.index(k), one(o[1:], 'x', 0), one(o[1:], 'y', 0), 1 if one(o[1:], 'stay-on-platform', False) else 0))
        resets = [(one(p[1:], 'x', 0), one(p[1:], 'y', 0)) for p in (field(lv, 'reset-points') or [])]
        name = (one(lv, 'name', 'Noname') or 'Noname').encode('latin-1', 'replace')[:60]
        author = (one(lv, 'author', 'unknown author') or 'unknown author').encode('latin-1', 'replace')[:60]
        b = bytearray()
        b += struct.pack('>B', len(name)) + name
        b += struct.pack('>B', len(author)) + author
        b += struct.pack('>HhhH', width, one(lv, 'start_pos_x', 100), one(lv, 'start_pos_y', 170), one(lv, 'time', 500))
        b += struct.pack('>BBhhB', background(one(lv, 'background', '')), music(one(lv, 'music', '')),
                         int(float(one(lv, 'gravity', 10)) * 10), int(float(one(lv, 'hor_autoscroll_speed', 0) or 0) * 10),
                         (1 if one(lv, 'back_scrolling', False) else 0))
        b += struct.pack('>B', int(one(lv, 'bkgd_speed', 50) or 50))
        for c in ('red', 'green', 'blue'):
            b += struct.pack('>B', int(one(lv, 'bkgd_%s_top' % c, 0) or 0))
        for c in ('red', 'green', 'blue'):
            b += struct.pack('>B', int(one(lv, 'bkgd_%s_bottom' % c, 0) or 0))
        ps = one(lv, 'particle_system', '') or ''
        b += struct.pack('>B', {'': 0, 'clouds': 1, 'snow': 2}.get(ps, 0))
        b += tm('background-tm') + tm('interactive-tm') + tm('foreground-tm')
        b += struct.pack('>H', len(objs))
        for o in objs: b += struct.pack('>BhhB', *o)
        b += struct.pack('>B', len(resets))
        for r in resets: b += struct.pack('>hh', *r)
        open(os.path.join(OUT, 'res', 'l', '%s%d.bin' % (letter, n + 1)), 'wb').write(b)
        level_ids['%s/%s' % (d, os.path.basename(path))] = '%s%d' % (letter, n + 1)
        level_titles['%s/%s' % (d, os.path.basename(path))] = name
        total += 1
print('levels', total, 'backgrounds', bgnames, 'music', musics)


# ---------- world maps (levels/worldmaps/*.stwm) ----------
def lstr(v, n=255):
    v = (v or '').encode('latin-1', 'replace')[:n]
    return struct.pack('>B', len(v)) + v
os.makedirs(os.path.join(OUT, 'res', 'w'), exist_ok=True)
with open(os.path.join(OUT, 'res', 'wt.bin'), 'wb') as f:
    n = max(wtiles) + 1
    f.write(struct.pack('>H', n))
    for tid in range(n):
        e = wtiles.get(tid)
        if e is None: f.write(struct.pack('>BBH', 0, 0, 0)); continue
        def b(name, d): v = one(e, name, d); return 1 if v else 0
        flags = b('north', True) | b('south', True) << 1 | b('east', True) << 2 | b('west', True) << 3 \
              | b('stop', True) << 4 | b('auto-walk', False) << 5 | 64
        ow = {'north-south': 1, 'south-north': 2, 'east-west': 3, 'west-east': 4}.get(one(e, 'one-way', '') or '', 0)
        f.write(struct.pack('>BBH', flags, ow, wtile_sprite[tid]))
WORLDMAPS = ['world1.stwm', 'bonusisland1.stwm', 'bonusisland2.stwm']
texts = set()
for wi, wm in enumerate(WORLDMAPS):
    root = parse(open(os.path.join(SYM, 'levels', 'worldmaps', wm), encoding='latin-1').read())[0][1:]
    props = field(root, 'properties') or []
    tm = field(root, 'tilemap') or []
    W, H = one(tm, 'width', 20), one(tm, 'height', 15)
    data = field(tm, 'data') or []
    b = bytearray()
    b += lstr(one(props, 'name', '<no file>'))
    b += struct.pack('>B', music(one(props, 'music', 'salcon.mod') or 'salcon.mod'))
    b += struct.pack('>hhhh', one(props, 'start_pos_x', 4), one(props, 'start_pos_y', 5), W, H)
    b += bytes(data[:W * H]) + bytes(max(0, W * H - len(data)))
    lvls = [e[1:] for e in (field(root, 'levels') or []) if isinstance(e, list) and e and e[0] == 'level']
    b += struct.pack('>H', len(lvls))
    for lv in lvls:
        name = one(lv, 'name', '') or ''
        lid = level_ids.get(name, '') if name else ''
        if name and not lid: print('world map', wm, 'unknown level', name)
        extro = one(lv, 'extro-filename', '') or ''
        if extro: texts.add(extro)
        def bb(k, d):
            v = one(lv, k, d)
            return 1 if v else 0
        flags = bb('auto-path', True) | bb('passive-message', True) << 1 | bb('invisible-teleporter', False) << 2 \
              | bb('apply-action-up', True) << 3 | bb('apply-action-down', True) << 4 \
              | bb('apply-action-left', True) << 5 | bb('apply-action-right', True) << 6
        b += lstr(lid) + lstr(level_titles.get(name, b'<no title>').decode('latin-1') if name else '')
        b += struct.pack('>hh', one(lv, 'x', 0), one(lv, 'y', 0))
        b += lstr(extro) + lstr(one(lv, 'map-message', '') or '') + struct.pack('>B', flags)
        b += struct.pack('>hh', one(lv, 'dest_x', -1), one(lv, 'dest_y', -1)) + lstr(one(lv, 'teleport-message', '') or '')
    open(os.path.join(OUT, 'res', 'w', '%d.bin' % wi), 'wb').write(b)
    print('world map', wm, W, 'x', H, len(lvls), 'entries')

# ---------- text files, sounds, title pictures ----------
os.makedirs(os.path.join(OUT, 'res', 'x'), exist_ok=True)
for t in sorted(texts | {'credits', 'intro.txt'}):
    raw = open(os.path.join(SYM, t), 'rb').read().replace(b'\r', b'')
    open(os.path.join(OUT, 'res', 'x', t), 'wb').write(raw)
SOUNDS = ['jump', 'bigjump', 'skid', 'distro', 'herring', 'brick', 'hurt', 'squish', 'fall', 'ricochet',
          'bump-upgrade', 'upgrade', 'excellent', 'coffee', 'shoot', 'lifeup', 'stomp', 'kick', 'explode', 'warp']
os.makedirs(os.path.join(OUT, 'res', 'a'), exist_ok=True)
import subprocess
for i, n in enumerate(SOUNDS):
    # 8-bit mono 11 kHz PCM: small, and every MMAPI phone plays audio/x-wav
    # written with a plain 44-byte header: FreeJ2ME and many phones skip exactly 44 bytes
    raw = subprocess.run(['ffmpeg', '-hide_banner', '-v', 'error', '-i', os.path.join(SYM, 'sounds', n + '.wav'),
                          '-ac', '1', '-ar', '11025', '-f', 'u8', '-'], check=True, stdout=subprocess.PIPE).stdout
    import wave
    w = wave.open(os.path.join(OUT, 'res', 'a', '%d.wav' % i), 'wb')
    w.setnchannels(1); w.setsampwidth(1); w.setframerate(11025); w.writeframes(raw); w.close()
def picture(path, name):
    n = len(bgnames); bgnames[name] = n
    Image.open(path).convert('RGB').quantize(128).save(os.path.join(OUT, 'res', 'b', '%d.png' % n), optimize=True)
    return n
pics = {'BG_TITLE': picture(os.path.join(SYM, 'images', 'title', 'background.PNG'), 'title')}
for k, fn in (('BG_ARCTIS2', 'arctis2'), ('BG_EXTRO', 'extro'), ('BG_OILTUX', 'oiltux')):
    v = background(fn + '.png')
    if v == 255:
        cands = [q for q in glob.glob(os.path.join(SYM, 'images', 'background', '*')) if os.path.splitext(os.path.basename(q))[0].lower() == fn]
        v = picture(cands[0], fn)
    pics[k] = v
for fn in ('arctis', 'arctis2', 'arctis_dark', 'cave2', 'extro', 'ocean', 'oiltux'):   # the editor's Bg-Image list
    if fn not in bgnames:
        cands = [q for q in glob.glob(os.path.join(IMG, 'background', '*')) + glob.glob(os.path.join(SYM, 'images', 'background', '*'))
                 if os.path.splitext(os.path.basename(q))[0].lower() == fn]
        picture(cands[0], fn)
with open(os.path.join(OUT, 'res', 'b', 'n.txt'), 'w') as f:
    for k in sorted(bgnames):
        if k != 'title': f.write('%d %s.png\n' % (bgnames[k], k))
save_png(Image.open(os.path.join(SYM, 'images', 'status', 'numbers.png')).convert('RGBA'), os.path.join(OUT, 'res', 'nu.png'))
logo = Image.open(os.path.join(SYM, 'images', 'title', 'logo.png')).convert('RGBA')
save_png(logo, os.path.join(OUT, 'res', 'lo.png'))
with open(os.path.join(OUT, 'src', 'Spr.java'), 'a') as f:
    f.write('// pictures in res/b\ninterface Pic {\n')
    for k, v in pics.items(): f.write('  int %s = %d;\n' % (k, v))
    f.write('}\n')
print('pictures', pics, 'texts', sorted(texts))

# ---------- music: XM -> MIDI ----------
if len(sys.argv) > 3:
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import mod2mid
    mdir = os.path.join(sys.argv[3], 'music')
    os.makedirs(os.path.join(OUT, 'res', 'm'), exist_ok=True)
    def find(name):
        for f in os.listdir(mdir):
            if f.lower() == name.lower(): return os.path.join(mdir, f)
        return None
    import subprocess, shutil
    have_ffmpeg = shutil.which('ffmpeg') is not None
    def conv(src, dst):
        p = find(src)
        if not p: print('missing music', src); return
        out = os.path.join(OUT, 'res', 'm', dst)
        mod2mid.convert(p, out)
        if have_ffmpeg:
            # the real thing: libopenmpt renders the tracker song, mono 22 kHz MP3 at 24 kbit/s
            subprocess.run(['ffmpeg', '-hide_banner', '-v', 'error', '-y', '-f', 'libopenmpt', '-i', p,
                            '-ac', '1', '-ar', '22050', '-c:a', 'libmp3lame', '-b:a', '24k',
                            out[:-4] + '.mp3'], check=True)
    # every distinct song file once; the game looks names up in m/a.txt (name=file stem)
    done = {}
    alias = []
    def song(name, stem):
        p = find(name)
        key = os.path.basename(p).lower() if p else name
        if key in done: alias.append('%s=%s' % (stem, done[key])); return
        conv(name, stem + '.mid'); done[key] = stem
    music('credits.xm'); music('leveldone.mod')      # the editor's Song list has every song
    with open(os.path.join(OUT, 'res', 'm', 'n.txt'), 'w') as f:
        for name, n in sorted(musics.items(), key=lambda kv: kv[0].lower()): f.write('%d %s\n' % (n, name))
    for name, n in musics.items():
        song(name, '%d' % n)
        base, ext = os.path.splitext(name)
        if find(base + '-fast' + ext): song(base + '-fast' + ext, '%df' % n)
        else: alias.append('%df=%d' % (n, n))   # no hurry-up version: 0.1.3 keeps the normal song
    song('salcon.mod', 'h'); song('leveldone.mod', 'd'); song('theme.mod', 't'); song('credits.xm', 'cr')
    open(os.path.join(OUT, 'res', 'm', 'a.txt'), 'w').write('\n'.join(alias) + '\n')
    print('music aliases', alias)
    print('music', sorted(os.listdir(os.path.join(OUT, 'res', 'm'))))

# the MIDlet icon: 0.1.3's 32x32 icon.xpm (PIL can't read it; ImageMagick can)
import subprocess
subprocess.run(['convert', os.path.join(SYM, 'images', 'icon.xpm'), 'PNG32:' + os.path.join(OUT, 'res', 'icon.png')], check=True)
