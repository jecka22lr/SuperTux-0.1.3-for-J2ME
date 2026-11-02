// Which detail a fresh install starts on (the Low jars carry x/low). usage: java LowStart
public class LowStart {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux());
    System.out.println("starts on " + Game.DETAIL_MODES[g.detail]);
    System.exit(0);
  }
}
