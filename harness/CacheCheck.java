// Low, Potato and Max Potato draw the level from cached images; RAM saver draws the same scenes
// directly. Scrolls two games side by side through levels (one with RAM saver) and reports any
// frame where the pictures differ. usage: java CacheCheck <outdir> <level ids | packs:abc>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class CacheCheck {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    SuperTux m = new SuperTux();
    Game g1 = new Game(m), g2 = new Game(m); Res.load();
    java.util.Vector ids = new java.util.Vector();
    for (int i = 1; i < a.length; i++) {
      if (a[i].startsWith("packs:")) {
        String p = a[i].substring(6);
        for (int k = 0; k < p.length(); k++)
          for (int n = 1; CacheCheck.class.getResourceAsStream("/l/" + p.charAt(k) + n + ".bin") != null; n++) ids.addElement("" + p.charAt(k) + n);
      } else ids.addElement(a[i]);
    }
    int bad = 0, frames = 0;
    for (int i = 0; i < ids.size(); i++) {
      String id = (String) ids.elementAt(i);
      int detail = 1 + i % 3; boolean land = (i / 3) % 2 == 0;
      Game[] gs = { g1, g2 };
      for (int k = 0; k < 2; k++) {
        Game g = gs[k];
        g.detail = detail; g.ramSaver = k == 1; g.showTouch = (i % 4) < 2;
        Menu.noEffects = true; Res.setHardSprites(detail == Game.MAXPOTATO);
        g.worldmap = null; g.levelId = id; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
        g.setLayout(land); g.loadLevel(false); g.startPlay();
        g.badguys.removeAllElements(); g.items.removeAllElements();
        g.tux.x = -500; g.tux.y = -500; g.tux.syncOld();
      }
      BufferedImage i1 = new BufferedImage(g1.sw, g1.sh, BufferedImage.TYPE_INT_RGB), i2 = new BufferedImage(g1.sw, g1.sh, BufferedImage.TYPE_INT_RGB);
      Graphics r1 = new Graphics(i1.createGraphics()), r2 = new Graphics(i2.createGraphics());
      int max = g1.level.width * 32 - g1.viewW, step = 7 + (i % 5) * 6, worst = 0, diffs = 0, worstS = 0, wx = 0, wy = 0;
      for (int sx = 0, f = 0; sx <= max + step; sx += step, f++) {
        int s = Math.min(sx, max);
        if (f % 40 == 39) s = Math.max(0, s - 300);              // now and then scroll back a bit
        g1.scrollX = g2.scrollX = s; g1.frameCounter = g2.frameCounter = f; g1.now = g2.now = f * 30;
        g1.draw(r1); g2.draw(r2);
        frames++;
        int n = 0, fx = 0, fy = 0;
        for (int y = 0; y < g1.sh; y++) for (int x = 0; x < g1.sw; x++) if (i1.getRGB(x, y) != i2.getRGB(x, y)) { n++; fx = x; fy = y; }
        if (n > 0) {
          diffs++;
          if (n > worst) {
            worst = n; worstS = s; wx = (fx * 2 + s) / 32; wy = (fy * 2 - g1.viewTop) / 32;
            BufferedImage both = new BufferedImage(g1.sw * 2, g1.sh, BufferedImage.TYPE_INT_RGB);
            both.createGraphics().drawImage(i1, 0, 0, null); both.createGraphics().drawImage(i2, g1.sw, 0, null);
            ImageIO.write(both, "png", new File(out, id + ".png"));
          }
        }
      }
      if (diffs > 0) { bad++; System.out.println(id + " " + Game.DETAIL_MODES[detail] + (land ? " land" : " port") + ": " + diffs + " frames differ, worst " + worst + " px at tile " + wx + "," + wy + " (scroll " + worstS + ")"); }
    }
    System.out.println(ids.size() + " levels, " + frames + " frames, " + bad + " levels with differences");
    System.exit(0);
  }
}
