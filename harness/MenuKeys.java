// Keys bound in Keyboard Setup (here QWERTY letters) must move through menus too, while the keypad
// keeps its menu meanings; during play they give their own bits, as before. usage: java MenuKeys
public class MenuKeys {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.setLayout(false);
    int[] km = g.keymap();                       // left, right, up, down, fire
    km[0] = 'a'; km[1] = 'd'; km[2] = 'w'; km[3] = 's'; km[4] = 'k';
    Menu.setCurrent(g.mainMenu, 0); g.state = Game.TITLE;
    int bad = 0;
    int[][] want = { { 'a', Game.K_LEFT }, { 'd', Game.K_RIGHT }, { 'w', Game.K_UP }, { 's', Game.K_DOWN }, { 'k', Game.K_SELECT },
        { Game.KEY_NUM2, Game.K_UP }, { Game.KEY_NUM8, Game.K_DOWN }, { Game.KEY_NUM5, Game.K_SELECT }, { 'z', 0 } };
    for (int i = 0; i < want.length; i++) {
      int b = g.pressBits(want[i][0]) & ~Game.K_ANY;
      boolean ok = want[i][1] == 0 ? b == 0 : (b & want[i][1]) != 0 && Game.menuAction(b) == Game.menuAction(want[i][1]);
      if (!ok) bad++;
      System.out.println("menu, key " + (want[i][0] > 0 ? "'" + (char) want[i][0] + "'" : "" + want[i][0]) + ": bits " + b + (ok ? "" : "  WRONG"));
    }
    Menu.setCurrent(null, 0); g.state = Game.PLAY;
    int b = g.pressBits('w') & ~Game.K_ANY;           // (as before: a bound key's own bits during play)
    System.out.println("play, key 'w': event bits " + b + (b == Game.K_UP ? "" : "  WRONG"));
    if (b != Game.K_UP) bad++;
    System.out.println(bad + " wrong");
    System.exit(0);
  }
}
