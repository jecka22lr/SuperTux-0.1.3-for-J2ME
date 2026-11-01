// Walks Tux through a level (moved along a little each frame, kept safe) and prints, every so often,
// how many bad guys the game still holds, how many have been seen, how many are dying, and the items
// and bullets: what grows as a level goes on. usage: java Progress <level ids...>
public class Progress {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    for (int k = 0; k < a.length; k++) {
      g.rnd.setSeed(7); BadGuy.rnd.setSeed(7);
      g.worldmap = null; g.levelId = a[k]; g.demo = false; g.level = null; g.testMode = false; g.lives = 99; g.tux.dying = 0;
      g.setLayout(false); g.loadLevel(false); g.startPlay();
      g.dbgInvincible = true; g.dbgFreeze = true;
      int end = g.level.width * 32 - 400;
      long t0 = System.nanoTime();
      for (int f = 0; g.scrollX < end && f < 20000; f++) {
        g.scrollX += 6; g.tux.x = g.scrollX + 200; g.tux.y = 64; g.tux.syncOld(); g.tux.dying = 0;
        g.now += 40;
        g.action(2f);
        g.collisions();
        if (f % 100 == 0) {
          int seen = 0, dying = 0, ahead = 0, behind = 0;
          for (int i = 0; i < g.badguys.size(); i++) {
            BadGuy b = (BadGuy) g.badguys.elementAt(i);
            if (b.seen) seen++;
            if (b.dying != BadGuy.DYING_NOT) dying++;
            if (b.x < g.scrollX) behind++;
          }
          long t = System.nanoTime();
          System.out.println(a[k] + " x " + (int) g.scrollX + "/" + end + "  bad " + g.badguys.size() + " seen " + seen + " dying " + dying
              + " behind " + behind + " items " + g.items.size() + " bullets " + g.bullets.size() + "  " + (t - t0) / 100000 / 10.0 + " ms/100 frames");
          t0 = t;
        }
      }
    }
    System.exit(0);
  }
}
