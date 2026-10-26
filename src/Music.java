// Music: 0.1.3's songs as MP3 recordings of the original tracker files (full build) or as
// MIDI conversions (lite build, or phones that can't play MP3). Played with MMAPI.
import java.io.InputStream;
import java.util.Hashtable;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

final class Music {
  static final int NONE = 0, LEVEL = 1, HURRYUP = 2, HERRING = 3, DONE = 4, TITLE = 5, FILE = 6;
  boolean enabled = true;
  int type = NONE;          // what is (or should be) playing, like World::get_music_type()
  int song = -1;            // level song number
  private Player player;
  private String current;
  private Hashtable alias;
  private boolean mp3Failed;
  boolean midiOnly;         // Options > Music > MIDI: cheaper on slow phones than decoding MP3

  void setMidiOnly(boolean on) { if (on != midiOnly) { midiOnly = on; stop(); } }

  private String resolve(String stem) {
    if (alias == null) {
      alias = new Hashtable();
      try {
        InputStream in = getClass().getResourceAsStream("/m/a.txt");
        if (in != null) {
          StringBuffer sb = new StringBuffer();
          int c;
          while ((c = in.read()) != -1) sb.append((char) c);
          in.close();
          String s = sb.toString();
          int i = 0;
          while (i < s.length()) {
            int nl = s.indexOf('\n', i); if (nl < 0) nl = s.length();
            String line = s.substring(i, nl).trim();
            int eq = line.indexOf('=');
            if (eq > 0) alias.put(line.substring(0, eq), line.substring(eq + 1));
            i = nl + 1;
          }
        }
      } catch (Throwable e) { }
    }
    String a = (String) alias.get(stem);
    return a != null ? a : stem;
  }

  // the level song (n) in normal or hurry-up speed, or one of the fixed songs
  void play(int what) {
    type = what;
    String f;
    switch (what) {
      case LEVEL: f = song + ""; break;
      case HURRYUP: f = song + "f"; break;
      case HERRING: f = "h"; break;
      case DONE: f = "d"; break;
      case TITLE: f = "t"; break;
      default: stop(); return;
    }
    start(resolve(f), what == DONE ? 1 : -1);
  }

  // a fixed song (theme, credits) by its stem in res/m
  void playFile(String stem, int loops) {
    type = FILE;
    fileStem = stem; fileLoops = loops;
    start(resolve(stem), loops);
  }
  private String fileStem; private int fileLoops;

  void start(String stem, int loops) {
    if (!enabled) { stop(); return; }
    if (stem.equals(current) && player != null) return;
    stop();
    if (!mp3Failed && !midiOnly && open("/m/" + stem + ".mp3", "audio/mpeg", loops)) { current = stem; return; }
    // a song that only exists as a recording names a stock MIDI song to use instead (m/a.txt mid:)
    String mid = (String) alias.get("mid:" + stem);
    if (open("/m/" + (mid != null ? resolve(mid) : stem) + ".mid", "audio/midi", loops)) current = stem;
  }

  private boolean open(String file, String type, int loops) {
    InputStream in = getClass().getResourceAsStream(file);
    if (in == null) return false;
    try {
      player = Manager.createPlayer(in, type);
      player.realize();
      player.prefetch();
      player.setLoopCount(loops);
      player.start();
      return true;
    } catch (Throwable e) {
      if (type.equals("audio/mpeg")) mp3Failed = true;     // this phone can't: use MIDI from now on
      try { if (player != null) player.close(); } catch (Throwable e2) { }
      player = null;
      return false;
    }
  }

  void stop() {
    if (player != null) {
      try { player.stop(); } catch (Throwable e) { }
      try { player.close(); } catch (Throwable e) { }
    }
    player = null; current = null;
  }

  // the phone took the screen: stop, and start the same song again when it comes back
  private boolean suspended;
  void suspend() { if (player != null) { stop(); suspended = true; } }
  void resume() {
    if (!suspended) return;
    suspended = false;
    if (!enabled) return;
    if (type == FILE && fileStem != null) playFile(fileStem, fileLoops);
    else { int t = type; type = NONE; play(t); }
  }

  void setEnabled(boolean on) {
    enabled = on;
    if (!on) stop();
    else if (type == FILE && fileStem != null) playFile(fileStem, fileLoops);
    else { int t = type; type = NONE; play(t); }
  }
}
