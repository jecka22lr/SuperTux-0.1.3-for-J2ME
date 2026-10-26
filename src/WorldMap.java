// The world map (worldmap.cpp): road tiles, level dots, teleporters, Tux walking from dot to dot,
// messages, and the savegame. Map units are 0.1.3's (32 per tile), drawn at half size.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.microedition.lcdui.Graphics;
import javax.microedition.rms.RecordStore;

final class WorldMap {
  static final int D_NONE = 0, D_WEST = 1, D_EAST = 2, D_NORTH = 3, D_SOUTH = 4;
  static final int N = 1, S = 2, E = 4, W = 8, STOP = 16, AUTO = 32;
  static final int NO_BONUS = 0, GROWUP_BONUS = 1, FLOWER_BONUS = 2;
  static final int MESSAGE_TIME = 2800;

  // antarctica.stwt
  static byte[] tFlags, tOneWay; static short[] tSprite;

  final Game game;
  String name, saveName;
  int music, startX, startY, width, height;
  byte[] tiles;
  // levels and other spots
  int count;
  String[] lid, ltitle, extro, mapMessage, teleportMessage;
  int[] lx, ly, destX, destY, flags;
  boolean[] solved;
  static final int F_AUTOPATH = 1, F_PASSIVE = 2, F_INVISIBLE = 4, F_UP = 8, F_DOWN = 16, F_LEFT = 32, F_RIGHT = 64;

  // Tux on the map
  int tileX, tileY, direction, backDirection, inputDirection, tuxInput;
  float offset;
  boolean moving;
  String passiveMessage = ""; long passiveEnd;
  long frozenUntil;          // the teleporter's one second pause
  int camX, camY;
  int entered = -1;          // level being played

  WorldMap(Game g, int n, String save) throws java.io.IOException {
    game = g;
    saveName = save;
    if (tFlags == null) {
      DataInputStream in = Res.open("/wt.bin");
      int c = in.readUnsignedShort();
      tFlags = new byte[c]; tOneWay = new byte[c]; tSprite = new short[c];
      for (int i = 0; i < c; i++) { tFlags[i] = in.readByte(); tOneWay[i] = in.readByte(); tSprite[i] = in.readShort(); }
      in.close();
    }
    DataInputStream in = Res.open("/w/" + n + ".bin");
    name = Res.readString(in);
    music = in.readUnsignedByte();
    startX = in.readShort(); startY = in.readShort(); width = in.readShort(); height = in.readShort();
    tiles = new byte[width * height];
    in.readFully(tiles);
    count = in.readUnsignedShort();
    lid = new String[count]; ltitle = new String[count]; extro = new String[count];
    mapMessage = new String[count]; teleportMessage = new String[count];
    lx = new int[count]; ly = new int[count]; destX = new int[count]; destY = new int[count];
    flags = new int[count]; solved = new boolean[count];
    for (int i = 0; i < count; i++) {
      lid[i] = Res.readString(in); ltitle[i] = Res.readString(in);
      lx[i] = in.readShort(); ly[i] = in.readShort();
      extro[i] = Res.readString(in); mapMessage[i] = Res.readString(in);
      flags[i] = in.readUnsignedByte();
      destX[i] = in.readShort(); destY[i] = in.readShort();
      teleportMessage[i] = Res.readString(in);
    }
    in.close();
    tileX = startX; tileY = startY;
  }

  int at(int x, int y) {
    int t = tiles[y * width + x] & 0xff;
    return t < tFlags.length ? t : 0;
  }

  int atLevel() {
    for (int i = 0; i < count; i++) if (lx[i] == tileX && ly[i] == tileY) return i;
    return -1;
  }

  static int reverse(int d) {
    switch (d) { case D_WEST: return D_EAST; case D_EAST: return D_WEST; case D_NORTH: return D_SOUTH; case D_SOUTH: return D_NORTH; }
    return D_NONE;
  }

  int nx(int x, int d) { return d == D_WEST ? x - 1 : d == D_EAST ? x + 1 : x; }
  int ny(int y, int d) { return d == D_NORTH ? y - 1 : d == D_SOUTH ? y + 1 : y; }

  boolean pathOk(int d, int ox, int oy) {
    int x = nx(ox, d), y = ny(oy, d);
    if (!(x >= 0 && x < width && y >= 0 && y < height)) return false;
    int ow = tOneWay[at(x, y)];
    if (ow != 0)
      return !((ow == 1 && d != D_SOUTH) || (ow == 2 && d != D_NORTH) || (ow == 3 && d != D_WEST) || (ow == 4 && d != D_EAST));
    int fo = tFlags[at(ox, oy)], fn = tFlags[at(x, y)];
    switch (d) {
      case D_WEST: return (fo & W) != 0 && (fn & E) != 0;
      case D_EAST: return (fo & E) != 0 && (fn & W) != 0;
      case D_NORTH: return (fo & N) != 0 && (fn & S) != 0;
      case D_SOUTH: return (fo & S) != 0 && (fn & N) != 0;
    }
    return false;
  }

