// Max Potato's Animations toggle does something: with it on, a level's picture changes between ticks
// (animated tiles) and a bumped brick bounces; with it off neither. usage: java AnimOn <outdir>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class AnimOn {
  static BufferedImage shot(Game g) {
    BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
    g.draw(new Graphics(im.createGraphics())); return im;
  }
  static int diff(BufferedImage a, BufferedImage b) {
    int n = 0;
    for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) n++;
    return n;
  }
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    Game g = new Game(new SuperTux()); Res.load();
    Menu.noEffects = true; Res.setHardSprites(true);
    for (int pass = 0; pass < 4; pass++) {
      boolean on = pass % 2 == 1;
      g.detail = Game.MAXPOTATO; g.anims = on; g.ramSaver = pass >= 2;
      g.worldmap = null; g.levelId = "a1"; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
      g.setLayout(false); g.loadLevel(false); g.startPlay();
      g.badguys.removeAllElements(); g.items.removeAllElements();
      g.tux.x = -500; g.tux.y = -500; g.tux.syncOld();
      g.scrollX = 0; g.frameCounter = 0; g.now = 0;
      BufferedImage f0 = shot(g);
      g.frameCounter = 13; BufferedImage f1 = shot(g);
      // bump the first brick on screen
      int bx = -1, by = -1;
      for (int x = 0; x < 20 && bx < 0; x++) for (int y = 0; y < 15; y++)
        if ((Res.tFlags[g.level.tileAt(x, y)] & Res.BRICK) != 0) { bx = x; by = y; break; }
      g.frameCounter = 0;
      BufferedImage b0 = shot(g);
      if (bx >= 0) { g.bumpBrick(bx * 32, by * 32); for (int i = 0; i < Game.NE; i++) if (g.bbOn[i]) g.bbOff[i] = -6; }
      BufferedImage b1 = shot(g);
      System.out.println("anims " + on + (g.ramSaver ? " ram saver" : " cached") + ": tiles animate " + diff(f0, f1) + " px, brick at " + bx + "," + by + " bounces " + diff(b0, b1) + " px");
      if (pass == 0) { ImageIO.write(b0, "png", new File(out, "off_b0.png")); ImageIO.write(b1, "png", new File(out, "off_b1.png")); }
      if (on && pass == 1) { ImageIO.write(f0, "png", new File(out, "f0.png")); ImageIO.write(b1, "png", new File(out, "b1.png")); }
    }
    System.exit(0);
  }
}
