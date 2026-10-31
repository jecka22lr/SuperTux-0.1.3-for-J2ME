// Game over in a world map adventure, then the title: on a Max Potato jar (Animations off) Tux must
// stand still there. usage: java OverTitle <map> <level id>
public class OverTitle {
  public static void main(String[] a) throws Exception {
    Game g = new Game(new SuperTux()); Res.load();
    g.coinTile = -1;
    for (int i = 0; i < Res.tFlags.length; i++) if ((Res.tFlags[i] & Res.DISTRO) != 0) { g.coinTile = i; break; }
    System.out.println("detail " + g.detail + " anims " + g.anims + " maxpotato-only " + Game.MAXPOTATO_ONLY);
    g.loadingMap = Integer.parseInt(a[0]); g.loadingSave = "ot"; g.openWorldMap();
    g.startLevel(a[1]); g.startPlay();
    g.lives = -1; g.tux.kill(Player.KILL_KILL);
    for (int i = 0; i < 400 && g.state != Game.TITLE; i++) g.frame(40);
    System.out.println("state " + g.state + " (title " + Game.TITLE + "), level " + g.levelId + ", dying " + g.tux.dying);
    float x0 = g.tux.x, y0 = g.tux.y;
    for (int i = 0; i < 100; i++) g.frame(40);
    System.out.println("tux moved " + (g.tux.x - x0) + ", " + (g.tux.y - y0) + " in 4 s; scroll " + g.scrollX);
    System.exit(0);
  }
}
