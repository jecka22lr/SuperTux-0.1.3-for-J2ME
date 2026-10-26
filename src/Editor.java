// The level editor (leveleditor.cpp) as Jecka's Symbian build has it: the level on the left, the
// grey button bar on the right, a pointer moved with the keypad (centre clicks, hold to paint,
// # is the right button), level subsets, level settings, bad guys and testing the level.
// Editor coordinates are 0.1.3's 640x480 units, drawn at half size. Levels are saved on the
// phone (RMS); a saved built-in level replaces the original, like st_dir/levels did.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.rms.RecordStore;

final class Editor {
  static final int SM_CURSOR = 0, SM_SQUARE = 1;
  static final String[] KIND_NAMES = { "mriceblock", "jumpy", "mrbomb", "stalactite", "flame", "fish",
      "bouncingsnowball", "flyingsnowball", "spiky", "snowball", "bomb" };
  // the built-in level subsets and their level ids (a1, b1, c1...); the community packs are added
  // from x/g.txt (Game.loadWorlds) and also listed under Bonus Levels
  static String[] BUILTIN = { "world1", "bonus1", "bonus2" };
  static String BUILTIN_LETTERS = "abc";
  static String[] PACK_DIRS = new String[0], PACK_TITLES = new String[0], PACK_AUTHORS = new String[0],
      PACK_STORY = new String[0];

  static void setPacks(String[] dirs, String letters, String[] titles, String[] authors, String[] story) {
    PACK_DIRS = dirs; PACK_TITLES = titles; PACK_AUTHORS = authors; PACK_STORY = story;
    String[] b = new String[3 + dirs.length];
    b[0] = "world1"; b[1] = "bonus1"; b[2] = "bonus2";
    System.arraycopy(dirs, 0, b, 3, dirs.length);
    BUILTIN = b; BUILTIN_LETTERS = "abc" + letters;
  }

  final Game game;
  // subset and level
  String subset;                 // null: nothing loaded
  int levelNo, levelCount;
  Level lv;
  Vector bad = new Vector();     // int[] { kind, x, y, stay }
  boolean changed;
  long levelInfoEnd;
  // view and pointer
  int posX, cursorX, cursorY;    // units
  int vmX = 144, vmY = 120;      // pointer, screen pixels
  int vmScroll, vmScrollDir; boolean scrolling;
  boolean grid = true, help;
  int selMode = SM_CURSOR;
  int selX1, selY1, selX2, selY2;
  int activeTm = 1;              // 0 background, 1 interactive, 2 foreground
  boolean curIsTile = true; int curTile, curKind;
  int curGroup = -1; boolean curObjects;
  boolean leftHeld, rightHeld, selectObject;
  int selected = -1;             // bad guy index
  int pressedButton = -1, hoverButton = -1; long hoverStart; boolean showInfo;
  long repeatAt; int repeatBits;
  boolean swallow;
  // tables
  static String[] groupNames; static int[][] groupTiles; static short[] editorFrame;
  static String[] songNames, bgNames; static int[] songNo, bgNo;
  Image ghost;                   // the tile atlas at half alpha (layers that aren't being edited)
  boolean ghostTried;
  Image minimap; boolean minimapDirty = true; int[] tileColor;
  Image bgImg; int bgNo2 = -2;
  // menus
  Menu menu, loadMenu, newMenu, subsetMenu, settingsMenu, groupMenu, objectsMenu, propsMenu, confirm;
  long groupEffect, objectsEffect;
  int confirmWhat, confirmArg;
  static final int C_GOTO = 1, C_QUIT = 2, C_CREATE = 3;
  static final int M_RETURN = 1, M_SUBSET = 2, M_QUIT = 3, M_NAME = 10, M_CREATE = 11, M_TITLE = 12, M_DESC = 13,
      M_SAVE = 14, M_APPLY = 20, M_LNAME = 21, M_AUTHOR = 22, M_SONG = 23, M_BG = 24, M_PART = 25, M_LEN = 26,
      M_TIME = 27, M_GRAV = 28, M_BGSPEED = 29, M_TR = 30, M_TG = 31, M_TB = 32, M_BR = 33, M_BGR = 34, M_BB = 35,
      M_KIND = 40, M_STAY = 41, M_OK = 42;

  Editor(Game g) {
    game = g;
    loadTables();
    menu = new Menu(); loadMenu = new Menu(); newMenu = new Menu(); subsetMenu = new Menu();
    settingsMenu = new Menu(); groupMenu = new Menu(); objectsMenu = new Menu(); propsMenu = new Menu(); confirm = new Menu();
    menu.add(Menu.LABEL, "Level Editor Menu", 0).add(Menu.HL, "", 0)
        .add(Menu.ACTION, "Return To Level Editor", M_RETURN)
        .add(Menu.DEACTIVE, "Level Subset Settings", M_SUBSET, subsetMenu, false)
        .add(Menu.GOTO, "Load Level Subset", 0, loadMenu, false)
        .add(Menu.GOTO, "New Level Subset", 0, newMenu, false)
        .add(Menu.HL, "", 0).add(Menu.ACTION, "Quit Level Editor", M_QUIT);
    newMenu.add(Menu.LABEL, "New Level Subset", 0).add(Menu.HL, "", 0)
        .add(Menu.TEXTFIELD, "Enter Name", M_NAME).add(Menu.DEACTIVE, "Create", M_CREATE)
        .add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
    subsetMenu.add(Menu.LABEL, "Level Subset Settings", 0).add(Menu.HL, "", 0)
        .add(Menu.TEXTFIELD, "Title", M_TITLE).add(Menu.TEXTFIELD, "Description", M_DESC)
        .add(Menu.HL, "", 0).add(Menu.DEACTIVE, "Save Changes", M_SAVE).add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
    settingsMenu.add(Menu.LABEL, "Level Settings", 0).add(Menu.HL, "", 0)
        .add(Menu.TEXTFIELD, "Name    ", M_LNAME).add(Menu.TEXTFIELD, "Author  ", M_AUTHOR)
        .add(Menu.STRINGSELECT, "Song    ", M_SONG).add(Menu.STRINGSELECT, "Bg-Image", M_BG)
        .add(Menu.STRINGSELECT, "Particle", M_PART).add(Menu.NUMFIELD, "Length  ", M_LEN)
        .add(Menu.NUMFIELD, "Time    ", M_TIME).add(Menu.NUMFIELD, "Gravity ", M_GRAV)
        .add(Menu.NUMFIELD, "Bg-Img-Speed", M_BGSPEED).add(Menu.NUMFIELD, "Top Red     ", M_TR)
        .add(Menu.NUMFIELD, "Top Green   ", M_TG).add(Menu.NUMFIELD, "Top Blue    ", M_TB)
        .add(Menu.NUMFIELD, "Bottom Red  ", M_BR).add(Menu.NUMFIELD, "Bottom Green", M_BGR)
        .add(Menu.NUMFIELD, "Bottom Blue ", M_BB).add(Menu.HL, "", 0).add(Menu.ACTION, "Apply Changes", M_APPLY);
    groupMenu.add(Menu.LABEL, "Tilegroup", 0).add(Menu.HL, "", 0);
    for (int i = 0; i < groupNames.length; i++) groupMenu.add(Menu.ACTION, groupNames[i], i + 1);
    groupMenu.add(Menu.HL, "", 0);
    objectsMenu.arrangeLeft = true;
    objectsMenu.add(Menu.LABEL, "Objects", 0).add(Menu.HL, "", 0).add(Menu.ACTION, "BadGuys", 1).add(Menu.HL, "", 0);
  }

