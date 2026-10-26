// Plays levels to the end with a simple bot (run right, jump at walls and gaps), with the cheats a
// tester would use: invincible, no time limit, lifted out of pits, and a nudge forward when stuck.
// Draws every frame (exercising the Low/Potato tile caches), cycles Full/Low/Potato and portrait/
// landscape, saves a few screenshots and prints the drawing work per frame.
// usage: java PlayPacks <outdir> <seed> <levels per pack> <pack letters>
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class PlayPacks {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    Random r = new Random(Long.parseLong(a[1]));
    int per = Integer.parseInt(a[2]);
    String packs = a[3];
    SuperTux m = new SuperTux();
    Game g = new Game(m);
    g.worldmap = null;
    Res.load();
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    int run = 0, errors = 0, done = 0, total = 0;
    long[][] work = new long[3][4];   // per detail: frames, regions, alpha px, fill px
    for (int p = 0; p < packs.length(); p++) {
      char letter = packs.charAt(p);
      int n = 0;
      while (PlayPacks.class.getResourceAsStream("/l/" + letter + (n + 1) + ".bin") != null) n++;
      int[] pick = new int[Math.min(per, n)];
      for (int i = 0; i < pick.length; i++) {
        int v; boolean dup;
        do { v = 1 + r.nextInt(n); dup = false; for (int j = 0; j < i; j++) if (pick[j] == v) dup = true; } while (dup);
        pick[i] = v;
      }
      for (int i = 0; i < pick.length; i++, run++) {
        String id = "" + letter + pick[i];
        int detail = run % 3; boolean land = (run / 3) % 2 == 1;
        g.detail = detail; Menu.noEffects = detail == Game.POTATO;
        Menu.setCurrent(null, 0); g.worldmap = null;
        g.levelId = id; g.demo = false; g.lives = 4; g.score = 0; g.distros = 0; g.tux.dying = 0; g.level = null; g.testMode = false;
        g.setLayout(land);
        if (!land) g.viewTop = g.showTouch ? 64 : 160;
        BufferedImage img = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
        Graphics gr = new Graphics(img.createGraphics());
        String name = "?", result = "stuck";
        int frames = 0, rescues = 0, nudges = 0, deaths = 0, jumpHold = 0, lastProgress = 0;
        float bestX = 0;
        boolean shotEnd = false;
        total++;
        try {
          g.loadLevel(false);
          name = g.level.name;
          int width = g.level.width * 32;
          for (; frames < 15000; frames++) {
            Player t = g.tux;
            // bot: run right; jump at a wall or a gap ahead, or when stuck
            boolean wall = g.level.solid(t.x + t.w + 12, t.y + t.h - 8) || g.level.solid(t.x + t.w + 12, t.y + 4);
            boolean gap = t.onGround() && !g.level.solid(t.x + t.w + 40, t.y + t.h + 8) && !g.level.solid(t.x + t.w + 40, t.y + t.h + 72);
            if (jumpHold > 0) jumpHold--;
            else if ((wall || gap || frames - lastProgress > 60) && t.onGround()) jumpHold = 22;
            boolean back = frames - lastProgress > 200 && (frames / 25) % 2 == 0;
            g.keys = (back ? Game.K_LEFT : Game.K_RIGHT) | Game.K_FIRE | (jumpHold > 2 ? Game.K_UP : 0);
            g.now += 30; g.frameCounter = (int) (g.now / 25);
            g.handleInput();
            g.checkEnd();
            if (g.state == Game.INTRO) { g.startPlay(); continue; }
            if (g.state != Game.PLAY || g.level == null) { if (result.equals("stuck")) result = "left level (state " + g.state + ")"; break; }
            if (g.endSequence != 0 && result.equals("stuck")) { result = "FINISHED"; done++; }
            if (g.endSequence == 1) g.action(1.5f); else if (g.endSequence == 0) g.action(3f);
            // cheats
            g.timeEnd = g.now + 200000;
            t.invincibleT = g.now + 3000;
            if (t.dying != 0) { deaths++; t.dying = 0; t.y = 0; t.setVelocity(0, 0); t.syncOld(); }
            if (t.y > 520) {                     // fell into a pit: lift onto the next ground ahead
              rescues++;
              outer:
              for (int x = (int) t.x / 32 + 1; x < g.level.width; x++)
                for (int y = 2; y < Level.H; y++)
                  if (g.level.solid(x * 32 + 16, y * 32 + 16) && !g.level.solid(x * 32 + 16, y * 32 - 48)) {
                    t.x = x * 32; t.y = y * 32 - t.h - 4; t.setVelocity(0, 0); t.syncOld(); break outer;
                  }
            }
            if (t.x > bestX + 4) { bestX = t.x; lastProgress = frames; }
            if (frames - lastProgress > 600) {   // really stuck: nudge forward
              nudges++; t.x = bestX + 96; t.y = 0; t.setVelocity(0, 0); t.syncOld(); lastProgress = frames; bestX = t.x;
            }
            Graphics.resetCounters();
            g.draw(gr);
            work[detail][0]++; work[detail][1] += Graphics.nRegion; work[detail][2] += Graphics.pxAlpha; work[detail][3] += Graphics.pxFill;
            if (frames == 150) ImageIO.write(img, "png", new File(out, id + "_a.png"));
            if (t.x > width / 2 && t.x < width / 2 + 40) ImageIO.write(img, "png", new File(out, id + "_b.png"));
            if (g.endSequence != 0 && !shotEnd) { shotEnd = true; ImageIO.write(img, "png", new File(out, id + "_c.png")); }
          }
        } catch (Throwable e) {
          errors++; result = "ERROR " + e;
          StackTraceElement[] st = e.getStackTrace();
          for (int k = 0; k < Math.min(8, st.length); k++) result += "\n      at " + st[k];
        }
        System.out.println(id + " '" + name + "' " + (land ? "land" : "port") + " " + Game.DETAIL_MODES[detail]
            + ": " + result + " frames=" + frames + " pits=" + rescues + " nudges=" + nudges + " hits=" + deaths
            + " x=" + (int) bestX + "/" + (g.level != null ? g.level.width * 32 : 0));
      }
    }
    System.out.println("finished " + done + "/" + total + ", errors " + errors);
    for (int d = 0; d < 3; d++) if (work[d][0] > 0)
      System.out.println(Game.DETAIL_MODES[d] + ": per frame " + work[d][1] / work[d][0] + " drawRegion calls, "
          + work[d][2] / work[d][0] + " alpha-blended px, " + work[d][3] / work[d][0] + " filled px");
    System.exit(0);
  }
}
