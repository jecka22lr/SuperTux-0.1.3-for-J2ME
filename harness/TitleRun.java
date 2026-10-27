// Runs the title screen's demo for a while in a detail mode and saves a frame every second.
// usage: java -Ddetail=1 TitleRun <outdir> [seconds] [land]
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
public class TitleRun {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    Game g = new Game(new SuperTux()); Res.load();
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    g.detail = Integer.getInteger("detail", 1).intValue(); Menu.noEffects = g.detail >= Game.POTATO; Res.setHardSprites(g.detail == Game.MAXPOTATO);
    g.showTouch = true; g.buildMainMenu(); g.setLayout(a.length > 2);
    g.enterTitle(); Menu.setCurrent(g.mainMenu, 0);
    int secs = a.length > 1 ? Integer.parseInt(a[1]) : 20;
    BufferedImage img = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
    Graphics gr = new Graphics(img.createGraphics());
    for (int f = 0; f < secs * 33; f++) {
      g.frameStates(30, 0);
      g.draw(gr);
      if (f % 33 == 0) ImageIO.write(img, "png", new File(out, "t" + (f / 33 < 10 ? "0" : "") + f / 33 + ".png"));
    }
    System.exit(0);
  }
}
