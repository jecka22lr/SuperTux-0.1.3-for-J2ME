// 0.1.3's menu (menu.cpp) as Jecka's 320x240 Symbian build draws it: positions in 0.1.3's
// mixed units (x in screen pixels, y in 480-line units), translucent box, white items, the
// active one blue, light blue rules, the back arrow and check boxes, and the slide-in effect.
// Text fields take keypad multi-tap typing like the Symbian build (* switches case, # or C erases).
import java.util.Vector;
import javax.microedition.lcdui.Graphics;

final class Menu {
  static final int ACTION = 0, GOTO = 1, TOGGLE = 2, BACK = 3, DEACTIVE = 4, LABEL = 5, HL = 6,
      TEXTFIELD = 7, NUMFIELD = 8, STRINGSELECT = 9, CONTROLFIELD = 10;
  static final int A_NONE = 0, A_UP = 1, A_DOWN = 2, A_HIT = 3, A_LEFT = 4, A_RIGHT = 5;

  // the current menu and the ones under it (Menu::current_, last_menus)
  static Menu current;
  static Vector last = new Vector();
  static boolean caps;                   // multi-tap capitals
  private static int mtKey = -1, mtIndex; private static long mtTime;
  private static final String[] MT = { " 0", ".,-_!?1", "abc2", "def3", "ghi4", "jkl5", "mno6", "pqrs7", "tuv8", "wxyz9" };

  String[] text = new String[8], input = new String[8];
  String[][] list = new String[8][];
  int[] listActive = new int[8], kind = new int[8], id = new int[8];
  boolean[] toggled = new boolean[8];
  Menu[] target = new Menu[8];
  int count;
  int active;
  int hit = -1;
  int posY = 240, posYPortrait = 320;     // 480-line units
  int posX = -1;                          // screen x of the centre (-1: middle of the screen)
  boolean arrangeLeft;
  long effectEnd;                         // slide-in effect (500 ms, real time: game time stops in pause)
  static boolean noEffects;               // Options > Detail > Potato: no slide-in, solid boxes

  Menu add(int k, String t, int ident) { return add(k, t, ident, null, false); }

  Menu add(int k, String t, int ident, Menu to, boolean on) {
    if (count == text.length) grow();
    text[count] = t; kind[count] = k; id[count] = ident; target[count] = to; toggled[count] = on;
    input[count] = ""; list[count] = null; listActive[count] = 0;
    count++;
    return this;
  }

  private void grow() {
    int n = text.length * 2;
    String[] t2 = new String[n]; System.arraycopy(text, 0, t2, 0, count); text = t2;
    t2 = new String[n]; System.arraycopy(input, 0, t2, 0, count); input = t2;
    String[][] l2 = new String[n][]; System.arraycopy(list, 0, l2, 0, count); list = l2;
    int[] i2 = new int[n]; System.arraycopy(listActive, 0, i2, 0, count); listActive = i2;
    i2 = new int[n]; System.arraycopy(kind, 0, i2, 0, count); kind = i2;
    i2 = new int[n]; System.arraycopy(id, 0, i2, 0, count); id = i2;
    boolean[] b2 = new boolean[n]; System.arraycopy(toggled, 0, b2, 0, count); toggled = b2;
    Menu[] m2 = new Menu[n]; System.arraycopy(target, 0, m2, 0, count); target = m2;
    i2 = new int[n]; System.arraycopy(toggledCode, 0, i2, 0, count); toggledCode = i2;
  }

  void clear() { count = 0; active = 0; }

  // a new item right after the item with this id
  void insertAfter(int after, int k, String t, int ident) {
    int at = find(after) + 1;
    if (at <= 0) at = count;
    add(k, t, ident);
    int last = count - 1;
    for (int i = last; i > at; i--) swap(i, i - 1);
  }

  private void swap(int a, int b) {
    String s = text[a]; text[a] = text[b]; text[b] = s;
    s = input[a]; input[a] = input[b]; input[b] = s;
    String[] l = list[a]; list[a] = list[b]; list[b] = l;
    int v = listActive[a]; listActive[a] = listActive[b]; listActive[b] = v;
    v = kind[a]; kind[a] = kind[b]; kind[b] = v;
    v = id[a]; id[a] = id[b]; id[b] = v;
    boolean z = toggled[a]; toggled[a] = toggled[b]; toggled[b] = z;
    Menu m = target[a]; target[a] = target[b]; target[b] = m;
    v = toggledCode[a]; toggledCode[a] = toggledCode[b]; toggledCode[b] = v;
  }

