// Tux on hand-built terrain: runs down stairs and falls beside a block, printing where he is each
// frame, to catch "landing on air". usage: java PhysProbe <frameMs> [stairWidth]
public class PhysProbe {
  static Game g; static Level lv; static int solid;
  static void fill(int x0, int y0, int x1, int y1, int t) { for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) lv.ia[y * lv.width + x] = (byte) t; }
  public static void main(String[] a) throws Exception {
    int ms = Integer.parseInt(a[0]); int sw = a.length > 1 ? Integer.parseInt(a[1]) : 1;
    g = new Game(new SuperTux()); Res.load();
    for (int i = 1; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.SOLID) != 0 && (Res.tFlags[i] & (Res.BRICK | Res.FULLBOX | Res.ICE)) == 0) { solid = i; break; }
    g.worldmap = null; g.detail = Integer.getInteger("detail", 0).intValue(); g.levelId = "a1"; g.demo = false; g.level = null; g.loadLevel(false); g.startPlay();
    lv = g.level; g.badguys.removeAllElements();
    for (int y = Level.H - 1; y > 0; y--) { int tt = lv.tileAt(3, y); if ((Res.tFlags[tt] & Res.SOLID) != 0) solid = tt; else break; }
    // stairs down to the right: a step every sw tiles, one tile lower each time
    fill(0, 0, 60, Level.H - 1, 0);
    fill(0, 6, 9, Level.H - 1, solid);
    int top = 7;
    for (int s = 0; s < 6; s++) { fill(10 + s * sw, top + s, 10 + s * sw + sw - 1, Level.H - 1, solid); }
    Player t = g.tux;
    if (System.getProperty("big") != null) t.grow();
    t.x = 4 * 32; t.y = 6 * 32 - t.h; t.setVelocity(0, 0); t.syncOld();
    float fr = ms / 10f;
    System.out.println("stairs, width " + sw + ", frame " + ms + " ms");
    for (int f = 0; f < 90; f++) {
      g.keys = Game.K_RIGHT | (a.length > 2 ? Game.K_FIRE : 0); g.now += ms;
      g.handleInput(); t.action(fr);
      int col = (int) (t.x + t.w / 2) / 32, groundTop = -1;
      for (int y = 0; y < Level.H; y++) if (lv.solid(col * 32 + 16, y * 32 + 1)) { groundTop = y * 32; break; }
      float bottom = t.y + t.h;
      System.out.println(f + " x=" + (int) t.x + " bottom=" + bottom + " vy=" + t.getVY() + " onGround=" + t.onGround()
          + " groundTop(under centre)=" + groundTop + (t.onGround() && groundTop >= 0 && bottom < groundTop - 1 ? "   <-- standing above the ground" : ""));
      String dir = System.getProperty("shots");
      if (dir != null) {
        g.scrollX = Math.max(0, (int) t.x - 200);
        java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(g.sw, g.sh, 1);
        g.draw(new javax.microedition.lcdui.Graphics(im.createGraphics()));
        javax.imageio.ImageIO.write(im, "png", new java.io.File(dir, "f" + (f < 10 ? "0" : "") + f + ".png"));
      }
      if (t.x > (10 + 6 * sw) * 32) break;
    }
    System.exit(0);
  }
}
