import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;
public class ProbeMap {
  public static void main(String[] a) throws Exception {
    SuperTux m = new SuperTux(); Game g = new Game(m); Res.load();
    for (int d = 0; d < 3; d++) {
      g.detail = d; g.setLayout(false); g.loadingMap = 0; g.loadingSave = "probe"; g.openWorldMap();
      BufferedImage img = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB); Graphics gr = new Graphics(img.createGraphics());
      g.worldmap.draw(gr, g.sw, g.sh, false); Graphics.resetCounters();
      g.worldmap.draw(gr, g.sw, g.sh, false);
      System.out.println(Game.DETAIL_MODES[d] + " standing: regions=" + Graphics.nRegion + " px=" + Graphics.pxRegion + " fill=" + Graphics.pxFill);
      javax.imageio.ImageIO.write(img, "png", new java.io.File("map" + d + ".png"));
      // walk: move the camera by moving Tux a few tiles
      g.worldmap.tileY += 3; Graphics.resetCounters(); g.worldmap.draw(gr, g.sw, g.sh, false);
      System.out.println("   after moving 3 tiles: regions=" + Graphics.nRegion);
      javax.imageio.ImageIO.write(img, "png", new java.io.File("mapm" + d + ".png"));
    }
    System.exit(0);
  }
}
