// Tux falls past a floating block while holding towards it: does he stop in mid-air?
// usage: java PhysProbe2 <frameMs> <gapPx> [run]
public class PhysProbe2 {
  public static void main(String[] a) throws Exception {
    int ms = Integer.parseInt(a[0]); int gap = Integer.parseInt(a[1]);
    Game g = new Game(new SuperTux()); Res.load();
    g.worldmap = null; g.levelId = "a1"; g.demo = false; g.level = null; g.loadLevel(false); g.startPlay();
    Level lv = g.level; g.badguys.removeAllElements();
    int solid = 0;
    for (int y = Level.H - 1; y > 0; y--) { int tt = lv.tileAt(3, y); if ((Res.tFlags[tt] & Res.SOLID) != 0) solid = tt; else break; }
    for (int i = 0; i < lv.ia.length; i++) lv.ia[i] = 0;
    for (int x = 0; x < 40; x++) lv.ia[14 * lv.width + x] = (byte) solid;
    lv.ia[8 * lv.width + 20] = (byte) solid;     // the floating block, column 20 (x 640..671), row 8 (y 256..287)
    Player t = g.tux;
    t.x = 640 - 32 - gap; t.y = Integer.getInteger("y0", 230).intValue(); t.setVelocity(0, 0); t.syncOld(); t.gravity = true;
    float fr = ms / 10f;
    float lastY = t.y;
    for (int f = 0; f < 60; f++) {
      g.keys = Game.K_RIGHT | (a.length > 2 ? Game.K_FIRE : 0); g.now += ms;
      g.handleInput(); t.action(fr); t.checkBounds();
      System.out.println(f + " x=" + t.x + " y=" + t.y + " (bottom " + (t.y + t.h) + ") vy=" + t.getVY() + " vx=" + t.vx + " ground=" + t.onGround()
          + (t.y == lastY && !t.onGround() ? "  <-- stalled" : "") + (t.onGround() && t.y + t.h < 448 ? "  <-- standing in the air?" : ""));
      lastY = t.y;
      if (t.y + t.h >= 448 && t.onGround()) break;
    }
    System.exit(0);
  }
}
