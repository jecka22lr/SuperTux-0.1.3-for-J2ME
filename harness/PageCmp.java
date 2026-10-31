// The 2 MB Max Potato jar keeps its tile and sprite sheets in pages (tools/pages.py). Run once with the
// Max Potato jar's files on the classpath and once with the paged jar's: prints a checksum of every
// frame (levels with their enemies, every world map dot, the title menu) so the two runs can be
// diffed, and in the paged run the most page memory a level or map needed.
// usage: java PageCmp <outdir> <packs, e.g. abcot>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class PageCmp {
  static int pageKB() {
    long n = 0;
    Image[][] ps = { Res.tPage, Res.sPage };
    for (int k = 0; k < 2; k++) if (ps[k] != null) for (int i = 0; i < ps[k].length; i++)
      if (ps[k][i] != null) n += ps[k][i].getWidth() * ps[k][i].getHeight();
    return (int) (n * 4 / 1024);
  }
  static long crc(BufferedImage im) {
    java.util.zip.CRC32 c = new java.util.zip.CRC32();
    for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) { int p = im.getRGB(x, y); c.update(p); c.update(p >> 8); c.update(p >> 16); }
    return c.getValue();
  }
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    SuperTux m = new SuperTux();
    Game g = new Game(m); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    Menu.noEffects = true; Res.setHardSprites(true);
    g.detail = Game.MAXPOTATO; g.ramSaver = true;
    int worst = 0; String worstId = "";
    String packs = a[1];
    int li = 0;
    for (int k = 0; k < packs.length(); k++)
      for (int n = 1; PageCmp.class.getResourceAsStream("/l/" + packs.charAt(k) + n + ".bin") != null; n++, li++) {
        String id = "" + packs.charAt(k) + n;
        boolean land = li % 2 == 0;
        g.showTouch = li % 4 < 2;
        g.worldmap = null; g.levelId = id; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
        g.setLayout(land); g.loadLevel(false); g.startPlay();
        Menu.setCurrent(null, 0);
        int kb = pageKB(); if (kb > worst) { worst = kb; worstId = id; }
        BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
        Graphics r = new Graphics(im.createGraphics());
        int max = g.level.width * 32 - g.viewW;
        for (int sx = 0, f = 0; sx <= max + 40; sx += 40, f++) {
          int s = Math.min(sx, max);
          g.scrollX = s; g.frameCounter = f; g.now = f * 70;
          g.tux.x = s + 200; g.tux.y = 200; g.tux.syncOld();
          g.draw(r);
          long c = crc(im);
          System.out.println(id + " " + f + " " + Long.toHexString(c));
          if (a.length > 2 && a[2].equals(id + ":" + f)) ImageIO.write(im, "png", new File(out, id + "_" + f + ".png"));
        }
      }
    for (int map = 0; map < 12; map++) {
      try {
        g.worldmap = null; g.state = Game.TITLE;
        g.setLayout(map % 2 == 0); g.loadingMap = map; g.loadingSave = "pc"; g.openWorldMap(); Menu.setCurrent(null, 0);
      } catch (Throwable e) { continue; }
      if (g.worldmap == null || g.state != Game.WORLDMAP) continue;   // (a jar without this map)
      WorldMap w = g.worldmap;
      BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
      Graphics r = new Graphics(im.createGraphics());
      for (int l = 0; l < w.count; l++) {
        w.tileX = w.lx[l]; w.tileY = w.ly[l]; w.stop();
        g.frameCounter = l; g.now = l * 100;
        g.draw(r);
        int kb = pageKB(); if (kb > worst) { worst = kb; worstId = "map" + map; }
        System.out.println("map" + map + " " + l + " " + Long.toHexString(crc(im)));
        if (a.length > 2 && a[2].equals("map" + map + ":" + l)) ImageIO.write(im, "png", new File(out, "map" + map + "_" + l + ".png"));
      }
    }
    System.out.println("pages: worst " + worst + " KB (" + worstId + ")" + (Res.paged ? "" : " (not paged)"));
    System.exit(0);
  }
}
