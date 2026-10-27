// "Nokia" sound mode: the WAVs through Nokia's own sound API (com.nokia.mid.sound) instead of
// MMAPI players. Only created after NokiaProbe found the API.
import com.nokia.mid.sound.Sound;

final class NokiaSfx {
  private final Sound[] sounds = new Sound[20];
  int loaded;

  // some Nokia UI implementations have the class but can only play tones: treat that as missing
  NokiaSfx() {
    boolean wav = false;
    try {
      int[] f = Sound.getSupportedFormats();
      for (int i = 0; f != null && i < f.length; i++) if (f[i] == Sound.FORMAT_WAV) wav = true;
    } catch (Throwable e) { }
    if (!wav) throw new IllegalArgumentException("no WAV");
  }

  boolean load(int n, byte[] wav) {
    try { sounds[n] = new Sound(wav, Sound.FORMAT_WAV); loaded++; return true; }
    catch (Throwable e) { sounds[n] = null; return false; }
  }

  boolean play(int n) {
    Sound s = sounds[n];
    if (s == null) return false;
    try {
      if (s.getState() == Sound.SOUND_PLAYING) return true;   // still playing: let it finish (fewer stalls)
      s.play(1);
      return true;
    } catch (Throwable e) { return false; }
  }

  void stop() {
    for (int i = 0; i < sounds.length; i++)
      if (sounds[i] != null) { try { sounds[i].stop(); } catch (Throwable e) { } }
  }

  void release() {
    for (int i = 0; i < sounds.length; i++)
      if (sounds[i] != null) { try { sounds[i].release(); } catch (Throwable e) { } sounds[i] = null; }
    loaded = 0;
  }
}
