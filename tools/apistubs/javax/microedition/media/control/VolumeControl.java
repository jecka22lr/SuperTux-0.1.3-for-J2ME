// Compile-time stand-in for JSR-135's VolumeControl (the API classes used for compiling lack it);
// never packed into the jar: the phone's own class is used.
package javax.microedition.media.control;
public interface VolumeControl extends javax.microedition.media.Control {
  void setMute(boolean mute); boolean isMuted(); int setLevel(int level); int getLevel();
}
