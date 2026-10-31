// The level editor on the 2 MB jar (tile pages, see-through layers made a page at a time) against the
// Max Potato jar: run once with each jar's files on the classpath and diff the outputs. Every level of
// every pack, scrolled across, with each layer active in turn, each tile group open in the palette and
// a tile from it in hand. Prints a checksum a frame, and in the paged run the most page memory used.
// usage: java EdCmp <outdir> [id:frame to save]
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class EdCmp {
  public static void main(String[] a) throws Exception {
    File out = new File(a[0]); out.mkdirs();
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    Menu.noEffects = true; Res.setHardSprites(true);
    g.detail = Game.MAXPOTATO; g.ramSaver = true;
    int worst = 0; String worstAt = "";
    for (int land = 0; land < 2; land++) {
      g.setLayout(land == 1);
      g.openEditor();
      Editor e = g.editor;
      String[] subs = new String[3 + Editor.PACK_DIRS.length];
      subs[0] = "world1"; subs[1] = "bonus1"; subs[2] = "bonus2";
      System.arraycopy(Editor.PACK_DIRS, 0, subs, 3, Editor.PACK_DIRS.length);
      int k = 0;
      for (int si = 0; si < subs.length; si++) {
        e.loadSubset(subs[si]);
        for (int n = 1; n <= e.levelCount; n++, k++) {
          if ((n + land) % 2 == 1) continue;           // every other level each way round
          e.gotoLevel(n);
          Menu.setCurrent(null, 0);
          BufferedImage im = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
          Graphics r = new Graphics(im.createGraphics());
          int max = Math.max(0, e.lv.width * 32 - 400);
          for (int f = 0; f < 8; f++) {
            e.posX = (int) ((long) max * f / 7); e.posX -= e.posX % 32;
            e.activeTm = f % 3;
            e.curGroup = Editor.groupTiles.length == 0 ? -1 : (k + f) % (Editor.groupTiles.length + 1) - 1;
            e.curObjects = e.curGroup < 0 && f % 2 == 0;
            if (e.curGroup >= 0) { int[] gt = Editor.groupTiles[e.curGroup]; e.curTile = gt[f % gt.length]; e.dropPalette(); }
            e.curIsTile = !e.curObjects;
            e.cursorX = e.posX + 96 + f * 32; e.cursorY = 32 + f * 16;
            e.minimapDirty = true;
            g.frameCounter = f * 5; g.now = f * 150;
            g.draw(r);
            String id = subs[si] + "/" + n + (land == 1 ? "L" : "P");
            java.util.zip.CRC32 c = new java.util.zip.CRC32();
            for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) { int p = im.getRGB(x, y); c.update(p); c.update(p >> 8); c.update(p >> 16); }
            System.out.println(id + " " + f + " " + Long.toHexString(c.getValue()));
            if (a.length > 1 && a[1].equals(id + ":" + f)) ImageIO.write(im, "png", new File(out, id.replace('/', '_') + "_" + f + ".png"));
            if (Res.paged) {
              long px = 0;
              Image[][] ps = { Res.tPage, Res.sPage, Res.gPage };
              for (int q = 0; q < 3; q++) for (int i = 0; i < ps[q].length; i++) if (ps[q][i] != null) px += ps[q][i].getWidth() * ps[q][i].getHeight();
              if (px * 4 / 1024 > worst) { worst = (int) (px * 4 / 1024); worstAt = id + ":" + f; }
            }
          }
        }
      }
    }
    System.out.println("pages: worst " + worst + " KB (" + worstAt + ")" + (Res.paged ? "" : " (not paged)") + (Res.ghostFailed ? " GHOSTS FAILED" : ""));
    System.exit(0);
  }
}