  // ---------------- data ----------------
  void loadTables() {
    if (groupNames != null) return;
    try {
      DataInputStream in = Res.open("/te.bin");
      int n = in.readUnsignedShort();
      editorFrame = new short[n];
      for (int i = 0; i < n; i++) editorFrame[i] = in.readShort();
      int g = in.readUnsignedByte();
      groupNames = new String[g]; groupTiles = new int[g][];
      for (int i = 0; i < g; i++) {
        groupNames[i] = Res.readString(in);
        groupTiles[i] = new int[in.readUnsignedByte()];
        for (int j = 0; j < groupTiles[i].length; j++) groupTiles[i][j] = in.readUnsignedByte();
      }
      in.close();
    } catch (Exception e) { groupNames = new String[0]; groupTiles = new int[0][]; editorFrame = new short[0]; }
    String[] s = Res.lines("/m/n.txt"), b = Res.lines("/b/n.txt");
    songNames = new String[s.length]; songNo = new int[s.length];
    for (int i = 0; i < s.length; i++) { int sp = s[i].indexOf(' '); songNo[i] = Integer.parseInt(s[i].substring(0, sp)); songNames[i] = s[i].substring(sp + 1); }
    bgNames = new String[b.length + 1]; bgNo = new int[b.length + 1];
    for (int i = 0; i < b.length; i++) { int sp = b[i].indexOf(' '); bgNo[i] = Integer.parseInt(b[i].substring(0, sp)); bgNames[i] = b[i].substring(sp + 1); }
    bgNames[b.length] = ""; bgNo[b.length] = 255;
  }

