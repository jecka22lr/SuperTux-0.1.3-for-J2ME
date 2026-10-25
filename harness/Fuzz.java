// Plays every level with random keys, without the game thread, and reports exceptions.
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.microedition.lcdui.Graphics;

public class Fuzz {
  public static void main(String[] a) throws Exception {
    SuperTux m = new SuperTux();
    Game g = new Game(m);
    g.worldmap = null;
    Res.load();
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    Random r = new Random(1);
    BufferedImage img = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
    Graphics gr = new Graphics(img.createGraphics());
    int errors = 0;
    String[] sets = { "a", "b", "c" }; int[] size = { 26, 22, 28 };
    for (int set = 0; set < 3; set++)
      for (int lv = 1; lv <= size[set]; lv++) {
        g.levelId = sets[set] + lv; g.demo = false; g.lives = 4; g.tux.dying = 0; g.level = null;
        g.setLayout((lv & 1) == 0);
        g.loadLevel(false);
        int frames = 0, deaths = 0; float maxX = 0;
        try {
          for (; frames < 4000; frames++) {
            if (frames % 20 == 0) {
              int k = r.nextInt(8);
              g.keys = (k < 5 ? Game.K_RIGHT : 0) | (r.nextInt(3) == 0 ? Game.K_UP : 0) | (r.nextInt(4) == 0 ? Game.K_FIRE : 0)
                  | (k == 6 ? Game.K_LEFT : 0) | (k == 7 ? Game.K_DOWN : 0);
            }
            if (frames % 300 == 0) { g.tux.grow(); g.tux.gotCoffee = true; }
            g.now += 30; g.frameCounter = (int) (g.now / 25);
            g.handleInput();
            g.checkEnd();
            if (g.state != Game.PLAY || g.level == null) { if (g.state == Game.INTRO) { g.startPlay(); continue; } break; }
            if (g.endSequence == 1) g.action(1.5f); else if (g.endSequence == 0) g.action(3f);
            if (g.tux.dying != 0) deaths++;
            if (g.tux.x > maxX) maxX = g.tux.x;
            if (frames % 50 == 0) g.draw(gr);
            if (g.lives < 1) g.lives = 4;
          }
        } catch (Throwable e) {
          errors++;
          System.out.println("set " + set + " level " + lv + " frame " + frames + ": " + e);
          StackTraceElement[] st = e.getStackTrace();
          for (int i = 0; i < Math.min(6, st.length); i++) System.out.println("    at " + st[i]);
        }
        System.out.println("set " + set + " lv " + lv + " '" + g.level.name + "' bad=" + g.badguys.size() + " maxX=" + (int) maxX + "/" + g.level.width * 32 + " frames=" + frames);
      }
    System.out.println("errors " + errors);
    System.exit(0);
  }
}
