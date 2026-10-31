// A level that doesn't fit: page loads fail once the pages take a budget. The game must settle (pages on
// screen stay, no reloading the same pages every frame) instead of thrashing.
// usage: java MemSqueeze <budget KB> <level ids...>
import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class MemSqueeze {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    Menu.noEffects = true; g.detail = Game.MAXPOTATO; g.ramSaver = true;
    Image.budgetKB = Integer.parseInt(a[0]);
    for (int k = 1; k < a.length; k++) {
      g.worldmap = null; g.levelId = a[k]; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
      g.setLayout(false); g.loadLevel(false); g.startPlay(); g.settle();
      Menu.setCurrent(null, 0);
      BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
      Graphics r = new Graphics(im.createGraphics());
      int max = g.level.width * 32 - g.viewW, worstLoads = 0, steadyLoads = 0, frames = 0;
      Res.starved = 0;
      for (int s = 0; s <= max; s += 8, frames++) {
        g.scrollX = s; g.frameCounter = frames; g.now = frames * 40;
        g.tux.x = s + 200; g.tux.y = 200; g.tux.syncOld();
        int l0 = Image.loads;
        g.draw(r);
        int n = Image.loads - l0;
        if (n > worstLoads) worstLoads = n;
        steadyLoads += n;
      }
      // standing still for 50 frames: nothing should load
      int l0 = Image.loads;
      for (int f = 0; f < 50; f++) { g.frameCounter++; g.draw(r); }
      System.out.println(a[k] + ": " + frames + " frames scrolling, " + steadyLoads + " page loads (most in a frame " + worstLoads
          + "), standing still " + (Image.loads - l0) + " loads, pictures " + Res.pageKB() + " KB, missed " + Res.starved);
    }
    System.exit(0);
  }
}
