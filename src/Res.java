// Resources: tile and sprite atlases plus their tables (made by tools/convert.py).
import java.io.DataInputStream;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

final class Res {
  // tile flags
  static final int SOLID = 1, BRICK = 2, ICE = 4, WATER = 8, FULLBOX = 16, DISTRO = 32, GOAL = 64;

  static Image tileImg, sprImg, fontImg;
  static final int WHITE = 0, GOLD = 1, BLUE = 2, BLACK = 3, SHADOW = 4;
  // the class resources are read through; set from an instance (a Res.class literal compiles to
  // Class.forName for CLDC, which threw NoClassDefFoundError in FreeJ2ME)
  static Class cls;
  // tile atlas rects
  static short[] tfx, tfy; static byte[] tfw, tfh;
  // tiles (compact index)
  static byte[] tFlags, tData; static short[] tAnim, tNext; static short[][] tFrames;
  // sprite atlas rects
  static short[] sfx, sfy; static byte[] sfw, sfh;
  // sprites
  static short[] sDelay; static byte[] sHx, sHy; static short[][] sFrames;

  static DataInputStream open(String name) throws IOException {
    java.io.InputStream in = cls.getResourceAsStream(name);
    if (in == null) throw new IOException("missing " + name);
    return new DataInputStream(in);
  }

  // Max Potato's sprite sheet: the same pictures with hard edges (s2.png, one see-through colour)
  static boolean hardSprites;
  static void setHardSprites(boolean on) {
    if (sPaged) return;                // the paged sheets are Max Potato's already
    if (on == hardSprites && sprImg != null) return;
    Image old = sprImg;
    sprImg = null;
    System.gc();                       // one sheet in memory at a time
    try { sprImg = Image.createImage(on ? "/s2.png" : "/s.png"); hardSprites = on; }
    catch (Throwable e) {              // no s2.png in this jar: keep the normal sheet
      try { sprImg = Image.createImage("/s.png"); } catch (Throwable e2) { sprImg = old; }
      hardSprites = false;
    }
  }

  // Max Potato jar (2 MB heap phones): the sheets come in small pages (tools/pages.py) and only the
  // pages the level or the world map uses are kept. tfx/tfy and sfx/sfy are then page positions.
  // needTiles / needSprites load ahead and let go of the rest; anything drawn from a page that isn't
  // in memory loads it there and then (the level editor relies on that for its tile palette).
  static boolean paged, sPaged;        // tiles in pages (lite and 2 MB jars); sprites too (2 MB jar)
  static boolean keepTiles;            // lite jar without RAM saver: every tile page stays once loaded
  static Image[] tPage, sPage;
  static Image[] gPage;                // editor: tile pages at half alpha (layers not being edited)
  static boolean ghostFailed;          // out of memory for those: the editor draws its layers solid
  static byte[] tfp, sfp;              // a frame's page (0xff: none)
  static boolean[] tWant, sWant;
  static int spriteSet = -1;
  // when each page was last drawn (Game.draw counts frames in tick): running out of memory lets go of
  // the pages that haven't been drawn for a while, never the ones on screen
  static int tick;
  static int[] tUsed, sUsed;
  static int starved;                  // pages that couldn't be loaded at all (the FPS readout shows it)

  private static byte[] readPages(String name, short[] fx, short[] fy) throws IOException {
    DataInputStream in = open(name);
    int pages = in.readUnsignedByte();
    for (int i = 0; i < pages; i++) in.readUnsignedShort();
    int n = in.readUnsignedShort();
    byte[] fp = new byte[n];
    for (int i = 0; i < n && i < fx.length; i++) {
      fp[i] = in.readByte(); fx[i] = (short) in.readUnsignedByte(); fy[i] = (short) in.readUnsignedByte();
    }
    in.close();
    return fp;
  }

