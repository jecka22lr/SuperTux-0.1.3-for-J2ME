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

  static void load() throws IOException {
    tileImg = Image.createImage("/t.png");
    sprImg = Image.createImage("/s.png");
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
  }

  // draw tile (compact index) with its top left at screen x,y; tick = 25 ms frame counter
  static void drawTile(Graphics g, int t, int x, int y, int tick) {
    short[] fr = tFrames[t];
    if (fr.length == 0) return;
    int f = fr.length == 1 ? fr[0] : fr[((tick * 25) / tAnim[t]) % fr.length];
    g.drawRegion(tileImg, tfx[f], tfy[f], tfw[f] & 0xff, tfh[f] & 0xff, 0, x, y, Graphics.TOP | Graphics.LEFT);
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
    g.drawRegion(sprImg, sfx[f], sfy[f], sfw[f] & 0xff, sfh[f] & 0xff, 0,
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
    g.drawRegion(sprImg, sfx[f], sfy[f], sfw[f] & 0xff, sfh[f] & 0xff, 0, x, y, Graphics.TOP | Graphics.LEFT);
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
