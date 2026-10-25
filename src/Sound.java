// Sound effects: 0.1.3's WAVs (8-bit mono 11 kHz) played with MMAPI. Every sound gets its own
// player, made and prefetched once in the background at start-up, so playing one is just a rewind
// and start (making a player per sound was slow enough to lag behind the game).
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

final class Sound implements Runnable {
  // sound.cpp's order
  static final int JUMP = 0, BIGJUMP = 1, SKID = 2, DISTRO = 3, HERRING = 4, BRICK = 5, HURT = 6,
      SQUISH = 7, FALL = 8, RICOCHET = 9, BUMP_UPGRADE = 10, UPGRADE = 11, EXCELLENT = 12, COFFEE = 13,
      SHOOT = 14, LIFEUP = 15, STOMP = 16, KICK = 17, EXPLODE = 18, WARP = 19, COUNT = 20;

  boolean enabled = true;
  private final Player[] players = new Player[COUNT];
  private final boolean[] broken = new boolean[COUNT];
  private final int[] queue = new int[8];
  private int qHead, qTail;
  private Player last;
  private Thread thread;

  // start the worker, which prepares every player before playing anything
  void init() {
    if (thread == null) { thread = new Thread(this); thread.start(); }
  }

  void play(int n) {
    if (!enabled) return;
    synchronized (this) {
      int next = (qTail + 1) % queue.length;
      if (next != qHead) { queue[qTail] = n; qTail = next; }
      notify();
    }
  }

  public void run() {
    for (int i = 0; i < COUNT; i++) prepare(i);
    synchronized (this) { qHead = qTail; }       // nothing queued during loading plays late
    while (true) {
      int n;
      synchronized (this) {
        while (qHead == qTail) { try { wait(); } catch (InterruptedException e) { return; } }
        n = queue[qHead]; qHead = (qHead + 1) % queue.length;
      }
      if (enabled) start(n);
    }
  }

  private void prepare(int n) {
    try {
      InputStream in = getClass().getResourceAsStream("/a/" + n + ".wav");
      if (in == null) { broken[n] = true; return; }
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[2048];
      int r;
      while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
      in.close();
      Player p = Manager.createPlayer(new ByteArrayInputStream(out.toByteArray()), "audio/x-wav");
      p.realize();
      p.prefetch();
      players[n] = p;
    } catch (Throwable e) { broken[n] = true; }
  }

  private void start(int n) {
    Player p = players[n];
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
      } catch (Throwable e2) { }
    }
  }

  void stop() {
    synchronized (this) { qHead = qTail; }
    for (int i = 0; i < COUNT; i++) if (players[i] != null) { try { players[i].stop(); } catch (Throwable e) { } }
  }
}
