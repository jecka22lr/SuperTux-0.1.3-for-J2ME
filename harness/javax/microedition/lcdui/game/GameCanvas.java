package javax.microedition.lcdui.game;
import javax.microedition.lcdui.*;
import java.awt.image.BufferedImage;
public abstract class GameCanvas extends Canvas {
  public static final int UP_PRESSED = 2, DOWN_PRESSED = 64, LEFT_PRESSED = 4, RIGHT_PRESSED = 32, FIRE_PRESSED = 256;
  public static volatile int keyStates;                  // set by the test driver
  public static BufferedImage screen = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
  public static volatile BufferedImage lastFrame;
  public static volatile int frames;
  Graphics g;
  protected GameCanvas(boolean suppress) { g = new Graphics(screen.createGraphics()); }
  protected Graphics getGraphics() { return g; }
  public int getKeyStates() { return keyStates; }
  public void flushGraphics() {
    BufferedImage c = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    c.getGraphics().drawImage(screen, 0, 0, null);
    lastFrame = c; frames++;
  }
}