  int posX() {
    float x = tileX * 32;
    if (direction == D_WEST) x -= offset - 32; else if (direction == D_EAST) x += offset - 32;
    return (int) x;
  }

  int posY() {
    float y = tileY * 32;
    if (direction == D_NORTH) y -= offset - 32; else if (direction == D_SOUTH) y += offset - 32;
    return (int) y;
  }

  void stop() { offset = 0; direction = D_NONE; moving = false; }

  // Tux::update
  void tuxUpdate(float delta) {
    if (!moving) {
      if (tuxInput != D_NONE) {
        int l = atLevel();
        if ((l < 0 || solved[l] || lid[l].length() == 0 || game.dbgWalk) && pathOk(tuxInput, tileX, tileY)) {
          tileX = nx(tileX, tuxInput); tileY = ny(tileY, tuxInput);
          moving = true; direction = tuxInput; backDirection = reverse(direction);
        } else if (tuxInput == backDirection) {
          moving = true; direction = tuxInput;
          tileX = nx(tileX, direction); tileY = ny(tileY, direction);
          backDirection = reverse(direction);
        }
      }
    } else {
      offset += 20.0f * delta;
      if (offset > 32) {
        offset -= 32;
        int l = atLevel();
        if (l >= 0 && lid[l].length() == 0 && mapMessage[l].length() > 0 && (flags[l] & F_PASSIVE) != 0) {
          if ((direction == D_NORTH && (flags[l] & F_DOWN) != 0) || (direction == D_SOUTH && (flags[l] & F_UP) != 0)
              || (direction == D_WEST && (flags[l] & F_RIGHT) != 0) || (direction == D_EAST && (flags[l] & F_LEFT) != 0)) {
            passiveMessage = mapMessage[l];
            passiveEnd = game.now + MESSAGE_TIME;
          }
        }
        int cur = tFlags[at(tileX, tileY)];
        if ((cur & STOP) != 0 || (l >= 0 && (lid[l].length() > 0 || destX[l] != -1))) stop();
        else {
          if ((cur & AUTO) != 0) {
            int dir = D_NONE;
            if ((cur & N) != 0 && backDirection != D_NORTH) dir = D_NORTH;
            else if ((cur & S) != 0 && backDirection != D_SOUTH) dir = D_SOUTH;
            else if ((cur & E) != 0 && backDirection != D_EAST) dir = D_EAST;
            else if ((cur & W) != 0 && backDirection != D_WEST) dir = D_WEST;
            if (dir != D_NONE) { direction = dir; backDirection = reverse(direction); }
            else { stop(); return; }
          }
          if (pathOk(direction, tileX, tileY)) { tileX = nx(tileX, direction); tileY = ny(tileY, direction); }
          else stop();
        }
      }
    }
  }

  // WorldMap::update; enter = the select key was pressed this frame
  void update(float delta, boolean enter) {
    if (game.now < frozenUntil) return;
    if (enter && !moving) {
      int l = atLevel();
      if (l >= 0 && lid[l].length() > 0) {
        entered = l;
        game.startLevel(lid[l]);
        return;
      } else if (l >= 0 && destX[l] != -1 && destY[l] != -1) {
        game.sound(Sound.WARP);
        backDirection = D_NONE;
        tileX = destX[l]; tileY = destY[l];
        frozenUntil = game.now + 1000;
      }
    } else {
      tuxUpdate(delta);
      tuxInput = inputDirection;
    }
  }

  static final int ES_FINISHED = 0, ES_ABORT = 1, ES_GAME_OVER = 2;

  // back from a level (the rest of WorldMap::update); returns true when the map should close
  boolean levelDone(int status, boolean gotCoffee, boolean big) {
    boolean quit = false;
    int l = entered;
    entered = -1;
    if (status == ES_FINISHED && l >= 0) {
      boolean old = solved[l];
      solved[l] = true;
      game.bonus = gotCoffee ? FLOWER_BONUS : big ? GROWUP_BONUS : NO_BONUS;
      if (old != solved[l] && (flags[l] & F_AUTOPATH) != 0) {
        int cur = tFlags[at(tileX, tileY)];
        int dir = D_NONE;
        if ((cur & N) != 0 && backDirection != D_NORTH) dir = D_NORTH;
        else if ((cur & S) != 0 && backDirection != D_SOUTH) dir = D_SOUTH;
        else if ((cur & E) != 0 && backDirection != D_EAST) dir = D_EAST;
        else if ((cur & W) != 0 && backDirection != D_WEST) dir = D_WEST;
        if (dir != D_NONE) tuxInput = dir;
      }
      if (extro[l].length() > 0) {
        save();
        game.showExtro("/x/" + extro[l]);
        return true;
      }
    } else if (status == ES_GAME_OVER) {
      quit = true;
      game.resetStatus();
    }
    save();
    return quit;
  }

