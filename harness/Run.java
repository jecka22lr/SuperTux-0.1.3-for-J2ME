// Test driver: runs the MIDlet on the desktop stand-in and saves screenshots.
// usage: java Run <outdir> <map> <level|title> "<ms>:<cmd> ..."
//   cmd: K<bits> | P<code> | R<code> | S<name> | X<x> | L<0|1> | B<n> | U<data> | D (kill Tux) | F (finish) | Q
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.game.GameCanvas;

public class Run {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    SuperTux m = new SuperTux();
    m.start();
    Game g = m.game;
    while (g.level == null && g.state != 4) Thread.sleep(20);
    if (g.state != 4 && !a[2].equals("title")) {
      synchronized (g) {
        g.loadingMap = Integer.parseInt(a[1]); g.loadingSave = "test" + a[1];
        g.openWorldMap();
        WorldMap w = g.worldmap;
        for (int i = 0; i < w.count; i++) if (w.lid[i].equals(a[2])) { w.tileX = w.lx[i]; w.tileY = w.ly[i]; w.entered = i; }
        if (!a[2].equals("map")) g.startLevel(a[2]);
      }
    }
    long t0 = System.currentTimeMillis();
    String[] cmds = a.length > 3 ? a[3].trim().split("\\s+") : new String[0];
    for (int i = 0; i < cmds.length; i++) {
      String c = cmds[i];
      int colon = c.indexOf(':');
      long at = Long.parseLong(c.substring(0, colon));
      String op = c.substring(colon + 1);
      while (System.currentTimeMillis() - t0 < at) Thread.sleep(2);
      char k = op.charAt(0); String v = op.substring(1);
      if (k == 'K') GameCanvas.keyStates = Integer.parseInt(v);
      else if (k == 'P') g.keyPressed(Integer.parseInt(v));
      else if (k == 'R') g.keyReleased(Integer.parseInt(v));
      else if (k == 'S') {
        ImageIO.write(GameCanvas.lastFrame, "png", new File(out, v + ".png"));
        System.out.println(v + " bad=" + g.badguys.size() + " items=" + g.items.size() + " lives=" + g.lives + " score=" + g.score + " bonus=" + g.bonus + " level=" + (g.level == null ? "-" : g.level.name) + " x=" + g.tux.x + " y=" + g.tux.y + " vx=" + g.tux.vx + " scroll=" + g.scrollX + " state=" + g.state + " frames=" + GameCanvas.frames + " msg=" + g.message + (g.editor != null ? " vm=" + g.editor.vmX + "," + g.editor.vmY + " pos=" + g.editor.posX + " menu=" + (Menu.current != null) + " tile=" + g.editor.curTile + " group=" + g.editor.curGroup + " tm=" + g.editor.activeTm : ""));
      } else if (k == 'X') { g.tux.x = Float.parseFloat(v); g.tux.oldX = g.tux.x; g.tux.y = 300; g.tux.oldY = 300; }
      else if (k == 'L') g.setLayout(v.equals("1"));
      else if (k == 'B') {   // drop Tux onto bad guy number v
        BadGuy b = (BadGuy) g.badguys.elementAt(Integer.parseInt(v));
        g.tux.x = b.x; g.tux.y = b.y - 100; g.tux.syncOld(); g.scrollX = b.x - 200; g.clampScroll();
        System.out.println("drop on kind " + b.kind + " at " + b.x + "," + b.y);
      }
      else if (k == 'U') {   // bump the first bonus box holding data v from below
        Level l = g.level; int want = Integer.parseInt(v);
        outer:
        for (int x = 0; x < l.width; x++) for (int y = 0; y < Level.H; y++) {
          int t = l.tileAt(x, y);
          if ((Res.tFlags[t] & Res.FULLBOX) != 0 && Res.tData[t] == want) {
            g.tux.x = x * 32; g.tux.y = (y + 1) * 32 + 4; g.tux.syncOld(); g.tux.setVY(4); g.tux.gravity = true;
            g.scrollX = x * 32 - 200; g.clampScroll();
            System.out.println("box data " + want + " at " + x + "," + y);
            break outer;
          }
        }
      }
      else if (k == 'D') g.tux.kill(Player.KILL_KILL);
      else if (k == 'V') g.lives = Integer.parseInt(v);
      else if (k == 'T') g.showFps = true;
      else if (k == 'J') System.out.println("bits 4=" + g.keyBits(52) + " 1=" + g.keyBits(49) + " 6=" + g.keyBits(54) + " 2=" + g.keyBits(50) + " star=" + g.keyBits(42) + " keys=" + java.util.Arrays.toString(g.keymapUp));
      else if (k == 'Y') { g.openEditor(); g.editor.loadSubset("world1"); }
      else if (k == 'Z') g.editor.pressButton(Editor.B_TEST);
      else if (k == 'G') { g.editor.curGroup = Integer.parseInt(v); g.editor.curObjects = false; g.editor.curTile = Editor.groupTiles[g.editor.curGroup][2]; }
      else if (k == 'O') { g.editor.curObjects = true; g.editor.curGroup = -1; }
      else if (k == 'C') g.score = Integer.parseInt(v);
      else if (k == 'W') { String[] q = v.split(","); g.worldmap.tileX = Integer.parseInt(q[0]); g.worldmap.tileY = Integer.parseInt(q[1]); }
      else if (k == 'E') g.keyPressed(-6);
      else if (k == 'F') g.beginEnd(2);
      else if (k == 'Q') break;
    }
    g.stop();
    System.exit(0);
  }
}
