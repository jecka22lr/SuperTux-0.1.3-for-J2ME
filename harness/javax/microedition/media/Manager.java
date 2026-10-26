package javax.microedition.media;
// Desktop test stand-in: records what would play.
public class Manager {
  public static String last;
  public static void playTone(int note, int ms, int vol) throws MediaException { }
  public static Player createPlayer(java.io.InputStream in, String type) throws MediaException {
    last = type;
    return new Player() {
      public void realize() {} public void prefetch() {} public void start() {} public void stop() {}
      public void close() {} public void setLoopCount(int n) {} public long setMediaTime(long t) { return 0; }
      public Object getControl(String n) { return null; } public int getState() { return PREFETCHED; }
    };
  }
}
