// Like CacheCheck, but Tux runs through the level (bumping bricks, taking coins) in both games with
// the same keys, so the cache's partial redraws get compared too.
// usage: java CacheRun <outdir> <level ids | packs:abc>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class CacheRun {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    SuperTux m = new SuperTux();
    Game g1 = new Game(m), g2 = new Game(m); Res.load();
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g1.coinTile = g2.coinTile = i; break; }
    java.util.Vector ids = new java.util.Vector();
    for (int i = 1; i < a.length; i++) {
      if (a[i].startsWith("packs:")) {
        String p = a[i].substring(6);
        for (int k = 0; k < p.length(); k++)
          for (int n = 1; CacheRun.class.getResourceAsStream("/l/" + p.charAt(k) + n + ".bin") != null; n++) ids.addElement("" + p.charAt(k) + n);
      } else ids.addElement(a[i]);
    }
    int bad = 0, frames = 0;
    for (int i = 0; i < ids.size(); i++) {
      String id = (String) ids.elementAt(i);
      int detail = 1 + i % 3; boolean land = (i / 3) % 2 == 0;
      Game[] gs = { g1, g2 };
      for (int k = 0; k < 2; k++) {
        Game g = gs[k];
        g.detail = detail; g.ramSaver = k == 1; g.showTouch = true;
        Menu.noEffects = true; Res.setHardSprites(detail == Game.MAXPOTATO);
        g.worldmap = null; g.levelId = id; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
        g.setLayout(land); g.loadLevel(false); g.startPlay();
        g.badguys.removeAllElements(); g.now = 0;
      }
      BufferedImage i1 = new BufferedImage(g1.sw, g1.sh, 1), i2 = new BufferedImage(g1.sw, g1.sh, 1);
      Graphics r1 = new Graphics(i1.createGraphics()), r2 = new Graphics(i2.createGraphics());
      int worst = 0, diffs = 0;
      for (int f = 0; f < 1500; f++) {
        for (int k = 0; k < 2; k++) {
          Game g = gs[k];
          g.keys = Game.K_RIGHT | ((f / 15) % 3 == 0 ? Game.K_UP : 0) | (f % 200 < 100 ? Game.K_FIRE : 0);
          g.now += 30; g.frameCounter = (int) (g.now / 25);
          g.handleInput(); g.checkEnd();
          if (g.state == Game.INTRO) g.startPlay();
          if (g.state != Game.PLAY || g.level == null) break;
          g.action(3f); g.timeEnd = g.now + 100000; g.tux.invincibleT = g.now + 3000;
          if (g.tux.y > 520) { g.tux.y = 0; g.tux.syncOld(); }
          if (g.tux.dying != 0) { g.tux.dying = 0; g.tux.y = 0; g.tux.syncOld(); }
        }
        if (g1.state != Game.PLAY || g2.state != Game.PLAY || g1.level == null || g2.level == null) break;
        g1.draw(r1); g2.draw(r2); frames++;
        int n = 0;
        for (int y = 0; y < g1.sh; y++) for (int x = 0; x < g1.sw; x++) if (i1.getRGB(x, y) != i2.getRGB(x, y)) n++;
        if (n > 0) {
          diffs++;
          if (n > worst) {
            worst = n;
            BufferedImage both = new BufferedImage(g1.sw * 2, g1.sh, 1);
            both.createGraphics().drawImage(i1, 0, 0, null); both.createGraphics().drawImage(i2, g1.sw, 0, null);
            ImageIO.write(both, "png", new File(out, id + ".png"));
          }
        }
      }
      if (diffs > 0) { bad++; System.out.println(id + " " + Game.DETAIL_MODES[detail] + (land ? " land" : " port") + ": " + diffs + " frames differ, worst " + worst + " px"); }
    }
    System.out.println(ids.size() + " levels, " + frames + " frames, " + bad + " with differences");
    System.exit(0);
  }
}
