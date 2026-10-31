#!/usr/bin/env python3
"""Max Potato jar for phones with a 2 MB heap (Nokia 6600 fold): cuts the tile sheet (t.png) and the
sprite sheet (s2.png) into small pages, so the game keeps only the pages the current level or the
world map uses (Res.need). Writes tp/<n>.png + tp.bin and sp/<n>.png + sp.bin, removes t.png and s2.png.
Page file: u8 pages, per page u8 w, u8 h; u16 frames, per frame u8 page (255: none), u8 x, u8 y.
usage: pages.py <extracted jar dir> [report]"""
import sys, os, struct, glob, subprocess
from PIL import Image

D = sys.argv[1]
PW, PH = int(os.environ.get("PW", 32)), int(os.environ.get("PH", 32))         # a page: 32x32 = 4 KB decoded at 4 bytes a pixel


def read_t(d):
    p = [0]
    def u8(): v = d[p[0]]; p[0] += 1; return v
    def s8(): v = u8(); return v - 256 if v > 127 else v
    def u16(): v = struct.unpack('>H', d[p[0]:p[0] + 2])[0]; p[0] += 2; return v
    def s16(): v = struct.unpack('>h', d[p[0]:p[0] + 2])[0]; p[0] += 2; return v
    n = u16(); rects = []
    for i in range(n):
        x = u8(); yl = u8(); w = u8(); h = u8(); yh = u8(); rects.append((x, yl | yh << 8, w, h))
    n = u16(); tiles = []
    for i in range(n):
        fl = s8(); s8(); s16(); nx = s16(); f = u8(); tiles.append((fl, nx, [s16() for j in range(f)]))
    return rects, tiles


def read_s(d):
    p = [0]
    def u8(): v = d[p[0]]; p[0] += 1; return v
    def s8(): v = u8(); return v - 256 if v > 127 else v
    def u16(): v = struct.unpack('>H', d[p[0]:p[0] + 2])[0]; p[0] += 2; return v
    def s16(): v = struct.unpack('>h', d[p[0]:p[0] + 2])[0]; p[0] += 2; return v
    n = u16(); rects = [(s16(), s16(), u8(), u8()) for i in range(n)]
    n = u16(); spr = []
    for i in range(n):
        s16(); s8(); s8(); f = u8(); spr.append([s16() for j in range(f)])
    return rects, spr


def paginate(img, rects, groups):
    """groups: lists of frame numbers that are wanted together (a tile's animation, a sprite's frames),
    in the order to lay them out. Shelf-packs them into pages; a group never straddles two pages."""
    place = [None] * len(rects)
    pages = []                   # [w, h, [(frame, x, y)]]
    cur = None
    def new_page(w, h):
        pg = [w, h, [], 0, 0, 0]  # w, h, frames, shelf x, shelf y, shelf height
        pages.append(pg); return pg
    def fits(pg, fr):
        # try laying the group out from the page's current shelf position without changing it
        x, y, sh = pg[3], pg[4], pg[5]; out = []
        for f in fr:
            w, h = rects[f][2], rects[f][3]
            if x + w > pg[0]: x, y, sh = 0, y + sh, 0
            if y + h > pg[1] or w > pg[0]: return None
            out.append((f, x, y)); x += w; sh = max(sh, h)
        return out, x, y, sh
    for fr in groups:
        fr = [f for f in fr if place[f] is None]
        if not fr: continue
        r = fits(cur, fr) if cur else None
        if r is None:
            w = max(PW, max(rects[f][2] for f in fr)); h = max(PH, max(rects[f][3] for f in fr))
            cur = [w, h, [], 0, 0, 0]
            r = fits(cur, fr)
            if r is not None: pages.append(cur)
            else:        # a group too big for one page: one frame a page
                for f in fr:
                    pg = new_page(rects[f][2], rects[f][3]); pg[2].append((f, 0, 0)); place[f] = (len(pages) - 1, 0, 0)
                cur = None
                continue
        out, cur[3], cur[4], cur[5] = r
        for f, x, y in out:
            cur[2].append((f, x, y)); place[f] = (pages.index(cur), x, y)
    imgs = []
    for pg in pages:
        # trim the page to what it holds
        w = max(x + rects[f][2] for f, x, y in pg[2]); h = max(y + rects[f][3] for f, x, y in pg[2])
        if img.mode == 'P':      # Max Potato's sprites: keep s2.png's palette and see-through colour
            im = Image.new('P', (w, h), img.info.get('transparency', 0)); im.putpalette(img.getpalette())
            if 'transparency' in img.info: im.info['transparency'] = img.info['transparency']
        else: im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        for f, x, y in pg[2]:
            sx, sy, fw, fh = rects[f]
            im.paste(img.crop((sx, sy, sx + fw, sy + fh)), (x, y))
        imgs.append(im)
    return place, imgs


