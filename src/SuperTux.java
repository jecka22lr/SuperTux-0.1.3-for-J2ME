// SuperTux for J2ME (MIDP 2.0 / CLDC 1.1): a remake of SuperTux 0.1.3 for 240x320 phones.
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

public class SuperTux extends MIDlet {
  Game game;

  protected void startApp() {
    if (game == null) {
      game = new Game(this);
      Display.getDisplay(this).setCurrent(game);
      game.start();
    } else game.systemPause();   // back from a call: stay paused
  }

  protected void pauseApp() {
    if (game != null) game.systemPause();
  }

  protected void destroyApp(boolean unconditional) {
    if (game != null) game.stop();
  }

  void quit() {
    destroyApp(true);
    notifyDestroyed();
  }
}
