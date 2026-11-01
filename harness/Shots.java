// Screenshots for a release page: Full detail, Tux standing on the ground a little into each level,
// a few moments of play so enemies are moving, saved at 2x. Also the title and world maps.
// usage: java Shots <outdir> <land|port> <items: title | map:N | levelid[:column]>...
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class Shots {
  static void save(Game g, File out, String name) throws Exception {
    BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
    g.draw(new Graphics(im.createGraphics()));
    BufferedImage big = new BufferedImage(g.sw * 2, g.sh * 2, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D b = big.createGraphics();
    b.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    b.drawImage(im, 0, 0, g.sw * 2, g.sh * 2, null);
    ImageIO.write(big, "png", new File(out, name + ".png"));
  }
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    g.detail = Game.FULL; g.ramSaver = false; g.showTouch = false; Menu.noEffects = false;
    Res.setHardSprites(false);
    boolean land = a[1].equals("land");
    g.setLayout(land);
    String tag = land ? "L" : "P";
    for (int k = 2; k < a.length; k++) {
      String it = a[k];
      g.rnd.setSeed(k); BadGuy.rnd.setSeed(k);
      if (it.equals("title")) {
        g.enterTitle();
        for (int f = 0; f < 120; f++) g.frame(30);
        Menu.current.effectEnd = 0;
        save(g, out, tag + "_title");
      } else if (it.startsWith("map:")) {
        g.loadingMap = Integer.parseInt(it.substring(4)); g.loadingSave = "shots" + it.substring(4); g.openWorldMap();
        Menu.setCurrent(null, 0);
        for (int f = 0; f < 40; f++) g.frame(30);
        save(g, out, tag + "_map" + it.substring(4));
      } else {
        String id = it; int col = 0;
        if (it.indexOf(':') > 0) { id = it.substring(0, it.indexOf(':')); col = Integer.parseInt(it.substring(it.indexOf(':') + 1)); }
        g.worldmap = null; g.levelId = id; g.demo = false; g.level = null; g.testMode = false; g.lives = 4; g.tux.dying = 0;
        g.loadLevel(false); g.startPlay();
        Menu.setCurrent(null, 0);
        if (col > 0) { g.dbgPlace(col); g.scrollX = Math.max(0, g.tux.x - g.viewW * 2 / 5); g.clampScroll(); }
        g.dbgInvincible = true;
        for (int f = 0; f < 50; f++) { g.keys = 0; g.frame(30); if (g.state != Game.PLAY) break; }
        g.dbgInvincible = false;
        save(g, out, tag + "_" + id + (col > 0 ? "_" + col : ""));
      }
    }
    System.exit(0);
  }
}
