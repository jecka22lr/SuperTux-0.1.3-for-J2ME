// Like BotHash, but the screen is walked through the whole level (Tux carried along out of reach
// above it), so every crowd of bad guys gets woken and collides. Prints a checksum of every bad guy
// every 25 frames, to diff two builds. usage: java CrowdHash <level ids...>
public class CrowdHash {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    for (int k = 0; k < a.length; k++) {
      g.rnd.setSeed(5); BadGuy.rnd.setSeed(5);
      g.worldmap = null; g.levelId = a[k]; g.demo = false; g.level = null; g.testMode = false; g.lives = 99; g.tux.dying = 0;
      g.setLayout(false); g.loadLevel(false); g.startPlay();
      int max = g.level.width * 32 - g.viewW, collided = 0;
      long h = 0;
      for (int f = 0, s = 0; s < max + 2000; f++, s += 3) {
        float sx = Math.min(s, max);
        g.tux.x = sx + 160; g.tux.y = -400; g.tux.syncOld(); g.tux.dying = 0; g.tux.setVelocity(0, 0);
        g.now += 40;
        g.action(2f);
        g.scrollX = sx;
        if (f % 25 == 24) {
          for (int i = 0; i < g.badguys.size(); i++) {
            BadGuy b = (BadGuy) g.badguys.elementAt(i);
            h = h * 31 + Float.floatToIntBits(b.x) + 3 * Float.floatToIntBits(b.y) + 5 * b.dying + 11 * b.kind + 13 * b.dir;
          }
          System.out.println(a[k] + " " + f + " bad " + g.badguys.size() + " " + Long.toHexString(h));
        }
      }
    }
    System.exit(0);
  }
}
