// The first-start detail picker: on a fresh install it comes up before the sheets load, moves with
// the keys and taps, starts the game on the picked detail and saves it, and never shows again.
// usage: java PickTest <outdir>   
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;

public class PickTest {
  static void shot(Game g, File out, String n) throws Exception {
    BufferedImage im = new BufferedImage(Displayable.W, Displayable.H, BufferedImage.TYPE_INT_RGB);
    g.paintAll(new Graphics(im.createGraphics()));
    ImageIO.write(im, "png", new File(out, n + ".png"));
  }
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    int bad = 0;
    for (int land = 0; land < 2; land++) {
      Displayable.W = land == 1 ? 320 : 240; Displayable.H = land == 1 ? 240 : 320;
      javax.microedition.rms.RecordStore.deleteRecordStore("stux");   // a fresh install
      Res.tileImg = null; Res.tPage = null;
      Game g = new Game(new SuperTux());
      if (!g.firstRun) { System.out.println("fresh install not seen as first run"); bad++; }
      javax.microedition.lcdui.Image f = javax.microedition.lcdui.Image.createImage("/f.png");
      Res.fontImg = f; g.pickSuggest(); g.state = Game.PICK;
      System.out.println((land == 1 ? "landscape" : "portrait") + ": test " + g.pickMs + " ms, suggested " + Game.DETAIL_MODES[g.pickSuggested]);
      if (Res.tileImg != null || Res.tPage != null) { System.out.println("sheets loaded before picking"); bad++; }
      shot(g, out, (land == 1 ? "L" : "P") + "_pick");
      g.pickSel = 0;
      g.keyPressed(Game.KEY_NUM8); g.keyReleased(Game.KEY_NUM8); g.frame(20);
      g.keyPressed(-2); g.keyReleased(-2); g.frame(20);              // the joystick down too
      if (g.pickSel != 2) { System.out.println("down twice: on " + g.pickSel); bad++; }
      shot(g, out, (land == 1 ? "L" : "P") + "_pick2");
      if (land == 0) {                                                // portrait: by touch, tap Low twice
        int y = g.pickRowY(1) + 10;
        g.pointerPressed(100, y); g.pointerReleased(100, y); g.frame(20);
        if (g.pickSel != 1 || g.state != Game.PICK) { System.out.println("tap once: " + g.pickSel + " state " + g.state); bad++; }
        g.pointerPressed(100, y); g.pointerReleased(100, y); g.frame(20);
      } else { g.keyPressed(Game.KEY_NUM5); g.keyReleased(Game.KEY_NUM5); g.frame(20); }
      int want = land == 0 ? Game.LOW : Game.POTATO;
      System.out.println("picked " + Game.DETAIL_MODES[g.detail] + ", state " + g.state + (g.detail == want && g.state == Game.NOTICE ? "" : "  WRONG"));
      if (g.detail != want || g.state != Game.NOTICE) bad++;
      Game g2 = new Game(new SuperTux());
      System.out.println("next start: first run " + g2.firstRun + ", detail " + Game.DETAIL_MODES[g2.detail]);
      if (g2.firstRun || g2.detail != want) bad++;
    }
    System.out.println(bad + " wrong");
    System.exit(0);
  }
}
