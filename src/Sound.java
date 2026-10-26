// Sound effects: 0.1.3's WAVs (8-bit mono 11 kHz) played with MMAPI. Every sound gets its own
// player, made and prefetched once in the background, so playing one is just a start (making a
// player per sound was slow enough to lag behind the game).
//
// Some phones (S60 3rd Edition FP1 on a ~369 MHz CPU, like the E71) freeze the whole Java VM for a
// moment whenever a player starts. Options > Sound picks how effects are played:
//   Normal  stop, rewind and restart the effect every time (the original behaviour)
//   Fast    only start a player that isn't already playing: no stop or rewind call (MMAPI rewinds a
//           player that reached the end by itself), and an effect still playing is not cut off
//   MIDI    notes sent to one prefetched MIDI player (MIDIControl): no player is started at all
//   Nokia   the WAVs through Nokia's own sound API (com.nokia.mid.sound) instead of MMAPI
//   Off     no effects (music is separate)
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

final class Sound implements Runnable {
  // sound.cpp's order
  static final int JUMP = 0, BIGJUMP = 1, SKID = 2, DISTRO = 3, HERRING = 4, BRICK = 5, HURT = 6,
      SQUISH = 7, FALL = 8, RICOCHET = 9, BUMP_UPGRADE = 10, UPGRADE = 11, EXCELLENT = 12, COFFEE = 13,
      SHOOT = 14, LIFEUP = 15, STOMP = 16, KICK = 17, EXPLODE = 18, WARP = 19, COUNT = 20;

  static final int NORMAL = 0, FAST = 1, MIDI = 2, NOKIA = 3, OFF = 4;
  static final String[] MODES = { "Normal", "Fast", "MIDI", "Nokia", "Off" };

  // MIDI versions: note, length pairs (MIDI note numbers, 60 = middle C; length in units of about
  // 31 ms). Notes above 127 are drums: 128 + General MIDI drum note, played on channel 10.
  private static final int D = 128;
  private static final int[][] TONES = {
    { 72, 2, 79, 3 },                        // jump
    { 67, 2, 74, 4 },                        // big jump
    { D + 42, 2 },                           // skid: hi-hat
    { 83, 2, 88, 4 },                        // coin
    { 79, 2, 83, 2, 86, 2, 91, 4 },          // herring (invincible)
    { D + 38, 2, D + 40, 2 },                // brick: snares
    { 76, 3, 69, 3, 62, 5 },                 // hurt
    { D + 39, 2 },                           // squish: hand clap
    { 79, 3, 74, 3, 69, 3, 64, 3, 59, 5 },   // fall
    { 88, 2 },                               // ricochet
    { 64, 2, 69, 3 },                        // bump a bonus block
    { 72, 2, 76, 2, 79, 4 },                 // power-up appears
    { 72, 2, 76, 2, 79, 2, 84, 5 },          // excellent (grow)
    { 74, 2, 78, 2, 81, 2, 86, 5 },          // coffee (fire flower)
    { 91, 2 },                               // shoot
    { 72, 3, 76, 3, 79, 3, 84, 3, 88, 6 },   // 1-up
    { D + 36, 2 },                           // stomp: bass drum
    { D + 37, 2 },                           // kick: side stick
    { D + 36, 1, D + 49, 6 },                // explode: bass drum and crash
    { 60, 2, 64, 2, 67, 2, 72, 2, 76, 2, 79, 2, 84, 4 }  // warp
  };

  int mode = NORMAL;
  private int ready = -1;                    // the mode whose players are prepared
  private Player[] wav;
  private MidiSfx midi;
  private NokiaSfx nokia;
  private final boolean[] broken = new boolean[COUNT];
  private final int[] queue = new int[8];
  private int qHead, qTail;
  private Player last;
  private Thread thread;

  boolean enabled() { return mode != OFF; }

  // start the worker, which prepares the players before playing anything
  void init() {
    if (thread == null) { thread = new Thread(this); thread.start(); }
  }

  void setMode(int m) {
    if (m < NORMAL || m > OFF || m == mode) return;
    stop();
    synchronized (this) { mode = m; notify(); }
  }

  void play(int n) {
    if (mode == OFF) return;
    synchronized (this) {
      int next = (qTail + 1) % queue.length;
      if (next != qHead) { queue[qTail] = n; qTail = next; }
      notify();
    }
  }

