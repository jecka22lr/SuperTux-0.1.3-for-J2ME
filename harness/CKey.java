// C / Backspace (-8): pauses during play and on the world map, backs out of menus, still deletes a
// save slot in the slot menu, and a C bound in Keyboard Setup keeps its own meaning. usage: java CKey
public class CKey {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.setLayout(false);
    int bad = 0;
    Menu.setCurrent(null, 0); g.state = Game.PLAY;
    int b = g.pressBits(-8);
    System.out.println("play: " + b + ((b & Game.K_ESC) != 0 ? "" : "  WRONG")); if ((b & Game.K_ESC) == 0) bad++;
    b = g.pressBits(-8);
    System.out.println("slot menu keeps delete bit: " + ((b & Game.K_CLEAR) != 0 ? "yes" : "NO")); if ((b & Game.K_CLEAR) == 0) bad++;
    int[] km = g.keymap(); km[2] = -8;          // C bound to jump
    b = g.pressBits(-8) & ~Game.K_ANY;
    System.out.println("bound to jump: " + b + (b == Game.K_UP ? "" : "  WRONG")); if (b != Game.K_UP) bad++;
    System.out.println(bad + " wrong");
    System.exit(0);
  }
}
