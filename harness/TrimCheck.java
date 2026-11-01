// Letting go of the pictures behind the screen (Game.trimBehind) must not change a single frame:
// scrolls through levels once without and once with trimming, compares every frame and prints how
// much the pictures took at the start, the most, and at the end each way.
// usage: java TrimCheck <level ids...>
import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;

public class TrimCheck {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    Menu.noEffects = true; g.detail = Game.MAXPOTATO; g.ramSaver = true;
    int bad = 0;
    for (int k = 0; k < a.length; k++) {
      java.util.Vector crcs = new java.util.Vector();
      String report = a[k];
      for (int pass = 0; pass < 2; pass++) {
        g.rnd.setSeed(3); BadGuy.rnd.setSeed(3);
        g.worldmap = null; g.levelId = a[k]; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
        g.setLayout(false); g.loadLevel(false); g.startPlay(); g.settle();
        Menu.setCurrent(null, 0);
        BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
        Graphics r = new Graphics(im.createGraphics());
        int max = g.level.width * 32 - g.viewW, most = 0, start = Res.pageKB(), diffs = 0;
        for (int s = 0, f = 0; s <= max; s += 24, f++) {
          g.scrollX = s; g.frameCounter = f; g.now = f * 40;
          g.tux.x = s + 200; g.tux.y = 200; g.tux.syncOld();
          if (pass == 1 && System.getProperty("notrim") == null) g.trimBehind();
          g.draw(r);
          java.util.zip.CRC32 c = new java.util.zip.CRC32();
          for (int y = 20; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) { int p = im.getRGB(x, y); c.update(p); c.update(p >> 8); c.update(p >> 16); }   // (not the HUD: its timer runs on the clock)
          if (pass == 0) { crcs.addElement(new Long(c.getValue())); if (System.getProperty("dump") != null && f == Integer.getInteger("dump").intValue()) javax.imageio.ImageIO.write(im, "png", new java.io.File("p0.png")); }
          else if (((Long) crcs.elementAt(f)).longValue() != c.getValue()) {
            if (diffs == 0 && System.getProperty("dump") == null) System.out.println("first difference at frame " + f);
            if (System.getProperty("dump") != null && f == Integer.getInteger("dump").intValue()) javax.imageio.ImageIO.write(im, "png", new java.io.File("p1.png"));
            diffs++;
          }
          most = Math.max(most, Res.pageKB());
        }
        report += (pass == 0 ? "  plain: " : "  trimmed: ") + "start " + start + " KB, most " + most + ", end " + Res.pageKB()
            + (pass == 1 ? ", frames differing " + diffs + (g.level.backScrolling ? " (scrolls back: not trimmed)" : "") : "");
        if (pass == 1 && diffs > 0) bad++;
      }
      System.out.println(report);
    }
    System.out.println(bad + " levels with differences");
    System.exit(0);
  }
}