  // drop the pages not wanted, then load the missing ones (one lot of memory at a time)
  private static void fetch(Image[] page, boolean[] want, String dir) {
    boolean freed = false;
    for (int i = 0; i < page.length; i++)
      if (!want[i] && page[i] != null) { page[i] = null; if (page == tPage) gPage[i] = null; freed = true; }
    if (freed) System.gc();
    int[] used = page == tPage ? tUsed : sUsed;
    for (int i = 0; i < page.length; i++)
      if (want[i]) { used[i] = tick; if (page[i] == null) page[i] = loadPage(page, dir, i); }
  }

  // one page. Out of memory: let go of the see-through copies and of pages not drawn for 40 frames,
  // then of pages not drawn this frame, and try again each time. Pages in use always stay, so a level
  // that doesn't fit can't fall into dropping and reloading the same pages every frame.
  private static Image loadPage(Image[] page, String dir, int i) {
    for (int k = 0; k < 3; k++) {
      try { return Image.createImage(dir + i + ".png"); }
      catch (OutOfMemoryError e) {
        if (k == 2) break;
        dropGhosts();
        int age = k == 0 ? 40 : 1;
        freeOld(tPage, tUsed, age); freeOld(sPage, sUsed, age);
        System.gc();
      } catch (Throwable e) { return null; }
    }
    starved++;
    return null;
  }

  private static void freeOld(Image[] page, int[] used, int age) {
    if (page == null) return;
    for (int j = 0; j < page.length; j++) if (page[j] != null && tick - used[j] >= age) page[j] = null;
  }

  // memory the pages take, in KB (the FPS readout)
  static int pageKB() {
    long n = 0;
    Image[][] ps = { tPage, sPage, gPage };
    for (int k = 0; k < 3; k++) if (ps[k] != null)
      for (int i = 0; i < ps[k].length; i++) if (ps[k][i] != null) n += ps[k][i].getWidth() * ps[k][i].getHeight();
    return (int) (n >> 8);
  }

  static void dropGhosts() {
    if (gPage != null) for (int j = 0; j < gPage.length; j++) gPage[j] = null;
  }

  // the tile pages a level can show: its tiles, what they turn into (bricks, bonus blocks) and the coin
  static void needTiles(Level l, int coin) {
    if (!paged || l == null) return;
    if (keepTiles) {                   // load them all (once): like the whole sheet, a bit smaller
      for (int i = 0; i < tWant.length; i++) tWant[i] = true;
      fetch(tPage, tWant, "/tp/");
      return;
    }
    boolean[] tile = new boolean[tFrames.length];
    byte[][] maps = { l.bg, l.ia, l.fg };
    for (int m = 0; m < 3; m++) { byte[] b = maps[m]; for (int i = 0; i < b.length; i++) tile[b[i] & 0xff] = true; }
    if (coin >= 0) tile[coin] = true;
    for (boolean more = true; more; ) {
      more = false;
      for (int t = 0; t < tile.length; t++)
        if (tile[t] && tNext[t] >= 0 && tNext[t] < tile.length && !tile[tNext[t]]) { tile[tNext[t]] = true; more = true; }
    }
    for (int i = 0; i < tWant.length; i++) tWant[i] = false;
    for (int t = 0; t < tile.length; t++) if (tile[t]) {
      short[] fr = tFrames[t];
      for (int j = 0; j < fr.length; j++) { int p = tfp[fr[j]] & 0xff; if (p < tWant.length) tWant[p] = true; }
    }
    fetch(tPage, tWant, "/tp/");
  }

