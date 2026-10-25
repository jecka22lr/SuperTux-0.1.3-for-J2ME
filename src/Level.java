// One level: three 15-row tilemaps (compact tile indices) and its settings.
import java.io.DataInputStream;
import java.io.IOException;

final class Level {
  static final int H = 15;
  String name, author;
  int width, startX, startY, time, background, music, bkgdSpeed, particles;
  float gravity, autoscroll;
  boolean backScrolling;
  int topColor, bottomColor;
  byte[] bg, ia, fg;
  // objects: kind, x, y, stay-on-platform
  byte[] objKind, objStay; short[] objX, objY;
  short[] resetX, resetY;

  // a level by id ("a5", "u0_3"...): a copy saved on the phone (level editor) wins over the built-in one
  static Level load(String id) throws IOException {
    byte[] b = WorldMap.read("L" + id);
    if (b != null) return new Level(new DataInputStream(new java.io.ByteArrayInputStream(b)));
    return new Level(Res.open("/l/" + id + ".bin"));
  }

  static boolean exists(String id) {
    if (WorldMap.read("L" + id) != null) return true;
    java.io.InputStream in = Res.cls.getResourceAsStream("/l/" + id + ".bin");
    if (in == null) return false;
    try { in.close(); } catch (IOException e) { }
    return true;
  }