  // touch: where the items were last drawn (screen pixels)
  int[] itemY = new int[0]; int boxX, boxY, boxW = -1, boxH;

  // the item under a tap: its index, -1 inside the box but on nothing, -2 outside the box
  int itemAt(int x, int y) {
    if (boxW < 0 || x < boxX || x >= boxX + boxW || y < boxY || y >= boxY + boxH) return -2;
    for (int i = 0; i < count && i < itemY.length; i++)
      if (y >= itemY[i] - 6 && y < itemY[i] + 6 && kind[i] != HL && kind[i] != LABEL && kind[i] != DEACTIVE) return i;
    return -1;
  }

  int find(int ident) {
    for (int i = 0; i < count; i++) if (id[i] == ident && kind[i] != HL && kind[i] != LABEL) return i;
    return -1;
  }

  boolean isToggled(int ident) { int i = find(ident); return i >= 0 && toggled[i]; }
  void setToggled(int ident, boolean on) { int i = find(ident); if (i >= 0) toggled[i] = on; }
  String inputOf(int ident) { int i = find(ident); return i >= 0 ? input[i] : ""; }
  void setInput(int ident, String s) { int i = find(ident); if (i >= 0) input[i] = s; }
  void setList(int ident, String[] l, int act) { int i = find(ident); if (i >= 0) { list[i] = l; listActive[i] = act; } }
  int listOf(int ident) { int i = find(ident); return i >= 0 ? listActive[i] : 0; }

  static void setCurrent(Menu m, long now) {
    last.removeAllElements();
    if (m != null) m.effectEnd = System.currentTimeMillis() + 500;
    current = m;
  }

  static void push(Menu m, long now) {
    if (current != null) last.addElement(current);
    current = m;
    m.effectEnd = System.currentTimeMillis() + 500;
  }

  static void pop(long now) {
    if (last.size() > 0) {
      current = (Menu) last.lastElement();
      last.removeElementAt(last.size() - 1);
      current.effectEnd = System.currentTimeMillis() + 500;
    } else current = null;
  }

  // a key for the active field (Menu::event's typing part); true when it was used
  boolean key(int code) {
    if (count == 0) return false;
    int k = kind[active];
    boolean soft = code == -6 || code == -7;
    if (k == CONTROLFIELD) {
      // the joystick moves over the fields; select starts listening and the next key is taken
      // (0.1.3 took every key, so there was no way to move past a field without changing it)
      if (!waiting) { if (soft) { pop(0); return true; } return false; }
      waiting = false;
      if (soft) return true;
      toggledCode[active] = code;
      action(A_DOWN, 0);
      return true;
    }
    if (k != TEXTFIELD && k != NUMFIELD) return false;
    if (code == '#' || code == -8) {
      if (input[active].length() > 0) input[active] = input[active].substring(0, input[active].length() - 1);
      mtKey = -1;
      return true;
    }
    if (code >= '0' && code <= '9') {
      int d = code - '0';
      if (k == NUMFIELD) { if (input[active].length() < 6) input[active] += (char) code; return true; }
      long t = System.currentTimeMillis();
      String s = input[active];
      if (d == mtKey && t - mtTime < 1000 && s.length() > 0) { mtIndex = (mtIndex + 1) % MT[d].length(); s = s.substring(0, s.length() - 1); }
      else mtIndex = 0;
      char c = MT[d].charAt(mtIndex);
      if (caps && c >= 'a' && c <= 'z') c = (char) (c - 'a' + 'A');
      if (s.length() < 40) input[active] = s + c;
      mtKey = d; mtTime = t;
      return true;
    }
    if (code == '*' && k == TEXTFIELD) { caps = !caps; mtKey = -1; return true; }
    // QWERTY phones (E71, C6...) send the letter itself: type it straight in
    if (k == TEXTFIELD && code >= 32 && code < 127 && code != '#') {
      if (input[active].length() < 40) input[active] += (char) code;
      mtKey = -1;
      return true;
    }
    mtKey = -1;
    return false;
  }
  int[] toggledCode = new int[8];          // CONTROLFIELD values (key codes)
  boolean waiting;                         // a CONTROLFIELD is waiting for its key