  // ---------------- drawing ----------------
  void draw(Graphics g, int sw, int sh, boolean landscape) {
    int vw = sw * 2, vh = sh * 2;
    int px = posX(), py = posY();
    camX = -px + vw / 2; camY = -py + vh / 2;
    if (camX > 0) camX = 0;
    if (camY > 0) camY = 0;
    if (camX < vw - width * 32) camX = vw - width * 32;
    if (camY < vh - height * 32) camY = vh - height * 32;
    if (width * 32 < vw) camX = (vw - width * 32) / 2;
    if (height * 32 < vh) camY = (vh - height * 32) / 2;
    int x0 = Math.max(0, -camX / 32), y0 = Math.max(0, -camY / 32);
    int x1 = Math.min(width - 1, (vw - camX) / 32), y1 = Math.min(height - 1, (vh - camY) / 32);
    if (width * 32 < vw || height * 32 < vh) { g.setColor(0); g.fillRect(0, 0, sw, sh); }
    if (!game.ramSaver) drawCached(g, sw, sh, x0, y0, x1, y1);   // every detail level: looks the same
    else {
      if (width * 32 >= vw && height * 32 >= vh) { g.setColor(0); g.fillRect(0, 0, sw, sh); }
      for (int y = y0; y <= y1; y++)
        for (int x = x0; x <= x1; x++)
          Res.drawSpriteTL(g, tSprite[at(x, y)], (x * 32 + camX) / 2, (y * 32 + camY) / 2);
    }
    for (int i = 0; i < count; i++) {
      int s;
      if (lid[i].length() == 0) {
        if (destX[i] != -1 && (flags[i] & F_INVISIBLE) == 0) s = Spr.WM_TELEPORTER; else continue;
      } else s = solved[i] ? Spr.WM_DOT_GREEN : Spr.WM_DOT_RED;
      int dx = (lx[i] * 32 + camX) / 2, dy = (ly[i] * 32 + camY) / 2;
      if (dx > -16 && dx < sw && dy > -16 && dy < sh) Res.drawSpriteTL(g, s, dx, dy);   // on screen only
    }
    int ts = game.bonus == GROWUP_BONUS ? Spr.WM_TUX : game.bonus == FLOWER_BONUS ? Spr.WM_FIRETUX : Spr.WM_SMALLTUX;
    Res.drawSpriteTL(g, ts, (px + camX) / 2, (py + camY - 10) / 2);
    status(g, sw, sh, landscape);
  }

  // The map tiles (not with RAM saver) kept in an offscreen ring of 16-pixel cells (cell x, y lives at
  // (x % cN, y % cM)), so a frame is up to four blits plus the cells that scrolled into view,
  // instead of a sprite per visible tile. The map never changes, so nothing else redraws it.
  private javax.microedition.lcdui.Image cache; private Graphics cacheG;
  private int cN, cM, cX0, cY0, cX1 = -1, cY1 = -1;

  private void drawCached(Graphics g, int sw, int sh, int x0, int y0, int x1, int y1) {
    int n = x1 - x0 + 1, m = y1 - y0 + 1;
    if (cache == null || cN < n || cM < m) {
      cache = null; cacheG = null;
      cN = Math.max(n, sw / 16 + 2); cM = Math.max(m, sh / 16 + 2);
      cache = javax.microedition.lcdui.Image.createImage(cN << 4, cM << 4);
      cacheG = cache.getGraphics();
      cX1 = -1;
    }
    for (int y = y0; y <= y1; y++)
      for (int x = x0; x <= x1; x++) {
        if (cX1 >= 0 && x >= cX0 && x <= cX1 && y >= cY0 && y <= cY1) continue;
        int cx = (x % cN) << 4, cy = (y % cM) << 4;
        cacheG.setClip(cx, cy, 16, 16);
        cacheG.setColor(0); cacheG.fillRect(cx, cy, 16, 16);
        Res.drawSpriteTL(cacheG, tSprite[at(x, y)], cx, cy);
      }
    cX0 = x0; cY0 = y0; cX1 = x1; cY1 = y1;
    // where cell (x0, y0) lands on the screen, then the ring in up to two pieces each way
    int sx = x0 * 16 + (camX >> 1), sy = y0 * 16 + (camY >> 1);
    int sx0 = x0 % cN, sy0 = y0 % cM;
    int w1 = Math.min(n, cN - sx0), h1 = Math.min(m, cM - sy0);
    blit(g, sx0, sy0, w1, h1, sx, sy);
    if (w1 < n) blit(g, 0, sy0, n - w1, h1, sx + (w1 << 4), sy);
    if (h1 < m) {
      blit(g, sx0, 0, w1, m - h1, sx, sy + (h1 << 4));
      if (w1 < n) blit(g, 0, 0, n - w1, m - h1, sx + (w1 << 4), sy + (h1 << 4));
    }
  }

