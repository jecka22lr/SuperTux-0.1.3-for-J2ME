// Toggle run, the Audio menu volumes, and the editor pointer reaching the button bar.
public class FeatTest {
  static int fails;
  static void check(boolean ok, String what) { System.out.println((ok ? "ok   " : "FAIL ") + what); if (!ok) fails++; }
  static void frame(Game g, int keys) {
    g.keys = keys; g.now += 30; g.frameCounter = (int) (g.now / 25);
    g.handleInput(); g.action(3f);
  }
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.setLayout(false);
    g.worldmap = null; g.levelId = "a1"; g.demo = false; g.level = null; g.loadLevel(false); g.startPlay();
    g.badguys.removeAllElements();
    // hold run (default)
    frame(g, Game.K_FIRE); check(g.tux.inFire, "hold run: running while held");
    frame(g, 0); check(!g.tux.inFire, "hold run: stops on release");
    // toggle run
    g.toggleRun = true;
    frame(g, Game.K_FIRE); check(g.tux.inFire, "toggle: press turns running on");
    frame(g, Game.K_FIRE); check(g.tux.inFire, "toggle: still on while held");
    frame(g, 0); check(g.tux.inFire, "toggle: stays on after release");
    frame(g, Game.K_FIRE); frame(g, 0); check(!g.tux.inFire, "toggle: second press turns it off");
    g.tux.grow(); g.tux.gotCoffee = true;
    int b0 = g.bullets.size();
    frame(g, Game.K_FIRE); frame(g, 0);
    frame(g, 0); frame(g, 0);
    int b1 = g.bullets.size();
    frame(g, Game.K_FIRE); frame(g, 0);
    int b2 = g.bullets.size();
    check(b1 == b0 + 1 && b2 == b1 + 1, "toggle: a fireball per press, on and off (" + b0 + " " + b1 + " " + b2 + ")");
    frame(g, Game.K_FIRE | Game.K_RIGHT); frame(g, Game.K_RIGHT); frame(g, Game.K_RIGHT);
    check(g.tux.inFire, "toggle: running while walking without holding run");
    g.loadLevel(false);
    check(!g.runLatched, "toggle: off again on a new try");
    // audio menu
    check(g.optionsMenu.find(Game.M_SOUND) < 0 && g.audioMenu.find(Game.M_SOUND) >= 0, "Sound moved to Options > Audio");
    g.audioMenu.setList(Game.M_SFXVOL, Sound.VOLUMES, 1); g.optionsChanged();
    check(g.sfxVol == 1 && g.sounds.volume == 50, "SFX volume 50%");
    g.audioMenu.setList(Game.M_MUSVOL, Sound.VOLUMES, 0); g.optionsChanged();
    check(g.musicVol == 0 && g.music.volume == 25, "Music volume 25%");
    Game g2 = new Game(new SuperTux());
    check(g2.sfxVol == 1 && g2.musicVol == 0 && g2.sounds.volume == 50 && g2.music.volume == 25, "volumes kept after a restart");
    g.toggleRun = true; g.saveSettings();
    Game g3 = new Game(new SuperTux());
    check(g3.toggleRun, "toggle run kept after a restart");
    // editor: the pointer reaches the bar without scrolling the level
    g.openEditor(); g.editor.loadSubset("world1"); Menu.setCurrent(null, 0);
    Editor e = g.editor;
    e.vmX = 20; e.vmY = 100; e.posX = 0;
    for (int i = 0; i < 30; i++) e.step(Game.K_RIGHT);
    check(e.posX == 0 && e.vmX >= e.px(), "editor: pointer goes into the button bar, level not scrolled (posX " + e.posX + ", x " + e.vmX + ")");
    System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
    System.exit(0);
  }
}
