// The end sequence walks Tux right and jumps when he's stuck. Builds a wall past the end and
// reports how high he gets. usage: java EndProbe <frameMs> <wallTiles>
public class EndProbe {
  public static void main(String[] a) throws Exception {
    int ms = Integer.parseInt(a[0]), wall = Integer.parseInt(a[1]);
    Game g = new Game(new SuperTux()); Res.load();
    g.worldmap = null; g.levelId = "a1"; g.demo = false; g.level = null; g.loadLevel(false); g.startPlay();
    Level lv = g.level; g.badguys.removeAllElements();
    int solid = 0;
    for (int y = Level.H - 1; y > 0; y--) { int tt = lv.tileAt(3, y); if ((Res.tFlags[tt] & Res.SOLID) != 0) solid = tt; else break; }
    int W = lv.width, end = W - 5;
    for (int x = end - 20; x < W; x++) for (int y = 0; y < Level.H; y++) lv.ia[y * W + x] = (byte) (y >= 13 ? solid : 0);
    for (int y = 13 - wall; y < 13; y++) lv.ia[y * W + end + 2] = (byte) solid;
    Player t = g.tux;
    t.x = (end - 3) * 32; t.y = 13 * 32 - t.h; t.setVelocity(0, 0); t.syncOld();
    float minY = t.y; int jumps = 0; boolean air = false; float x0 = 0;
    for (int f = 0; f < 7000 / ms && g.state == Game.PLAY; f++) {
      g.keys = Game.K_RIGHT; g.now += ms; g.frameCounter = (int) (g.now / 25);
      g.handleInput(); g.checkEnd();
      if (g.state != Game.PLAY) break;
      if (g.endSequence == 1) g.action(ms / 20f); else g.action(ms / 10f);
      if (t.y < minY) minY = t.y;
      boolean onG = t.onGround();
      if (!onG && !air) jumps++;
      air = !onG;
    }
    System.out.println(ms + " ms, wall " + wall + ": end x=" + (int) t.x + " (wall at " + (end + 2) * 32 + "), highest jump "
        + (int) (13 * 32 - t.h - minY) + " px, hops " + jumps + ", endSequence " + g.endSequence);
    System.exit(0);
  }
}
