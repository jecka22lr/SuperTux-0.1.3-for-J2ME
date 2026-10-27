// Renders every screen of the game in portrait and landscape, touch on and off, for checking text
// overlaps and other layout problems by eye. usage: java UiShots <outdir>
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;

public class UiShots {
  static Game g; static File out; static String tag;

  static void shot(String name) throws Exception {
    if (Menu.current != null) Menu.current.effectEnd = 0;
    g.draw(new Graphics(new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB).createGraphics()));   // twice: the second uses any cache     // the slide-in finished
    BufferedImage img = new BufferedImage(g.sw, g.sh, BufferedImage.TYPE_INT_RGB);
    Graphics gr = new Graphics(img.createGraphics());
    g.draw(gr);
    ImageIO.write(img, "png", new File(out, tag + "_" + name + ".png"));
  }

  static void title() { g.helpOpen = false; g.state = Game.TITLE; g.levelId = "m1"; g.demo = true; g.level = null; g.testMode = false; g.loadLevel(false); g.state = Game.TITLE; }

  public static void main(String[] a) throws Exception {
    out = new File(a[0]); out.mkdirs();
    SuperTux m = new SuperTux(); g = new Game(m); Res.load();
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    Menu.noEffects = true;
    int[][] combos = { { 0, 1, 0 }, { 1, 1, 0 }, { 0, 0, 0 }, { 1, 0, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 0, 1, 2 }, { 1, 1, 2 }, { 0, 1, 3 }, { 1, 1, 3 } };   // landscape, touch, detail
    for (int c = 0; c < combos.length; c++) {
      boolean land = combos[c][0] == 1; g.showTouch = combos[c][1] == 1; g.detail = combos[c][2];
      Menu.noEffects = g.detail >= Game.POTATO; Res.setHardSprites(g.detail == Game.MAXPOTATO);
      g.buildMainMenu();
      g.setLayout(land);
      tag = (land ? "L" : "P") + (g.showTouch ? "t" : "k") + g.detail;
      title();
      Menu.setCurrent(g.mainMenu, 0); shot("01title");
      Menu.setCurrent(g.optionsMenu, 0); shot("02options");
      Menu.push(g.keysMenu, 0); shot("03keys");
      Menu.setCurrent(null, 0); g.helpOpen = true;
      int[][] hs = g.helpScreens();
      for (g.helpPage = 0; g.helpPage < hs.length; g.helpPage++) shot("04help" + g.helpPage);
      g.helpOpen = false;
      g.state = Game.CONTROLS; shot("05controls");
      g.state = Game.ABOUT; shot("06about");
      g.state = Game.AUDIOINFO; g.audioInfo = null; shot("07audioinfo");
      title();
      Menu.setCurrent(g.adventureMenu, 0); shot("08adventure");
      g.curStart = g.startTitle.length - 1; g.updateSlots(); Menu.setCurrent(g.loadGameMenu, 0); shot("09slots");
      g.generateContrib(); Menu.setCurrent(g.contribMenu, 0); shot("10bonus");
      // the pack with the longest level names
      for (int p = 0; p < g.contribSubsets.length && p < 2; p++) {
        String sub = g.contribSubsets[p];
        g.contribSubsetMenu.clear();
        g.contribSubsetMenu.add(Menu.LABEL, Editor.subsetTitle(sub), 0).add(Menu.HL, "", 0);
        int pack = Editor.indexOf(Editor.PACK_DIRS, sub);
        if (pack >= 0 && Editor.PACK_STORY[pack] != null) g.contribSubsetMenu.add(Menu.ACTION, "The Story", Game.M_STORY);
        for (int i = 1; i <= Editor.subsetLevels(sub); i++)
          g.contribSubsetMenu.add(Menu.ACTION, Game.fitName(Editor.levelName(Editor.levelId(sub, i)), g.sw / 8 - 1), i);
        g.contribSubsetMenu.add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
        g.contribSubsetMenu.active = g.contribSubsetMenu.count - 3;
        Menu.setCurrent(g.contribSubsetMenu, 0); shot("11pack" + p);
      }
      // confirm delete
      g.confirmMenu.clear();
      g.confirmMenu.add(Menu.DEACTIVE, land ? "Are you sure you want to delete slot 3?" : "Delete slot 3?", 0)
          .add(Menu.HL, "", 0).add(Menu.ACTION, "Yes", Game.M_YES).add(Menu.ACTION, "No", Game.M_NO).add(Menu.HL, "", 0);
      Menu.setCurrent(g.confirmMenu, 0); shot("12confirm");
      Menu.setCurrent(null, 0);
      // world maps: Tux on the level with the longest title
      int[] maps = { 0, 3, 7 };
      for (int mi = 0; mi < maps.length; mi++) {
        try {
          g.loadingMap = maps[mi]; g.loadingSave = "ui" + maps[mi]; g.openWorldMap();
        } catch (Throwable e) { continue; }
        WorldMap w = g.worldmap;
        int best = 0;
        for (int i = 0; i < w.count; i++) if (w.ltitle[i].length() > w.ltitle[best].length()) best = i;
        w.tileX = w.lx[best]; w.tileY = w.ly[best];
        Menu.setCurrent(null, 0); shot("13map" + maps[mi]);
        if (mi == 0) { Menu.setCurrent(g.worldmapMenu, 0); shot("14mappause"); g.openDebug(); shot("14mapdebug"); Menu.setCurrent(null, 0); }
      }
      // level: the longest level name in the jar for the intro, then play screens
      String[] longest = { "f2", "a1", "i24" };
      for (int k = 0; k < longest.length; k++) {
        g.worldmap = null; g.levelId = longest[k]; g.demo = false; g.level = null; g.tux.dying = 0;
        try { g.loadLevel(true); } catch (Throwable e) { continue; }
        if (g.level == null) continue;
        g.lives = 4; g.state = Game.INTRO; shot("15intro" + k);
      }
      g.levelId = "a1"; g.level = null; g.loadLevel(false);
      g.score = 1234567; g.distros = 99; g.lives = 99; g.showFps = true; g.fpsText = "33.0 max120 sfx450";
      shot("16play");
      Menu.setCurrent(g.gameMenu, 0); shot("17pause"); Menu.setCurrent(null, 0);
      g.timeEnd = g.now - 1; shot("18timesup");
      g.timeEnd = g.now + 100000;
      g.testMode = true; shot("19testplay"); g.testMode = false;
      g.showFps = false;
      g.state = Game.PLAY; Menu.setCurrent(g.gameMenu, 0); g.openDebug(); shot("21debug"); Menu.setCurrent(null, 0);
      g.state = Game.GAMEOVER; shot("20gameover");
      g.showText("/x/credits", Pic.BG_TITLE, 2.4f, Game.AFTER_TITLE); g.textScroll = 600; shot("22credits");
      g.showText("/x/intro.txt", Pic.BG_ARCTIS2, 1.0f, Game.AFTER_TITLE); g.textScroll = 200; shot("23intro");
      if (Editor.PACK_STORY.length > 0 && Editor.PACK_STORY[0] != null) { g.showText(Editor.PACK_STORY[0], Pic.BG_OILTUX, 1.0f, Game.AFTER_TITLE); g.textScroll = 200; shot("24story"); }
      // editor
      title();
      g.openEditor(); shot("25editormenu");
      g.editor.loadSubset("world1"); Menu.setCurrent(null, 0); shot("26editor");
      g.editor.help = true; shot("27editorhelp"); g.editor.help = false;
      g.editor.pressButton(Editor.B_SETTINGS); shot("28editorsettings");
      Menu.setCurrent(null, 0);
      g.state = Game.TITLE;
    }
    // her report: in a level, Options, Detail Full -> Max Potato, then close and reopen Options
    for (int land = 0; land < 2; land++) {
      g.showTouch = true; g.detail = Game.FULL; Menu.noEffects = false; Res.setHardSprites(false);
      g.setLayout(land == 1); tag = (land == 1 ? "L" : "P") + "switch";
      g.worldmap = null; g.levelId = "a1"; g.demo = false; g.level = null; g.loadLevel(false); g.startPlay();
      Menu.setCurrent(g.gameMenu, 0); Menu.push(g.optionsMenu, 0);
      g.optionsMenu.setList(Game.M_DETAIL, Game.DETAIL_MODES, Game.FULL); shot("1full");
      for (int d = 1; d <= 3; d++) {
        g.optionsMenu.setList(Game.M_DETAIL, Game.DETAIL_MODES, d); g.optionsChanged(); shot("2detail" + d);
        g.optionsMenu.active = (g.optionsMenu.active + 1) % g.optionsMenu.count; shot("3moved" + d);
      }
      Menu.pop(0); shot("4pause"); Menu.push(g.optionsMenu, 0); shot("5reopen");
      g.optionsMenu.setList(Game.M_DETAIL, Game.DETAIL_MODES, 0); g.optionsChanged(); shot("6backfull");
      Menu.setCurrent(null, 0);
    }
    System.exit(0);
  }
}
