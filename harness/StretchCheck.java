// Options > Stretch on big screens: draws a level and the title on 480x800, 800x480 and 360x640 screens
// in each Stretch mode, upright and sideways, saves the pictures, and checks that a tap lands where
// it was aimed: a tap on the screen spot where logical point (x, y) is shown must come back as (x, y)
// give or take the scaling. usage: java StretchCheck <outdir>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;

public class StretchCheck {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    int[][] screens = { { 480, 800 }, { 800, 480 }, { 360, 640 } };
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    java.lang.reflect.Field pq = Game.class.getDeclaredField("pq"), tail = Game.class.getDeclaredField("pqTail");
    java.lang.reflect.Field head = Game.class.getDeclaredField("pqHead");
    pq.setAccessible(true); tail.setAccessible(true); head.setAccessible(true);
    int bad = 0;
    for (int s = 0; s < screens.length; s++) {
      Displayable.W = screens[s][0]; Displayable.H = screens[s][1];
      for (int land = 0; land < 2; land++)
        for (int mode = 0; mode < 3; mode++) {
          g.stretch = mode; g.scaled = null; g.showTouch = true;
          g.setLayout(land == 1);
          g.worldmap = null; g.levelId = "a1"; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
          g.loadLevel(false); g.startPlay();
          BufferedImage im = new BufferedImage(Displayable.W, Displayable.H, BufferedImage.TYPE_INT_RGB);
          g.paintAll(new Graphics(im.createGraphics()));
          String name = Displayable.W + "x" + Displayable.H + (land == 1 ? "_land_" : "_port_") + Game.STRETCH_MODES[mode];
          ImageIO.write(im, "png", new File(out, name + ".png"));
          // taps: where would logical (lx, ly) be on the screen? find it by scanning, then tap there
          int[][] pts = { { 10, 10 }, { g.sw - 11, g.sh - 11 }, { g.sw / 2, g.sh / 3 } };
          for (int p = 0; p < pts.length && mode != 0; p++) {
            int lx = pts[p][0], ly = pts[p][1], sx = -1, sy = -1;
            int iw = g.rotate ? g.sh : g.sw, ih = g.rotate ? g.sw : g.sh;
            int px = g.rotate ? 239 - ly : lx, py = g.rotate ? lx : ly;         // where it is on the picture as shown
            sx = g.scDX + (px * g.scDW + g.scDW / 2) / iw; sy = g.scDY + (py * g.scDH + g.scDH / 2) / ih;
            head.set(g, new Integer(0)); tail.set(g, new Integer(0));
            g.pointerPressed(sx, sy);
            int[] q = (int[]) pq.get(g); int t = ((Integer) tail.get(g)).intValue();
            int last = (t - 3 + q.length) % q.length;
            int gx = q[last + 1], gy = q[last + 2];
            if (Math.abs(gx - lx) > 1 || Math.abs(gy - ly) > 1) { bad++; System.out.println(name + ": tap for " + lx + "," + ly + " came back " + gx + "," + gy); }
          }
          System.out.println(name + ": picture " + g.scDW + "x" + g.scDH + " at " + g.scDX + "," + g.scDY);
        }
    }
    System.out.println(bad + " taps off target");
    System.exit(0);
  }
}
