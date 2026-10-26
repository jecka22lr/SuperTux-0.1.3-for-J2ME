// "MIDI" sound mode: effects as notes sent straight to one prefetched MIDI player through
// MIDIControl.shortMidiEvent. No player is started per effect, which is the call that freezes
// some phones' Java (S60 3rd Edition FP1 on a slow CPU). Kept in its own class so phones without
// MIDIControl never load it.
import java.io.ByteArrayInputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.MIDIControl;

final class MidiSfx {
  private Player player;
  private MIDIControl midi;

  // a near-empty format 0 MIDI file: just enough for the phone to make a MIDI player
  private static final byte[] EMPTY = {
    'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
    'M', 'T', 'r', 'k', 0, 0, 0, 12,
    0, (byte) 0xFF, 0x51, 3, 0x07, (byte) 0xA1, 0x20,   // tempo 120 bpm
    0, (byte) 0xFF, 0x2F, 0                             // end of track
  };

  boolean open() {
    try {
      player = Manager.createPlayer(new ByteArrayInputStream(EMPTY), "audio/midi");
      player.realize();
      player.prefetch();
      midi = (MIDIControl) player.getControl("javax.microedition.media.control.MIDIControl");
      if (midi == null) midi = (MIDIControl) player.getControl("MIDIControl");
      if (midi == null) { close(); return false; }
      midi.shortMidiEvent(0xC0, 80, 0);     // channel 1: square lead, close to the original bleeps
      midi.shortMidiEvent(0xB0, 7, 110);    // channel volume
      midi.shortMidiEvent(0xB9, 7, 110);    // drums (channel 10) volume
      return true;
    } catch (Throwable e) { close(); return false; }
  }

  void note(int channel, int note, boolean on) {
    try { midi.shortMidiEvent((on ? 0x90 : 0x80) | channel, note, on ? 100 : 0); } catch (Throwable e) { }
  }

  void allOff() {
    try { midi.shortMidiEvent(0xB0, 123, 0); midi.shortMidiEvent(0xB9, 123, 0); } catch (Throwable e) { }
  }

  void close() {
    if (player != null) { try { player.close(); } catch (Throwable e) { } }
    player = null; midi = null;
  }
}
