package com.nokia.mid.sound;
public class Sound {
  public static final int FORMAT_WAV = 5, SOUND_PLAYING = 0, SOUND_STOPPED = 1;
  public Sound(byte[] d, int f) { }
  public int getState() { return SOUND_STOPPED; }
  public void play(int loops) { } public void stop() { } public void release() { }
}
