// Sound effects: 0.1.3's WAVs (8-bit mono 11 kHz) played with MMAPI. Every sound gets its own
// player, made and prefetched once in the background, so playing one is just a start (making a
// player per sound was slow enough to lag behind the game).
//
// Some phones (S60 3rd Edition FP1 on a ~369 MHz CPU, like the E71) freeze the whole Java VM for a
// moment whenever a player starts. Options > Sound picks how effects are played:
//   Normal  stop, rewind and restart the effect every time (the original behaviour)
//   Fast    only start a player that isn't already playing: no stop or rewind call (MMAPI rewinds a
//           player that reached the end by itself), and an effect still playing is not cut off
//   MIDI    notes sent to one prefetched MIDI player (MIDIControl): no player is started at all;
//           phones without MIDIControl get the Tones version
//   Tones   the same notes as plain beeps (Manager.playTone): no player at all, works on any MIDP 2
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

  static final int NORMAL = 0, FAST = 1, MIDI = 2, TONES = 3, NOKIA = 4, OFF = 5;
  static final String[] MODES = { "Normal", "Fast", "MIDI", "Tones", "Nokia", "Off" };

  // MIDI versions: note, length pairs (MIDI note numbers, 60 = middle C; length in units of about
  // 31 ms). Notes above 127 are drums: 128 + General MIDI drum note, played on channel 10.
  private static final int D = 128;
  private static final int[][] NOTES = {
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
  // which WAV set: "/a/" 8-bit 11 kHz (the jars' own), or the test sets "/a16/" and "/a8/" (16-bit
  // 16 or 8 kHz) when the jar has them (SFXTEST builds)
  static final String[] FORMATS = { "8bit 11k", "16bit 16k", "16bit 8k", "ADPCM 8k", "AU ulaw", "MIDI file" };
  static final String[] FORMAT_DIRS = { "/a/", "/a16/", "/a8/", "/aa/", "/au/", null };   // null: made here
  static final String[] FORMAT_EXT = { ".wav", ".wav", ".wav", ".wav", ".au", null };
  static final String[] FORMAT_TYPE = { "audio/x-wav", "audio/x-wav", "audio/x-wav", "audio/x-wav", "audio/basic", "audio/midi" };
  static final int MIDI_FILE = 5;
  static boolean formatAvailable(int f) {
    return f == 0 || f == MIDI_FILE || Sound.class.getResourceAsStream(FORMAT_DIRS[f] + "0" + FORMAT_EXT[f]) != null;
  }

  // Test option "Pause music": the music stops while an effect plays and carries on after it, for
  // phones that can't play two sounds at once (System property supports.mixing=false)
  Music music;
  boolean pauseMusic;
  private long resumeAt;
  private final int[] lengthMs = new int[COUNT];
  int format;
  // how long the slowest sound start took in the last second (Show FPS shows it)
  volatile int stallMs, stallMax;
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

  // a different WAV set: the players are made again from it
  void setFormat(int f) {
    if (f == format || f < 0 || f >= FORMATS.length || !formatAvailable(f)) return;
    stop();
    synchronized (this) { format = f; ready = -1; notify(); }
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
          long left = resumeAt - System.currentTimeMillis();
          if (resumeAt != 0 && left <= 0) break;
          try { if (resumeAt != 0) wait(left); else wait(); } catch (InterruptedException e) { return; }
        }
        m = mode;
        if (ready == m && qHead != qTail) { n = queue[qHead]; qHead = (qHead + 1) % queue.length; }
      }
      if (n < 0 && ready == m && resumeAt != 0 && System.currentTimeMillis() >= resumeAt) {
        resumeAt = 0;                    // Pause music: the effect is over, the music carries on
        if (music != null) music.resumeAfterSfx();
        continue;
      }
      if (n < 0 && ready == m) continue;
      if (n < 0) {
        prepare(m);
        synchronized (this) { ready = m; qHead = qTail; }   // nothing queued meanwhile plays late
        continue;
      }
      if (pauseMusic && music != null && m != OFF) {
        music.pauseForSfx();
        int len = lengthMs[n] > 0 ? lengthMs[n] : 0;
        if (m == MIDI || m == TONES) { len = 0; int[] t = NOTES[n]; for (int i = 1; i < t.length; i += 2) len += t[i] * 31; }
        long end = System.currentTimeMillis() + (len > 0 ? len : 400) + 30;
        if (end > resumeAt) resumeAt = end;
      }
      long t0 = System.currentTimeMillis();
      if (m == NORMAL) startNormal(n);
      else if (m == FAST) startFast(n);
      else if (m == MIDI || m == TONES) playMidi(n);
      else if (m == NOKIA) playNokia(n);
      if (m != MIDI && m != TONES) {     // (MIDI and Tones sleep through their notes on purpose)
        int ms = (int) (System.currentTimeMillis() - t0);
        if (ms > stallMax) stallMax = ms;
      }
    }
  }

  // make what the mode needs and free what it doesn't
  private int preparedFormat;
  private void prepare(int m) {
    if (format != preparedFormat) {      // another WAV set: drop the players made from the old one
      if (wav != null) for (int i = 0; i < COUNT; i++) if (wav[i] != null) { try { wav[i].close(); } catch (Throwable e) { } }
      wav = null; last = null;
      if (nokia != null) { nokia.release(); nokia = null; }
      preparedFormat = format;
    }
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
      if (FORMAT_DIRS[format] == null) return midiFile(n);
      InputStream in = getClass().getResourceAsStream(FORMAT_DIRS[format] + n + FORMAT_EXT[format]);
      if (in == null) return null;
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[2048];
      int r;
      while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
      in.close();
      return out.toByteArray();
    } catch (Throwable e) { return null; }
  }

  // a small format 0 MIDI file of effect n (the MIDI mode's notes; 96 ticks a beat at 120 bpm, so a
  // tick is about 5.2 ms and a note unit of 31 ms is 6 ticks)
  private byte[] midiFile(int n) {
    java.io.ByteArrayOutputStream t = new java.io.ByteArrayOutputStream();
    int[] nt = NOTES[n];
    t.write(0); t.write(0xC0); t.write(80);                  // square lead
    t.write(0); t.write(0xB0); t.write(7); t.write(110);
    int total = 0;
    for (int i = 0; i < nt.length; i += 2) {
      int note = nt[i], ch = 0, len = nt[i + 1] * 6;
      if (note >= D) { note -= D; ch = 9; }
      t.write(0); t.write(0x90 | ch); t.write(note); t.write(100);
      vlq(t, len); t.write(0x80 | ch); t.write(note); t.write(0);
      total += nt[i + 1] * 31;
    }
    t.write(0); t.write(0xFF); t.write(0x2F); t.write(0);
    lengthMs[n] = total;
    byte[] trk = t.toByteArray();
    byte[] out = new byte[22 + trk.length];
    byte[] h = { 'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k' };
    System.arraycopy(h, 0, out, 0, 18);
    out[18] = (byte) (trk.length >> 24); out[19] = (byte) (trk.length >> 16); out[20] = (byte) (trk.length >> 8); out[21] = (byte) trk.length;
    System.arraycopy(trk, 0, out, 22, trk.length);
    return out;
  }

  private static void vlq(java.io.ByteArrayOutputStream o, int v) {
    if (v >= 0x80) { if (v >= 0x4000) o.write(0x80 | (v >> 14)); o.write(0x80 | ((v >> 7) & 0x7f)); }
    o.write(v & 0x7f);
  }

  private Player makeWav(int n) {
    try {
      byte[] b = readWav(n);
      if (b == null) return null;
      Player p = Manager.createPlayer(new ByteArrayInputStream(b), FORMAT_TYPE[format]);
      p.realize();
      p.prefetch();
      try { long d = p.getDuration(); lengthMs[n] = d > 0 ? (int) (d / 1000) : 0; } catch (Throwable e) { }
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
    MidiSfx s = mode == MIDI ? midi : null;    // no MIDIControl (or Tones): beeps instead
    int m = mode;
    int[] t = NOTES[n];
    for (int i = 0; i < t.length; i += 2) {
      int note = t[i], ch = 0, ms = t[i + 1] * 31;
      if (note >= D) { note -= D; ch = 9; }
      if (s != null) {
        s.note(ch, note, true);
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
        s.note(ch, note, false);
      } else {
        // a drum becomes a short low blip; playTone returns at once, so wait out the note here
        if (ch == 9) { note = 40 + (note & 7); ms = Math.min(ms, 40); }
        try { Manager.playTone(note, ms, 70); } catch (Throwable e) { }
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
      }
      if (mode != m) return;
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