def write(prefix, place, imgs):
    os.makedirs(os.path.join(D, prefix), exist_ok=True)
    names = []
    for i, im in enumerate(imgs):
        n = os.path.join(D, prefix, '%d.png' % i)
        if im.mode == 'P': im.save(n, 'PNG', optimize=True, transparency=im.info.get('transparency'))
        else: im.save(n, 'PNG', optimize=True)
        names.append(n)
    subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'pngsqueeze.py')] + names,
                   check=True, stdout=subprocess.DEVNULL)
    t = struct.pack('>B', len(imgs))
    for im in imgs: t += struct.pack('>BB', im.size[0] & 0xff, im.size[1] & 0xff)
    t += struct.pack('>H', len(place))
    for p in place: t += struct.pack('>BBB', *(p if p else (255, 0, 0)))
    open(os.path.join(D, prefix + '.bin'), 'wb').write(t)


rects, tiles = read_t(open(os.path.join(D, 't.bin'), 'rb').read())
timg = Image.open(os.path.join(D, 't.png')).convert('RGBA')
tgroups = [t[2] for t in tiles] + [[f] for f in range(len(rects))]
tplace, timgs = paginate(timg, rects, tgroups)
assert all(w <= 255 and h <= 255 for w, h in (im.size for im in timgs))
write('tp', tplace, timgs)

srects, spr = read_s(open(os.path.join(D, 's.bin'), 'rb').read())
simg = Image.open(os.path.join(D, 's2.png'))
assert simg.mode == 'P'
splace, simgs = paginate(simg, srects, spr + [[f] for f in range(len(srects))])
write('sp', splace, simgs)
os.remove(os.path.join(D, 't.png')); os.remove(os.path.join(D, 's2.png'))

if len(sys.argv) > 2:
    def kb(pages, imgs): return sum(imgs[p].size[0] * imgs[p].size[1] for p in pages) * 4 // 1024
    coin = [i for i, t in enumerate(tiles) if t[0] & 32][0]
    res = []
    for fn in glob.glob(os.path.join(D, 'l', '*.bin')):
        b = open(fn, 'rb').read(); q = 0
        q += 1 + b[q]; q += 1 + b[q]
        w = struct.unpack('>H', b[q:q + 2])[0]; q += 2 + 25
        ts = set(b[q:q + w * 45]) | {coin}; st = list(ts)
        while st:
            t = st.pop(); nx = tiles[t][1]
            if 0 <= nx < len(tiles) and nx not in ts: ts.add(nx); st.append(nx)
        pages = set(tplace[f][0] for t in ts for f in tiles[t][2])
        res.append((kb(pages, timgs), os.path.basename(fn)))
    res.sort()
    print('tile pages', len(timgs), 'all', kb(range(len(timgs)), timgs), 'KB; per level worst', res[-3:], 'median', res[len(res) // 2])
    lv = set(splace[f][0] for s in list(range(0, 92)) + [109, 110] for f in spr[s])
    wm = set(splace[f][0] for s in list(range(74, 92)) + [109, 110] + list(range(113, len(spr))) for f in spr[s])
    print('sprite pages', len(simgs), 'all', kb(range(len(simgs)), simgs), 'KB; level set', kb(lv, simgs), 'map set', kb(wm, simgs))