  // sprite pages: 0 levels, menus and the title; 1 the world map; 2 the level editor (its icons; the
  // enemies it shows load as they're drawn). Whatever else was loaded as it was drawn goes too.
  static void needSprites(int set) { needSprites(set, false); }
  static void needSprites(int set, boolean again) {
    if (again) spriteSet = -1;
    if (!sPaged || set == spriteSet) return;
    spriteSet = set;
    for (int i = 0; i < sWant.length; i++) sWant[i] = false;
    for (int s = 0; s < sFrames.length; s++) {
      boolean ui = s >= Spr.ONEUP && s < Spr.IC_UP || s == Spr.IC_LEFT || s == Spr.IC_RIGHT;
      boolean ed = s >= Spr.IC_UP && s < Spr.WM_TILES || s == Spr.LARGETUX_WALK_RIGHT;
      // levels: small Tux and the items up front; big and fire Tux and the enemies load when first drawn
      boolean lv = s <= Spr.SMALLTUX_STAR || s >= Spr.ONEUP && s <= Spr.CLOUD;
      if (set == 0 ? ui || lv : set == 1 ? ui || s >= Spr.WM_TILES : ui || ed) {
        short[] fr = sFrames[s];
        for (int j = 0; j < fr.length; j++) { int p = sfp[fr[j]] & 0xff; if (p < sWant.length) sWant[p] = true; }
      }
    }
    fetch(sPage, sWant, "/sp/");
  }

  static Image tileImage(int f) {
    if (!paged) return tileImg;
    int p = tfp[f] & 0xff;
    if (p >= tPage.length) return null;
    tUsed[p] = tick;
    if (tPage[p] == null) tPage[p] = loadPage(tPage, "/tp/", p);
    return tPage[p];
  }

  static Image spriteImage(int f) {
    if (!sPaged) return sprImg;
    int p = sfp[f] & 0xff;
    if (p >= sPage.length) return null;
    sUsed[p] = tick;
    if (sPage[p] == null) sPage[p] = loadPage(sPage, "/sp/", p);
    return sPage[p];
  }

