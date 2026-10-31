// The lite jar keeps its tiles in pages (all of them, or only the level's with RAM saver). Run once with
// the lite jar's files and once with the same files plus the whole tile sheet (t.png, no tp.bin): prints
// a checksum of every frame in every Detail mode with RAM saver off and on, to diff the two runs, and
// in the paged run the most tile page memory a level needed with and without RAM saver.
// usage: java LiteCmp <packs, e.g. abc>
import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class LiteCmp {
  static int tileKB() {
    long n = 0;
    if (Res.tPage != null) for (int i = 0; i < Res.tPage.length; i++) if (Res.tPage[i] != null) n += Res.tPage[i].getWidth() * Res.tPage[i].getHeight();
    if (Res.tileImg != null) n += Res.tileImg.getWidth() * Res.tileImg.getHeight();
    return (int) (n * 4 / 1024);
  }
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    int[] worst = new int[2];
    int li = 0;
    for (int k = 0; k < a[0].length(); k++)
      for (int n = 1; LiteCmp.class.getResourceAsStream("/l/" + a[0].charAt(k) + n + ".bin") != null; n++, li++) {
        String id = "" + a[0].charAt(k) + n;
        for (int mode = 0; mode < 8; mode++) {
          if ((mode + li) % 3 != 0) continue;            // a third of the combinations a level, all of them overall
          int detail = mode % 4; boolean rs = mode >= 4;
          g.detail = detail; g.ramSaver = rs; Menu.noEffects = detail >= Game.POTATO; Res.setHardSprites(detail == Game.MAXPOTATO);
          boolean land = li % 2 == 0; g.showTouch = li % 4 < 2;
          g.worldmap = null; g.levelId = id; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
          g.setLayout(land); g.loadLevel(false); g.startPlay();
          g.ptCount = 0;                                  // snow and rain are random: leave them out
          Menu.setCurrent(null, 0);
          int kb = tileKB(); if (kb > worst[rs ? 1 : 0]) worst[rs ? 1 : 0] = kb;
          BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
          Graphics r = new Graphics(im.createGraphics());
          int max = g.level.width * 32 - g.viewW;
          for (int sx = 0, f = 0; sx <= max + 90; sx += 90, f++) {
            int s = Math.min(sx, max);
            g.scrollX = s; g.frameCounter = f; g.now = f * 70;
            g.tux.x = s + 200; g.tux.y = 200; g.tux.syncOld();
            g.draw(r);
            java.util.zip.CRC32 c = new java.util.zip.CRC32();
            for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) { int p = im.getRGB(x, y); c.update(p); c.update(p >> 8); c.update(p >> 16); }
            String key = id + " " + Game.DETAIL_MODES[detail] + (rs ? " rs" : "") + " " + f;
            System.out.println(key + " " + Long.toHexString(c.getValue()));
            if (a.length > 1 && a[1].equals(key.replace(' ', '_'))) javax.imageio.ImageIO.write(im, "png", new java.io.File(key.replace(' ', '_') + ".png"));
          }
        }
      }
    System.out.println("tiles: worst " + worst[0] + " KB, with RAM saver " + worst[1] + " KB" + (Res.paged ? "" : " (whole sheet)"));
    System.exit(0);
  }
}
