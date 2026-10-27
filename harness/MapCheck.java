// The world map is drawn from a cached picture except with RAM saver: put Tux on every level dot of
// every map in both games and compare. usage: java MapCheck <outdir>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class MapCheck {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    SuperTux m = new SuperTux();
    Game g1 = new Game(m), g2 = new Game(m); Res.load();
    int bad = 0, total = 0;
    for (int map = 0; map < 12; map++)
      for (int mode = 0; mode < 8; mode++) {
        int detail = mode % 4; boolean land = mode >= 4;
        Game[] gs = { g1, g2 };
        try {
          for (int k = 0; k < 2; k++) {
            Game g = gs[k];
            g.detail = detail; g.ramSaver = k == 1; g.showTouch = true;
            Menu.noEffects = detail >= Game.POTATO; Res.setHardSprites(detail == Game.MAXPOTATO);
            g.setLayout(land);
            g.loadingMap = map; g.loadingSave = "mc" + k; g.openWorldMap();
            Menu.setCurrent(null, 0);
          }
        } catch (Throwable e) { if (mode == 0) System.out.println("map " + map + ": " + e); break; }
        WorldMap w1 = g1.worldmap, w2 = g2.worldmap;
        BufferedImage i1 = new BufferedImage(g1.sw, g1.sh, 1), i2 = new BufferedImage(g1.sw, g1.sh, 1);
        Graphics r1 = new Graphics(i1.createGraphics()), r2 = new Graphics(i2.createGraphics());
        int diffs = 0, worst = 0;
        for (int l = 0; l < w1.count; l++) {
          for (int k = 0; k < 2; k++) { WorldMap w = k == 0 ? w1 : w2; w.tileX = w1.lx[l]; w.tileY = w1.ly[l]; w.stop(); }
          g1.frameCounter = g2.frameCounter = l; g1.now = g2.now = l * 100;
          g1.draw(r1); g2.draw(r2); total++;
          int n = 0;
          for (int y = 0; y < g1.sh; y++) for (int x = 0; x < g1.sw; x++) if (i1.getRGB(x, y) != i2.getRGB(x, y)) n++;
          if (n > 0) {
            diffs++;
            if (n > worst) {
              worst = n;
              BufferedImage both = new BufferedImage(g1.sw * 2, g1.sh, 1);
              both.createGraphics().drawImage(i1, 0, 0, null); both.createGraphics().drawImage(i2, g1.sw, 0, null);
              ImageIO.write(both, "png", new File(out, "map" + map + "_" + mode + ".png"));
            }
          }
        }
        if (diffs > 0) { bad++; System.out.println("map " + map + " " + Game.DETAIL_MODES[detail] + (land ? " land" : " port") + ": " + diffs + "/" + w1.count + " differ, worst " + worst + " px"); }
      }
    System.out.println(total + " views, " + bad + " map/mode pairs with differences");
    System.exit(0);
  }
}
