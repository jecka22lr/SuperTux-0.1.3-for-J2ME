// Checks Debug Mode: the key combo and the touch taps open it from both pause menus, and every
// cheat runs without errors (Invincible lifts Tux out of a pit, Warp To End reaches the end).
public class DebugTest {
  static void check(boolean ok, String what) { System.out.println((ok ? "ok   " : "FAIL ") + what); if (!ok) fails++; }
  static int fails;
  public static void main(String[] a) throws Exception {
    SuperTux m = new SuperTux(); Game g = new Game(m); Res.load();
    int[] seq = Game.DBG_COMBO;
    // level
    g.worldmap = null; g.levelId = "a3"; g.demo = false; g.lives = 4; g.level = null; g.loadLevel(false); g.startPlay();
    Menu.setCurrent(g.gameMenu, 0);
    for (int i = 0; i < seq.length; i++) g.frameStates(30, seq[i] | Game.K_ANY);
    check(Menu.current == g.debugMenu, "level: combo opens Debug Mode");
    Menu.pop(0); check(Menu.current == g.gameMenu, "Back returns to the pause menu");
    // wrong combo
    Menu.setCurrent(g.gameMenu, 0);
    int[] bad = { Game.K_UP, Game.K_DOWN, Game.K_UP, Game.K_DOWN, Game.K_LEFT, Game.K_RIGHT, Game.K_LEFT, Game.K_RIGHT };
    for (int i = 0; i < bad.length; i++) g.frameStates(30, bad[i] | Game.K_ANY);
    check(Menu.current == g.gameMenu, "a wrong sequence does nothing");
    // extra Up first still works
    Menu.setCurrent(g.gameMenu, 0); g.dbgStep = 0;
    g.frameStates(30, Game.K_UP | Game.K_ANY);
    for (int i = 0; i < seq.length; i++) g.frameStates(30, seq[i] | Game.K_ANY);
    check(Menu.current == g.debugMenu, "Up Up Up Down Down... also works");
    // touch taps on the title
    Menu.setCurrent(g.gameMenu, 0);
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(g.sw, g.sh, 1);
    g.draw(new javax.microedition.lcdui.Graphics(img.createGraphics()));
    int tx = g.gameMenu.boxX + g.gameMenu.boxW / 2, ty = g.gameMenu.itemY[0];
    for (int i = 0; i < 6; i++) g.touch(Game.T_PRESS, tx, ty);
    check(Menu.current == g.gameMenu, "6 taps: not yet");
    g.touch(Game.T_PRESS, tx, ty);
    check(Menu.current == g.debugMenu, "7 taps on the Pause title open Debug Mode");
    // cheats
    Player t = g.tux;
    g.debugMenu.active = g.debugMenu.find(Game.M_DBG_TUX);
    g.frameStates(30, Game.K_LEFT | Game.K_ANY); check(t.size == Player.BIG && t.gotCoffee, "Tux < : Fire");
    g.frameStates(30, Game.K_RIGHT | Game.K_ANY); check(t.size == Player.SMALL && !t.gotCoffee, "Tux > : Small");
    g.frameStates(30, Game.K_RIGHT | Game.K_ANY); check(t.size == Player.BIG && !t.gotCoffee, "Tux > : Big");
    check(Menu.current == g.debugMenu, "the picker keeps the menu open");
    g.debugAction(Game.M_DBG_LIVES); check(g.lives == 99, "99 Lives");
    g.debugMenu.setToggled(Game.M_DBG_INV, true); g.debugAction(Game.M_DBG_INV);
    g.debugMenu.setToggled(Game.M_DBG_TIME, true); g.debugAction(Game.M_DBG_TIME);
    Menu.setCurrent(null, 0);
    long left = g.timeEnd - g.now;
    for (int i = 0; i < 100; i++) { g.keys = Game.K_RIGHT; g.frameStates(30, 0); }
    check(Math.abs((g.timeEnd - g.now) - left) <= 1000, "Freeze time");
    t.y = 700; g.frameStates(30, 0);
    check(t.y < 640 && t.dying == Player.DYING_NOT, "Invincible: out of the pit");
    int n = 0; for (int i = 0; i < g.badguys.size(); i++) if (((BadGuy) g.badguys.elementAt(i)).dying == BadGuy.DYING_NOT) n++;
    g.debugAction(Game.M_DBG_KILL);
    int n2 = 0; for (int i = 0; i < g.badguys.size(); i++) if (((BadGuy) g.badguys.elementAt(i)).dying == BadGuy.DYING_NOT) n2++;
    check(n2 <= n && (n == 0 || n2 < n), "Defeat Enemies " + n + " -> " + n2);
    g.debugAction(Game.M_DBG_END);
    check(t.x > (g.level.width - 14) * 32, "Warp To End x=" + (int) t.x + "/" + g.level.width * 32);
    for (int i = 0; i < 400 && g.state == Game.PLAY; i++) { g.keys = Game.K_RIGHT; g.frameStates(30, 0); }
    check(g.state != Game.PLAY || g.endSequence != 0, "walks into the end");
    // world map
    g.enterTitle();
    check(!g.dbgInvincible && !g.dbgFreeze, "cheats reset at the title");
    g.loadingMap = 0; g.loadingSave = "dbgtest"; g.openWorldMap();
    Menu.setCurrent(g.worldmapMenu, 0);
    for (int i = 0; i < seq.length; i++) g.frameStates(30, seq[i] | Game.K_ANY);
    check(Menu.current == g.debugMapMenu, "world map: combo opens Debug Mode");
    g.debugAction(Game.M_DBG_SOLVEALL);
    int s = 0, c = 0; for (int i = 0; i < g.worldmap.count; i++) if (g.worldmap.lid[i].length() > 0) { c++; if (g.worldmap.solved[i]) s++; }
    check(s == c, "Solve All Levels " + s + "/" + c);
    Menu.setCurrent(null, 0);
    java.awt.image.BufferedImage mi = new java.awt.image.BufferedImage(g.sw, g.sh, 1);
    g.draw(new javax.microedition.lcdui.Graphics(mi.createGraphics()));
    javax.imageio.ImageIO.write(mi, "png", new java.io.File(a.length > 0 ? a[0] : "/tmp/dbgmap.png"));
    g.debugMapMenu.setToggled(Game.M_DBG_WALK, true); g.debugAction(Game.M_DBG_WALK);
    check(g.dbgWalk, "Walk past levels");
    g.debugAction(Game.M_DBG_LIVES); check(g.lives == 99, "map 99 Lives");
    // Finish Level from a level reached through the map
    System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
    System.exit(0);
  }
}