  int codeOf(int ident) { int i = find(ident); return i >= 0 ? toggledCode[i] : 0; }
  void setCode(int ident, int c) {
    int i = find(ident);
    if (i >= 0) toggledCode[i] = c;
  }

  // Menu::action
  void action(int a, long now) {
    hit = -1;
    if (count == 0) return;
    if (a == A_UP) active = active > 0 ? active - 1 : count - 1;
    else if (a == A_DOWN) active = active < count - 1 ? active + 1 : 0;
    else if ((a == A_LEFT || a == A_RIGHT) && kind[active] == STRINGSELECT && list[active] != null && list[active].length > 0) {
      int n = list[active].length;
      listActive[active] = (listActive[active] + (a == A_LEFT ? n - 1 : 1)) % n;
    } else if (a == A_HIT) {
      hit = active;
      switch (kind[active]) {
        case GOTO: if (target[active] != null) push(target[active], now); break;
        case TOGGLE: toggled[active] = !toggled[active]; break;
        case CONTROLFIELD: waiting = true; break;
        case ACTION: setCurrent(null, now); toggled[active] = true; break;
        case TEXTFIELD: case NUMFIELD: a = A_DOWN; active = active < count - 1 ? active + 1 : 0; break;
        case BACK: pop(now); break;
      }
    }
    // skip the rules and labels
    for (int guard = 0; guard < count && (kind[active] == DEACTIVE || kind[active] == LABEL || kind[active] == HL); guard++)
      active = a == A_UP ? (active > 0 ? active - 1 : count - 1) : (active < count - 1 ? active + 1 : 0);
  }

  int check() { return hit != -1 ? id[hit] : -1; }

  static int screenW = 320;

  // room left for a field's value on this screen (the narrow portrait screen cuts long ones)
  int room(int i) { return Math.max(4, (screenW - 16) / 8 - text[i].length() - 1 - (kind[i] == STRINGSELECT ? 4 : 0)); }

  String shown(int i) {
    String s = kind[i] == CONTROLFIELD ? (waiting && i == active ? "Press a key" : Game.keyName(toggledCode[i])) : input[i];
    int r = room(i) - 1;
    return s.length() > r ? ".." + s.substring(s.length() - (r - 2)) : s;     // keep the end in view while typing
  }

  // the longest choice of any left/right item: every value box gets this width, so the labels line
  // up and the menu doesn't change size while cycling through values
  int selLen() {
    int m = 0;
    for (int i = 0; i < count; i++)
      if (kind[i] == STRINGSELECT && list[i] != null)
        for (int j = 0; j < list[i].length; j++) m = Math.max(m, list[i][j].length());
    return m;
  }

  String listText(int i) {
    String s = list[i] != null && list[i].length > 0 ? list[i][listActive[i]] : "";
    int r = room(i);
    return s.length() > r ? s.substring(0, r - 2) + ".." : s;
  }

  int width() {
    int w = 0;
    for (int i = 0; i < count; i++) {
      boolean field = kind[i] == TEXTFIELD || kind[i] == NUMFIELD || kind[i] == CONTROLFIELD;
      int l = text[i].length() + (field ? shown(i).length() + 1 : 0) + (kind[i] == STRINGSELECT ? selLen() + 3 : listText(i).length());   // + the arrows and the value box's edges
      if (l > w) { w = l; if (kind[i] == TOGGLE) w += 2; }
    }
    return w * 16 + 24;
  }

  // Menu::draw; cx is the screen centre, sh the screen height
  // Potato and Max Potato: the solid menu box with everything on it is drawn into an image and
  // only drawn again when something on it changes (cursor, values, text, the typing caret)
  private javax.microedition.lcdui.Image cache; private int cacheKey;

