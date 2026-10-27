// Sweeps Tux falling past a floating block from many heights and distances while holding towards
// it, and reports any frame where he stops falling without landing on the block's top or the floor.
public class PhysSweep {
  public static void main(String[] a) throws Exception {
    int ms = Integer.parseInt(a[0]); boolean big = a.length > 1 && a[1].equals("big");
    Game g = new Game(new SuperTux()); Res.load();
    g.worldmap = null; g.levelId = "a1"; g.demo = false; g.level = null; g.loadLevel(false); g.startPlay();
    Level lv = g.level; g.badguys.removeAllElements();
    int solid = 0;
    for (int y = Level.H - 1; y > 0; y--) { int tt = lv.tileAt(3, y); if ((Res.tFlags[tt] & Res.SOLID) != 0) solid = tt; else break; }
    for (int i = 0; i < lv.ia.length; i++) lv.ia[i] = 0;
    for (int x = 0; x < 40; x++) lv.ia[14 * lv.width + x] = (byte) solid;
    lv.ia[8 * lv.width + 20] = (byte) solid;     // block: x 640..671, y 256..287
    Player t = g.tux;
    if (big) t.grow();
    float fr = ms / 10f;
    int bad = 0, runs = 0;
    for (int dir = 0; dir < 2; dir++)
    for (int gap = 0; gap <= 40; gap += 2)
      for (int y0 = 100; y0 <= 290; y0 += 3) {
        runs++;
        t.x = dir == 0 ? 640 - 32 - gap : 672 + gap; t.y = y0 - (big ? 32 : 0); t.setVelocity(0, 0); t.syncOld(); t.gravity = true; t.jumping = false;
        float lastY = t.y; String log = ""; int still = 0;
        for (int f = 0; f < 80; f++) {
          g.keys = dir == 0 ? Game.K_RIGHT : Game.K_LEFT; g.now += ms;
          g.handleInput(); t.action(fr);
          float b = t.y + t.h;
          boolean onBlock = b == 256 && t.x + t.w > 640 && t.x < 672;
          log += "\n   " + f + " x=" + t.x + " y=" + t.y + " vy=" + t.getVY();
          if (b >= 448) break;
          boolean up = t.y < lastY - 0.01f;
          still = !onBlock && t.y <= lastY + 0.01f ? still + 1 : 0;
          if (f > 0 && !onBlock && (up || still >= 2)) {
            bad++;
            if (bad <= 6) System.out.println((up ? "LIFTED: " : "stall: ") + "dir " + dir + " gap " + gap + " y0 " + y0 + " frame " + f + log);
            break;
          }
          lastY = t.y;
        }
      }
    System.out.println((big ? "big" : "small") + " " + ms + " ms: " + bad + " stalls in " + runs + " falls");
    System.exit(0);
  }
}