  private void blit(Graphics g, int cx, int cy, int cw, int ch, int x, int y) {
    g.drawRegion(cache, cx << 4, cy << 4, cw << 4, ch << 4, 0, x, y, Graphics.TOP | Graphics.LEFT);
  }

  // WorldMap::draw_status
  void status(Graphics g, int sw, int sh, boolean landscape) {
    // no score/coins/lives bar on the map (Jecka's call); only the level name and messages
    int textY = landscape ? 100 : 140;
    if (!moving) {
      int l = atLevel();
      if (l >= 0) {
        if (lid[l].length() > 0) {
          String[] t = Game.wrap(ltitle[l], sw / 8);
          for (int i = 0; i < t.length; i++) Res.textCentered(g, Res.WHITE, t[i], sw / 2, sh - 10 - (t.length - 1 - i) * 10);
        }
        else if (destX[l] != -1) {
          if (teleportMessage[l].length() > 0) game.centeredWrapped(g, Res.GOLD, teleportMessage[l], sw / 2, sh / 2 - 5, 10);
        }
        if (mapMessage[l].length() > 0 && (flags[l] & F_PASSIVE) == 0)
          game.centeredWrapped(g, Res.GOLD, mapMessage[l], sw / 2, textY, 10);
      }
    }
    if (passiveEnd > game.now) game.centeredWrapped(g, Res.GOLD, passiveMessage, sw / 2, textY, 10);
  }

  // ---------------- savegame (RMS instead of slotN.stsg) ----------------
  void save() {
    try {
      int n = 0;
      for (int i = 0; i < count; i++) if (solved[i]) n++;
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bo);
      out.writeByte(1);
      out.writeUTF("Icyisland - " + n + "/" + count);
      out.writeInt(game.lives); out.writeInt(game.score); out.writeInt(game.distros);
      out.writeShort(tileX); out.writeShort(tileY); out.writeByte(backDirection); out.writeByte(game.bonus);
      out.writeShort(count);
      for (int i = 0; i < count; i++) out.writeBoolean(solved[i] && lid[i].length() > 0);
      out.close();
      byte[] b = bo.toByteArray();
      RecordStore rs = RecordStore.openRecordStore(saveName, true);
      if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length); else rs.addRecord(b, 0, b.length);
      rs.closeRecordStore();
    } catch (Throwable e) { }
  }

  // WorldMap::loadgame; a missing save starts a new game
  void load() {
    byte[] b = read(saveName);
    if (b == null) return;
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
      in.readByte(); in.readUTF();
      game.lives = in.readInt(); game.score = in.readInt(); game.distros = in.readInt();
      if (game.lives < 0) game.lives = Game.START_LIVES;
      tileX = in.readShort(); tileY = in.readShort(); backDirection = in.readByte(); game.bonus = in.readByte();
      int n = in.readShort();
      for (int i = 0; i < n && i < count; i++) solved[i] = in.readBoolean();
    } catch (Throwable e) { }
  }

  static byte[] read(String store) {
    try {
      RecordStore rs = RecordStore.openRecordStore(store, false);
      byte[] b = rs.getNumRecords() > 0 ? rs.getRecord(1) : null;
      rs.closeRecordStore();
      return b;
    } catch (Throwable e) { return null; }
  }

  // slotinfo()
  static String slotInfo(String prefix, int slot) {
    byte[] b = read(prefix + slot);
    if (b == null) return "Slot " + slot + " - Free";
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
      in.readByte();
      String t = in.readUTF();
      return t.length() > 0 ? "Slot " + slot + " - " + t : "Slot " + slot + " - Savegame";
    } catch (Throwable e) { return "Slot " + slot + " - Savegame"; }
  }

  static void delete(String store) {
    try { RecordStore.deleteRecordStore(store); } catch (Throwable e) { }
  }
}