  void draw(Graphics g, int cx, int sh, boolean portrait, long now) {
    if (!noEffects) { drawDirect(g, cx, sh, portrait, now); return; }
    int key = stateKey(cx, sh, portrait);
    int bx = boxX, by = boxY;
    if (cache == null || key != cacheKey || boxW <= 0) {
      drawDirect(g, cx, sh, portrait, now);              // works out the box (boxX..boxH) too
      if (boxW > 0 && boxH > 0) {
        if (cache == null || cache.getWidth() != boxW || cache.getHeight() != boxH)
          cache = javax.microedition.lcdui.Image.createImage(boxW, boxH);
        Graphics cg = cache.getGraphics();
        cg.translate(-boxX, -boxY);
        drawDirect(cg, cx, sh, portrait, now);
        cacheKey = key;
      }
      return;
    }
    g.drawImage(cache, bx, by, Graphics.TOP | Graphics.LEFT);
    if (count > 0 && kind[active] == TEXTFIELD)
      Res.chars(g, Res.WHITE, caps ? "ABC   * switches case" : "abc   * switches case", 5, sh - 15);
  }

  // everything the menu shows, folded into one number
  private int stateKey(int cx, int sh, boolean portrait) {
    int k = count * 31 + active;
    k = k * 31 + cx; k = k * 31 + sh; k = k * 31 + (portrait ? 1 : 0); k = k * 31 + (waiting ? 1 : 0) + (caps ? 2 : 0);
    k = k * 31 + posYPortrait * 7 + posY;
    for (int i = 0; i < count; i++) {
      k = k * 31 + kind[i];
      k = k * 31 + (text[i] != null ? text[i].hashCode() : 0);
      k = k * 31 + (toggled[i] ? 1 : 0) + listActive[i] * 2;
      if (kind[i] == TEXTFIELD || kind[i] == NUMFIELD) k = k * 31 + input[i].hashCode();
      if (kind[i] == CONTROLFIELD) k = k * 31 + toggledCode[i];
    }
    if (count > 0 && kind[active] == TEXTFIELD) k = k * 31 + (int) ((System.currentTimeMillis() / 500) % 2);   // caret
    return k;
  }

