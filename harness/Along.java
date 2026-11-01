// What a frame costs along a level: walks the screen through it (no physics), and every 20 columns
// prints the drawRegion calls and pixels a frame draws, the bad guys the game holds, how many of them
// act (seen, not asleep), and the collision pairs it checks. usage: java Along <level id>
import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;

public class Along {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    Menu.noEffects = true; g.detail = Game.MAXPOTATO; g.ramSaver = true;
    g.worldmap = null; g.levelId = a[0]; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
    g.setLayout(false); g.loadLevel(false); g.startPlay(); g.settle();
    BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
    Graphics r = new Graphics(im.createGraphics());
    int max = g.level.width * 32 - g.viewW;
    for (int s = 0; s <= max; s += 32) {
      g.scrollX = s;
      for (int i = 0; i < g.badguys.size(); i++) {   // what BadGuy.action does with seen and removable
        BadGuy b = (BadGuy) g.badguys.elementAt(i);
        if (b.x < s - 256) b.removable = true; else if (b.x <= s + g.viewW + 256) b.seen = true;
      }
      for (int i = g.badguys.size() - 1; i >= 0; i--) if (((BadGuy) g.badguys.elementAt(i)).removable) g.badguys.removeElementAt(i);
      if ((s >> 5) % 20 != 0) continue;
      Graphics.nRegion = 0; Graphics.pxRegion = 0; Graphics.pxFill = 0;
      g.draw(r);
      int n = g.badguys.size(), seen = 0, acting = 0;
      for (int i = 0; i < n; i++) {
        BadGuy b = (BadGuy) g.badguys.elementAt(i);
        if (b.seen) { seen++; if (b.x <= s + g.viewW * 2) acting++; }
      }
      long pairs = 0; int k = 0;
      for (int i = 0; i < n; i++) { BadGuy b = (BadGuy) g.badguys.elementAt(i); if (b.seen) pairs += n - i - 1; }
      System.out.println("col " + (s >> 5) + ": draws " + Graphics.nRegion + " (" + Graphics.pxRegion / 1000 + "k px), bad guys " + n
          + ", seen " + seen + ", acting " + acting + ", pair checks up to " + pairs);
    }
    System.exit(0);
  }
}
