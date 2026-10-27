// Whether the Nokia UI sound API is there. Deliberately no reference to com.nokia.mid.sound, so a
// phone without it can load and call this safely; NokiaSfx (which does reference it) is only
// touched after this says yes. (Some VMs link a class's references when loading it.)
final class NokiaProbe {
  private static int state;   // 0 unknown, 1 available, 2 unavailable

  static boolean available() {
    if (state == 0) {
      try { Class.forName("com.nokia.mid.sound.Sound"); state = 1; } catch (Throwable e) { state = 2; }
    }
    return state == 1;
  }
}
