// The Options > Stretch row must show up once the canvas turns out big, even if it was small when the
// menus were built (and go away again on a small screen). usage: java StretchRow
import javax.microedition.lcdui.Displayable;

public class StretchRow {
  public static void main(String[] a) throws Exception {
    Displayable.W = 240; Displayable.H = 320;
    Game g = new Game(new SuperTux()); Res.load();
    System.out.println("built at 240x320: row " + (g.optionsMenu.find(Game.M_STRETCH) >= 0));
    Displayable.W = 480; Displayable.H = 800; g.stretchRow();
    System.out.println("then 480x800: row " + (g.optionsMenu.find(Game.M_STRETCH) >= 0) + " at " + g.optionsMenu.find(Game.M_STRETCH)
        + " after Toggle run at " + g.optionsMenu.find(Game.M_TOGGLERUN));
    Displayable.W = 320; Displayable.H = 240; g.stretchRow();
    System.out.println("then 320x240: row " + (g.optionsMenu.find(Game.M_STRETCH) >= 0));
    Displayable.W = 640; Displayable.H = 360; g.stretchRow();
    System.out.println("then 640x360: row " + (g.optionsMenu.find(Game.M_STRETCH) >= 0));
    System.exit(0);
  }
}