  // a tile frame's page at half alpha, made from the page when first needed (null: no memory for it)
  static Image ghostImage(int f) {
    int p = tfp[f] & 0xff;
    if (p >= tPage.length || ghostFailed) return null;
    if (gPage[p] == null) {
      Image src = tileImage(f);
      if (src == null) return null;
      try {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getRGB(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = ((px[i] >>> 25) << 24) | (px[i] & 0xffffff);
        gPage[p] = Image.createRGBImage(px, w, h, true);
      } catch (Throwable e) { dropGhosts(); ghostFailed = true; System.gc(); return null; }
    }
    return gPage[p];
  }

  // a tile (compact index), see-through or not: the editor on the paged jar
  static void drawTileGhost(Graphics g, int t, int x, int y, int tick, boolean see) {
    short[] fr = tFrames[t];
    if (fr.length == 0) return;
    int f = fr.length == 1 ? fr[0] : fr[((tick * 25) / tAnim[t]) % fr.length];
    Image img = see ? ghostImage(f) : null;
    if (img == null) img = tileImage(f);
    if (img == null) return;
    g.drawRegion(img, tfx[f], tfy[f], tfw[f] & 0xff, tfh[f] & 0xff, 0, x, y, Graphics.TOP | Graphics.LEFT);
  }

  // one tile frame at x, y (editor overlays)
  static void drawFrame(Graphics g, int f, int x, int y) {
    Image img = tileImage(f);
    if (img != null) g.drawRegion(img, tfx[f], tfy[f], tfw[f] & 0xff, tfh[f] & 0xff, 0, x, y, Graphics.TOP | Graphics.LEFT);
  }

  static void load() throws IOException {
    paged = cls.getResourceAsStream("/tp.bin") != null;
    sPaged = cls.getResourceAsStream("/sp.bin") != null;
    if (!paged) tileImg = Image.createImage("/t.png");
    if (!sPaged) {
      try { sprImg = Image.createImage("/s.png"); }
      catch (IOException e) { sprImg = Image.createImage("/s2.png"); hardSprites = true; }   // the Max Potato jar
    } else hardSprites = true;
    fontImg = Image.createImage("/f.png");
    DataInputStream in = open("/t.bin");
    int n = in.readUnsignedShort();
    tfx = new short[n]; tfy = new short[n]; tfw = new byte[n]; tfh = new byte[n];
    for (int i = 0; i < n; i++) {
      tfx[i] = (short) in.readUnsignedByte();
      int ylo = in.readUnsignedByte();
      tfw[i] = in.readByte(); tfh[i] = in.readByte();
      tfy[i] = (short) (ylo | (in.readUnsignedByte() << 8));
    }
    n = in.readUnsignedShort();
    tFlags = new byte[n]; tData = new byte[n]; tAnim = new short[n]; tNext = new short[n];
    tFrames = new short[n][];
    for (int i = 0; i < n; i++) {
      tFlags[i] = in.readByte(); tData[i] = in.readByte();
      tAnim[i] = in.readShort(); tNext[i] = in.readShort();
      int f = in.readUnsignedByte();
      tFrames[i] = new short[f];
      for (int j = 0; j < f; j++) tFrames[i][j] = in.readShort();
    }
    in.close();

    in = open("/s.bin");
    n = in.readUnsignedShort();
    sfx = new short[n]; sfy = new short[n]; sfw = new byte[n]; sfh = new byte[n];
    for (int i = 0; i < n; i++) {
      sfx[i] = in.readShort(); sfy[i] = in.readShort();
      sfw[i] = in.readByte(); sfh[i] = in.readByte();
    }
    n = in.readUnsignedShort();
    sDelay = new short[n]; sHx = new byte[n]; sHy = new byte[n]; sFrames = new short[n][];
    for (int i = 0; i < n; i++) {
      sDelay[i] = in.readShort(); sHx[i] = in.readByte(); sHy[i] = in.readByte();
      int f = in.readUnsignedByte();
      sFrames[i] = new short[f];
      for (int j = 0; j < f; j++) sFrames[i][j] = in.readShort();
    }
    in.close();
    if (paged) {
      tfp = readPages("/tp.bin", tfx, tfy);
      in = open("/tp.bin"); tPage = new Image[in.readUnsignedByte()]; in.close();
      tWant = new boolean[tPage.length]; gPage = new Image[tPage.length]; tUsed = new int[tPage.length];
    }
    if (sPaged) {
      sfp = readPages("/sp.bin", sfx, sfy);
      in = open("/sp.bin"); sPage = new Image[in.readUnsignedByte()]; in.close();
      sWant = new boolean[sPage.length]; sUsed = new int[sPage.length];
    }
  }

  // draw tile (compact index) with its top left at screen x,y; tick = 25 ms frame counter
  static void drawTile(Graphics g, int t, int x, int y, int tick) {
    short[] fr = tFrames[t];
    if (fr.length == 0) return;
    int f = fr.length == 1 ? fr[0] : fr[((tick * 25) / tAnim[t]) % fr.length];
    Image img = paged ? tileImage(f) : tileImg;
    if (img == null) return;
    g.drawRegion(img, tfx[f], tfy[f], tfw[f] & 0xff, tfh[f] & 0xff, 0, x, y, Graphics.TOP | Graphics.LEFT);
  }

  static void drawTileImg(Graphics g, Image img, int t, int x, int y, int tick) {
    short[] fr = tFrames[t];
    if (fr.length == 0) return;
    int f = fr.length == 1 ? fr[0] : fr[((tick * 25) / tAnim[t]) % fr.length];
    g.drawRegion(img, tfx[f], tfy[f], tfw[f] & 0xff, tfh[f] & 0xff, 0, x, y, Graphics.TOP | Graphics.LEFT);
  }

  // draw sprite s at world position (wx, wy) relative to the camera; time in ms picks the frame
  static void drawSprite(Graphics g, int s, int sx, int sy, long time) {
    short[] fr = sFrames[s];
    int f = fr[fr.length == 1 ? 0 : (int) ((time / sDelay[s]) % fr.length)];
    Image img = sPaged ? spriteImage(f) : sprImg;
    if (img == null) return;
    g.drawRegion(img, sfx[f], sfy[f], sfw[f] & 0xff, sfh[f] & 0xff, 0,
        sx - (sHx[s] >> 1), sy - (sHy[s] >> 1), Graphics.TOP | Graphics.LEFT);
  }

  // 0.1.3's 8x9 bitmap fonts with its shadow: the glyph shapes in half transparent black, moved
  // shadowsize pixels right and shadowsize/2 down (0.1.3 halves y, not x, at 320x240)
  static void text(Graphics g, int font, String s, int x, int y) { text(g, font, s, x, y, 1); }

  static void text(Graphics g, int font, String s, int x, int y, int shadow) {
    if (shadow != 0) chars(g, SHADOW, s, x + shadow, y + (shadow >> 1));
    chars(g, font, s, x, y);
  }

  static void textCentered(Graphics g, int font, String s, int cx, int y) {
    text(g, font, s, cx - s.length() * 4, y, 1);
  }

  static void textCentered(Graphics g, int font, String s, int cx, int y, int shadow) {
    text(g, font, s, cx - s.length() * 4, y, shadow);
  }

  // translucent rectangle (0.1.3's fillrect with alpha)
  private static int[] fillBuf = new int[0];
  private static int fillArgb;
  static void fillAlpha(Graphics g, int x, int y, int w, int h, int argb) {
    if (w <= 0 || h <= 0) return;
    int rows = Math.min(h, 16);
    if (fillBuf.length < w * rows || fillArgb != argb) {
      if (fillBuf.length < w * rows) fillBuf = new int[w * rows];
      for (int i = 0; i < fillBuf.length; i++) fillBuf[i] = argb;
      fillArgb = argb;
    }
    for (int yy = 0; yy < h; yy += rows) {
      int n = Math.min(rows, h - yy);
      g.drawRGB(fillBuf, 0, w, x, y + yy, w, n, true);
    }
  }

  static void drawSpriteTL(Graphics g, int s, int x, int y) {
    int f = sFrames[s][0];
    Image img = sPaged ? spriteImage(f) : sprImg;
    if (img == null) return;
    g.drawRegion(img, sfx[f], sfy[f], sfw[f] & 0xff, sfh[f] & 0xff, 0, x, y, Graphics.TOP | Graphics.LEFT);
  }

  // a text file from res/x, one string per line
  static String[] lines(String name) {
    java.util.Vector v = new java.util.Vector();
    try {
      java.io.InputStream in = cls.getResourceAsStream(name);
      StringBuffer sb = new StringBuffer();
      int c;
      while ((c = in.read()) != -1) {
        if (c == '\n') { v.addElement(sb.toString()); sb.setLength(0); }
        else if (c != '\r') sb.append((char) c);
      }
      if (sb.length() > 0) v.addElement(sb.toString());
      in.close();
    } catch (Exception e) {
      v.addElement("File was not found!");
      v.addElement(name);
    }
    String[] r = new String[v.size()];
    v.copyInto(r);
    return r;
  }

  static String readString(DataInputStream in) throws IOException {
    byte[] b = new byte[in.readUnsignedByte()];
    in.readFully(b);
    char[] c = new char[b.length];
    for (int i = 0; i < b.length; i++) c[i] = (char) (b[i] & 0xff);
    return new String(c);
  }

  // a reused image's Graphics with no translation or clip left over: some VMs (FreeJ2ME) hand back
  // the same object every time, still translated from the last redraw
  static Graphics fresh(javax.microedition.lcdui.Image img) {
    Graphics g = img.getGraphics();
    g.translate(-g.getTranslateX(), -g.getTranslateY());
    g.setClip(0, 0, img.getWidth(), img.getHeight());
    return g;
  }

  static void chars(Graphics g, int font, String s, int x, int y) {
    int n = s.length();
    for (int i = 0; i < n; i++) {
      int c = s.charAt(i);
      if (c < 32 || c > 126) c = c == 0xe4 ? 'a' : c == 0xf6 ? 'o' : c == 0xfc ? 'u' : '?';
      int k = c - 32;
      if (c != ' ')
        g.drawRegion(fontImg, (k & 15) * 8, font * 54 + (k >> 4) * 9, 8, 9, 0, x + i * 8, y, Graphics.TOP | Graphics.LEFT);
    }
  }
}
