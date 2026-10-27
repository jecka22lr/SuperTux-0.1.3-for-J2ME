// Draws every tile's first frame on magenta, 16 to a row, in compact index order, for comparing
// with the PC images (tools: cmp_tiles.py). usage: java TileCheck out.png
import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;
public class TileCheck {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    int n = Res.tFlags.length, rows = (n + 15) / 16;
    BufferedImage img = new BufferedImage(16 * 20, rows * 20, BufferedImage.TYPE_INT_RGB);
    Graphics gr = new Graphics(img.createGraphics());
    gr.setColor(0xff00ff); gr.fillRect(0, 0, 16 * 20, rows * 20);
    for (int i = 1; i < n; i++) { gr.setClip((i % 16) * 20, (i / 16) * 20, 20, 20); Res.drawTile(gr, i, (i % 16) * 20, (i / 16) * 20, 0); }
    javax.imageio.ImageIO.write(img, "png", new java.io.File(a[0]));
    System.out.println(n + " tiles");
    System.exit(0);
  }
}
