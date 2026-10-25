package javax.microedition.midlet;
// Desktop test stand-in for MIDP's MIDlet.
public abstract class MIDlet {
  public static boolean destroyed;
  protected abstract void startApp() throws MIDletStateChangeException;
  protected abstract void pauseApp();
  protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;
  public final void notifyDestroyed() { destroyed = true; }
  public final String getAppProperty(String k) { return null; }
  public void start() throws Exception { startApp(); }
}
