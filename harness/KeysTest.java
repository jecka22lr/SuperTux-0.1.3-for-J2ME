// Portrait and Landscape key maps are separate, survive a restart, and old saves move over.
public class KeysTest {
  static int fails;
  static void check(boolean ok, String what) { System.out.println((ok ? "ok   " : "FAIL ") + what); if (!ok) fails++; }
  static void bind(Game g, int action, int code) { g.keysMenu.setCode(Game.M_KEYS + action, code); g.keysMenu.hit = -1; g.keysChanged(); }
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.setLayout(false);
    check(g.keysMenu.text[0].equals("Keys: Portrait"), "title " + g.keysMenu.text[0]);
    bind(g, 2, Game.KEY_NUM1);                       // portrait: jump on 1
    check(g.keyBits(Game.KEY_NUM1) == Game.K_UP, "portrait: 1 jumps");
    g.setLayout(true);
    check(g.keysMenu.text[0].startsWith("Keys: Landscape"), "title " + g.keysMenu.text[0]);
    check(g.keyBits(Game.KEY_NUM1) != Game.K_UP, "landscape: 1 doesn't jump (portrait's key)");
    int[] d = g.keyDefaults();
    for (int i = 0; i < 5; i++) check(g.keymapLand[i] == d[i], "landscape default " + i);
    bind(g, 0, Game.KEY_NUM3);                       // landscape: left on 3
    check(g.keyBits(Game.KEY_NUM3) == Game.K_LEFT, "landscape: 3 walks left");
    g.setLayout(false);
    check(g.keyBits(Game.KEY_NUM4) == Game.K_LEFT, "portrait: still 4 for left");
    check(g.keyBits(Game.KEY_NUM1) == Game.K_UP, "portrait: still 1 for jump");
    // restart
    Game g2 = new Game(new SuperTux());
    g2.setLayout(false);
    check(g2.keyBits(Game.KEY_NUM1) == Game.K_UP, "after restart: portrait 1 jumps");
    g2.setLayout(true);
    check(g2.keyBits(Game.KEY_NUM3) == Game.K_LEFT, "after restart: landscape 3 walks left");
    // Default Keys in landscape resets only landscape
    g2.keysMenu.active = g2.keysMenu.find(Game.M_KEYS + 9); g2.keysMenu.action(Menu.A_HIT, 0); g2.keysChanged();
    check(g2.keyBits(Game.KEY_NUM3) != Game.K_LEFT && !g2.landKeysSet, "landscape Default Keys");
    g2.setLayout(false);
    check(g2.keyBits(Game.KEY_NUM1) == Game.K_UP, "portrait untouched by landscape Default Keys");
    // a number key bound to an action does only that (no direction from the key states), upright
    // and sideways
    Game g3 = new Game(new SuperTux());
    javax.microedition.lcdui.Displayable.W = 240; javax.microedition.lcdui.Displayable.H = 320;
    g3.setLayout(true);
    check(g3.rotate, "sideways on an upright screen");
    bind(g3, 1, Game.KEY_NUM6);                       // Right on 6
    g3.keyPressed(Game.KEY_NUM6);
    check(g3.currentKeys() == Game.K_RIGHT, "sideways: 6 bound to Right gives only Right (" + g3.currentKeys() + ")");
    g3.keyReleased(Game.KEY_NUM6);
    check(g3.currentKeys() == 0, "released");
    g3.keyPressed(-4); check(g3.currentKeys() != 0, "joystick still works sideways"); g3.keyReleased(-4);
    check(g3.currentKeys() == 0, "joystick released");
    // a rotation sensor: the screen turns wide
    javax.microedition.lcdui.Displayable.W = 320; javax.microedition.lcdui.Displayable.H = 240;
    g3.sizeChanged(320, 240); if (g3.layoutDirty) { g3.layoutDirty = false; g3.setLayout(g3.wantLandscape); }
    check(g3.landscape && !g3.rotate && g3.sw == 320, "phone turned: landscape, not drawn sideways");
    g3.optionsMenu.setToggled(Game.M_LANDSCAPE, true); g3.optionsMenu.active = g3.optionsMenu.find(Game.M_LANDSCAPE); g3.optionsMenu.action(Menu.A_HIT, 0); g3.optionsChanged();
    check(!g3.wantLandscape && g3.landscape && g3.sw == 320, "Landscape off while held sideways: stays landscape, no black bars");
    javax.microedition.lcdui.Displayable.W = 240; javax.microedition.lcdui.Displayable.H = 320;
    g3.sizeChanged(240, 320); if (g3.layoutDirty) { g3.layoutDirty = false; g3.setLayout(g3.wantLandscape); }
    check(!g3.landscape && g3.sw == 240, "turned back upright: portrait");
    g3.keyPressed(Game.KEY_NUM6); check(g3.currentKeys() == Game.K_RIGHT, "portrait: 6 is Right"); g3.keyReleased(Game.KEY_NUM6);
    bind(g3, 2, Game.KEY_NUM6);                       // portrait: Jump on 6
    g3.keyPressed(Game.KEY_NUM6); check(g3.currentKeys() == Game.K_UP, "portrait: 6 bound to Jump only jumps (" + g3.currentKeys() + ")"); g3.keyReleased(Game.KEY_NUM6);
    System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
    System.exit(0);
  }
}