  // level subsets: the three built-in ones and the ones made here ("subsets" lists them)
  static String[] subsets() {
    byte[] b = WorldMap.read("subsets");
    if (b == null) return new String[0];
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
      String[] r = new String[in.readShort()];
      for (int i = 0; i < r.length; i++) r[i] = in.readUTF();
      return r;
    } catch (Exception e) { return new String[0]; }
  }

  static void writeSubsets(String[] l) {
    try {
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bo);
      out.writeShort(l.length);
      for (int i = 0; i < l.length; i++) out.writeUTF(l[i]);
      out.close();
      store("subsets", bo.toByteArray());
    } catch (Exception e) { }
  }

  static void store(String name, byte[] b) throws Exception {
    RecordStore rs = RecordStore.openRecordStore(name, true);
    if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length); else rs.addRecord(b, 0, b.length);
    rs.closeRecordStore();
  }

  static int indexOf(String[] a, String s) {
    for (int i = 0; i < a.length; i++) if (a[i].equals(s)) return i;
    return -1;
  }

  static String levelId(String sub, int n) {
    int b = indexOf(BUILTIN, sub);
    if (b >= 0) return BUILTIN_LETTERS.charAt(b) + "" + n;
    return "u" + indexOf(subsets(), sub) + "_" + n;
  }

  static int subsetLevels(String sub) {
    int n = 0;
    while (n < 500 && Level.exists(levelId(sub, n + 1))) n++;
    return n;
  }

  // the info file: title and description
  static String[] info(String sub) {
    byte[] b = WorldMap.read("S" + sub);
    if (b != null) {
      try {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
        return new String[] { in.readUTF(), in.readUTF() };
      } catch (Exception e) { }
    }
    int p = indexOf(PACK_DIRS, sub);
    if (p >= 0) return new String[] { PACK_TITLES[p], "by " + PACK_AUTHORS[p] };
    return new String[] { sub, "" };
  }

  static String subsetTitle(String sub) { return info(sub)[0]; }

  static String levelName(String id) {
    try { return Level.load(id).name; } catch (Exception e) { return id; }
  }

  // ---------------- opening and levels ----------------
  void open() {
    Menu.setCurrent(subset == null ? menu : null, game.now);
    leftHeld = rightHeld = false;
    help = false;
  }

  void loadSubset(String sub) {
    subset = sub;
    menu.kind[menu.find(M_SUBSET)] = Menu.GOTO;
    levelNo = 1; levelCount = subsetLevels(sub);
    lv = null; changed = false;
    gotoLevel(1);
    Menu.setCurrent(null, game.now);
  }

  void gotoLevel(int n) {
    try {
      lv = Level.load(levelId(subset, n));
    } catch (Exception e) { lv = new Level(); }
    levelNo = n;
    bad.removeAllElements();
    for (int i = 0; i < lv.objKind.length; i++) bad.addElement(new int[] { lv.objKind[i], lv.objX[i] & 0xffff, lv.objY[i], lv.objStay[i] });
    changed = false; selected = -1;
    levelInfoEnd = System.currentTimeMillis() + 2500;
    minimapDirty = true;
    posX = 0;
  }

  // le_unload_level: ask first when there are changes, then do the action
  void leave(int what, int arg) {
    if (lv != null && changed) {
      confirmWhat = what; confirmArg = arg;
      askConfirm("Save changes to level " + levelNo + "?");
      return;
    }
    doLeave(what, arg);
  }

  void doLeave(int what, int arg) {
    changed = false;
    if (what == C_GOTO) gotoLevel(arg);
    else if (what == C_QUIT) { Menu.setCurrent(null, game.now); game.leaveEditor(); }
  }

  void askConfirm(String text) {
    confirm.clear();
    String[] l = Game.wrap(text, game.sw / 8 - 2);
    for (int i = 0; i < l.length; i++) confirm.add(Menu.DEACTIVE, l[i], 0);
    confirm.add(Menu.HL, "", 0).add(Menu.ACTION, "Yes", 1).add(Menu.ACTION, "No", 2).add(Menu.HL, "", 0);
    Menu.setCurrent(confirm, game.now);
  }

  void writeBadGuys() {
    int n = bad.size();
    lv.objKind = new byte[n]; lv.objStay = new byte[n]; lv.objX = new short[n]; lv.objY = new short[n];
    for (int i = 0; i < n; i++) {
      int[] b = (int[]) bad.elementAt(i);
      lv.objKind[i] = (byte) b[0]; lv.objX[i] = (short) b[1]; lv.objY[i] = (short) b[2]; lv.objStay[i] = (byte) b[3];
    }
  }

  void save() {
    if (lv == null) return;
    writeBadGuys();
    try { lv.save(levelId(subset, levelNo)); changed = false; } catch (Exception e) { game.message = "Saving: " + e; }
  }

  // ---------------- input ----------------
  void frame(long dt, int ev) {
    int held = game.currentKeys();
    if (help) { if (ev != 0) help = false; return; }
    Menu m = Menu.current;
    if (m != null) { menus(m, ev); if (Menu.current == null) swallow = true; return; }
    if (lv == null) { Menu.setCurrent(menu, game.now); return; }

    // 1 / 3 (run keys): smooth scroll that speeds up while held and stops on a tile edge
    scrolling = false;
    int sx = (held & Game.K_FIRE) != 0 ? ((held & Game.K_RIGHT) != 0 ? 1 : 0) - ((held & Game.K_LEFT) != 0 ? 1 : 0) : 0;
    if (sx != 0) {
      if (vmScrollDir != sx) vmScroll = 4; else if (vmScroll < 32) vmScroll += 2;
      vmScrollDir = sx;
      posX += sx * vmScroll;
      scrolling = true;
      clampPos();
      motion(0, 0);
    } else if (vmScrollDir != 0) {
      posX = vmScrollDir > 0 ? ((posX + 31) / 32) * 32 : (posX / 32) * 32;
      vmScrollDir = 0;
      clampPos();
    }

    if ((ev & Game.K_SOFT) != 0) { Menu.setCurrent(menu, game.now); return; }
    if ((ev & Game.K_STAR) != 0) { help = true; return; }
    if ((ev & Game.K_FIRE) != 0 && (ev & (Game.K_LEFT | Game.K_RIGHT)) == 0) grid = !grid;
    // pointer: a step per press, repeating while held (like Symbian's key repeat)
    int dirs = ev & (Game.K_LEFT | Game.K_RIGHT | Game.K_UP | Game.K_DOWN);
    if ((ev & Game.K_FIRE) != 0) dirs = 0;
    long t = System.currentTimeMillis();
    if (dirs != 0) { step(dirs); repeatBits = dirs; repeatAt = t + 300; }
    else if (repeatBits != 0) {
      if ((held & Game.K_FIRE) != 0 || (held & repeatBits) == 0) repeatBits = 0;
      else if (t >= repeatAt) { step(repeatBits & held); repeatAt = t + 80; }
    }
    // centre key = left button, # = right button
    boolean left = (held & Game.K_SELECT) != 0 || (ev & Game.K_SELECT) != 0;
    // the select press that closed a menu isn't a click (Apply Changes reopened the settings)
    if (swallow) { if (left) { leftHeld = false; return; } swallow = false; }
    boolean right = (held & Game.K_POUND) != 0;
    if (left && !leftHeld) buttonDown();
    if (!left && leftHeld) buttonUp();
    if (right && !rightHeld && vmX >= px()) { int b = buttonAt(vmX, vmY); if (b >= 0) { hoverButton = b; showInfo = true; } }
    leftHeld = left; rightHeld = right;
    if (game.touchKeys != 0 && vmX < px()) {     // finger near the level's edge: scroll that way
      int old = posX;
      if (vmX < 10) posX -= 16; else if (vmX >= px() - 10) posX += 16;
      if (posX != old) { clampPos(); motion(0, 0); scrolling = true; }
    }
    if (leftHeld && vmX < px() && !selectObject && curIsTile) change();
  }

  // the button bar: 48 pixels with readable labels in the game's font (the Symbian build's 16x8 icons
  // have 3 pixel text, unreadable at 240 lines), the picture icons kept
  static final int BAR = 64;
  int px() { return game.sw - BAR; }

  // touch: the pointer goes where the finger is (Game holds the select key while it's down);
  // true when the tap only closes the help
  boolean touch(int type, int x, int y) {
    if (help) return type == Game.T_PRESS;
    int ox = vmX, oy = vmY;
    vmX = Math.max(0, Math.min(game.sw - 1, x)); vmY = Math.max(0, Math.min(239, y));
    if (lv != null) {
      motion(vmX - ox, vmY - oy);
    }
    return false;
  }

  void clampPos() {
    int max = lv.width * 32 + BAR * 2 - game.sw * 2;
    if (posX > max) posX = max;
    if (posX < 0) posX = 0;
  }

  // le_vmouse_step: over the level a tile at a time, over the button bar half a button
  void step(int dirs) {
    int dx = ((dirs & Game.K_RIGHT) != 0 ? 1 : 0) - ((dirs & Game.K_LEFT) != 0 ? 1 : 0);
    int dy = ((dirs & Game.K_DOWN) != 0 ? 1 : 0) - ((dirs & Game.K_UP) != 0 ? 1 : 0);
    int ox = vmX, oy = vmY, oldPos = posX;
    int panel = px();
    if (vmX < panel) {
      int col = (vmX * 2 + posX) / 32 + dx;
      int row = vmY / 16 + dy;
      if (row < 0) row = 0;
      if (row > 14) row = 14;
      vmY = row * 16 + 2;
      int x = (col * 32 - posX) / 2 + 2;
      if (x < 0) {
        posX = col * 32;
        if (posX < 0) posX = 0;
        x = (col * 32 - posX) / 2 + 2;
      }
      if (x >= panel) {
        // past the right edge: scroll the level a tile; at the level's end, into the button bar
        int max = lv.width * 32 + BAR * 2 - game.sw * 2;
        if (dx > 0 && posX < max) { posX = Math.min(max, posX + 32); x = (col * 32 - posX) / 2 + 2; }
        if (x >= panel) x = panel + 8;
      }
      vmX = x;
    } else if (dy != 0 && dx == 0) {
      // over the bar: jump to the next button above or below in this column
      int best = -1, by = 0;
      for (int b = 0; b < B_PANEL + 40; b = b == B_FG ? B_PANEL : b + 1) {
        int[] r = rect(b);
        if (r == null || vmX < r[0] || vmX >= r[0] + r[2]) continue;
        int cy = r[1] + r[3] / 2;
        if ((dy > 0 && cy > vmY && (best < 0 || cy < by)) || (dy < 0 && cy < vmY && (best < 0 || cy > by))) { best = b; by = cy; }
      }
      if (best >= 0) vmY = by; else vmY += dy * 8;
    } else {
      int x = vmX + dx * 16;
      if (x < panel) {
        int col = ((panel - 1) * 2 + posX) / 32;
        x = (col * 32 - posX) / 2 + 2;
        vmY = (vmY / 16) * 16 + 2;
      }
      if (x > game.sw - 1) x = game.sw - 1;
      vmX = x;
      vmY += dy * 8;
    }
    if (vmY < 0) vmY = 0;
    if (vmY > 239) vmY = 239;
    clampPos();
    if (rightHeld && vmX < panel) posX = oldPos - (vmX - ox) * 2;     // # held: drag the level
    clampPos();
    motion(vmX - ox, vmY - oy);
  }

  // SDL_MOUSEMOTION
  void motion(int dx, int dy) {
    if (vmX < px()) {
      if (curIsTile) { cursorX = ((posX + vmX * 2) / 32) * 32; cursorY = (vmY * 2 / 32) * 32; }
      else { cursorX = vmX * 2; cursorY = vmY * 2; }
      if (leftHeld) { selX2 = vmX * 2 + posX; selY2 = vmY * 2; }
    }
    int b = vmX >= px() ? buttonAt(vmX, vmY) : -1;
    if (b != hoverButton) { hoverButton = b; hoverStart = System.currentTimeMillis(); showInfo = false; }
  }

  void buttonDown() {
    if (vmX < px()) {
      selX1 = selX2 = vmX * 2 + posX; selY1 = selY2 = vmY * 2;
      pressedButton = -1;
    } else pressedButton = buttonAt(vmX, vmY);
  }

  void buttonUp() {
    leftHeld = false;
    if (vmX < px()) { click(); return; }
    int b = buttonAt(vmX, vmY);
    if (b >= 0 && b == pressedButton) pressButton(b);
    pressedButton = -1;
  }

  // a click on the level (le_mouse_clicked[LEFT])
  void click() {
    if (selectObject) {
      int cx = cursorX + (curIsTile ? 0 : posX), cy = cursorY;
      selected = -1;
      for (int i = 0; i < bad.size(); i++) {
        int[] b = (int[]) bad.elementAt(i);
        if (cx < b[1] + 32 && cx + 32 > b[1] && cy < b[2] + 32 && cy + 32 > b[2]) { selected = i; break; }
      }
      selectObject = false;
    } else if (!curIsTile) {
      bad.addElement(new int[] { curKind, cursorX + posX, cursorY, 0 });
      changed = true; minimapDirty = true;
    }
  }

  // le_change
  void change() {
    changed = true; minimapDirty = true;
    if (selMode == SM_CURSOR) {
      lv.set(activeTm, cursorX, cursorY, curTile);
      for (int i = 0; i < bad.size(); i++) {
        int[] b = (int[]) bad.elementAt(i);
        if (cursorX < b[1] + 32 && cursorX + 32 > b[1] && cursorY < b[2] + 32 && cursorY + 32 > b[2]) { bad.removeElementAt(i); break; }
      }
    } else {
      int x1 = Math.min(selX1, selX2) / 32, x2 = Math.max(selX1, selX2) / 32;
      int y1 = Math.min(selY1, selY2) / 32, y2 = Math.max(selY1, selY2) / 32;
      for (int i = bad.size() - 1; i >= 0; i--) {
        int[] b = (int[]) bad.elementAt(i);
        if (b[1] / 32 >= x1 && b[1] / 32 <= x2 && b[2] / 32 >= y1 && b[2] / 32 <= y2) bad.removeElementAt(i);
      }
      for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) lv.set(activeTm, x * 32, y * 32, curTile);
    }
  }

  // ---------------- the button bar ----------------
  static final int B_UP = 0, B_DOWN = 1, B_SAVE = 2, B_EXIT = 3, B_MODE = 4, B_RUBBER = 5, B_GROUP = 6, B_OBJECTS = 7,
      B_SELECTONE = 8, B_PROPS = 9, B_TEST = 10, B_SETTINGS = 11, B_BG = 12, B_IA = 13, B_FG = 14, B_PANEL = 100;
  static final String[] INFO = { "Next level", "Previous level", "Save level", "Exit", "Select tiles", "Rubber",
      "Select Tilegroup", "Select Objects", "Select an Object", "Edit object properties", "Test level",
      "Level settings", "Background", "Interactive", "Foreground" };

  // button rectangle in screen pixels: x, y, w, h (null when hidden)
  // 0.1.3's own bar, 64 pixels wide with its full-size icons (the half-size Symbian ones are
  // unreadable at 240 pixels): x, y, w, h in screen pixels, null when hidden
  int[] rect(int b) {
    int p = px();
    switch (b) {
      case B_UP: return new int[] { p, 10, 32, 16 };
      case B_DOWN: return new int[] { p + 32, 10, 32, 16 };
      case B_SAVE: return new int[] { p, 26, 32, 16 };
      case B_EXIT: return new int[] { p + 32, 26, 32, 16 };
      case B_MODE: return new int[] { p, 42, 32, 16 };
      case B_RUBBER: return new int[] { p + 32, 42, 32, 16 };
      case B_GROUP: return new int[] { p, 58, 64, 16 };
      case B_OBJECTS: return new int[] { p, 74, 64, 16 };
      case B_SELECTONE: return curObjects ? new int[] { p, 138, 32, 32 } : null;
      case B_PROPS: return curObjects ? new int[] { p + 32, 138, 32, 32 } : null;
      case B_TEST: return new int[] { p, 170, 32, 32 };
      case B_SETTINGS: return new int[] { p + 32, 170, 32, 32 };
      case B_BG: return new int[] { p, 204, 32, 10 };
      case B_IA: return new int[] { p, 216, 32, 10 };
      case B_FG: return new int[] { p, 228, 32, 10 };
    }
    int i = b - B_PANEL;
    int n = curGroup >= 0 ? groupTiles[curGroup].length : curObjects ? KIND_NAMES.length : 0;
    int bottom = curObjects ? 138 : 170;
    if (i < 0 || i >= n || 90 + (i / 4) * 16 + 16 > bottom) return null;
    return new int[] { p + (i % 4) * 16, 90 + (i / 4) * 16, 16, 16 };
  }

  int buttonAt(int x, int y) {
    for (int b = 0; b <= B_FG; b++) { int[] r = rect(b); if (r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) return b; }
    for (int b = B_PANEL; b < B_PANEL + 40; b++) { int[] r = rect(b); if (r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) return b; }
    return -1;
  }

  void pressButton(int b) {
    long now = game.now;
    switch (b) {
      case B_TEST: writeBadGuys(); game.playTest(lv); break;
      case B_SAVE: save(); break;
      case B_EXIT: Menu.setCurrent(menu, now); break;
      case B_UP:
        if (levelNo < levelCount) leave(C_GOTO, levelNo + 1);
        else { confirmWhat = C_CREATE; confirmArg = levelNo + 1; askConfirm("Level " + (levelNo + 1) + " doesn't exist. Create it?"); }
        break;
      case B_DOWN: if (levelNo > 1) leave(C_GOTO, levelNo - 1); break;
      case B_RUBBER: curIsTile = true; curTile = 0; break;
      case B_MODE: selMode = selMode == SM_CURSOR ? SM_SQUARE : SM_CURSOR; break;
      case B_SELECTONE: selectObject = true; break;
      case B_PROPS: if (selected >= 0) openProps(); break;
      case B_GROUP:
        Menu.setCurrent(groupMenu, now); groupEffect = System.currentTimeMillis() + 200; break;
      case B_OBJECTS:
        Menu.setCurrent(objectsMenu, now); objectsEffect = System.currentTimeMillis() + 200; break;
      case B_SETTINGS: updateSettings(); Menu.setCurrent(settingsMenu, now); break;
      case B_BG: activeTm = 0; break;
      case B_IA: activeTm = 1; break;
      case B_FG: activeTm = 2; break;
      default:
        int i = b - B_PANEL;
        if (curGroup >= 0) { curIsTile = true; curTile = groupTiles[curGroup][i]; }
        else if (curObjects) { curIsTile = false; curKind = i; }
        motion(0, 0);
    }
  }

  // ---------------- menus ----------------
  void menus(Menu m, int ev) {
    long now = game.now;
    if ((ev & Game.K_ESC) != 0 && (ev & (Game.K_STAR | Game.K_POUND)) == 0 || (ev & Game.K_SOFT) != 0) {
      if (m == confirm) { answer(false); return; }
      Menu.pop(now);
      if (Menu.current == null && lv == null) Menu.setCurrent(menu, now);
      return;
    }
    m.action(Game.menuAction(ev), now);
    if (m == menu) {
      switch (menu.check()) {
        case M_RETURN: Menu.setCurrent(lv != null ? null : menu, now); break;
        case M_SUBSET: { String[] in = info(subset); subsetMenu.setInput(M_TITLE, in[0]); subsetMenu.setInput(M_DESC, in[1]); break; }
        case M_QUIT: if (lv != null) leave(C_QUIT, 0); else doLeave(C_QUIT, 0); break;
      }
      if (Menu.current == loadMenu) fillLoadMenu();
    } else if (m == loadMenu) {
      int i = loadMenu.check();
      if (i >= 1) loadSubset(loadMenu.text[loadMenu.find(i)]);
    } else if (m == newMenu) {
      String name = newMenu.inputOf(M_NAME);
      newMenu.kind[newMenu.find(M_CREATE)] = name.length() == 0 ? Menu.DEACTIVE : Menu.ACTION;
      if (newMenu.check() == M_CREATE) createSubset(name);
    } else if (m == subsetMenu) {
      String[] in = info(subset);
      boolean same = in[0].equals(subsetMenu.inputOf(M_TITLE)) && in[1].equals(subsetMenu.inputOf(M_DESC));
      subsetMenu.kind[subsetMenu.find(M_SAVE)] = same ? Menu.DEACTIVE : Menu.ACTION;
      if (subsetMenu.check() == M_SAVE) {
        saveInfo(subset, subsetMenu.inputOf(M_TITLE), subsetMenu.inputOf(M_DESC));
        Menu.setCurrent(menu, now);
      }
    } else if (m == settingsMenu) {
      if (settingsMenu.check() == M_APPLY) { applySettings(); Menu.setCurrent(null, now); }
    } else if (m == groupMenu) {
      int i = groupMenu.check();
      if (i >= 1) { curGroup = i - 1; curObjects = false; Menu.setCurrent(null, now); }
    } else if (m == objectsMenu) {
      if (objectsMenu.check() == 1) { curObjects = true; curGroup = -1; Menu.setCurrent(null, now); }
    } else if (m == propsMenu) {
      if (propsMenu.check() == M_OK && selected >= 0) {
        int[] b = (int[]) bad.elementAt(selected);
        b[0] = propsMenu.listOf(M_KIND); b[3] = propsMenu.isToggled(M_STAY) ? 1 : 0;
        changed = true;
        Menu.setCurrent(null, now);
      }
    } else if (m == confirm) {
      int c = confirm.check();
      if (c == 1 || c == 2) answer(c == 1);
    }
  }

  void answer(boolean yes) {
    Menu.setCurrent(null, game.now);
    if (confirmWhat == C_CREATE) {
      if (yes) {
        try { new Level().save(levelId(subset, confirmArg)); levelCount = Math.max(levelCount, confirmArg); } catch (Exception e) { }
        leave(C_GOTO, confirmArg);
      }
      return;
    }
    if (yes) save();
    doLeave(confirmWhat, confirmArg);
  }

  void fillLoadMenu() {
    loadMenu.clear();
    loadMenu.add(Menu.LABEL, "Load Level Subset", 0).add(Menu.HL, "", 0);
    String[] c = subsets();
    for (int i = 0; i < BUILTIN.length; i++) loadMenu.add(Menu.ACTION, BUILTIN[i], i + 1);
    for (int i = 0; i < c.length; i++) loadMenu.add(Menu.ACTION, c[i], BUILTIN.length + i + 1);
    loadMenu.add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
  }

  // LevelSubset::create
  void createSubset(String name) {
    String[] c = subsets();
    if (indexOf(c, name) < 0 && indexOf(BUILTIN, name) < 0) {
      String[] n = new String[c.length + 1];
      System.arraycopy(c, 0, n, 0, c.length);
      n[c.length] = name;
      writeSubsets(n);
      saveInfo(name, "Unknown Title", "No description so far.");
      try { new Level().save(levelId(name, 1)); } catch (Exception e) { }
    }
    loadSubset(name);
    newMenu.setInput(M_NAME, "");
    subsetMenu.setInput(M_TITLE, subsetTitle(name)); subsetMenu.setInput(M_DESC, info(name)[1]);
    Menu.setCurrent(subsetMenu, game.now);
  }

  static void saveInfo(String sub, String title, String desc) {
    try {
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bo);
      out.writeUTF(title); out.writeUTF(desc);
      out.close();
      store("S" + sub, bo.toByteArray());
    } catch (Exception e) { }
  }

  // update_level_settings_menu
  void updateSettings() {
    settingsMenu.setInput(M_LNAME, lv.name); settingsMenu.setInput(M_AUTHOR, lv.author);
    int s = 0; for (int i = 0; i < songNo.length; i++) if (songNo[i] == lv.music) s = i;
    settingsMenu.setList(M_SONG, songNames, s);
    int b = bgNames.length - 1; for (int i = 0; i < bgNo.length; i++) if (bgNo[i] == lv.background) b = i;
    settingsMenu.setList(M_BG, bgNames, b);
    settingsMenu.setList(M_PART, new String[] { "", "clouds", "snow" }, lv.particles);
    settingsMenu.setInput(M_LEN, "" + lv.width); settingsMenu.setInput(M_TIME, "" + lv.time);
    settingsMenu.setInput(M_GRAV, "" + (int) lv.gravity); settingsMenu.setInput(M_BGSPEED, "" + lv.bkgdSpeed);
    settingsMenu.setInput(M_TR, "" + (lv.topColor >> 16)); settingsMenu.setInput(M_TG, "" + ((lv.topColor >> 8) & 255));
    settingsMenu.setInput(M_TB, "" + (lv.topColor & 255)); settingsMenu.setInput(M_BR, "" + (lv.bottomColor >> 16));
    settingsMenu.setInput(M_BGR, "" + ((lv.bottomColor >> 8) & 255)); settingsMenu.setInput(M_BB, "" + (lv.bottomColor & 255));
  }

  static int num(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }
  static int c255(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }

  // apply_level_settings_menu
  void applySettings() {
    changed = true; minimapDirty = true;
    lv.name = settingsMenu.inputOf(M_LNAME); lv.author = settingsMenu.inputOf(M_AUTHOR);
    lv.music = songNo[settingsMenu.listOf(M_SONG)];
    lv.background = bgNo[settingsMenu.listOf(M_BG)];
    lv.particles = settingsMenu.listOf(M_PART);
    lv.changeSize(num(settingsMenu.inputOf(M_LEN), lv.width));
    lv.time = num(settingsMenu.inputOf(M_TIME), lv.time);
    lv.gravity = num(settingsMenu.inputOf(M_GRAV), (int) lv.gravity);
    lv.bkgdSpeed = c255(num(settingsMenu.inputOf(M_BGSPEED), lv.bkgdSpeed));
    lv.topColor = (c255(num(settingsMenu.inputOf(M_TR), 0)) << 16) | (c255(num(settingsMenu.inputOf(M_TG), 0)) << 8) | c255(num(settingsMenu.inputOf(M_TB), 0));
    lv.bottomColor = (c255(num(settingsMenu.inputOf(M_BR), 0)) << 16) | (c255(num(settingsMenu.inputOf(M_BGR), 0)) << 8) | c255(num(settingsMenu.inputOf(M_BB), 0));
    clampPos();
  }

  // le_change_object_properties
  void openProps() {
    int[] b = (int[]) bad.elementAt(selected);
    propsMenu.clear();
    propsMenu.add(Menu.LABEL, "BadGuy Properties", 0).add(Menu.HL, "", 0)
        .add(Menu.STRINGSELECT, "Kind", M_KIND).add(Menu.TOGGLE, "StayOnPlatform", M_STAY, null, b[3] != 0)
        .add(Menu.HL, "", 0).add(Menu.ACTION, "Ok", M_OK);
    propsMenu.setList(M_KIND, KIND_NAMES, b[0]);
    Menu.setCurrent(propsMenu, game.now);
  }

  // ---------------- drawing ----------------
  static int badSprite(int k) {
    switch (k) {
      case BadGuy.MRICEBLOCK: return Spr.MRICEBLOCK_LEFT;
      case BadGuy.JUMPY: return Spr.JUMPY_LEFT_UP;
      case BadGuy.MRBOMB: return Spr.MRBOMB_LEFT;
      case BadGuy.STALACTITE: return Spr.STALACTITE;
      case BadGuy.FLAME: return Spr.FLAME;
      case BadGuy.FISH: return Spr.FISH;
      case BadGuy.BOUNCINGSNOWBALL: return Spr.BOUNCINGSNOWBALL_LEFT;
      case BadGuy.FLYINGSNOWBALL: return Spr.FLYINGSNOWBALL;
      case BadGuy.SPIKY: return Spr.SPIKY_LEFT;
      case BadGuy.BOMB: return Spr.MRBOMB_TICKING_LEFT;
    }
    return Spr.SNOWBALL_LEFT;
  }

  void draw(Graphics g) {
    int sw = game.sw, sh = game.sh, p = px();
    g.setColor(0); g.fillRect(0, 0, sw, sh);
    if (help) { drawHelp(g); return; }
    if (lv != null) drawLevel(g);
    drawInterface(g);
    Menu m = Menu.current;
    if (m != null) {
      long t = System.currentTimeMillis();
      if (m == groupMenu) {
        groupMenu.posX = sw - 64 - groupMenu.width() / 4 + (int) Math.max(0, groupEffect - t);
        groupMenu.posY = groupMenu.posYPortrait = 66 + groupMenu.count * 12;
      } else if (m == objectsMenu) {
        objectsMenu.posX = sw - 64 - objectsMenu.width() / 4 + (int) Math.max(0, objectsEffect - t);
        objectsMenu.posY = objectsMenu.posYPortrait = 82 + objectsMenu.count * 12;
      }
      // landscape: Level Settings sits over the map so its long fields stay clear of the button panel
      m.draw(g, m == settingsMenu && game.landscape ? p / 2 : sw / 2, sh, !game.landscape, game.now);
    }
    // the pointer
    g.setClip(0, 0, sw, sh);
    if (selectObject) g.drawRegion(Res.sprImg, Res.sfx[Res.sFrames[Spr.IC_SELECT_CURSOR][0]], Res.sfy[Res.sFrames[Spr.IC_SELECT_CURSOR][0]],
        32, 32, 0, vmX - 16, vmY - 16, Graphics.TOP | Graphics.LEFT);
    else if (m == null) g.drawRegion(Res.sprImg, Res.sfx[Res.sFrames[Spr.IC_MOUSECURSOR][0]], Res.sfy[Res.sFrames[Spr.IC_MOUSECURSOR][0]],
        16, 16, 0, vmX, vmY, Graphics.TOP | Graphics.LEFT);
  }

  void drawLevel(Graphics g) {
    int p = px();
    g.setClip(0, 0, p, 240);
    // background picture or gradient
    if (lv.background != 255) {
      if (bgNo2 != lv.background) { bgImg = null; try { bgImg = Image.createImage("/b/" + lv.background + ".png"); } catch (Exception e) { } bgNo2 = lv.background; }
    }
    if (lv.background != 255 && bgImg != null) {
      int s = (posX * lv.bkgdSpeed / 100 / 2) % 320;
      g.drawImage(bgImg, -s, 0, Graphics.TOP | Graphics.LEFT);
      g.drawImage(bgImg, 320 - s, 0, Graphics.TOP | Graphics.LEFT);
    } else {
      int a = lv.topColor, b = lv.bottomColor;
      for (int y = 0; y < 240; y += 4) {
        g.setColor(((((a >> 16) * (240 - y) + (b >> 16) * y) / 240) << 16) | (((((a >> 8) & 255) * (240 - y) + ((b >> 8) & 255) * y) / 240) << 8)
            | (((a & 255) * (240 - y) + (b & 255) * y) / 240));
        g.fillRect(0, y, p, 4);
      }
    }
    if (!ghostTried) {
      ghostTried = true;
      try {
        int w = Res.tileImg.getWidth(), h = Res.tileImg.getHeight();
        int[] px = new int[w * h];
        Res.tileImg.getRGB(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = ((px[i] >>> 25) << 24) | (px[i] & 0xffffff);
        ghost = Image.createRGBImage(px, w, h, true);
      } catch (Throwable e) { ghost = null; }
    }
    // the current tile, see-through, under the cursor
    if (curIsTile && !selectObject && curTile != 0)
      Res.drawTileImg(g, ghost != null ? ghost : Res.tileImg, curTile, (cursorX - posX) / 2, cursorY / 2, game.frameCounter);
    int col0 = posX / 32, cols = p / 16 + 2;
    for (int y = 0; y < 15; y++)
      for (int x = col0; x < col0 + cols && x < lv.width; x++) {
        int sx = (x * 32 - posX) / 2, sy = y * 16, i = y * lv.width + x;
        for (int l = 0; l < 3; l++) {
          int t = (l == 0 ? lv.bg : l == 1 ? lv.ia : lv.fg)[i] & 0xff;
          if (t != 0) Res.drawTileImg(g, l == activeTm || ghost == null ? Res.tileImg : ghost, t, sx, sy, game.frameCounter);
        }
        int t = lv.ia[i] & 0xff;
        if (t < editorFrame.length && editorFrame[t] >= 0) {
          int f = editorFrame[t];
          g.drawRegion(Res.tileImg, Res.tfx[f], Res.tfy[f], Res.tfw[f] & 0xff, Res.tfh[f] & 0xff, 0, sx, sy, Graphics.TOP | Graphics.LEFT);
        }
      }
    for (int i = 0; i < bad.size(); i++) {
      int[] b = (int[]) bad.elementAt(i);
      Res.drawSprite(g, badSprite(b[0]), (b[1] - posX) / 2, b[2] / 2, game.now);
    }
    Res.drawSprite(g, Spr.LARGETUX_WALK_RIGHT, (100 - posX) / 2, 120, 0);
    // grid
    if (grid) {
      g.setColor(0xe1e1e1);
      for (int x = 0; x < p / 16 + 1; x++) g.fillRect(x * 16 - (posX % 32) / 2, 0, 1, 240);
      for (int y = 0; y < 15; y++) g.fillRect(0, y * 16, p, 1);
    }
    // selection
    if (!selectObject && Menu.current == null) {
      if (selMode == SM_CURSOR) {
        if (curIsTile) Res.drawSpriteTL(g, Spr.IC_SELECT, (cursorX - posX) / 2, cursorY / 2);
      } else if (leftHeld && vmX < p) {
        int x1 = Math.min(selX1, selX2) / 32, x2 = Math.max(selX1, selX2) / 32;
        int y1 = Math.min(selY1, selY2) / 32, y2 = Math.max(selY1, selY2) / 32;
        Res.fillAlpha(g, (x1 * 32 - posX) / 2, y1 * 16, (x2 - x1 + 1) * 16, (y2 - y1 + 1) * 16, 0x67adeab1);
        int sx = (Math.min(selX1, selX2) - posX) / 2, sy = Math.min(selY1, selY2) / 2;
        int w = Math.abs(selX2 - selX1) / 2, h = Math.abs(selY2 - selY1) / 2;
        g.setColor(0x00ff00);
        g.fillRect(sx, sy, w, 1); g.fillRect(sx + w, sy, 1, h); g.fillRect(sx, sy + h, w, 1); g.fillRect(sx, sy, 1, h);
      }
    }
    if (!curIsTile && !selectObject) Res.drawSprite(g, badSprite(curKind), cursorX / 2, cursorY / 2, game.now);
    if (selected >= 0 && selected < bad.size()) {
      int[] b = (int[]) bad.elementAt(selected);
      int x = (b[1] - posX) / 2, y = b[2] / 2;
      g.setColor(0xff0000);
      g.fillRect(x, y, 16, 1); g.fillRect(x, y, 1, 16); g.fillRect(x, y + 16, 16, 1); g.fillRect(x + 16, y, 1, 16);
    }
    g.setClip(0, 0, game.sw, game.sh);
  }

  void drawInterface(Graphics g) {
    int sw = game.sw, sh = game.sh, p = px();
    if (lv != null && (scrolling || rightHeld || sh > 240)) drawMinimap(g, sh > 240 ? 250 : 0);
    // button bar
    g.setColor(0x323232); g.fillRect(p, 0, BAR, 240);
    if (lv == null) {
      String[] t = Game.wrap(Menu.current == null ? "No Level Subset loaded - Press Softkey and choose one in the menu" : "No Level Subset loaded", sw / 8 - 2);
      for (int i = 0; i < t.length; i++) Res.text(g, Res.WHITE, t[i], 10, (sh > 240 ? 290 : 215) - (t.length - 1 - i) * 10, 1);
      return;
    }
    String n = levelNo + "/" + levelCount;
    Res.text(g, Res.WHITE, n, p + (BAR - n.length() * 8) / 2, 1, 1);
    button(g, B_UP, Spr.IC_UP); button(g, B_DOWN, Spr.IC_DOWN);
    button(g, B_SAVE, Spr.IC_SAVE); button(g, B_EXIT, Spr.IC_EXIT);
    button(g, B_MODE, selMode == SM_SQUARE ? Spr.IC_SELECT_MODE1 : Spr.IC_SELECT_MODE2); button(g, B_RUBBER, Spr.IC_RUBBER);
    button(g, B_GROUP, Spr.IC_TILEGROUP); button(g, B_OBJECTS, Spr.IC_OBJECTS);
    if (curGroup >= 0 || curObjects) {
      Res.fillAlpha(g, p, 90, BAR, (curObjects ? 138 : 170) - 90, 0xc8646464);
      int c = curGroup >= 0 ? groupTiles[curGroup].length : KIND_NAMES.length;
      for (int i = 0; i < c; i++) button(g, B_PANEL + i, -1);
    }
    if (curObjects) { button(g, B_SELECTONE, Spr.IC_SELECT_ONE); button(g, B_PROPS, Spr.IC_PROPERTIES); }
    else {                                   // what the centre key places
      // like 0.1.3, at the bottom right of the bar
      int tx = p + 40, ty = 212;
      Res.fillAlpha(g, tx - 1, ty - 1, 18, 18, 0xc84b4b4b);
      if (curTile == 0) Res.text(g, Res.GOLD, "--", tx, ty + 4, 1);
      else {
        g.setClip(tx, ty, 16, 16);
        Res.drawTile(g, curTile, tx, ty, game.frameCounter);
        if (curTile < editorFrame.length && editorFrame[curTile] >= 0) {
          int f = editorFrame[curTile];
          g.drawRegion(Res.tileImg, Res.tfx[f], Res.tfy[f], Res.tfw[f] & 0xff, Res.tfh[f] & 0xff, 0, tx, ty, Graphics.TOP | Graphics.LEFT);
        }
        g.setClip(0, 0, sw, sh);
      }
    }
    button(g, B_TEST, Spr.IC_TEST_LEVEL); button(g, B_SETTINGS, Spr.IC_SETTINGS);
    button(g, B_BG, Spr.IC_BKGRD); button(g, B_IA, Spr.IC_INTACT); button(g, B_FG, Spr.IC_FRGRD);
    int[] r = rect(B_BG + activeTm);
    g.setColor(0xffff00); g.drawRect(r[0], r[1], r[2] - 1, r[3] - 1);
    if (Menu.current != null) { }
    else if (sh <= 240) Res.text(g, Res.WHITE, "* for Help", 10, 215, 1);
    else Res.text(g, Res.WHITE, "* for Help", 10, 300, 1);
    if (Menu.current == null && System.currentTimeMillis() < levelInfoEnd) Res.chars(g, Res.WHITE, lv.name, p / 2 - lv.name.length() * 4, 0);
    // the button's name after hovering 1.5 s, or at once with #
    if (hoverButton >= 0 && Menu.current == null && (showInfo || System.currentTimeMillis() - hoverStart > 1500)) {
      String info = hoverButton >= B_PANEL ? (curGroup >= 0 ? groupNames[curGroup] : "BadGuy") : INFO[hoverButton];
      int[] br = rect(hoverButton);
      if (br != null) Res.text(g, Res.WHITE, info, Math.max(0, br[0] - 16 - info.length() * 8 + 16), br[1], 1);
    }
  }

  void button(Graphics g, int b, int icon) {
    int[] r = rect(b);
    if (r == null) return;
    Res.fillAlpha(g, r[0], r[1], r[2], r[3], 0xc84b4b4b);
    Res.fillAlpha(g, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, 0xc8afafaf);
    if (icon >= 0) {
      int f = Res.sFrames[icon][0];
      Res.drawSpriteTL(g, icon, r[0] + (r[2] - (Res.sfw[f] & 0xff)) / 2, r[1] + (r[3] - (Res.sfh[f] & 0xff)) / 2);
    }
    else if (icon == -1 && curGroup >= 0) {
      int t = groupTiles[curGroup][b - B_PANEL];
      g.setClip(r[0], r[1], r[2], r[3]);           // big editor pictures stay inside their button
      Res.drawTile(g, t, r[0], r[1], 0);
      if (t < editorFrame.length && editorFrame[t] >= 0) {
        int f = editorFrame[t];
        g.drawRegion(Res.tileImg, Res.tfx[f], Res.tfy[f], Res.tfw[f] & 0xff, Res.tfh[f] & 0xff, 0, r[0], r[1], Graphics.TOP | Graphics.LEFT);
      }
      g.setClip(0, 0, game.sw, game.sh);
    } else if (icon == -1) {
      g.setClip(r[0], r[1], r[2], r[3]);
      Res.drawSprite(g, badSprite(b - B_PANEL), r[0], r[1], 0);
      g.setClip(0, 0, game.sw, game.sh);
    }
    if (b == pressedButton && leftHeld) Res.fillAlpha(g, r[0], r[1], r[2], r[3], 0xc84b4b4b);
    else if (b == hoverButton) Res.fillAlpha(g, r[0], r[1], r[2], r[3], 0x80969696);
  }

  // a button with its name in the game's font instead of the tiny icon
  void label(Graphics g, int b, String t) {
    button(g, b, -2);
    int[] r = rect(b);
    // dark letters on the light grey button, like the icons; the layer being edited in blue
    Res.chars(g, b >= B_BG && b == B_BG + activeTm ? Res.BLUE : Res.BLACK, t, r[0] + (r[2] - t.length() * 8) / 2, r[1] + (r[3] - 9) / 2 + 1);
  }

  // portrait: a tap or drag on the minimap under the level centres the view there
  void minimapTouch(int x) {
    if (lv == null) return;
    int p = px(), mw = Math.min(lv.width * 2, p), left = (p - mw) / 2;
    int col = (x - left) * lv.width / Math.max(1, mw);
    posX = col * 32 - p;               // the view is p screen pixels = p * 2 world units wide
    clampPos();
    motion(0, 0);
  }

  void drawMinimap(Graphics g, int y0) {
    int p = px();
    int mw = Math.min(lv.width * 2, p), left = (p - mw) / 2;
    if (minimapDirty || minimap == null || minimap.getWidth() != mw) {
      if (tileColor == null) tileColor = new int[Res.tFlags.length];
      int[] px = new int[mw * 30];
      for (int c = 0; c < mw; c++) {
        int x = c * lv.width / mw;
        for (int y = 0; y < 15; y++) {
          int i = y * lv.width + x, col = 0;
          for (int l = 0; l < 3; l++) { int t = (l == 0 ? lv.bg : l == 1 ? lv.ia : lv.fg)[i] & 0xff; if (t != 0) col = color(t); }
          px[(y * 2) * mw + c] = col; px[(y * 2 + 1) * mw + c] = col;
        }
      }
      minimap = Image.createRGBImage(px, mw, 30, true);
      minimapDirty = false;
    }
    g.drawImage(minimap, left, y0, Graphics.TOP | Graphics.LEFT);
    Res.fillAlpha(g, left, y0, mw, 30, 0x80c8c8c8);
    int vx = left + (posX / 32) * mw / lv.width, vw = (p / 16) * mw / lv.width;
    g.setColor(0xffffff);
    g.fillRect(vx, y0, vw, 1); g.fillRect(vx, y0, 1, 30); g.fillRect(vx + vw, y0, 1, 30); g.fillRect(vx, y0 + 29, vw, 1);
  }

  // the average colour of a tile (for the minimap)
  int color(int t) {
    if (tileColor[t] != 0) return tileColor[t];
    short[] fr = Res.tFrames[t];
    int c = 0xff808080;
    if (fr.length > 0) {
      int f = fr[0], w = Res.tfw[f] & 0xff, h = Res.tfh[f] & 0xff;
      int[] px = new int[w * h];
      Res.tileImg.getRGB(px, 0, w, Res.tfx[f], Res.tfy[f], w, h);
      long r = 0, gg = 0, b = 0; int n = 0;
      for (int i = 0; i < px.length; i++) if ((px[i] >>> 24) > 128) { r += (px[i] >> 16) & 255; gg += (px[i] >> 8) & 255; b += px[i] & 255; n++; }
      if (n > 0) c = 0xff000000 | (int) (r / n) << 16 | (int) (gg / n) << 8 | (int) (b / n);
      else c = 0x00000000;
    }
    tileColor[t] = c == 0 ? 1 : c;
    return tileColor[t] == 1 ? 0 : tileColor[t];
  }

  // le_showhelp (the Symbian page)
  void drawHelp(Graphics g) {
    int sw = game.sw, sh = game.sh;
    for (int y = 0; y < sh; y += 4) { int v = y * 255 / sh; g.setColor((v << 16) | (v << 8) | v); g.fillRect(0, y, sw, 4); }
    Res.textCentered(g, Res.BLUE, "- Help -", sw / 2, 8, 2);
    String[] keys = {
      "2468 / joy move pointer",
      "5 / centre click, hold: paint",
      game.rotate ? "1 / 7      scroll the level" : "1 / 3      scroll the level",
      "# + move   drag the level",
      "0          grid on/off",
      "*          this help",
      "Softkey    editor menu",
    };
    String[] para = {
      "TileGroup / Objects: pick what to place. BkGrd, IntAct, FrGrd: the layer (background, interactive, foreground). Only IntAct tiles touch Tux.",
      "Wrench: level settings, length. Penguin: test the level. Arrows: next / previous level. # on a button: its name.",
      "Moving the pointer past the right edge scrolls too. Touch: hold near an edge to scroll, or tap the map under the level.",
      "Typing: multi-tap, * = case, # or C deletes.",
    };
    // landscape is only 240 tall: no gap after the keys and the text starts higher when it wouldn't fit
    int lines = keys.length;
    for (int i = 0; i < para.length; i++) lines += Game.wrap(para[i], (sw - 4) / 8).length;
    boolean tight = 30 + 9 + lines * 9 > sh - 22;
    int y = tight ? 22 : 30;
    for (int i = 0; i < keys.length; i++, y += 9) Res.text(g, Res.WHITE, keys[i], 2, y, 1);
    if (!tight) y += 9;
    for (int i = 0; i < para.length; i++) {
      String[] l = Game.wrap(para[i], (sw - 4) / 8);
      for (int j = 0; j < l.length; j++, y += 9) Res.text(g, Res.WHITE, l[j], 2, y, 1);
    }
    Res.text(g, Res.GOLD, "Press any key", 2, sh - 20, 1);
  }
}
