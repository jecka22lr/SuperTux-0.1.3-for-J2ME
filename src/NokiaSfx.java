// "Nokia" sound mode: the WAVs through Nokia's own sound API (com.nokia.mid.sound) instead of
// MMAPI players. Kept in its own class so phones without the Nokia UI API never load it.
import com.nokia.mid.sound.Sound;

final class NokiaSfx {
  private final Object[] sounds = new Object[20];

  static boolean available() {
    try { Class.forName("com.nokia.mid.sound.Sound"); return true; } catch (Throwable e) { return false; }
  }

  void load(int n, byte[] wav) {
    try { sounds[n] = new Sound(wav, Sound.FORMAT_WAV); } catch (Throwable e) { sounds[n] = null; }
  }

  boolean play(int n) {
    Sound s = (Sound) sounds[n];
    if (s == null) return false;
    try {
      if (s.getState() == Sound.SOUND_PLAYING) return true;   // still playing: let it finish
      s.play(1);
      return true;
    } catch (Throwable e) { return false; }
  }

  void stop() {
    for (int i = 0; i < sounds.length; i++)
      if (sounds[i] != null) { try { ((Sound) sounds[i]).stop(); } catch (Throwable e) { } }
  }

  void release() {
    for (int i = 0; i < sounds.length; i++)
      if (sounds[i] != null) { try { ((Sound) sounds[i]).release(); } catch (Throwable e) { } sounds[i] = null; }
  }
}
