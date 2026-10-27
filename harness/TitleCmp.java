// The title demo in one detail mode, drawn from the caches and (second game) with RAM saver, with
// the same random numbers; reports frames that differ. usage: java -Ddetail=2 TitleCmp <outdir> [secs] [land]
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
public class TitleCmp {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    SuperTux m = new SuperTux();
    Game g1 = new Game(m), g2 = new Game(m); Res.load();
    int detail = Integer.getInteger("detail", 2).intValue();
    Game[] gs = { g1, g2 };
    java.lang.reflect.Field rf = Game.class.getDeclaredField("rnd"); rf.setAccessible(true);
    java.lang.reflect.Field mf = java.lang.reflect.Field.class.getDeclaredField("modifiers"); mf.setAccessible(true);
    mf.setInt(rf, rf.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
    for (int k = 0; k < 2; k++) {
      Game g = gs[k];
      for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
      rf.set(g, new java.util.Random(7));
      g.detail = detail; g.ramSaver = k == 1; g.showTouch = true;
      Menu.noEffects = detail >= Game.POTATO; Res.setHardSprites(detail == Game.MAXPOTATO);
      g.buildMainMenu(); g.setLayout(a.length > 2); g.enterTitle(); g.now = 0;
    }
    Menu.setCurrent(null, 0);
    int secs = a.length > 1 ? Integer.parseInt(a[1]) : 60;
    BufferedImage i1 = new BufferedImage(g1.sw, g1.sh, 1), i2 = new BufferedImage(g1.sw, g1.sh, 1);
    Graphics r1 = new Graphics(i1.createGraphics()), r2 = new Graphics(i2.createGraphics());
    int diffs = 0, worst = 0;
    for (int f = 0; f < secs * 33; f++) {
      for (int k = 0; k < 2; k++) { gs[k].now += 30; gs[k].frameCounter = (int) (gs[k].now / 25); gs[k].demoFrame(30); }
      g1.draw(r1); g2.draw(r2);
      int n = 0;
      for (int y = 0; y < g1.sh; y++) for (int x = 0; x < g1.sw; x++) if (i1.getRGB(x, y) != i2.getRGB(x, y)) n++;
      if (n > 0) {
        diffs++;
        if (n > worst) {
          worst = n;
          BufferedImage both = new BufferedImage(g1.sw * 2, g1.sh, 1);
          both.createGraphics().drawImage(i1, 0, 0, null); both.createGraphics().drawImage(i2, g1.sw, 0, null);
          ImageIO.write(both, "png", new File(out, "worst.png"));
          System.out.println("frame " + f + ": " + n + " px, scroll " + g1.scrollX + " / " + g2.scrollX + ", tux " + g1.tux.x + " / " + g2.tux.x);
        }
      }
    }
    System.out.println(Game.DETAIL_MODES[detail] + ": " + diffs + " of " + secs * 33 + " frames differ");
    System.exit(0);
  }
}