  public void run() {
    while (true) {
      int n = -1, m;
      synchronized (this) {
        while (qHead == qTail && ready == mode) {
          try { wait(); } catch (InterruptedException e) { return; }
        }
        m = mode;
        if (ready == m) { n = queue[qHead]; qHead = (qHead + 1) % queue.length; }
      }
      if (n < 0) {
        prepare(m);
        synchronized (this) { ready = m; qHead = qTail; }   // nothing queued meanwhile plays late
        continue;
      }
      if (m == NORMAL) startNormal(n);
      else if (m == FAST) startFast(n);
      else if (m == MIDI) playMidi(n);
      else if (m == NOKIA) playNokia(n);
    }
  }

  // make what the mode needs and free what it doesn't
  private void prepare(int m) {
    if (m != NORMAL && m != FAST && wav != null) {
      for (int i = 0; i < COUNT; i++)
        if (wav[i] != null) { try { wav[i].close(); } catch (Throwable e) { } }
      wav = null; last = null;
    }
    if (m != MIDI && midi != null) { midi.close(); midi = null; }
    if (m != NOKIA && nokia != null) { nokia.release(); nokia = null; }
    for (int i = 0; i < COUNT; i++) broken[i] = false;

    if ((m == NORMAL || m == FAST) && wav == null) {
      wav = new Player[COUNT];
      for (int i = 0; i < COUNT; i++) wav[i] = makeWav(i);
    } else if (m == MIDI && midi == null) {
      try {
        MidiSfx s = new MidiSfx();
        if (s.open()) midi = s;
      } catch (Throwable e) { }
    } else if (m == NOKIA && nokia == null && NokiaSfx.available()) {
      try {
        NokiaSfx s = new NokiaSfx();
        for (int i = 0; i < COUNT; i++) {
          byte[] b = readWav(i);
          if (b != null) s.load(i, b);
        }
        nokia = s;
      } catch (Throwable e) { }
    }
  }

  private byte[] readWav(int n) {
    try {
      InputStream in = getClass().getResourceAsStream("/a/" + n + ".wav");
      if (in == null) return null;
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[2048];
      int r;
      while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
      in.close();
      return out.toByteArray();
    } catch (Throwable e) { return null; }
  }

  private Player makeWav(int n) {
    try {
      byte[] b = readWav(n);
      if (b == null) return null;
      Player p = Manager.createPlayer(new ByteArrayInputStream(b), "audio/x-wav");
      p.realize();
      p.prefetch();
      return p;
    } catch (Throwable e) { return null; }
  }

  private void startNormal(int n) {
    Player p = wav != null ? wav[n] : null;
    if (p == null || broken[n]) return;
    try {
      try { p.stop(); } catch (Throwable e) { }
      p.setMediaTime(0);
      p.start();
      last = p;
    } catch (Throwable e) {
      // phones that play one sound at a time: stop the one still playing and try again
      try {
        if (last != null && last != p) last.stop();
        p.setMediaTime(0);
        p.start();
        last = p;
      } catch (Throwable e2) { broken[n] = true; }
    }
  }

  private void startFast(int n) {
    Player p = wav != null ? wav[n] : null;
    if (p == null || broken[n]) return;
    try {
      if (p.getState() == Player.STARTED) return;   // still playing: let it finish
      p.start();
      last = p;
    } catch (Throwable e) {
      // one sound at a time: let the playing one finish rather than paying for a stop
      if (last == null || last == p) broken[n] = true;
    }
  }

  // plays the notes one after another on this (the sound) thread
  private void playMidi(int n) {
    MidiSfx s = midi;
    if (s == null) return;
    int[] t = TONES[n];
    for (int i = 0; i < t.length; i += 2) {
      int note = t[i], ch = 0;
      if (note >= D) { note -= D; ch = 9; }
      s.note(ch, note, true);
      try { Thread.sleep(t[i + 1] * 31); } catch (InterruptedException e) { }
      s.note(ch, note, false);
      if (mode != MIDI) return;
    }
  }

  private void playNokia(int n) {
    NokiaSfx s = nokia;
    if (s != null && !broken[n] && !s.play(n)) broken[n] = true;
  }

  void stop() {
    synchronized (this) { qHead = qTail; }
    Player[] ps = wav;
    if (ps != null)
      for (int i = 0; i < ps.length; i++)
        if (ps[i] != null) { try { ps[i].stop(); } catch (Throwable e) { } }
    MidiSfx m = midi;
    if (m != null) m.allOff();
    NokiaSfx k = nokia;
    if (k != null) k.stop();
  }
}