  void drawDirect(Graphics g, int cx, int sh, boolean portrait, long now) {
    screenW = cx * 2;
    if (posX >= 0) cx = posX;
    int py = portrait ? posYPortrait : posY;
    int mh = count * 24, mw = width();
    if (mh + 20 > sh * 2) {                       // taller than the screen: keep the active item in view
      int ay = py + 24 * active - mh / 2 + 12;
      if (ay > sh * 2 - 40) py -= ay - (sh * 2 - 40);
      if (ay < 40) py += 40 - ay;
    }
    // the translucent box; Potato: one solid fill in about the colour it shows over the sky
    if (noEffects) { g.setColor(0x6f8bc4); g.fillRect(cx - mw / 4, (py - 12 * count - 10) / 2, mw / 2, (mh + 20) / 2); }
    else Res.fillAlpha(g, cx - mw / 4, (py - 12 * count - 10) / 2, mw / 2, (mh + 20) / 2, 0x7d96b4c8);
    boxX = cx - mw / 4; boxY = (py - 12 * count - 10) / 2; boxW = mw / 2; boxH = (mh + 20) / 2;
    if (itemY.length < count) itemY = new int[count + 8];
    for (int i = 0; i < count; i++) itemY[i] = (py + 24 * i - mh / 2 + 12) / 2;
    long t = System.currentTimeMillis();
    int eff = !noEffects && effectEnd > t ? (int) (effectEnd - t) / 4 : 0;
    for (int i = 0; i < count; i++) {
      int eo = (i % 2) != 0 ? eff : -eff;
      int y = py + 24 * i - mh / 2 + 12 + eo;
      if (y < -40 || y > sh * 2 + 40) continue;
      int tw = text[i].length() * 8;
      boolean field = kind[i] == TEXTFIELD || kind[i] == NUMFIELD || kind[i] == CONTROLFIELD;
      String in = field ? shown(i) : "";
      int iw = field ? (in.length() + 1) * 8 : 0;
      String lt = listText(i);
      int lw = (kind[i] == STRINGSELECT ? selLen() : lt.length()) * 8;
      int x = cx - (kind[i] == STRINGSELECT ? 6 : 0);   // centres label, arrows and value box in the menu
      if (arrangeLeft) x += 12 - mw / 4 + (tw + iw + lw) / 2;
      boolean act = i == active;
      int font = act ? Res.BLUE : Res.WHITE, shadow = act ? 3 : 2;
      switch (kind[i]) {
        case DEACTIVE: text480(g, Res.BLACK, text[i], x, y, 2); break;
        case HL: {
          int hx = cx - mw / 4, yy = (y - 12 - eo + 6) / 2;
          if (noEffects) { g.setColor(0x96c8ff); g.fillRect(hx, yy, mw / 2, 2); }
          else Res.fillAlpha(g, hx, yy, mw / 2, 2, 0xe196c8ff);
          g.setColor(0xffffff); g.fillRect(hx, yy, mw / 2, 1);
          break;
        }
        case LABEL: text480(g, Res.WHITE, text[i], x, y, 2); break;
        case TEXTFIELD: case NUMFIELD: case CONTROLFIELD: {
          int ip = iw / 2, tp = (tw + 8) / 2;
          // (the 320x240 build halves these widths, which left the text sticking out; full width here)
          g.setColor(0xffffff); g.fillRect(x + tp - iw / 2 - 5, (y - 10) / 2, iw + 10, 10);
          g.setColor(0x7f7f7f); g.fillRect(x + tp - iw / 2 - 4, (y - 9) / 2, iw + 8, 9);   // half black over white
          String s = in;
          if (kind[i] != CONTROLFIELD) s += act && (t / 500) % 2 == 0 ? "_" : " ";
          text480(g, Res.GOLD, s, x + tp, y, 2);
          text480(g, font, text[i], x - (iw + 8) / 2, y, shadow);
          break;
        }
        case STRINGSELECT: {
          int lp2 = lw + 8, tp = (tw + 8) / 2;
          // the arrows sit right against the value box (the 320x240 build's halved x put them by the label)
          int bx = x + tp - lp2 / 2 - 1;
          Res.drawSpriteTL(g, Spr.IC_LEFT, bx - 9, (y - 8) / 2);
          Res.drawSpriteTL(g, Spr.IC_RIGHT, bx + lp2 + 3, (y - 8) / 2);
          g.setColor(0xffffff); g.fillRect(bx, (y - 10) / 2, lp2 + 2, 10);
          g.setColor(0x7f7f7f); g.fillRect(x + tp - lp2 / 2, (y - 9) / 2, lp2, 9);         // half black over white
          text480(g, Res.GOLD, lt, x + tp, y, 2);
          text480(g, font, text[i], x - lp2 / 2, y, shadow);
          break;
        }
        case BACK:
          text480(g, font, text[i], x, y, shadow);
          // Surface::draw halves x as well as y in the 320x240 build, so the arrow and the check
          // boxes land left of the text; placed the same way relative to the centre in portrait
          Res.drawSpriteTL(g, Spr.MENU_BACK, (160 + x - cx + tw / 2 + 8) / 2 - 160 + cx, (y - 8) / 2);
          break;
        case TOGGLE:
          text480(g, font, text[i], x, y, shadow);
          Res.drawSpriteTL(g, toggled[i] ? Spr.CHECKBOX_CHECKED : Spr.CHECKBOX, (160 + x - cx + (tw + 8) / 2) / 2 - 160 + cx, (y - 8) / 2);
          break;
        default: text480(g, font, text[i], x, y, shadow);
      }
    }
    if (count > 0 && kind[active] == TEXTFIELD)
      Res.chars(g, Res.WHITE, caps ? "ABC   * switches case" : "abc   * switches case", 5, sh - 15);
  }

  // Text::draw_align(A_HMIDDLE, A_VMIDDLE) with y in 480-line units
  static void text480(Graphics g, int font, String s, int cx, int y, int shadow) {
    int x = cx - (s.length() * 8) / 2;
    y -= 4;
    if (shadow != 0) Res.chars(g, Res.SHADOW, s, x + shadow, (y + shadow) / 2);
    Res.chars(g, font, s, x, y / 2);
  }
}
