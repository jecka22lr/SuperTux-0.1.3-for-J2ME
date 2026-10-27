// Draws whole levels as one long picture each (landscape view stitched across), to look for broken
// tiles and other visual faults. usage: java -Ddetail=0 LevelStrip <outdir> <level ids...|pack letters:abc>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class LevelStrip {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    Game g = new Game(new SuperTux()); Res.load();
    g.detail = Integer.getInteger("detail", 0).intValue();
    Menu.noEffects = g.detail >= Game.POTATO; Res.setHardSprites(g.detail == Game.MAXPOTATO);
    java.util.Vector ids = new java.util.Vector();
    for (int i = 1; i < a.length; i++) {
      if (a[i].startsWith("packs:")) {
        String p = a[i].substring(6);
        for (int k = 0; k < p.length(); k++)
          for (int n = 1; LevelStrip.class.getResourceAsStream("/l/" + p.charAt(k) + n + ".bin") != null; n++) ids.addElement("" + p.charAt(k) + n);
      } else ids.addElement(a[i]);
    }
    for (int i = 0; i < ids.size(); i++) {
      String id = (String) ids.elementAt(i);
      try {
        g.worldmap = null; g.levelId = id; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
        g.showTouch = false; g.setLayout(true);
        g.loadLevel(false); g.startPlay();
        Level lv = g.level;
        g.tux.x = -500; g.tux.y = -500; g.tux.syncOld();
        int W = lv.width * 16, H = 240;
        BufferedImage strip = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D sg = strip.createGraphics();
        BufferedImage img = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
        Graphics gr = new Graphics(img.createGraphics());
        for (int sx = 0; ; sx += 640) {
          int s = Math.min(sx, lv.width * 32 - g.viewW);
          g.scrollX = s; g.frameCounter = 0;
          g.draw(gr);
          sg.drawImage(img, s / 2, 0, null);
          if (s + g.viewW >= lv.width * 32) break;
        }
        ImageIO.write(strip, "png", new File(out, id + ".png"));
        System.out.println(id + " '" + lv.name + "' " + lv.width);
      } catch (Throwable e) { System.out.println(id + " ERROR " + e); e.printStackTrace(); }
    }
    System.exit(0);
  }
}
