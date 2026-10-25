package javax.microedition.lcdui;
import javax.microedition.midlet.MIDlet;
public class Display {
  static Display d = new Display();
  public static Displayable current;
  public static Display getDisplay(MIDlet m) { return d; }
  public void setCurrent(Displayable x) { current = x; }
  public Displayable getCurrent() { return current; }
}
