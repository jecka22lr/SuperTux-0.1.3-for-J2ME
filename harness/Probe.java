// Drawing work of one frame, part by part, for each detail level and layout.
import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;
public class Probe {
  static String c() { String s = "regions=" + Graphics.nRegion + " regionPx=" + Graphics.pxRegion + " alphaPx=" + Graphics.pxAlpha + " fillPx=" + Graphics.pxFill + " images=" + Graphics.nImage + " imagePx=" + Graphics.pxImage; Graphics.resetCounters(); return s; }
  public static void main(String[] a) throws Exception {
    SuperTux m = new SuperTux(); Game g = new Game(m); g.worldmap = null; Res.load();
    for (int land = 0; land < 2; land++) for (int d = 0; d < 3; d++) {
      g.detail = d; g.levelId = a[0]; g.demo = false; g.level = null; g.tux.dying = 0; g.setLayout(land == 1);
      if (land == 0) g.viewTop = g.showTouch ? 64 : 160;
      g.loadLevel(false);
      BufferedImage img = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
      Graphics gr = new Graphics(img.createGraphics());
      for (int f = 0; f < 200; f++) { g.keys = Game.K_RIGHT; g.now += 30; g.frameCounter = (int) (g.now / 25); g.handleInput(); g.action(3f); g.draw(gr); }
      g.keys = Game.K_RIGHT; g.now += 30; g.handleInput(); g.action(3f);
      Graphics.resetCounters();
      System.out.println((land == 1 ? "land " : "port ") + Game.DETAIL_MODES[d] + " showTouch=" + g.showTouch);
      g.drawWorld(gr); System.out.println("  world  " + c());
      g.status(gr); System.out.println("  hud    " + c()); g.status(gr); System.out.println("  hud2   " + c());
      if (g.showTouch) { g.drawTouch(gr); g.drawMenuCorner(gr); System.out.println("  touch  " + c()); }
      Menu.setCurrent(g.gameMenu, 0); Menu.noEffects = true; g.gameMenu.draw(gr, g.sw / 2, g.sh, land == 0, 0); System.out.println("  pause menu " + c()); Menu.setCurrent(null, 0);
    }
    System.exit(0);
  }
}