  void save(String id) throws Exception {
    byte[] b = toBytes();
    javax.microedition.rms.RecordStore rs = javax.microedition.rms.RecordStore.openRecordStore("L" + id, true);
    if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length); else rs.addRecord(b, 0, b.length);
    rs.closeRecordStore();
  }

  Level(byte[] data) throws IOException { this(new DataInputStream(new java.io.ByteArrayInputStream(data))); }

  // Level::init_defaults
  Level() {
    name = "UnNamed"; author = "UnNamed";
    music = 0; background = 7;              // Mortimers_chipdisko.mod, arctis.png
    width = 21; startX = 100; startY = 170; time = 100; gravity = 10; bkgdSpeed = 50;
    topColor = 0; bottomColor = 0xffffff;
    bg = new byte[width * H]; ia = new byte[width * H]; fg = new byte[width * H];
    objKind = new byte[0]; objStay = new byte[0]; objX = new short[0]; objY = new short[0];
    resetX = new short[0]; resetY = new short[0];
  }

  Level(DataInputStream in) throws IOException {
    byte[] nb = new byte[in.readUnsignedByte()];
    in.readFully(nb);
    name = latin1(nb);
    nb = new byte[in.readUnsignedByte()];
    in.readFully(nb);
    author = latin1(nb);
    width = in.readUnsignedShort();
    startX = in.readShort(); startY = in.readShort();
    time = in.readUnsignedShort();
    background = in.readUnsignedByte(); music = in.readUnsignedByte();
    gravity = in.readShort() / 10f;
    autoscroll = in.readShort() / 10f;
    backScrolling = in.readUnsignedByte() != 0;
    bkgdSpeed = in.readUnsignedByte();
    topColor = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
    bottomColor = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
    particles = in.readUnsignedByte();
    int n = width * H;
    bg = new byte[n]; ia = new byte[n]; fg = new byte[n];
    in.readFully(bg); in.readFully(ia); in.readFully(fg);
    int no = in.readUnsignedShort();
    objKind = new byte[no]; objStay = new byte[no]; objX = new short[no]; objY = new short[no];
    for (int i = 0; i < no; i++) {
      objKind[i] = in.readByte(); objX[i] = in.readShort(); objY[i] = in.readShort(); objStay[i] = in.readByte();
    }
    int nr = in.readUnsignedByte();
    resetX = new short[nr]; resetY = new short[nr];
    for (int i = 0; i < nr; i++) { resetX[i] = in.readShort(); resetY[i] = in.readShort(); }
    in.close();
  }

  byte[] toBytes() throws IOException {
    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
    java.io.DataOutputStream out = new java.io.DataOutputStream(bo);
    str(out, name); str(out, author);
    out.writeShort(width); out.writeShort(startX); out.writeShort(startY); out.writeShort(time);
    out.writeByte(background); out.writeByte(music);
    out.writeShort((int) (gravity * 10)); out.writeShort((int) (autoscroll * 10));
    out.writeByte(backScrolling ? 1 : 0); out.writeByte(bkgdSpeed);
    out.writeByte(topColor >> 16); out.writeByte(topColor >> 8); out.writeByte(topColor);
    out.writeByte(bottomColor >> 16); out.writeByte(bottomColor >> 8); out.writeByte(bottomColor);
    out.writeByte(particles);
    out.write(bg); out.write(ia); out.write(fg);
    out.writeShort(objKind.length);
    for (int i = 0; i < objKind.length; i++) {
      out.writeByte(objKind[i]); out.writeShort(objX[i]); out.writeShort(objY[i]); out.writeByte(objStay[i]);
    }
    out.writeByte(resetX.length);
    for (int i = 0; i < resetX.length; i++) { out.writeShort(resetX[i]); out.writeShort(resetY[i]); }
    out.close();
    return bo.toByteArray();
  }

  private static void str(java.io.DataOutputStream out, String s) throws IOException {
    int n = Math.min(255, s.length());
    out.writeByte(n);
    for (int i = 0; i < n; i++) out.writeByte(s.charAt(i));
  }

  // Level::change_size (at least 21 tiles)
  void changeSize(int w) {
    if (w < 21) w = 21;
    byte[][] maps = { bg, ia, fg };
    for (int m = 0; m < 3; m++) {
      byte[] n = new byte[w * H];
      for (int y = 0; y < H; y++) System.arraycopy(maps[m], y * width, n, y * w, Math.min(w, width));
      maps[m] = n;
    }
    bg = maps[0]; ia = maps[1]; fg = maps[2];
    width = w;
  }

  // Level::change on one layer (0 background, 1 interactive, 2 foreground)
  void set(int layer, float x, float y, int tile) {
    int tx = (int) x / 32, ty = (int) y / 32;
    if (tx < 0 || ty < 0 || tx >= width || ty >= H) return;
    (layer == 0 ? bg : layer == 1 ? ia : fg)[ty * width + tx] = (byte) tile;
  }

  static String latin1(byte[] b) {
    char[] c = new char[b.length];
    for (int i = 0; i < b.length; i++) c[i] = (char) (b[i] & 0xff);
    return new String(c);
  }

  // interactive tile at tile coordinates (0 outside the level)
  int tileAt(int x, int y) {
    if (x < 0 || x >= width || y < 0 || y >= H) return 0;
    return ia[y * width + x] & 0xff;
  }

  // interactive tile under a world position
  int tileAtPos(float x, float y) {
    // C-style truncation like 0.1.3: -10 / 32 is row 0
    return tileAt((int) x / 32, (int) y / 32);
  }

  int flagsAt(float x, float y) {
    return Res.tFlags[tileAtPos(x, y)];
  }

  boolean solid(float x, float y) { return (flagsAt(x, y) & Res.SOLID) != 0; }

  void change(float x, float y, int tile) {
    int tx = (int) x / 32, ty = (int) y / 32;
    if (tx < 0 || ty < 0) return;
    if (tx >= width || ty >= H) return;
    ia[ty * width + tx] = (byte) tile;
  }

  // collision_object_map: any solid tile under the rectangle (1 pixel smaller)
  boolean hitsMap(float bx, float by, float bw, float bh) {
    int sx = ((int) (bx + 1)) / 32, sy = ((int) (by + 1)) / 32;
    int mx = (int) (bx + bw), my = (int) (by + bh);
    for (int x = sx; x * 32 < mx; ++x)
      for (int y = sy; y * 32 < my; ++y)
        if ((Res.tFlags[tileAt(x, y)] & Res.SOLID) != 0) return true;
    return false;
  }

  // collision_func with the goal test
  boolean hitsGoal(float bx, float by, float bw, float bh) {
    int sx = (int) bx / 32, sy = (int) by / 32;
    int mx = (int) (bx + bw), my = (int) (by + bh);
    for (int x = sx; x * 32 < mx; ++x)
      for (int y = sy; y * 32 < my; ++y)
        if ((Res.tFlags[tileAt(x, y)] & Res.GOAL) != 0) return true;
    return false;
  }
}
