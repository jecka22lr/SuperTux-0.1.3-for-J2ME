// Plays levels with fixed input and fixed random numbers and prints a checksum of Tux and every bad
// guy every 25 frames, so two builds can be diffed to show they play exactly the same.
// usage: java BotHash <frames> <level ids...>
public class BotHash {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    int frames = Integer.parseInt(a[0]);
    for (int k = 1; k < a.length; k++) {
      g.rnd.setSeed(7); BadGuy.rnd.setSeed(7);
      g.worldmap = null; g.levelId = a[k]; g.demo = false; g.level = null; g.testMode = false; g.lives = 99; g.tux.dying = 0;
      g.setLayout(false); g.loadLevel(false); g.startPlay();
      long h = 0;
      for (int f = 0; f < frames; f++) {
        g.keys = Game.K_RIGHT | ((f / 15) % 3 == 0 ? Game.K_UP : 0) | (f % 40 < 3 ? Game.K_FIRE : 0);
        g.frame(40);
        if (g.state != Game.PLAY) { g.state = Game.PLAY; g.tux.dying = 0; }
        if (f % 25 == 24) {
          h = h * 31 + Float.floatToIntBits(g.tux.x) * 7 + Float.floatToIntBits(g.tux.y);
          for (int i = 0; i < g.badguys.size(); i++) {
            BadGuy b = (BadGuy) g.badguys.elementAt(i);
            h = h * 31 + Float.floatToIntBits(b.x) + 3 * Float.floatToIntBits(b.y) + 5 * b.dying + 11 * b.kind;
          }
          System.out.println(a[k] + " " + f + " bad " + g.badguys.size() + " " + Long.toHexString(h));
        }
      }
    }
    System.exit(0);
  }
}
