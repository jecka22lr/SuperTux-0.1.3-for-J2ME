// The game screen: main loop, the title screen and menus (Jecka's 320x240 Symbian build of 0.1.3),
// the world map, scrolling texts, and the level itself (world.cpp/gameloop.cpp).
// Two layouts:
//   landscape 320x240: exactly 0.1.3's half-size screen (640x480 world units visible)
//   portrait  240x320: 480 world units wide, the level fills the lower 240 pixels, sky above
// On a portrait phone the landscape layout is drawn sideways (hold the phone keypad-right).
import java.util.Random;
import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.rms.RecordStore;

final class Game extends GameCanvas implements Runnable {
  static final int DISTROS_LIFEUP = 100, MAX_LIVES = 99, START_LIVES = 4;
  static final int SCORE_BRICK = 5, SCORE_DISTRO = 25;
  static final int SND_JUMP = Sound.JUMP, SND_BIGJUMP = Sound.BIGJUMP, SND_SKID = Sound.SKID,
      SND_DISTRO = Sound.DISTRO, SND_BRICK = Sound.BRICK, SND_HURT = Sound.HURT, SND_LIFEUP = Sound.LIFEUP,
      SND_UPGRADE = Sound.UPGRADE, SND_KICK = Sound.KICK, SND_RICOCHET = Sound.RICOCHET, SND_FALL = Sound.FALL,
      SND_EXPLODE = Sound.EXPLODE, SND_SQUISH = Sound.SQUISH, SND_STOMP = Sound.STOMP,
      SND_BUMP_UPGRADE = Sound.BUMP_UPGRADE, SND_EXCELLENT = Sound.EXCELLENT, SND_COFFEE = Sound.COFFEE,
      SND_HERRING = Sound.HERRING, SND_SHOOT = Sound.SHOOT;
  static final int TIME_WARNING = 20000;

  // states
  static final int PLAY = 0, INTRO = 1, GAMEOVER = 2, ERROR = 4, TITLE = 5, WORLDMAP = 6, TEXT = 7,
      CONTROLS = 8, LOADING = 9, EDITOR = 10, HIGHSCORE = 11, NOTICE = 12;

  final SuperTux midlet;
  volatile boolean running = true;
  final Random rnd = new Random();
  final Music music = new Music();
  final Sound sounds = new Sound();
  boolean showFps;
  boolean showTouch;        // the Symbian build's "Show touch" buttons (on by default on touch phones)

  // time
  long now;                 // game time in ms
  int frameCounter;         // 0.1.3's global_frame_counter (25 ms ticks)
  int fpsCount; long fpsT; String fpsText = "0.0";

  // layout
  boolean landscape;        // chosen layout
  boolean rotate;           // landscape drawn sideways on a portrait screen
  int sw, sh;               // logical screen size
  int viewW, viewTop;       // world units visible across; world y of the screen top is -viewTop
  Image buffer;             // offscreen screen when rotating

  // player_status
  int score, distros, lives = START_LIVES, bonus, scoreMultiplier = 1;

  // world
  String levelId;
  boolean demo;             // the title screen's demo level
  Level level;
  Player tux;
  float scrollX;
  long scrollingT;
  long timeEnd;             // level time runs out at this game time
  int state = TITLE;
  long stateT, stateMin;
  int endSequence;          // 0 none, 1 running, 2 waiting
  long endT;
  float lastX;
  String message = "";
  Image bgImage; int bgImageNo = -1; int skyColor;
  Vector badguys = new Vector(), items = new Vector(), bullets = new Vector();
  WorldMap worldmap;
  boolean testMode;         // ST_GL_TEST: a level played from the level editor
  boolean singleLevel;      // one level of a level subset from Bonus Levels
  byte[] testLevel;
  Editor editor;
  int hsScore = 100, hsCandidate = -1; String hsName = "Grandma";   // load_hs() defaults
  long fadeStart; int fadeState = -1;

  // small effects: bouncy distros, bouncy bricks, broken brick pieces, floating scores
  static final int NE = 16;
  float[] bdX = new float[NE], bdY = new float[NE], bdYm = new float[NE]; boolean[] bdOn = new boolean[NE];
  float[] bbX = new float[NE], bbY = new float[NE], bbOff = new float[NE], bbM = new float[NE];
  int[] bbTile = new int[NE]; boolean[] bbOn = new boolean[NE];
  float[] pbX = new float[NE], pbY = new float[NE], pbXm = new float[NE], pbYm = new float[NE];
  long[] pbEnd = new long[NE]; int[] pbTile = new int[NE];
  float[] fsX = new float[NE], fsY = new float[NE]; long[] fsEnd = new long[NE]; String[] fsText = new String[NE];
  int coinTile;

  // particles (snow: 3 sizes, 2 layers / clouds)
  float[] ptX = new float[128], ptY = new float[128], ptSpeed = new float[128];
  int[] ptImg = new int[128], ptLayer = new int[128];
  int ptCount, ptKind; float ptVW;

  // input: held keys as bits, and presses queued for menus and screens
  int keys;
  static final int K_LEFT = 1, K_RIGHT = 2, K_UP = 4, K_DOWN = 8, K_FIRE = 16, K_SELECT = 32, K_ESC = 64,
      K_CLEAR = 128, K_ANY = 256, K_STAR = 512, K_POUND = 1024, K_SOFT = 2048;
  private final int[] evq = new int[16];
  int[] codes = new int[16]; int nCodes;            // keys pressed this frame
  // Keyboard Setup, like the Symbian build's keymap: one key each for left, right, jump, duck and
  // power/run. A key moved away stops doing its old job. Upright and sideways have a map each.
  static final int[] KEY_DEFAULT = { KEY_NUM4, KEY_NUM6, KEY_NUM2, KEY_NUM8, KEY_NUM0 };
  static final int[] KEY_DEFAULT_SIDE = { KEY_NUM2, KEY_NUM8, KEY_NUM6, KEY_NUM4, KEY_NUM0 };
  static final int[] KEY_BIT = { K_LEFT, K_RIGHT, K_UP, K_DOWN, K_FIRE };
  int[] keymapUp = { KEY_NUM4, KEY_NUM6, KEY_NUM2, KEY_NUM8, KEY_NUM0 };
  int[] keymapSide = { KEY_NUM2, KEY_NUM8, KEY_NUM6, KEY_NUM4, KEY_NUM0 };
  int[] keymap() { return rotate ? keymapSide : keymapUp; }
  int[] keyDefaults() { return rotate ? KEY_DEFAULT_SIDE : KEY_DEFAULT; }
  boolean bound(int code) { int[] k = keymap(); for (int i = 0; i < 5; i++) if (k[i] == code) return true; return false; }
  private int evHead, evTail;

  // menus (setup.cpp)
  static final int M_START = 1, M_CONTRIB = 2, M_OPTIONS = 3, M_CONTROLS = 4, M_CREDITS = 5, M_QUIT = 6,
      M_SOUND = 10, M_MUSIC = 11, M_FPS = 12, M_LANDSCAPE = 13, M_TOUCH = 14, M_CONTINUE = 20, M_ABORT = 21,
      M_WM_RETURN = 30, M_WM_QUIT = 31, M_YES = 41, M_NO = 40, M_EDITOR = 7, M_KEYS = 50, M_HSNAME = 60,
      M_STORY = 999, M_DETAIL = 15, M_FPSCAP = 16, M_RAM = 17, M_OPTHELP = 18;   // above any level number in a subset's level list
  Menu mainMenu, optionsMenu, loadGameMenu, contribMenu, gameMenu, worldmapMenu, confirmMenu, keysMenu,
      contribSubsetMenu, highscoreMenu;
  String[] contribSubsets; int contribSubset;
  int confirmSlot;
  // the worlds this jar has (x/g.txt, written by tools/convert.py): Start Game worlds, each with
  // its own save slots and intro, and the world maps under Bonus Levels
  String[] startTitle, startSave, startIntro; int[] startMap; int curStart;
  String[] bonusTitle; int[] bonusMap;
  String titleNote;
  static final String RELEASE = "Beta Release 1.2";
  // Options > Music and Detail. Low: plain sky colour instead of the background picture, no snow or
  // clouds. Potato: also no tile animations (coins stand still), no flying scores, coins, brick
  // pieces or bouncing bricks, and no fades or menu slides: everything is there, with as little
  // drawing as possible, for slow phones
  static final String[] MUSIC_MODES = { "Normal", "MIDI", "Off" };
  static final String[] DETAIL_MODES = { "Full", "Low", "Potato" };
  static final int FULL = 0, LOW = 1, POTATO = 2;
  int detail = FULL;
  int musicMode() { return !music.enabled ? 2 : music.midiOnly ? 1 : 0; }
  // Options > FPS cap: the shortest a frame may take. A lower cap leaves the phone idle time between
  // frames, which can smooth out sound and music on slow phones
  static final String[] FPSCAP_MODES = { "Max", "25", "20", "15" };
  static final int[] FPSCAP_MS = { 30, 40, 50, 66 };
  int fpsCap;
  // Options > RAM saver trades speed for memory and leaves the look alone; Detail trades looks for
  // speed. RAM saver: no tile cache (Low/Potato draw tiles one by one again), no cached touch
  // buttons, no stretched copy of the background picture (Full keeps the picture, sky colour above
  // it), and the logo, story and score pictures are let go before each level loads
  boolean ramSaver;
  Menu adventureMenu;

  void loadWorlds() {
    String[] l = Res.lines("/x/g.txt");
    Vector s = new Vector(), b = new Vector(), p = new Vector();
    for (int i = 0; i < l.length; i++) {
      String[] f = split(l[i], '|');
      if (f[0].equals("S") && f.length >= 5) s.addElement(f);
      else if (f[0].equals("B") && f.length >= 3) b.addElement(f);
      else if (f[0].equals("P") && f.length >= 5) p.addElement(f);
      else if (f[0].equals("T") && f.length >= 2) titleNote = f[1];
    }
    if (s.size() == 0) s.addElement(new String[] { "S", "Icyisland", "0", "slot", "/x/intro.txt" });
    startTitle = new String[s.size()]; startSave = new String[s.size()]; startIntro = new String[s.size()]; startMap = new int[s.size()];
    for (int i = 0; i < s.size(); i++) {
      String[] f = (String[]) s.elementAt(i);
      startTitle[i] = f[1]; startMap[i] = Integer.parseInt(f[2]); startSave[i] = f[3]; startIntro[i] = f[4];
    }
    bonusTitle = new String[b.size()]; bonusMap = new int[b.size()];
    for (int i = 0; i < b.size(); i++) {
      String[] f = (String[]) b.elementAt(i);
      bonusTitle[i] = f[1]; bonusMap[i] = Integer.parseInt(f[2]);
    }
    String[] dirs = new String[p.size()], titles = new String[p.size()], authors = new String[p.size()], story = new String[p.size()];
    String letters = "";
    for (int i = 0; i < p.size(); i++) {
      String[] f = (String[]) p.elementAt(i);
      dirs[i] = f[1]; letters += f[2].charAt(0); titles[i] = f[3]; authors[i] = f[4];
      story[i] = f.length > 5 && f[5].length() > 0 ? f[5] : null;
    }
    Editor.setPacks(dirs, letters, titles, authors, story);
  }

  static String[] split(String s, char c) {
    Vector v = new Vector();
    int from = 0, at;
    while ((at = s.indexOf(c, from)) >= 0) { v.addElement(s.substring(from, at)); from = at + 1; }
    v.addElement(s.substring(from));
    String[] r = new String[v.size()];
    v.copyInto(r);
    return r;
  }

  // title demo (title.cpp)
  boolean walking; long randomEnd;

  // scrolling text (display_text_file)
  String[] textLines; int textPic; float textScroll, textSpeed; int textAfter; Image textImg; int textImgNo = -1;
  static final int AFTER_TITLE = 0, AFTER_WORLDMAP = 1, AFTER_CREDITS = 2;
  int loadingWhat; String loadingSave; int loadingMap;

  Game(SuperTux m) {
    super(false);
    midlet = m;
    Res.cls = getClass();
    setFullScreenMode(true);
    tux = new Player(this);
    loadSettings();
    loadWorlds();
    buildMenus();
  }

  void start() { new Thread(this).start(); }
  void stop() { running = false; music.stop(); sounds.stop(); }

  void sound(int s) { sounds.play(s); }

  // ---------------- settings ----------------
  void loadSettings() {
    landscape = getWidth() > getHeight();
    try { showTouch = hasPointerEvents(); } catch (Throwable e) { }
    try {
      byte[] b = WorldMap.read("stux");
      if (b != null) {
        landscape = b[0] != 0;
        music.enabled = b[1] != 0; music.midiOnly = b[1] == 2;
        if (b.length > 3) { sounds.mode = soundModeOf(b[2]); showFps = b[3] != 0; }
        if (b.length > 4 && b[4] >= 2 && b[4] <= 6) {
          java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(b, 5, b.length - 5));
          for (int i = 0; i < 5; i++) keymapUp[i] = in.readInt();
          hsScore = in.readInt(); hsName = in.readUTF();
          if (b[4] >= 3) for (int i = 0; i < 5; i++) keymapSide[i] = in.readInt();
          if (b[4] >= 4) showTouch = in.readBoolean();
          if (b[4] >= 5) detail = Math.max(FULL, Math.min(POTATO, in.readByte()));
          if (b[4] >= 6) { fpsCap = Math.max(0, Math.min(FPSCAP_MS.length - 1, in.readByte())); ramSaver = in.readBoolean(); }
        }
      }
    } catch (Throwable e) { }
    Menu.noEffects = detail == POTATO;
    setLayout(landscape);
  }

  // settings byte 2: 0 off, 1 normal (what older versions saved for off / on), 2 fast, 3 MIDI, 4 Nokia
  static int soundModeOf(byte b) {
    return b == 0 ? Sound.OFF : b == 2 ? Sound.FAST : b == 3 ? Sound.MIDI : b == 4 ? Sound.NOKIA : Sound.NORMAL;
  }
  static int soundByte(int mode) {
    return mode == Sound.OFF ? 0 : mode == Sound.FAST ? 2 : mode == Sound.MIDI ? 3 : mode == Sound.NOKIA ? 4 : 1;
  }

  void saveSettings() {
    try {
      java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
      java.io.DataOutputStream out = new java.io.DataOutputStream(bo);
      out.writeByte(landscape ? 1 : 0); out.writeByte(!music.enabled ? 0 : music.midiOnly ? 2 : 1); out.writeByte(soundByte(sounds.mode));
      out.writeByte(showFps ? 1 : 0); out.writeByte(6);
      for (int i = 0; i < 5; i++) out.writeInt(keymapUp[i]);
      out.writeInt(hsScore); out.writeUTF(hsName);
      for (int i = 0; i < 5; i++) out.writeInt(keymapSide[i]);
      out.writeBoolean(showTouch);
      out.writeByte(detail);
      out.writeByte(fpsCap); out.writeBoolean(ramSaver);
      out.close();
      byte[] b = bo.toByteArray();
      RecordStore rs = RecordStore.openRecordStore("stux", true);
      if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length); else rs.addRecord(b, 0, b.length);
      rs.closeRecordStore();
    } catch (Throwable e) { }
  }

  void setLayout(boolean land) {
    landscape = land;
    boolean deviceLandscape = getWidth() > getHeight();
    rotate = land && !deviceLandscape;
    if (land) { sw = 320; sh = 240; viewW = 640; viewTop = 0; }
    else { sw = 240; sh = 320; viewW = 480; viewTop = showTouch ? 64 : 160; }   // touch buttons: level above them
    buffer = rotate ? Image.createImage(320, 240) : null;
    if (level != null) clampScroll();
    showKeys();
  }

  // ---------------- menus ----------------
  void buildMenus() {
    mainMenu = new Menu(); optionsMenu = new Menu(); loadGameMenu = new Menu(); contribMenu = new Menu();
    gameMenu = new Menu(); worldmapMenu = new Menu(); confirmMenu = new Menu();
    mainMenu.posY = 300; mainMenu.posYPortrait = 370;    // leaves room for the logo and the credits
    adventureMenu = new Menu();
    adventureMenu.add(Menu.LABEL, "Start Game", 0).add(Menu.HL, "", 0);
    for (int i = 0; i < startTitle.length; i++) adventureMenu.add(Menu.GOTO, startTitle[i], 300 + i, loadGameMenu, false);
    adventureMenu.add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
    mainMenu.add(Menu.GOTO, "Start Game", M_START, startTitle.length > 1 ? adventureMenu : loadGameMenu, false)
        .add(Menu.GOTO, "Bonus Levels", M_CONTRIB, contribMenu, false)
        .add(Menu.GOTO, "Options", M_OPTIONS, optionsMenu, false)
        .add(Menu.ACTION, "Controls", M_CONTROLS)
        .add(Menu.ACTION, "Level Editor", M_EDITOR)
        .add(Menu.ACTION, "Credits", M_CREDITS)
        .add(Menu.ACTION, "Quit", M_QUIT);
    optionsMenu.add(Menu.LABEL, "Options", 0).add(Menu.HL, "", 0)
        .add(Menu.STRINGSELECT, "Sound     ", M_SOUND)
        .add(Menu.STRINGSELECT, "Music     ", M_MUSIC)
        .add(Menu.STRINGSELECT, "Detail    ", M_DETAIL)
        .add(Menu.STRINGSELECT, "FPS cap   ", M_FPSCAP)
        .add(Menu.TOGGLE, "RAM saver ", M_RAM, null, ramSaver)
        .add(Menu.TOGGLE, "Show FPS  ", M_FPS, null, showFps)
        .add(Menu.TOGGLE, "Landscape ", M_LANDSCAPE, null, landscape)
        .add(Menu.TOGGLE, "Show touch", M_TOUCH, null, showTouch)
        .add(Menu.GOTO, "Keyboard Setup", 0, keysMenu = new Menu(), false)
        .add(Menu.GOTO, "Option Help", M_OPTHELP, null, false)    // no target: opens the help pages
        .add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
    optionsMenu.setList(M_SOUND, Sound.MODES, sounds.mode);
    optionsMenu.setList(M_MUSIC, MUSIC_MODES, musicMode());
    optionsMenu.setList(M_DETAIL, DETAIL_MODES, detail);
    optionsMenu.setList(M_FPSCAP, FPSCAP_MODES, fpsCap);
    keysMenu.add(Menu.LABEL, "Key Setup", 0).add(Menu.HL, "", 0)
        .add(Menu.CONTROLFIELD, "Left move", M_KEYS).add(Menu.CONTROLFIELD, "Right move", M_KEYS + 1)
        .add(Menu.CONTROLFIELD, "Jump", M_KEYS + 2).add(Menu.CONTROLFIELD, "Duck", M_KEYS + 3)
        .add(Menu.CONTROLFIELD, "Power/Run", M_KEYS + 4).add(Menu.HL, "", 0)
        .add(Menu.ACTION, "Default Keys", M_KEYS + 9).add(Menu.BACK, "Back", 0);
    showKeys();
    contribSubsetMenu = new Menu();
    highscoreMenu = new Menu();
    highscoreMenu.posY = 320; highscoreMenu.posYPortrait = 420;
    highscoreMenu.add(Menu.TEXTFIELD, "Enter your name:", M_HSNAME);
    loadGameMenu.add(Menu.LABEL, "Start Game", 0).add(Menu.HL, "", 0);
    for (int i = 1; i <= 5; i++) loadGameMenu.add(Menu.DEACTIVE, "Slot " + i, i);
    loadGameMenu.add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
    generateContrib();
    gameMenu.add(Menu.LABEL, "Pause", 0).add(Menu.HL, "", 0)
        .add(Menu.ACTION, "Continue", M_CONTINUE).add(Menu.GOTO, "Options", 0, optionsMenu, false)
        .add(Menu.HL, "", 0).add(Menu.ACTION, "Abort Level", M_ABORT);
    worldmapMenu.add(Menu.LABEL, "Pause", 0).add(Menu.HL, "", 0)
        .add(Menu.ACTION, "Continue", M_WM_RETURN).add(Menu.GOTO, "Options", 0, optionsMenu, false)
        .add(Menu.HL, "", 0).add(Menu.ACTION, "Quit Game", M_WM_QUIT);
  }

  // generate_contrib_menu: the community packs, the level editor's subsets, then the bonus islands
  void generateContrib() {
    String[] user = Editor.subsets();
    int packs = Editor.PACK_DIRS.length;
    contribSubsets = new String[packs + user.length];
    System.arraycopy(Editor.PACK_DIRS, 0, contribSubsets, 0, packs);
    System.arraycopy(user, 0, contribSubsets, packs, user.length);
    contribMenu.clear();
    contribMenu.add(Menu.LABEL, "Bonus Levels", 0).add(Menu.HL, "", 0);
    for (int i = 0; i < contribSubsets.length; i++)
      contribMenu.add(Menu.GOTO, Editor.subsetTitle(contribSubsets[i]), 100 + i, contribSubsetMenu, false);
    for (int i = 0; i < bonusTitle.length; i++) contribMenu.add(Menu.ACTION, bonusTitle[i], 1 + i);
    contribMenu.add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
  }

  // keys: the menu's Keyboard Setup values
  void keysChanged() {
    int[] k = keymap();
    if (keysMenu.check() == M_KEYS + 9) {          // Default Keys (an ACTION closes the menus: reopen)
      int[] d = keyDefaults();
      for (int i = 0; i < 5; i++) k[i] = d[i];
      showKeys(); saveSettings();
      Menu.setCurrent(optionsMenu, now); Menu.push(keysMenu, now);
      return;
    }
    boolean ch = false;
    for (int i = 0; i < 5; i++) {
      int c = keysMenu.codeOf(M_KEYS + i);
      if (c != k[i]) {
        for (int j = 0; j < 5; j++) if (j != i && k[j] == c) { k[j] = k[i]; }   // a key does one job: swap
        k[i] = c; ch = true;
      }
    }
    if (ch) { showKeys(); saveSettings(); }
  }

  void showKeys() {
    if (keysMenu == null) return;
    int[] k = keymap();
    for (int i = 0; i < 5; i++) keysMenu.setCode(M_KEYS + i, k[i]);
    keysMenu.text[0] = rotate ? "Key Setup (sideways)" : "Key Setup";
  }

  static String keyName(int code) {
    if (code >= '0' && code <= '9') return "Key " + (char) code;
    switch (code) {
      case '*': return "*";
      case '#': return "#";
      case -1: return "Joystick up";
      case -2: return "Joystick down";
      case -3: return "Joystick left";
      case -4: return "Joystick right";
      case -5: return "Centre key";
      case -8: return "C key";
      case -10: return "Call key";
      case -50: return "Pencil key";
    }
    if (code > 32 && code < 127) return String.valueOf((char) code);
    return "Key " + code;
  }

  // update_load_save_game_menu
  void updateSlots() {
    loadGameMenu.text[0] = startTitle.length > 1 ? fitName(startTitle[curStart], sw / 8 - 1) : "Start Game";
    for (int i = 0; i < 5; i++) {
      loadGameMenu.kind[2 + i] = Menu.ACTION;
      loadGameMenu.text[2 + i] = WorldMap.slotInfo(startSave[curStart], i + 1);
    }
  }

  // process_options_menu
  void optionsChanged() {
    // Sound is a left/right choice, which doesn't count as a hit
    if (optionsMenu.listOf(M_SOUND) != sounds.mode) {
      sounds.setMode(optionsMenu.listOf(M_SOUND));
      sounds.play(Sound.DISTRO);       // a sample of the new mode
      saveSettings();
    }
    if (optionsMenu.listOf(M_MUSIC) != musicMode()) {
      int m = optionsMenu.listOf(M_MUSIC);
      if (m == 2) music.setEnabled(false);
      else { music.setMidiOnly(m == 1); music.setEnabled(true); }
      saveSettings();
    }
    if (optionsMenu.listOf(M_DETAIL) != detail) {
      detail = optionsMenu.listOf(M_DETAIL);
      Menu.noEffects = detail == POTATO;
      loadBackground();
      saveSettings();
    }
    if (optionsMenu.listOf(M_FPSCAP) != fpsCap) {
      fpsCap = optionsMenu.listOf(M_FPSCAP);
      saveSettings();
    }
    if (optionsMenu.check() < 0) return;
    if (optionsMenu.check() == M_OPTHELP) { helpOpen = true; helpPage = 0; return; }

    if (ramSaver != optionsMenu.isToggled(M_RAM)) {
      ramSaver = !ramSaver;
      if (ramSaver) { tc = null; tcG = null; tcLevel = null; touchImg = null; }
      loadBackground();
    }

    showFps = optionsMenu.isToggled(M_FPS);
    if (showTouch != optionsMenu.isToggled(M_TOUCH)) { showTouch = !showTouch; setLayout(landscape); }
    if (landscape != optionsMenu.isToggled(M_LANDSCAPE)) setLayout(!landscape);
    saveSettings();
  }

  // ---------------- title screen ----------------
  void enterTitle() {
    if (hsCandidate > hsScore) {             // save_hs: a new high score asks for a name first
      hsScore = hsCandidate; hsCandidate = -1;
      highscoreMenu.setInput(M_HSNAME, hsName);
      state = HIGHSCORE; music.stop(); music.type = Music.NONE;
      Menu.setCurrent(highscoreMenu, now);
      return;
    }
    hsCandidate = -1;
    state = TITLE;
    worldmap = null;
    levelId = "m1"; demo = true;
    loadLevel(false);
    walking = true;
    randomEnd = now + 2000 + Math.abs(rnd.nextInt()) % 2000;
    Menu.setCurrent(mainMenu, now);
  }

  // draw_demo: Tux walks right, jumping on a random timer, at half speed
  void demoFrame(long dt) {
    tux.inRight = true; tux.inLeft = false; tux.inDown = false; tux.inFire = false;
    if (now < randomEnd) tux.inUp = !walking;
    else { randomEnd = now + 3000 + Math.abs(rnd.nextInt()) % 3000; walking = !walking; }
    if (level.width * 32 - 320 < tux.x) {      // wrap around at the end of the level
      tux.levelBegin();
      scrollX = 0;
    }
    tux.canJump = true;
    float last = tux.x;
    action(dt / 20f);
    if (last == tux.x) walking = false;
    if (music.type != Music.LEVEL) music.play(Music.LEVEL);
  }

  void titleMenus(int ev) {
    Menu m = Menu.current;
    if (m == null) { Menu.setCurrent(mainMenu, now); return; }
    if (m == loadGameMenu && (ev & K_CLEAR) != 0) {
      int slot = loadGameMenu.id[loadGameMenu.active];
      if (slot >= 1 && slot <= 5) {
        confirmSlot = slot;
        confirmMenu.clear();
        confirmMenu.add(Menu.DEACTIVE, landscape ? "Are you sure you want to delete slot " + slot + "?" : "Delete slot " + slot + "?", 0)
            .add(Menu.HL, "", 0).add(Menu.ACTION, "Yes", M_YES).add(Menu.ACTION, "No", M_NO).add(Menu.HL, "", 0);
        Menu.setCurrent(confirmMenu, now);
      }
      return;
    }
    if ((ev & K_ESC) != 0) { if (m != mainMenu) Menu.pop(now); return; }
    m.action(menuAction(ev), now);
    if (m == mainMenu) {
      switch (mainMenu.check()) {
        case M_START: curStart = 0; updateSlots(); break;
        case M_CONTROLS: state = CONTROLS; break;
        case M_CONTRIB: generateContrib(); break;
        case M_EDITOR: openEditor(); break;
        case M_CREDITS:
          showText("/x/credits", Pic.BG_TITLE, 2.4f, AFTER_TITLE);
          music.playFile("cr", 1);
          break;
        case M_QUIT: midlet.quit(); break;
      }
    } else if (m == optionsMenu) optionsChanged();
    else if (m == keysMenu) keysChanged();
    else if (m == contribSubsetMenu) {
      int n = contribSubsetMenu.check();
      if (n == M_STORY) {
        int pack = Editor.indexOf(Editor.PACK_DIRS, contribSubsets[contribSubset]);
        if (pack >= 0) showText(Editor.PACK_STORY[pack], Pic.BG_OILTUX, 1.0f, AFTER_TITLE);
      } else if (n >= 1) {
        singleLevel = true; worldmap = null; resetStatus();
        startLevel(Editor.levelId(contribSubsets[contribSubset], n));
      }
    }
    else if (m == adventureMenu) {      // which world's save slots Start Game shows
      int c = adventureMenu.check();
      if (c >= 300) { curStart = c - 300; updateSlots(); }
    }
    else if (m == loadGameMenu) {
      int slot = loadGameMenu.check();
      if (slot >= 1 && slot <= 5 && loadGameMenu.kind[loadGameMenu.find(slot)] == Menu.ACTION) {
        loadingSave = startSave[curStart] + slot; loadingMap = startMap[curStart];
        if (WorldMap.read(loadingSave) == null) showText(startIntro[curStart], Pic.BG_ARCTIS2, 1.0f, AFTER_WORLDMAP);
        else startLoading();
      }
    } else if (m == contribMenu) {
      int c = contribMenu.check();
      if (c >= 1 && c <= bonusMap.length) {
        loadingMap = bonusMap[c - 1]; loadingSave = "bonusisland" + loadingMap; startLoading();
      }
      if (c >= 100) {               // check_contrib_menu: the subset's levels
        contribSubset = c - 100;
        String sub = contribSubsets[contribSubset];
        contribSubsetMenu.clear();
        contribSubsetMenu.add(Menu.LABEL, Editor.subsetTitle(sub), 0).add(Menu.HL, "", 0);
        int pack = Editor.indexOf(Editor.PACK_DIRS, sub);
        if (pack >= 0 && Editor.PACK_STORY[pack] != null) contribSubsetMenu.add(Menu.ACTION, "The Story", M_STORY);
        int n = Editor.subsetLevels(sub);
        for (int i = 1; i <= n; i++)
          contribSubsetMenu.add(Menu.ACTION, fitName(Editor.levelName(Editor.levelId(sub, i)), sw / 8 - 1), i);
        contribSubsetMenu.add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
      }
    } else if (m == confirmMenu) {
      int c = confirmMenu.check();
      if (c == M_YES) WorldMap.delete(startSave[curStart] + confirmSlot);
      if (c == M_YES || c == M_NO) { updateSlots(); Menu.setCurrent(mainMenu, now); }
    }
  }

  static int menuAction(int ev) {
    if ((ev & K_UP) != 0) return Menu.A_UP;
    if ((ev & K_DOWN) != 0) return Menu.A_DOWN;
    if ((ev & K_SELECT) != 0) return Menu.A_HIT;
    if ((ev & K_LEFT) != 0) return Menu.A_LEFT;
    if ((ev & K_RIGHT) != 0) return Menu.A_RIGHT;
    return Menu.A_NONE;
  }

  // fadeout(): a black "Loading..." frame, then the world map
  void startLoading() { state = LOADING; stateT = 0; }

  void openWorldMap() {
    try {
      worldmap = new WorldMap(this, loadingMap, loadingSave);
    } catch (Exception e) { state = ERROR; message = "World map: " + e; return; }
    resetStatus();
    worldmap.load();
    Menu.setCurrent(null, now);
    state = WORLDMAP;
    music.song = worldmap.music;
    music.type = Music.NONE;
    music.play(Music.LEVEL);
  }

  void resetStatus() { score = 0; distros = 0; lives = START_LIVES; bonus = WorldMap.NO_BONUS; }

  // ---------------- scrolling text ----------------
  void showText(String file, int pic, float speed, int after) {
    String[] raw = Res.lines(file);
    int max = sw / 8;
    Vector v = new Vector();
    for (int i = 0; i < raw.length; i++) {
      String l = raw[i];
      char p = l.length() > 0 ? l.charAt(0) : (char) 0;
      boolean pre = p == ' ' || p == '\t' || p == '-';
      String body = pre ? l.substring(1) : l;
      if (body.length() > max) {
        // too wide for this screen: join the rest of the paragraph (same kind of line) and re-wrap it
        while (i + 1 < raw.length && raw[i + 1].length() > 1 && raw[i + 1].charAt(0) == p && raw[i + 1].trim().length() > 0)
          body = body.trim() + " " + raw[++i].substring(1).trim();
        body = body.trim();
        while (body.length() > max) {
          int cut = body.lastIndexOf(' ', max);
          if (cut <= 0) cut = max;
          v.addElement(pre ? p + body.substring(0, cut) : body.substring(0, cut));
          body = body.substring(cut).trim();
        }
      }
      v.addElement(pre ? p + body : body);
    }
    textLines = new String[v.size()];
    v.copyInto(textLines);
    textPic = pic; textSpeed = speed / 50; textScroll = 0; textAfter = after;
    state = TEXT;
    Menu.setCurrent(null, now);
  }

  void textFrame(long dt, int ev) {
    if ((ev & K_UP) != 0) textSpeed -= 0.01f;
    if ((ev & K_DOWN) != 0) textSpeed += 0.01f;
    if ((ev & K_SELECT) != 0 && textSpeed >= 0) textScroll += 60;
    if (textSpeed > 10) textSpeed = 10; else if (textSpeed < -10) textSpeed = -10;
    int y = 0;
    for (int i = 0; i < textLines.length; i++) y += lineStep(textLines[i]);
    boolean done = (ev & K_ESC) != 0 || sh + y - textScroll + 20 < 0;
    textScroll += textSpeed * dt;
    if (textScroll < 0) textScroll = 0;
    if (!done) return;
    if (textAfter == AFTER_WORLDMAP) startLoading();
    else if (textAfter == AFTER_CREDITS) {
      showText("/x/credits", Pic.BG_OILTUX, 2.4f, AFTER_TITLE);
      music.playFile("cr", 1);
    } else enterTitle();
  }

  static int lineStep(String l) {
    char c = l.length() > 0 ? l.charAt(0) : 0;
    return c == '-' ? 32 : 19;
  }

  void drawText(Graphics g) {
    drawPicture(g, textPic);
    int y = 0;
    for (int i = 0; i < textLines.length; i++) {
      String l = textLines[i];
      int yy = (sh + y - (int) textScroll) / 2;     // 0.1.3 starts at screen->h in 480-line units
      if (yy > -20 && yy < sh) {
        char c = l.length() > 0 ? l.charAt(0) : 0;
        if (c == ' ' || c == '\t') Res.textCentered(g, Res.WHITE, l.substring(1), sw / 2, yy, 1);
        else if (c == '-') Res.textCentered(g, Res.WHITE, l.substring(1), sw / 2, yy, 3);
        else Res.textCentered(g, Res.BLUE, l, sw / 2, yy, 1);
      }
      y += lineStep(l);
    }
  }

  // a 320x240 picture; on the portrait screen it is made 320 tall: the sky pictures get their upper
  // half stretched (like the level backgrounds), the paintings are scaled up and cropped at the sides
  Image textTall;
  void drawPicture(Graphics g, int n) {
    if (textImg == null || textImgNo != n) {
      textImg = null; textTall = null;
      try { textImg = Image.createImage("/b/" + n + ".png"); } catch (Exception e) { }
      textImgNo = n;
    }
    if (textImg == null) { g.setColor(0); g.fillRect(0, 0, sw, sh); return; }
    if (sh <= 240) { g.drawImage(textImg, (sw - 320) / 2, 0, Graphics.TOP | Graphics.LEFT); return; }
    if (textTall == null)
      textTall = n == Pic.BG_EXTRO || n == Pic.BG_OILTUX ? cover(textImg, sw, sh) : tall(textImg, sh - 240);
    g.drawImage(textTall, (sw - textTall.getWidth()) / 2, 0, Graphics.TOP | Graphics.LEFT);
  }

  // the picture with its upper half stretched down by extra rows (sky gets taller, the ground stays)
  static Image tall(Image src, int extra) {
    int w = src.getWidth(), h = src.getHeight(), half = h / 2;
    Image d = Image.createImage(w, h + extra);
    Graphics g = d.getGraphics();
    for (int y = 0; y < half + extra; y++)
      g.drawRegion(src, 0, y * half / (half + extra), w, 1, 0, 0, y, Graphics.TOP | Graphics.LEFT);
    g.drawRegion(src, 0, half, w, h - half, 0, 0, half + extra, Graphics.TOP | Graphics.LEFT);
    return d;
  }

  // scaled to cover w x h, centred
  static Image cover(Image src, int w, int h) {
    int sw0 = src.getWidth(), sh0 = src.getHeight();
    int[] in = new int[sw0 * sh0];
    src.getRGB(in, 0, sw0, 0, 0, sw0, sh0);
    int[] out = new int[w * h];
    int scaledW = sw0 * h / sh0, ox = (scaledW - w) / 2;
    for (int y = 0; y < h; y++) {
      int sy = y * sh0 / h;
      for (int x = 0; x < w; x++) out[y * w + x] = in[sy * sw0 + (x + ox) * sh0 / h];
    }
    return Image.createRGBImage(out, w, h, false);
  }

  // ---------------- controls screen (show_controls) ----------------
  void drawControls(Graphics g) {
    drawPicture(g, Pic.BG_TITLE);
    Res.fillAlpha(g, 0, 0, sw, sh, 0xaa000000);
    int top = landscape ? 0 : 40, keyX = landscape ? 140 : 96;
    Res.textCentered(g, Res.BLUE, "- Controls -", sw / 2, top + 15, 2);
    String[] names = { "Left", "Right", "Jump", "Duck", "Power / Run", "Run", "Jump + walk", "Pause menu" };
    String[] k = rotate
        ? new String[] { "2 / Joystick up", "8 / Joystick down", "6, 5 / Joystick right", "4 / Joystick left", "0", "1 / 7", "3 / 9", "Softkey, * or #" }
        : new String[] { "4 / Joystick left", "6 / Joystick right", "2, 5 / Centre key", "8 / Joystick down", "0", "1 / 3", "7 / 9", "Softkey, * or #" };
    int[] km = keymap();
    for (int i = 0; i < 5; i++) {               // the bound key, then the joystick when it has one
      int sl = k[i].indexOf(" / Joystick");
      k[i] = keyName(km[i]) + (i == 2 ? (rotate ? " / Joystick right" : ", 5 / Centre key") : sl >= 0 ? k[i].substring(sl) : "");
    }
    for (int i = 0; i < names.length; i++) {
      int y = top + 50 + i * 11;
      Res.text(g, Res.WHITE, names[i], 5, y, 1);
      Res.text(g, Res.GOLD, k[i], keyX, y, 1);
    }
    String[] hint = wrap(rotate ? "Hold the phone keypad-right. Change keys in Options > Keyboard Setup"
        : "Change them in Options > Keyboard Setup", sw / 8);
    for (int i = 0; i < hint.length; i++) Res.text(g, Res.WHITE, hint[i], 0, top + 180 + i * 10, 1);
    Res.text(g, Res.WHITE, "Press any key", 0, top + 215, 1);
  }

  // ---------------- Options > Option Help ----------------
  // what each option does, a page per option like the Controls screen: name, then one line per choice
  static final String[][] HELP = {
    { "Sound",
      "Normal|Every sound effect, like the original.",
      "Fast|An effect still playing isn't restarted. Fewer stalls.",
      "MIDI|Effects as short MIDI notes. For phones that freeze when a sound plays.",
      "Nokia|Effects through Nokia's own sound system. Try it on Nokia phones that freeze.",
      "Off|No sound effects." },
    { "Music",
      "Normal|MP3 recordings of the original songs (MIDI on phones without MP3).",
      "MIDI|Chiptune MIDI versions. Much lighter on slow phones.",
      "Off|No music." },
    { "Detail",
      "Full|Everything, like SuperTux 0.1.3.",
      "Low|Plain sky instead of the background picture, no snow or clouds. Faster.",
      "Potato|Also no animations or effects; defeated enemies vanish. Fastest." },
    { "FPS cap",
      "Max|Up to 33 frames a second.",
      "25, 20, 15|Fewer frames give the phone spare time, which can smooth out sound and music." },
    { "RAM saver",
      "On|Uses less memory and looks the same, but runs a bit slower. For phones that crash or lose sounds when memory runs out.",
      "Off|Keeps pictures ready in memory for speed." },
    { "Other options",
      "Show FPS|Frames per second under the score.",
      "Landscape|The 320x240 layout. On an upright phone it is turned sideways.",
      "Show touch|On-screen buttons and a MENU box for touch screens.",
      "Keyboard Setup|Pick the key for each action." }
  };
  boolean helpOpen; int helpPage;

  void helpInput(int ev) {
    if ((ev & K_ESC) != 0) { helpOpen = false; return; }
    if ((ev & (K_LEFT | K_UP)) != 0) { if (helpPage > 0) helpPage--; return; }
    if ((ev & (K_SELECT | K_RIGHT | K_DOWN | K_FIRE)) != 0 && ++helpPage >= HELP.length) helpOpen = false;
  }

  void drawHelp(Graphics g) {
    drawPicture(g, Pic.BG_TITLE);
    Res.fillAlpha(g, 0, 0, sw, sh, 0xc8000000);
    int top = landscape ? 0 : 20, per = sw / 8 - 2;
    String[] p = HELP[helpPage];
    Res.textCentered(g, Res.BLUE, "- " + p[0] + " -", sw / 2, top + 12, 2);
    int y = top + 36;
    for (int i = 1; i < p.length; i++) {
      int bar = p[i].indexOf('|');
      Res.text(g, Res.GOLD, p[i].substring(0, bar), 5, y, 1);
      String[] l = wrap(p[i].substring(bar + 1), per);
      for (int j = 0; j < l.length; j++) Res.text(g, Res.WHITE, l[j], 13, y + 11 + j * 10, 1);
      y += 11 + l.length * 10 + 6;
    }
    Res.text(g, Res.WHITE, "Page " + (helpPage + 1) + "/" + HELP.length, 0, sh - 22, 1);
    Res.text(g, Res.WHITE, landscape ? "5: next page   * or #: close" : "5 next page, * close", 0, sh - 11, 1);
  }

  // ---------------- level flow ----------------
  void startLevel(String id) {
    levelId = id; demo = false;
    tux.dying = Player.DYING_NOT;
    level = null;
    Menu.setCurrent(null, now);
    loadLevel(true);
  }

  void loadLevel(boolean intro) {
    float oldX = level != null && tux.dying != Player.DYING_NOT ? tux.x : -1;
    if (ramSaver) {                    // let go of everything the level doesn't need before loading it
      level = null; tcLevel = null; tc = null; tcG = null; touchImg = null;
      bgImage = null; bgTall = null;     // loadBackground loads it again (the sky colour stays)
      if (!demo) logoImg = null;
      numbersImg = null; textImg = null; textTall = null; textImgNo = -1;
      System.gc();
    }
    try {
      level = testLevel != null ? new Level(testLevel) : Level.load(levelId);
    } catch (Exception e) {
      state = ERROR; message = "Level " + levelId + ": " + e; return;
    }
    loadBackground();
    tux.init(level);
    if (!demo) applyBonus();
    if (oldX >= 0) {
      // respawn at the nearest reset point behind Tux
      int bx = -1, by = -1;
      for (int i = 0; i < level.resetX.length; i++)
        if ((level.resetX[i] & 0xffff) - 320 < oldX && bx < (level.resetX[i] & 0xffff)) { bx = level.resetX[i] & 0xffff; by = level.resetY[i]; }
      if (bx != -1) { tux.x = bx; tux.y = by; tux.syncOld(); scrollX = bx - 320; }
      else scrollX = 0;
    } else scrollX = 0;
    clampScroll();
    scrollingT = 0;
    endSequence = 0;
    badguys.removeAllElements(); items.removeAllElements(); bullets.removeAllElements();
    for (int i = 0; i < level.objKind.length; i++)
      addBadGuy(level.objX[i] & 0xffff, level.objY[i], level.objKind[i], level.objStay[i] != 0);
    for (int i = 0; i < NE; i++) { bdOn[i] = false; bbOn[i] = false; pbEnd[i] = 0; fsEnd[i] = 0; }
    initParticles();
    music.song = level.music;
    timeEnd = 0;
    if (demo) { music.type = Music.NONE; return; }
    if (intro) {           // GameSession::levelintro: wait 1 to 3 seconds
      state = INTRO; stateMin = now + 1000; stateT = now + 3000; music.stop(); music.type = Music.NONE;
      clearEvents();
    } else startPlay();
  }

  // the level's background picture: kept for Full (without RAM saver), otherwise only its top colour.
  // Also called when Detail or RAM saver change, so switching back to Full brings the picture back
  // straight away instead of at the next level
  void loadBackground() {
    if (level == null) return;
    boolean keepBg = detail == FULL;          // Low and Potato only need its colour
    boolean wantTall = keepBg && !ramSaver;   // the portrait copy with a taller sky: RAM saver skips it
    if (level.background != 255 && (level.background != bgImageNo || (keepBg && bgImage == null)
        || (wantTall && bgTall == null))) {
      bgImage = null; bgTall = null;
      try {
        bgImage = Image.createImage("/b/" + level.background + ".png");
        int[] px = new int[1];
        bgImage.getRGB(px, 0, 1, 0, 0, 1, 1);
        skyColor = px[0] & 0xffffff;
      } catch (Exception e) { bgImage = null; skyColor = level.topColor; }
      bgImageNo = level.background;
      bgTall = wantTall && bgImage != null ? tall(bgImage, 80) : null;
    }
    if (!keepBg) { bgImage = null; bgTall = null; }
    if (!wantTall) bgTall = null;
    if (level.background == 255) { bgImage = null; bgImageNo = -1; skyColor = level.topColor; }
  }

  // World::apply_bonuses
  void applyBonus() {
    if (bonus == WorldMap.FLOWER_BONUS) tux.gotCoffee = true;
    if (bonus != WorldMap.NO_BONUS) tux.grow();
  }

  void startPlay() {
    state = PLAY;
    timeEnd = now + level.time * 1000L;
    music.play(Music.LEVEL);
  }

  // GameSession's exit status goes back to the world map
  void levelExit(int status) {
    Menu.setCurrent(null, now);
    boolean coffee = tux.gotCoffee, big = tux.size == Player.BIG;
    level = null;
    if (testMode) {                          // back to the level editor (le_testlevel)
      testMode = false; testLevel = null; resetStatus(); music.stop(); music.type = Music.NONE;
      state = EDITOR; Menu.setCurrent(null, now);
      return;
    }
    if (status == WorldMap.ES_GAME_OVER) hsCandidate = score;
    if (worldmap == null) { singleLevel = false; resetStatus(); enterTitle(); return; }
    boolean quit = worldmap.levelDone(status, coffee, big);
    if (state == TEXT) return;              // extro and credits
    if (quit) { enterTitle(); return; }
    state = WORLDMAP;
    music.song = worldmap.music;
    music.type = Music.NONE;
    music.play(Music.LEVEL);
  }

  void initParticles() {
    ptCount = 0; ptKind = level.particles;
    if (ptKind == 2) {               // snow
      ptVW = 1280;
      ptCount = 128;
      for (int i = 0; i < ptCount; i++) {
        ptX[i] = Math.abs(rnd.nextInt()) % 1280;
        ptY[i] = Math.abs(rnd.nextInt()) % 480;
        ptLayer[i] = i % 2;
        int size = Math.abs(rnd.nextInt()) % 3;
        ptImg[i] = Spr.SNOW0 + size;
        float sp;
        do { sp = size / 60f + (Math.abs(rnd.nextInt()) % 10) / 300f; } while (sp < 0.01f);
        ptSpeed[i] = sp * level.gravity;
      }
    } else if (ptKind == 1) {        // clouds
      ptVW = 2000;
      ptCount = 15;
      for (int i = 0; i < ptCount; i++) {
        ptX[i] = Math.abs(rnd.nextInt()) % 2000;
        ptY[i] = Math.abs(rnd.nextInt()) % 480;
        ptLayer[i] = 0; ptImg[i] = Spr.CLOUD;
        ptSpeed[i] = -(250 + Math.abs(rnd.nextInt()) % 200) / 1000f;
      }
    }
  }

  // ---------------- world objects (world.cpp) ----------------
  BadGuy addBadGuy(float x, float y, int kind, boolean stay) {
    BadGuy b = new BadGuy(this, x, y, kind, stay);
    badguys.addElement(b);
    return b;
  }

  void addScore(float sx, float y, int s) {
    score += s;
    for (int i = 0; i < NE; i++) if (fsEnd[i] == 0) {
      fsX[i] = sx; fsY[i] = y - 16; fsEnd[i] = now + 1000; fsText[i] = String.valueOf(s); return;
    }
  }

  void tryGrabDistro(float x, float y, boolean bounce) {
    int t = level.tileAtPos(x, y);
    if ((Res.tFlags[t] & Res.DISTRO) != 0) {
      level.change(x, y, Res.tNext[t]);
      sound(SND_DISTRO);
      if (bounce) addBouncyDistro(((int) (x + 1) / 32) * 32, ((int) y / 32) * 32);
      score += SCORE_DISTRO;
      distros++;
    }
  }

  int distroCounter; boolean countingDistros;
  void tryBreakBrick(float x, float y, boolean small, int side) {
    int t = level.tileAtPos(x, y);
    int f = Res.tFlags[t];
    if ((f & Res.BRICK) != 0) {
      if (Res.tData[t] > 0) {
        addBouncyDistro(((int) (x + 1) / 32) * 32, ((int) y / 32) * 32);
        if (!countingDistros) { countingDistros = true; distroCounter = 5; }
        else distroCounter--;
        if (distroCounter <= 0) { countingDistros = false; level.change(x, y, Res.tNext[t]); }
        sound(SND_DISTRO);
        score += SCORE_DISTRO;
        distros++;
      } else if (!small) {
        level.change(x, y, Res.tNext[t]);
        float bx = ((int) (x + 1) / 32) * 32, by = ((int) y / 32) * 32;
        addPiece(t, bx, by, -1, -4); addPiece(t, bx, by + 16, -1.5f, -3);
        addPiece(t, bx + 16, by, 1, -4); addPiece(t, bx + 16, by + 16, 1.5f, -3);
        sound(SND_BRICK);
        score += SCORE_BRICK;
      }
    } else if ((f & Res.FULLBOX) != 0) tryEmptyBox(x, y, side);
  }

  void tryEmptyBox(float x, float y, int side) {
    int t = level.tileAtPos(x, y);
    if ((Res.tFlags[t] & Res.FULLBOX) == 0) return;
    int dir = side == Player.LEFT ? Item.RIGHT : Item.LEFT;
    int posx = ((int) (x + 1) / 32) * 32, posy = ((int) y / 32) * 32 - 32;
    switch (Res.tData[t]) {
      case 1:
        addBouncyDistro(posx, posy);
        sound(SND_DISTRO);
        score += SCORE_DISTRO;
        distros++;
        break;
      case 2:
        items.addElement(new Item(this, posx, posy, dir, tux.size == Player.SMALL ? Item.GROWUP : Item.ICEFLOWER));
        sound(SND_UPGRADE);
        break;
      case 3: items.addElement(new Item(this, posx, posy, dir, Item.HERRING)); break;
      case 4: items.addElement(new Item(this, posx, posy, dir, Item.ONEUP)); break;
    }
    level.change(x, y, Res.tNext[t]);
  }

  void tryBumpBadguy(float x, float y) {
    for (int i = 0; i < badguys.size(); i++) {
      BadGuy b = (BadGuy) badguys.elementAt(i);
      if (b.x >= x - 32 && b.x <= x + 32 && b.y >= y - 16 && b.y <= y + 16) b.bump();
    }
    for (int i = 0; i < items.size(); i++) {
      Item it = (Item) items.elementAt(i);
      if (it.h == 32 && it.x >= x - 32 && it.x <= x + 32 && it.y >= y - 16 && it.y <= y + 16) it.bump();
    }
  }

  void bumpBrick(float x, float y) {
    float bx = ((int) (x + 1) / 32) * 32, by = ((int) y / 32) * 32;
    for (int i = 0; i < NE; i++) if (!bbOn[i]) {
      bbOn[i] = true; bbX[i] = bx; bbY[i] = by; bbOff[i] = 0; bbM[i] = -0.9f;
      bbTile[i] = level.tileAtPos(bx, by);
      level.mark((int) bx >> 5);       // the cache leaves the brick out while it bounces
      break;
    }
    sound(SND_BRICK);
  }

  void addBouncyDistro(float x, float y) {
    for (int i = 0; i < NE; i++) if (!bdOn[i]) { bdOn[i] = true; bdX[i] = x; bdY[i] = y; bdYm[i] = -2; return; }
  }

  void addPiece(int tile, float x, float y, float xm, float ym) {
    for (int i = 0; i < NE; i++) if (pbEnd[i] == 0) {
      pbTile[i] = tile; pbX[i] = x; pbY[i] = y; pbXm[i] = xm; pbYm[i] = ym; pbEnd[i] = now + 200; return;
    }
  }

  void addBullet(float x, float y, float xm, int dir) {
    if (bullets.size() >= 2) return;
    bullets.addElement(new Bullet(this, x, y, xm, dir));
    sound(SND_SHOOT);
  }

  // ---------------- update ----------------
  void action(float fr) {
    tux.action(fr);
    tux.checkBounds();
    scrolling(fr);
    for (int i = 0; i < NE; i++) {
      if (bdOn[i]) { bdY[i] += bdYm[i] * fr; bdYm[i] += 0.1f * fr; if (bdYm[i] >= 0) bdOn[i] = false; }
      if (pbEnd[i] != 0) {
        pbX[i] += pbXm[i] * fr; pbY[i] += pbYm[i] * fr;
        if (now >= pbEnd[i]) pbEnd[i] = 0;
      }
      if (bbOn[i]) {
        bbOff[i] += bbM[i] * fr;
        if (bbOff[i] < -8) bbM[i] = 0.9f;
        if (bbOff[i] >= 0) { bbOn[i] = false; level.mark((int) bbX[i] >> 5); }
      }
      if (fsEnd[i] != 0) { fsY[i] -= 2 * fr; if (now >= fsEnd[i]) fsEnd[i] = 0; }
    }
    for (int i = 0; i < bullets.size(); i++) ((Bullet) bullets.elementAt(i)).action(fr);
    for (int i = 0; i < items.size(); i++) ((Item) items.elementAt(i)).action(fr);
    for (int i = 0; i < badguys.size(); i++) ((BadGuy) badguys.elementAt(i)).action(fr);
    // particles (not drawn below Full detail, so not moved either)
    if (detail != FULL) { }
    else if (ptKind == 2) {
      for (int i = 0; i < ptCount; i++) {
        ptY[i] += ptSpeed[i] * fr;
        if (ptY[i] > 480) { ptY[i] = ptY[i] % 480; ptX[i] = Math.abs(rnd.nextInt()) % 1280; }
      }
    } else if (ptKind == 1) for (int i = 0; i < ptCount; i++) ptX[i] += ptSpeed[i] * fr;

    collisions();
    for (int i = badguys.size() - 1; i >= 0; i--) if (((BadGuy) badguys.elementAt(i)).removable) badguys.removeElementAt(i);
    for (int i = items.size() - 1; i >= 0; i--) if (((Item) items.elementAt(i)).removable) items.removeElementAt(i);
    for (int i = bullets.size() - 1; i >= 0; i--) if (((Bullet) bullets.elementAt(i)).removable) bullets.removeElementAt(i);
  }

  // World::collision_handler
  void collisions() {
    for (int i = 0; i < bullets.size(); i++) {
      Bullet bl = (Bullet) bullets.elementAt(i);
      if (bl.removable) continue;
      for (int j = 0; j < badguys.size(); j++) {
        BadGuy b = (BadGuy) badguys.elementAt(j);
        if (b.dying != BadGuy.DYING_NOT || b.removable) continue;
        if (bl.overlaps(b)) { b.killMe(10); bl.removable = true; break; }
      }
    }
    int n = badguys.size();
    for (int i = 0; i < n; i++) {
      BadGuy a = (BadGuy) badguys.elementAt(i);
      if (a.dying != BadGuy.DYING_NOT || a.removable || !a.seen) continue;   // not yet on screen: not moving
      for (int j = i + 1; j < n; j++) {
        BadGuy b = (BadGuy) badguys.elementAt(j);
        if (b.dying != BadGuy.DYING_NOT || b.removable || !b.seen) continue;
        if (a.overlaps(b)) { b.collideBadguy(a); a.collideBadguy(b); }
      }
    }
    if (tux.dying != Player.DYING_NOT) return;
    for (int i = 0; i < badguys.size(); i++) {
      BadGuy b = (BadGuy) badguys.elementAt(i);
      if (b.dying != BadGuy.DYING_NOT || b.removable) continue;
      if (b.overlaps(tux)) {
        if (tux.prevY < tux.y && tux.prevY + tux.prevH < b.y + b.h / 2 && !tux.check(tux.invincibleT))
          b.squish(tux);
        else { tux.collideBadguy(b); b.collidePlayer(tux); }
      }
    }
    for (int i = 0; i < items.size(); i++) {
      Item it = (Item) items.elementAt(i);
      if (!it.removable && it.overlaps(tux)) it.collect(tux);
    }
  }

  void scrolling(float fr) {
    if (level.autoscroll != 0) { scrollX += level.autoscroll * fr; clampScroll(); return; }
    int xSpace = viewW * 3 / 5;          // 0.1.3: 384 of 640
    int tuxPos = (int) (tux.x + tux.w / 2);
    if (level.backScrolling) {
      if (tux.oldDir != tux.dir) scrollingT = now + 2000;
      if (scrollingT != 0 && now < scrollingT) {
        float fin;
        if (tux.vx > 0) fin = tuxPos - (viewW - xSpace);
        else if (tux.vx < 0) fin = tuxPos - xSpace;
        else fin = tux.dir == Player.RIGHT ? tuxPos - (viewW - xSpace) : tuxPos - xSpace;
        scrollX += (fin - scrollX) / (fr * 20) + (tux.vx * fr + tux.ax * fr * fr);
      } else follow(tuxPos, xSpace);
    } else follow(tuxPos, xSpace);
    clampScroll();
  }

  void follow(int tuxPos, int xSpace) {
    if (tux.vx > 0 && scrollX < tuxPos - (viewW - xSpace)) scrollX = tuxPos - (viewW - xSpace);
    else if (tux.vx < 0 && scrollX > tuxPos - xSpace && level.backScrolling) scrollX = tuxPos - xSpace;
    else if (tux.dir == Player.RIGHT && scrollX < tuxPos - (viewW - xSpace)) scrollX = tuxPos - (viewW - xSpace);
    else if (tux.dir == Player.LEFT && scrollX > tuxPos - xSpace && level.backScrolling) scrollX = tuxPos - xSpace;
  }

  void clampScroll() {
    if (scrollX > level.width * 32 - viewW) scrollX = level.width * 32 - viewW;
    if (scrollX < 0) scrollX = 0;
  }

  // GameSession::check_end_conditions
  void checkEnd() {
    int endpos = (level.width - 5) * 32;
    int goal = goalTile();
    if (endSequence == 0 && tux.x >= endpos) beginEnd(2);
    else if (endSequence != 0 && now >= endT) levelExit(WorldMap.ES_FINISHED);
    else if (endSequence == 1 && goal >= 0 && Res.tData[goal] >= 1) endSequence = 2;
    else if (endSequence == 0 && goal >= 0 && Res.tData[goal] == 0) beginEnd(1);
    else if (endSequence == 0 && tux.isDead()) {
      bonus = WorldMap.NO_BONUS;
      if (testMode) { if (lives < 0) levelExit(WorldMap.ES_GAME_OVER); else loadLevel(false); }
      else if (lives < 0) {        // drawendscreen: 2 to 5 seconds
        state = GAMEOVER; stateMin = now + 2000; stateT = now + 5000; music.stop(); music.type = Music.NONE;
        clearEvents();
      } else loadLevel(true); // restart_level: the level intro again, then the nearest reset point
    }
  }

  void beginEnd(int kind) {
    endSequence = kind; lastX = -1;
    music.play(Music.DONE);
    endT = now + 7000;
    tux.invincibleT = now + 7000;
  }

  int goalTile() {
    int sx = (int) tux.x / 32, sy = (int) tux.y / 32;
    int mx = (int) (tux.x + tux.w), my = (int) (tux.y + tux.h);
    for (int x = sx; x * 32 < mx; ++x)
      for (int y = sy; y * 32 < my; ++y) {
        int t = level.tileAt(x, y);
        if ((Res.tFlags[t] & Res.GOAL) != 0) return t;
      }
    return -1;
  }

  void handleInput() {
    if (endSequence != 0) {
      tux.oldFire = tux.inFire;
      tux.inFire = false; tux.inLeft = false; tux.inRight = true; tux.inDown = false;
      tux.inUp = (int) lastX == (int) tux.x;
      lastX = tux.x;
      return;
    }
    int k = currentKeys();
    tux.oldFire = tux.inFire;
    tux.inLeft = (k & K_LEFT) != 0;
    tux.inRight = (k & K_RIGHT) != 0;
    tux.inUp = (k & (K_UP | K_SELECT)) != 0;
    tux.inDown = (k & K_DOWN) != 0;
    tux.inFire = (k & K_FIRE) != 0;
  }

  int currentKeys() {
    int ks = getKeyStates();
    int k = keys;
    int up = UP_PRESSED, down = DOWN_PRESSED, left = LEFT_PRESSED, right = RIGHT_PRESSED;
    if (rotate) { up = RIGHT_PRESSED; down = LEFT_PRESSED; left = UP_PRESSED; right = DOWN_PRESSED; }
    if ((ks & left) != 0) k |= K_LEFT;
    if ((ks & right) != 0) k |= K_RIGHT;
    if ((ks & up) != 0) k |= K_UP;
    if ((ks & down) != 0) k |= K_DOWN;
    if ((ks & FIRE_PRESSED) != 0) k |= K_SELECT;
    return k | touchKeys;
  }

  // Number keys. Upright: 4/6 walk, 1/3 run, 2 jump, 5 jump/select, 7/9 jump while walking, 8 duck,
  // 0 fire/run. Sideways (keypad on the right) the same layout as seen by the player.
  int keyBits(int code) {
    int[] km = keymap(), d = keyDefaults();
    for (int i = 0; i < 5; i++) if (code == km[i]) return KEY_BIT[i];
    if (code == KEY_POUND) return K_POUND;
    for (int i = 0; i < 5; i++) if (code == d[i]) return 0;     // a default key that was moved away
    switch (code) {                        // the extras: run left/right, jump while walking, select
      case KEY_NUM1: return K_LEFT | K_FIRE;
      case KEY_NUM3: return rotate ? K_LEFT | K_UP : K_RIGHT | K_FIRE;
      case KEY_NUM7: return rotate ? K_RIGHT | K_FIRE : K_LEFT | K_UP;
      case KEY_NUM9: return K_RIGHT | K_UP;
      case KEY_NUM5: return K_SELECT;
    }
    return 0;
  }

  // menus and screens always use the standard keypad (a rebound key mustn't change what 2/8/5 do there)
  int navBits(int code) {
    if (rotate) {
      switch (code) {
        case KEY_NUM2: return K_LEFT; case KEY_NUM8: return K_RIGHT; case KEY_NUM6: return K_UP;
        case KEY_NUM4: return K_DOWN; case KEY_NUM5: return K_SELECT; case KEY_NUM0: return K_FIRE;
        case KEY_NUM1: return K_LEFT | K_FIRE; case KEY_NUM7: return K_RIGHT | K_FIRE;
      }
    } else switch (code) {
      case KEY_NUM4: return K_LEFT; case KEY_NUM6: return K_RIGHT; case KEY_NUM2: return K_UP;
      case KEY_NUM8: return K_DOWN; case KEY_NUM5: return K_SELECT; case KEY_NUM0: return K_FIRE;
      case KEY_NUM1: return K_LEFT | K_FIRE; case KEY_NUM3: return K_RIGHT | K_FIRE;
    }
    if (code == KEY_POUND) return K_POUND;
    return 0;
  }

  int pressBits(int code) {
    if (code == -6 || code == -7) return K_ESC | K_SOFT | K_ANY;
    if (state == PLAY && Menu.current == null && bound(code)) return keyBits(code) | K_ANY;   // * or # bound to an action
    if (code == KEY_STAR) return K_ESC | K_STAR | K_ANY;
    if (code == KEY_POUND) return K_ESC | K_POUND | K_ANY;
    if (code == -8) return K_CLEAR | K_ANY;
    int b = navBits(code);
    if (b == 0) {
      int a = 0;
      try { a = getGameAction(code); } catch (Throwable e) { }
      if (rotate) {
        if (a == UP) b = K_LEFT; else if (a == DOWN) b = K_RIGHT; else if (a == LEFT) b = K_DOWN; else if (a == RIGHT) b = K_UP;
      } else {
        if (a == UP) b = K_UP; else if (a == DOWN) b = K_DOWN; else if (a == LEFT) b = K_LEFT; else if (a == RIGHT) b = K_RIGHT;
      }
      if (a == FIRE) b = K_SELECT;
    }
    return b | K_ANY;
  }

  public void keyPressed(int code) {
    keys |= keyBits(code);
    if (code == 0) return;
    synchronized (evq) {
      int next = (evTail + 1) % evq.length;
      if (next != evHead) { evq[evTail] = code; evTail = next; }
    }
  }

  public void keyReleased(int code) { keys &= ~keyBits(code); }

  // ---------------- touch ----------------
  static final int T_PRESS = 1, T_DRAG = 2, T_RELEASE = 3;
  private final int[] pq = new int[24];
  private int pqHead, pqTail;
  int touchKeys;                         // what the finger holds down, like key bits

  public void pointerPressed(int x, int y) { pointer(T_PRESS, x, y); }
  public void pointerDragged(int x, int y) { pointer(T_DRAG, x, y); }
  public void pointerReleased(int x, int y) { pointer(T_RELEASE, x, y); }

  // screen to game coordinates (sideways landscape is drawn turned 90 degrees)
  private void pointer(int type, int x, int y) {
    int W = getWidth(), H = getHeight(), lx, ly;
    if (rotate) { int ox = (W - 240) / 2, oy = (H - 320) / 2; lx = y - oy; ly = 239 - (x - ox); }
    else { lx = x - (W - sw) / 2; ly = y - (H - sh) / 2; }
    synchronized (pq) {
      int next = (pqTail + 3) % pq.length;
      if (type == T_DRAG && pqHead != pqTail) {            // keep only the newest drag
        int last = (pqTail - 3 + pq.length) % pq.length;
        if (pq[last] == T_DRAG) { pq[last + 1] = lx; pq[last + 2] = ly; return; }
      }
      if (next == pqHead) return;
      pq[pqTail] = type; pq[pqTail + 1] = lx; pq[pqTail + 2] = ly; pqTail = next;
    }
  }

  // the on-screen buttons: x, y, w, h, key bits, label
  static final String[] T_LABEL = { "<^", "^", "^>", "<", "v", ">", "FIRE", "JUMP" };
  static final int[] T_BITS = { K_LEFT | K_UP, K_UP, K_RIGHT | K_UP, K_LEFT, K_DOWN, K_RIGHT, K_FIRE, K_UP };
  int[] touchRect(int i) {
    int h = landscape ? 40 : 48, y = sh - h;
    if (i < 6) return new int[] { (i % 3) * 40, y + (i / 3) * (h / 2), 40, h / 2 };
    return new int[] { sw - 120 + (i - 6) * 60, y, 60, h };
  }

  int touchButton(int x, int y) {
    for (int i = 0; i < 8; i++) { int[] r = touchRect(i); if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) return i; }
    return -1;
  }

  // Portrait below Full (without RAM saver): the buttons sit under the level on a plain sky fill, so
  // they are drawn once into an image and only redrawn when a different button is held or the sky
  // colour changes. Otherwise drawn each frame; in Potato as outlines and labels only.
  Image touchImg; int touchImgKey = -1;

  void drawTouch(Graphics g) {
    int pressed = 0;
    for (int i = 0; i < 8; i++)
      if ((touchKeys & T_BITS[i]) == T_BITS[i] && touchKeys != 0 && touchButton(lastTouchX, lastTouchY) == i) pressed |= 1 << i;
    if (!landscape && detail != FULL && !ramSaver) {
      int h = touchRect(6)[3], y0 = sh - h, sky = skyFill();
      int key = pressed ^ (sky << 8);
      if (touchImg == null || touchImg.getWidth() != sw || touchImg.getHeight() != h) { touchImg = Image.createImage(sw, h); touchImgKey = -1; }
      if (key != touchImgKey) {
        Graphics tg = touchImg.getGraphics();
        tg.setColor(sky); tg.fillRect(0, 0, sw, h);
        tg.translate(0, -y0);
        drawButtons(tg, pressed, false);
        touchImgKey = key;
      }
      g.drawImage(touchImg, 0, y0, Graphics.TOP | Graphics.LEFT);
      return;
    }
    drawButtons(g, pressed, detail == POTATO);   // Potato: outlines and labels only
  }

  void drawButtons(Graphics g, int pressed, boolean plain) {
    for (int i = 0; i < 8; i++) {
      int[] r = touchRect(i);
      boolean on = (pressed & (1 << i)) != 0;
      if (!plain) Res.fillAlpha(g, r[0], r[1], r[2], r[3], on ? 0xa0ffffff : i < 6 ? 0x508080b4 : i == 6 ? 0x5080b480 : 0x50b48080);
      g.setColor(plain && on ? 0xffff00 : 0xffffff); g.drawRect(r[0], r[1], r[2] - 1, r[3] - 1);
      Res.textCentered(g, Res.WHITE, T_LABEL[i], r[0] + r[2] / 2, r[1] + (r[3] - 9) / 2, plain ? 0 : 1);
    }
  }
  int lastTouchX = -1, lastTouchY = -1;

  // the MENU box while playing with Show touch: right edge, under the HUD
  static final int MENU_Y = 22, MENU_H = 14;
  boolean inMenuCorner(int x, int y) { return x >= sw - 48 && y >= MENU_Y - 4 && y < MENU_Y + MENU_H + 4; }
  void drawMenuCorner(Graphics g) {
    if (detail == POTATO) { g.setColor(0xffffff); g.drawRect(sw - 48, MENU_Y, 47, MENU_H - 1); }
    else Res.fillAlpha(g, sw - 48, MENU_Y, 48, MENU_H, 0x508080b4);
    Res.textCentered(g, Res.WHITE, "MENU", sw - 24, MENU_Y + 3, detail == POTATO ? 0 : 1);
  }

  // one finger: turns taps into the same presses and held keys the keypad gives
  int touch(int type, int x, int y) {
    lastTouchX = x; lastTouchY = y;
    if (helpOpen) return type == T_PRESS ? K_SELECT | K_ANY : 0;     // a tap turns the page
    Menu m = Menu.current;
    if (type == T_RELEASE) { touchKeys = 0; return 0; }
    if (m != null && state != NOTICE) {
      if (type != T_PRESS) return 0;
      int i = m.itemAt(x, y);
      if (i >= 0) {
        m.active = i;
        if (m.kind[i] == Menu.STRINGSELECT) return x < m.boxX + m.boxW / 2 ? K_LEFT : K_RIGHT;
        return K_SELECT | K_ANY;
      }
      return i == -2 ? K_ESC | K_SOFT | K_ANY : 0;
    }
    switch (state) {
      case PLAY: {
        if (!showTouch) return type == T_PRESS ? K_ESC | K_SOFT | K_ANY : 0;   // no buttons: any tap pauses
        if (type == T_PRESS && inMenuCorner(x, y)) { touchKeys = 0; return K_ESC | K_SOFT | K_ANY; }
        int b = touchButton(x, y);
        touchKeys = b >= 0 ? T_BITS[b] : 0;   // a stray tap on the level does nothing (no accidental pauses)
        return 0;
      }
      case WORLDMAP: {
        int tx = (worldmap.posX() + worldmap.camX) / 2 + 8, ty = (worldmap.posY() + worldmap.camY) / 2 + 3;
        if (type == T_PRESS && x > sw - 48 && y < 16) return K_ESC | K_SOFT | K_ANY;    // the MENU corner
        int dx = x - tx, dy = y - ty;
        if (Math.abs(dx) < 14 && Math.abs(dy) < 14) { touchKeys = 0; return type == T_PRESS ? K_SELECT | K_ANY : 0; }
        touchKeys = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? K_LEFT : K_RIGHT) : (dy < 0 ? K_UP : K_DOWN);
        return 0;
      }
      case EDITOR:
        if (y >= 240 && !editor.help) { touchKeys = 0; return 0; }      // the minimap under the portrait editor
        if (editor.touch(type, x, y)) return K_ANY;
        touchKeys = K_SELECT;
        return 0;
    }
    return type == T_PRESS ? K_SELECT | K_ANY : 0;
  }

  int nextEvent() {
    synchronized (evq) {
      if (evHead == evTail) return 0;
      int b = evq[evHead]; evHead = (evHead + 1) % evq.length;
      return b;
    }
  }

  void clearEvents() { synchronized (evq) { evHead = evTail; } }

  // the phone took the screen (a call): pause like 0.1.3's escape key
  // a call, a message or the menu key took the screen: pause and go quiet, like the Symbian build
  public void hideNotify() { systemPause(); music.suspend(); sounds.stop(); }
  public void showNotify() { music.resume(); }

  void systemPause() {
    if (state == PLAY && Menu.current == null) Menu.setCurrent(gameMenu, now);
    else if (state == WORLDMAP && Menu.current == null) Menu.setCurrent(worldmapMenu, now);
  }

  // ---------------- main loop ----------------
  public void run() {
    Graphics screen = getGraphics();
    try { Res.load(); } catch (Throwable e) { state = ERROR; message = "Load error: " + e; }
    sounds.init();
    coinTile = -1;
    if (state != ERROR) {
      for (int i = 0; i < Res.tFlags.length; i++)
        if ((Res.tFlags[i] & Res.DISTRO) != 0) { coinTile = i; break; }
      try { enterTitle(); state = NOTICE; stateT = System.currentTimeMillis() + 1000; music.stop(); } catch (Throwable e) { fail(e); }
    }
    long last = System.currentTimeMillis();
    while (running) {
      long t0 = System.currentTimeMillis();
      long dt = t0 - last; last = t0;
      if (dt > 60) dt = 60;            // slow phone or a hiccup: don't jump too far
      if (dt < 1) dt = 1;
      try {
        frame(dt);
      } catch (Throwable e) {
        fail(e);
      }
      try { paintAll(screen); } catch (Throwable e) { fail(e); }
      flushGraphics();
      fpsCount++;
      if (t0 - fpsT >= 1000) {
        int f10 = (int) (fpsCount * 10000L / Math.max(1, t0 - fpsT));
        fpsText = (f10 / 10) + "." + (f10 % 10);
        fpsCount = 0; fpsT = t0;
      }
      long spent = System.currentTimeMillis() - t0;
      int frameMs = FPSCAP_MS[fpsCap];
      if (spent < frameMs) { try { Thread.sleep(frameMs - spent); } catch (InterruptedException e) { } }
    }
  }

  void fail(Throwable e) {
    if (state == ERROR) return;
    state = ERROR; message = "Error: " + e;
    e.printStackTrace();
  }

  void frame(long dt) {
    int ev = 0, e;
    nCodes = 0;
    while ((e = nextEvent()) != 0) {
      if (nCodes < codes.length) codes[nCodes++] = e;
      if (Menu.current != null && Menu.current.key(e)) continue;      // typing into a field
      ev |= pressBits(e);
    }
    while (true) {
      int t, x, y;
      synchronized (pq) {
        if (pqHead == pqTail) break;
        t = pq[pqHead]; x = pq[pqHead + 1]; y = pq[pqHead + 2]; pqHead = (pqHead + 3) % pq.length;
      }
      ev |= touch(t, x, y);
    }
    if (helpOpen) { helpInput(ev); return; }          // everything else waits behind the help pages
    frameStates(dt, ev);
    if (state != fadeState) {
      if (!(fadeState == INTRO && state == PLAY) && state != LOADING && fadeState != LOADING) fadeStart = System.currentTimeMillis();
      fadeState = state;
    }
  }

  void frameStates(long dt, int ev) {
    if (state == NOTICE) { if (ev != 0 || System.currentTimeMillis() >= stateT) state = TITLE; return; }
    if (state == EDITOR) { now += dt; frameCounter = (int) (now / 25); editor.frame(dt, ev); return; }
    if (state == HIGHSCORE) {
      Menu m = Menu.current;
      if (m == null || (ev & K_SOFT) != 0) { hsName = highscoreMenu.inputOf(M_HSNAME); saveSettings(); enterTitle(); return; }
      m.action(menuAction(ev), now);
      if (m.check() == M_HSNAME) { hsName = highscoreMenu.inputOf(M_HSNAME); saveSettings(); enterTitle(); }
      return;
    }
    if (state == PLAY) {
      Menu m = Menu.current;
      if (testMode && (ev & K_ESC) != 0) { levelExit(WorldMap.ES_ABORT); return; }
      if (m == null && (ev & K_ESC) != 0) { Menu.setCurrent(gameMenu, now); return; }
      if (m != null) {                 // game_menu: time stops
        if ((ev & K_ESC) != 0) { Menu.pop(now); return; }
        m.action(menuAction(ev), now);
        if (m == gameMenu && gameMenu.check() == M_ABORT) { levelExit(WorldMap.ES_ABORT); return; }
        if (m == optionsMenu) optionsChanged();
        if (m == keysMenu) keysChanged();
        return;
      }
      now += dt;
      frameCounter = (int) (now / 25);
      handleInput();
      checkEnd();
      if (state == PLAY && level != null) {
        if (endSequence == 1) action(dt / 20f);
        else if (endSequence == 0) action(dt / 10f);
        if (endSequence == 0) {
          if (now >= timeEnd && tux.dying == Player.DYING_NOT) tux.kill(Player.KILL_KILL);
          if (tux.check(tux.invincibleT)) { if (music.type != Music.HERRING) music.play(Music.HERRING); }
          else if (timeEnd - now < TIME_WARNING) { if (music.type != Music.HURRYUP) music.play(Music.HURRYUP); }
          else if (music.type != Music.LEVEL) music.play(Music.LEVEL);
        }
      }
    } else if (state == INTRO) {
      now += dt;
      if (now >= stateT || (now >= stateMin && ev != 0)) startPlay();
    } else if (state == GAMEOVER) {
      now += dt;
      if (now >= stateT || (now >= stateMin && ev != 0)) levelExit(WorldMap.ES_GAME_OVER);
    } else if (state == TITLE) {
      now += dt;
      frameCounter = (int) (now / 25);
      demoFrame(dt);
      titleMenus(ev);
    } else if (state == WORLDMAP) {
      now += dt;
      frameCounter = (int) (now / 25);
      Menu m = Menu.current;
      if ((ev & K_ESC) != 0) { if (m == null) Menu.setCurrent(worldmapMenu, now); else Menu.pop(now); return; }
      worldmap.inputDirection = WorldMap.D_NONE;
      if (m == null) {
        int k = currentKeys();
        if ((k & K_LEFT) != 0) worldmap.inputDirection = WorldMap.D_WEST;
        else if ((k & K_RIGHT) != 0) worldmap.inputDirection = WorldMap.D_EAST;
        else if ((k & K_UP) != 0) worldmap.inputDirection = WorldMap.D_NORTH;
        else if ((k & K_DOWN) != 0) worldmap.inputDirection = WorldMap.D_SOUTH;
      }
      float delta = dt / 100f * 1.3f;
      worldmap.update(delta, m == null && (ev & (K_SELECT | K_FIRE)) != 0);
      if (state != WORLDMAP) return;
      if (m != null) {
        m.action(menuAction(ev), now);
        if (m == worldmapMenu && worldmapMenu.check() == M_WM_QUIT) { enterTitle(); return; }
        if (m == optionsMenu) optionsChanged();
        if (m == keysMenu) keysChanged();
      }
      if (music.type != Music.LEVEL) music.play(Music.LEVEL);
    } else if (state == TEXT) {
      textFrame(dt, ev);
    } else if (state == CONTROLS) {
      if (ev != 0) { state = TITLE; Menu.setCurrent(mainMenu, now); }
    } else if (state == LOADING) {
      if (stateT++ > 0) openWorldMap();     // one "Loading..." frame first
    }
  }

  // ---------------- drawing ----------------
  void paintAll(Graphics screen) {
    int W = getWidth(), H = getHeight();
    screen.setClip(0, 0, W, H);
    if (rotate) {
      Graphics g = buffer.getGraphics();
      draw(g);
      fade(g);
      screen.setColor(0); screen.fillRect(0, 0, W, H);
      screen.drawRegion(buffer, 0, 0, 320, 240, Sprite.TRANS_ROT90, (W - 240) / 2, (H - 320) / 2, Graphics.TOP | Graphics.LEFT);
    } else {
      int ox = (W - sw) / 2, oy = (H - sh) / 2;
      if (ox != 0 || oy != 0) { screen.setColor(0); screen.fillRect(0, 0, W, H); }
      screen.translate(ox, oy);
      screen.setClip(0, 0, sw, sh);
      draw(screen);
      fade(screen);
      screen.translate(-ox, -oy);
    }
  }

  void draw(Graphics g) {
    g.setClip(0, 0, sw, sh);
    if (helpOpen) { drawHelp(g); return; }
    if (state == ERROR) {
      g.setColor(0); g.fillRect(0, 0, sw, sh);
      int per = sw / 8 - 1, y = 2;
      for (int i = 0; i < message.length() && y < sh - 10; i += per, y += 11)
        Res.chars(g, Res.WHITE, message.substring(i, Math.min(message.length(), i + per)), 2, y);
      return;
    }
    if (state == LOADING) {
      g.setColor(0); g.fillRect(0, 0, sw, sh);
      Res.textCentered(g, Res.WHITE, "Loading...", sw / 2, sh / 4);
      return;
    }
    if (state == NOTICE) {             // the GPL notice the Symbian build shows for a second
      g.setColor(0); g.fillRect(0, 0, sw, sh);
      int y = sh / 4;
      Res.textCentered(g, Res.GOLD, "SuperTux 0.1.3", sw / 2, y, 1);
      Res.textCentered(g, Res.WHITE, "J2ME " + RELEASE, sw / 2, y + 12, 1);
      centeredWrapped(g, Res.WHITE, "This program comes with ABSOLUTELY NO WARRANTY. It is free software, and you are welcome to redistribute it under the GNU GPL version 2. See COPYING for details.", sw / 2, y + 35, 11);
      return;
    }
    if (state == EDITOR) { editor.draw(g); return; }
    if (state == HIGHSCORE) { drawHighscore(g); return; }
    if (state == TEXT) { drawText(g); return; }
    if (state == CONTROLS) { drawControls(g); return; }
    if (state == WORLDMAP) {
      worldmap.draw(g, sw, sh, landscape);
      if (showTouch && Menu.current == null) {
        Res.fillAlpha(g, sw - 48, 0, 48, 16, 0x508080b4);
        Res.textCentered(g, Res.WHITE, "MENU", sw - 24, 3, 1);
      }
      if (Menu.current != null) Menu.current.draw(g, sw / 2, sh, !landscape, now);
      return;
    }
    if (level == null) { g.setColor(0); g.fillRect(0, 0, sw, sh); return; }
    // portrait with touch buttons: the level sits above them while playing; the title screen has no
    // buttons, so its demo level stays centred like without touch
    if (!landscape) viewTop = showTouch && state != TITLE ? 64 : 160;
    if (state == INTRO || state == GAMEOVER) { screens(g); return; }
    drawWorld(g);
    if (state == TITLE) {
      if (Menu.current == mainMenu) {
        g.drawImage(logo(), landscape ? 80 : 40, landscape ? 8 : 24, Graphics.TOP | Graphics.LEFT);
        String[] c = landscape
            ? new String[] { " SuperTux 0.1.3 - " + RELEASE, "Made by SuperTux Devel Team",
                "Originally ported by Lars 'Anotherguest'", "2026 J2ME port", "by Jecka22LR" }
            : new String[] { " SuperTux 0.1.3 - J2ME", RELEASE, "Made by SuperTux Devel Team",
                "Originally ported by", "Lars 'Anotherguest'", "2026 J2ME port by Jecka22LR" };
        if (titleNote != null) {          // an edition's own line on top (x/g.txt T|...)
          String[] c2 = new String[c.length + 1];
          c2[0] = " " + titleNote;
          System.arraycopy(c, 0, c2, 1, c.length);
          c = c2;
        }
        int y0 = (landscape ? 392 : 535) - (c.length - (landscape ? 5 : 6)) * 17;
        for (int i = 0; i < c.length; i++) Res.chars(g, Res.WHITE, c[i], 0, (y0 + 17 * i) / 2);
      }
    } else {
      status(g);
      // the buttons stay while paused too: the level sits above them, so without them their space
      // would be an empty strip (the menu is drawn over them and takes the taps)
      if (showTouch && state == PLAY) {
        drawTouch(g);
        if (Menu.current == null) drawMenuCorner(g);
      }
    }
    if (Menu.current != null) Menu.current.draw(g, sw / 2, sh, !landscape, now);
  }

  // a short fade in from black when the screen changes (0.1.3's screens cut over with "Loading...")
  void fade(Graphics g) {
    long t = System.currentTimeMillis() - fadeStart;
    if (detail != POTATO && t >= 0 && t < 300) Res.fillAlpha(g, 0, 0, sw, sh, (int) (255 * (300 - t) / 300) << 24);
  }

  // save_hs: "Congratulations", the score in the big yellow numbers, and the name
  Image numbersImg;
  void drawHighscore(Graphics g) {
    drawPicture(g, Pic.BG_TITLE);
    int top = landscape ? 0 : 40;
    Res.textCentered(g, Res.BLUE, "Congratulations", sw / 2, top + 65, 2);
    Res.textCentered(g, Res.BLUE, "Your score:", sw / 2, top + 85, 1);
    if (numbersImg == null) { try { numbersImg = Image.createImage("/nu.png"); } catch (Exception e) { numbersImg = Image.createImage(1, 1); } }
    String n = String.valueOf(hsScore);
    int x = sw / 2 - n.length() * 16;
    for (int i = 0; i < n.length(); i++)
      if (numbersImg.getWidth() >= 320) g.drawRegion(numbersImg, (n.charAt(i) - '0') * 32, 0, 32, 32, 0, x + i * 32, top + 97, Graphics.TOP | Graphics.LEFT);
    if (Menu.current != null) Menu.current.draw(g, sw / 2, sh, !landscape, now);
  }

  void openEditor() {
    if (editor == null) editor = new Editor(this);
    editor.open();
    state = EDITOR;
    music.stop(); music.type = Music.NONE;
  }

  void leaveEditor() { enterTitle(); }

  // le_testlevel: play the level being edited, ESC comes back
  void playTest(Level l) {
    if (l.time == 0) l.time = 250;
    try { testLevel = l.toBytes(); } catch (Exception e) { return; }
    testMode = true; worldmap = null; resetStatus();
    levelId = "test"; demo = false; tux.dying = Player.DYING_NOT; level = null;
    Menu.setCurrent(null, now);
    loadLevel(false);
  }

  Image logoImg;
  Image logo() {
    if (logoImg == null) { try { logoImg = Image.createImage("/lo.png"); } catch (Exception e) { logoImg = Image.createImage(1, 1); } }
    return logoImg;
  }

  void drawWorld(Graphics g) {
    int sx0 = (int) scrollX;
    int top = viewTop >> 1;
    int col0 = (sx0 >> 5) - 4, col1 = (sx0 + viewW) >> 5;
    int ox = -(sx0 >> 1);
    if (detail != FULL && !ramSaver) {
      // Low and Potato: the tiles come from the cache, which covers the level's rows, so only the
      // sky above and below it needs filling; Low draws its animated tiles (coins, water) on top
      int sky = skyFill();
      g.setColor(sky);
      if (top > 0) g.fillRect(0, 0, sw, top);
      int bot = top + (Level.H << 4);
      if (bot < sh) g.fillRect(0, bot, sw, sh - bot);
      drawCached(g, sx0, top, sky);
      if (detail == LOW) { drawAnimated(g, level.bg, col0, col1, ox, top); drawAnimated(g, level.ia, col0, col1, ox, top); }
    } else {
      background(g, scrollX);
      if (detail == FULL) particles(g, 0);
      drawLayer(g, level.bg, col0, col1, ox, top, false);
      drawLayer(g, level.ia, col0, col1, ox, top, true);
    }
    if (detail != POTATO) for (int i = 0; i < NE; i++) if (bbOn[i])
      Res.drawTile(g, bbTile[i], ((int) bbX[i] - sx0) >> 1, ((int) (bbY[i] + bbOff[i]) + viewTop) >> 1, frameCounter);
    for (int i = 0; i < badguys.size(); i++) ((BadGuy) badguys.elementAt(i)).draw(g);
    tux.draw(g);
    for (int i = 0; i < bullets.size(); i++) ((Bullet) bullets.elementAt(i)).draw(g);
    if (detail != POTATO) for (int i = 0; i < NE; i++) if (fsEnd[i] != 0)
      Res.text(g, Res.GOLD, fsText[i], ((int) fsX[i] >> 1) + 8 - fsText[i].length() * 4, ((int) fsY[i] + viewTop) >> 1);
    for (int i = 0; i < items.size(); i++) ((Item) items.elementAt(i)).draw(g);
    if (detail != POTATO) for (int i = 0; i < NE; i++) {
      if (bdOn[i] && coinTile >= 0)
        Res.drawTile(g, coinTile, ((int) bdX[i] - sx0) >> 1, ((int) bdY[i] + viewTop) >> 1, 0);
      if (pbEnd[i] != 0) {
        int f = Res.tFrames[pbTile[i]].length > 0 ? Res.tFrames[pbTile[i]][0] : -1;
        if (f >= 0)
          g.drawRegion(Res.tileImg, Res.tfx[f] + (Math.abs(rnd.nextInt()) % 8), Res.tfy[f] + (Math.abs(rnd.nextInt()) % 8), 8, 8, 0,
              ((int) pbX[i] - sx0) >> 1, ((int) pbY[i] + viewTop) >> 1, Graphics.TOP | Graphics.LEFT);
      }
    }
    drawLayer(g, level.fg, col0, col1, ox, top, false);
    if (detail == FULL) particles(g, 1);
    g.setClip(0, 0, sw, sh);
  }

  Image bgTall;          // portrait: the background with a taller sky, filling the screen

  // the sky colour Low and Potato fill with (the top of the level's picture, or its gradient's top)
  int skyFill() { return bgImageNo >= 0 ? skyColor : level.topColor; }

  // Low: the animated tiles the cache leaves out, drawn each frame at their current frame
  void drawAnimated(Graphics g, byte[] map, int col0, int col1, int ox, int top) {
    int w = level.width;
    if (col0 < 0) col0 = 0;
    if (col1 >= w) col1 = w - 1;
    for (int y = 0; y < Level.H; y++) {
      int row = y * w;
      for (int x = col0; x <= col1; x++) {
        int t = map[row + x] & 0xff;
        if (t == 0 || Res.tFrames[t].length < 2 || (map == level.ia && bouncing(x, y))) continue;
        Res.drawTile(g, t, ox + (x << 4), top + (y << 4), frameCounter);
      }
    }
  }

  // Low and Potato tile cache: the background and interactive layers, over the sky colour, kept in an
  // offscreen ring of 16-pixel columns (column c lives at (c % tcN) * 16). Each frame is one or two
  // blits plus the few columns that scrolled into view, instead of a draw per visible tile. Any
  // change to the interactive tiles (Level.changes), a new level or sky colour redraws it all.
  Image tc; Graphics tcG; int tcN, tcFirst, tcLast = -1, tcChanges, tcSky, tcDetail; Level tcLevel;

  void drawCached(Graphics g, int sx0, int top, int sky) {
    int c0 = Math.max(0, sx0 >> 5), c1 = (sx0 + viewW - 1) >> 5;
    int n = c1 - c0 + 1, h = Level.H << 4;
    if (tc == null || tcN < n) {
      tc = null; tcG = null;
      tcN = n + 1;
      tc = Image.createImage(tcN << 4, h);
      tcG = tc.getGraphics();
      tcLast = -1;
    }
    if (tcLevel != level || tcSky != sky || tcDetail != detail) {
      tcLevel = level; tcSky = sky; tcDetail = detail; tcLast = -1;
    } else if (tcChanges != level.changes && tcLast >= 0) {
      // a coin, brick or bonus block changed: redraw just those columns (and up to 7 to their right,
      // which a wide tile there could reach into)
      int a = Math.max(level.chMin, tcFirst), b = Math.min(level.chMax + 7, tcLast);
      for (int c = a; c <= b; c++) cacheColumn(c, sky);
    }
    tcChanges = level.changes; level.chMin = Integer.MAX_VALUE; level.chMax = -1;
    for (int c = c0; c <= c1; c++)
      if (tcLast < 0 || c < tcFirst || c > tcLast) cacheColumn(c, sky);
    tcFirst = c0; tcLast = c1;
    int slot = c0 % tcN, x = (c0 << 4) - (sx0 >> 1);
    int w1 = Math.min(n, tcN - slot);
    g.drawRegion(tc, slot << 4, 0, w1 << 4, h, 0, x, top, Graphics.TOP | Graphics.LEFT);
    if (w1 < n) g.drawRegion(tc, 0, 0, (n - w1) << 4, h, 0, x + (w1 << 4), top, Graphics.TOP | Graphics.LEFT);
  }

  // one column, drawn like drawLayer would (bg then ia, rows top to bottom) but clipped to the
  // column; tiles up to 8 columns to the left can be wide enough to reach into it
  void cacheColumn(int c, int sky) {
    int x = (c % tcN) << 4, h = Level.H << 4, w = level.width;
    tcG.setClip(x, 0, 16, h);
    tcG.setColor(sky);
    tcG.fillRect(x, 0, 16, h);
    int a = Math.max(0, c - 7), b = Math.min(c, w - 1);
    for (int l = 0; l < 2; l++) {
      byte[] map = l == 0 ? level.bg : level.ia;
      for (int y = 0; y < Level.H; y++) {
        int row = y * w;
        for (int cx = a; cx <= b; cx++) {
          int t = map[row + cx] & 0xff;
          if (t == 0) continue;
          // Low: animated tiles are drawn each frame instead, and a bouncing brick is drawn moving
          if (detail == LOW && (Res.tFrames[t].length > 1 || (l == 1 && bouncing(cx, y)))) continue;
          if (cx < c) {
            short[] fr = Res.tFrames[t];
            if (fr.length == 0 || ((cx - c) << 4) + (Res.tfw[fr[0]] & 0xff) <= 0) continue;
          }
          Res.drawTile(tcG, t, x + ((cx - c) << 4), y << 4, 0);
        }
      }
    }
  }

  void background(Graphics g, float sx) {
    int top = viewTop >> 1;
    // Low and Potato, or a picture RAM saver (or a switch to Full mid-level) left unloaded: one fill
    if (detail != FULL || (bgImage == null && bgImageNo >= 0)) {
      g.setColor(skyFill());
      g.fillRect(0, 0, sw, sh);
      return;
    }
    if (bgImage != null) {
      Image im = top > 0 && bgTall != null ? bgTall : bgImage;
      int y0 = im == bgTall ? 0 : top;
      int iw = im.getWidth();
      if (im == bgImage && top > 0) {          // RAM saver in portrait: sky colour around the picture
        g.setColor(skyColor);
        g.fillRect(0, 0, sw, top);
        int bot = top + im.getHeight();
        if (bot < sh) g.fillRect(0, bot, sw, sh - bot);
      }
      int s = ((int) (sx * level.bkgdSpeed / 100f) >> 1) % iw;
      for (int x = -s; x < sw; x += iw) g.drawImage(im, x, y0, Graphics.TOP | Graphics.LEFT);
    } else {
      int a = level.topColor, b = level.bottomColor;
      for (int y = 0; y < sh; y += 4) {
        int r = ((a >> 16) * (sh - y) + (b >> 16) * y) / sh;
        int gg = (((a >> 8) & 0xff) * (sh - y) + ((b >> 8) & 0xff) * y) / sh;
        int bb = ((a & 0xff) * (sh - y) + (b & 0xff) * y) / sh;
        g.setColor((r << 16) | (gg << 8) | bb);
        g.fillRect(0, y, sw, 4);
      }
    }
  }

  void particles(Graphics g, int layer) {
    if (ptCount == 0) return;
    for (int i = 0; i < ptCount; i++) {
      if (ptLayer[i] != layer) continue;
      float x = (ptX[i] - scrollX) % ptVW;
      if (x < 0) x += ptVW;
      float y = ptY[i] % 480;
      if (y < 0) y += 480;
      if (x > viewW) x -= ptVW;
      if (x < -64) continue;
      Res.drawSprite(g, ptImg[i], (int) x >> 1, ((int) y + viewTop) >> 1, 0);
    }
  }

  void drawLayer(Graphics g, byte[] map, int col0, int col1, int ox, int top, boolean ia) {
    int w = level.width;
    if (col0 < 0) col0 = 0;
    if (col1 >= w) col1 = w - 1;
    for (int y = 0; y < Level.H; y++) {
      int row = y * w;
      for (int x = col0; x <= col1; x++) {
        int t = map[row + x] & 0xff;
        if (t == 0) continue;
        if (ia && detail != POTATO && bouncing(x, y)) continue;
        Res.drawTile(g, t, ox + (x << 4), top + (y << 4), detail == POTATO ? 0 : frameCounter);
      }
    }
  }

  boolean bouncing(int tx, int ty) {
    for (int i = 0; i < NE; i++)
      if (bbOn[i] && ((int) bbX[i] >> 5) == tx && ((int) bbY[i] >> 5) == ty) return true;
    return false;
  }

  // GameSession::drawstatus at 320x240; portrait puts the right half on a second row
  // the HUD's numbers as strings, remade only when they change (not a new String every frame);
  // Potato draws the HUD without the half-transparent shadow
  int[] hudVal = { -1, -1, -1, -1 }; String[] hudStr = new String[4];
  String num(int slot, int v, String prefix) {
    if (hudVal[slot] != v || hudStr[slot] == null) { hudVal[slot] = v; hudStr[slot] = prefix + v; }
    return hudStr[slot];
  }
  void hud(Graphics g, int font, String s, int x, int y) { Res.text(g, font, s, x, y, detail == POTATO ? 0 : 1); }

  void status(Graphics g) {
    int tl = (int) ((timeEnd - now) / 1000);
    if (tl < 0) tl = 0;
    hud(g, Res.WHITE, "SCORE", 0, 0);
    hud(g, Res.GOLD, num(0, score, ""), 48, 0);
    int rx = sw - 88;                  // portrait: TIME and LIVES share a right-hand column (fits x99)
    int tx = landscape ? 112 : rx;
    if (endSequence == 0 && now >= timeEnd) hud(g, Res.WHITE, "TIME'S UP", landscape ? 112 : rx, 0);
    else if (endSequence != 0 || timeEnd - now > TIME_WARNING || (frameCounter % 10) < 5) {
      hud(g, Res.WHITE, "TIME", tx, 0);
      hud(g, Res.GOLD, num(1, tl, ""), tx + 40, 0);
    }
    int cx = landscape ? 240 : 0, cy = landscape ? 0 : 10;
    hud(g, Res.WHITE, "COINS", cx, cy);
    hud(g, Res.GOLD, num(2, distros, ""), cx + 48, cy);   // same column as the score
    int lx = landscape ? 240 : rx;
    hud(g, Res.WHITE, "LIVES", lx, 10);
    drawLives(g, lx, 10);
    int fy = landscape ? 10 : 20;
    if (showFps) {                     // under the score
      hud(g, Res.WHITE, "FPS", 0, fy);
      hud(g, Res.GOLD, fpsText, 32, fy);
      fy += 10;
    }
    // the editor's test play: under the HUD (the bottom of the screen is where the touch buttons go)
    if (testMode) hud(g, Res.WHITE, "Press Softkey To Return", 0, fy);
  }

  // tux_life icons as the 320x240 build places them (Surface::draw halves x: 4.5 pixels apart)
  void drawLives(Graphics g, int lx, int y) {
    if (!landscape) {                  // portrait: LIVES, one Tux centred on the text line, then x<lives>
      Res.drawSpriteTL(g, Spr.TUX_LIFE, lx + 44, y - 4);
      hud(g, Res.GOLD, num(3, Math.max(0, lives), "x"), lx + 62, y);
      return;
    }
    if (lives >= 5) {
      String s = lives + "x";
      hud(g, Res.GOLD, s, lx + 63 - s.length() * 8, y);
      Res.drawSpriteTL(g, Spr.TUX_LIFE, lx + 61, y);
    } else for (int i = 0; i < lives; ++i) Res.drawSpriteTL(g, Spr.TUX_LIFE, lx + (565 + 9 * i) / 2 - 240, y);
  }

  // level intro (GameSession::levelintro) and game over (drawendscreen)
  void screens(Graphics g) {
    background(g, 0);
    int cx = sw / 2, y = sh / 2 - 20;
    if (state == INTRO) {
      int extra = centeredWrapped(g, Res.GOLD, level.name, cx, y, 10) - 1;
      Res.textCentered(g, Res.WHITE, "TUX x " + lives, cx, y + 12 + extra * 10);
      centeredWrapped(g, Res.WHITE, "by " + level.author, cx, y + 80 + extra * 10, 10);
    } else {
      Res.textCentered(g, Res.BLUE, "GAMEOVER", cx, y);
      Res.textCentered(g, Res.GOLD, "SCORE: " + score, cx, y + 12);
      Res.textCentered(g, Res.GOLD, "COINS: " + distros, cx, y + 28);
      centeredWrapped(g, Res.WHITE, "HIGH SCORE: " + hsName + " " + hsScore, cx, y + 60, 10);
    }
  }

  // centred text broken at spaces to fit the screen; returns the number of lines
  int centeredWrapped(Graphics g, int font, String t, int cx, int y, int lineH) {
    String[] l = wrap(t, sw / 8);
    for (int i = 0; i < l.length; i++) Res.textCentered(g, font, l[i], cx, y + i * lineH);
    return l.length;
  }

  // a level title short enough for one menu line: drop a "Level 11:" style prefix, then cut
  static String fitName(String t, int max) {
    if (t.length() <= max) return t;
    int colon = t.indexOf(": ");
    if (colon > 0 && colon < 14) t = t.substring(colon + 2);
    return t.length() <= max ? t : t.substring(0, max - 2) + "..";
  }

  static String[] wrap(String t, int max) {
    Vector v = new Vector();
    t = t.trim();
    while (t.length() > max) {
      int cut = t.lastIndexOf(' ', max);
      if (cut <= 0) cut = max;
      v.addElement(t.substring(0, cut));
      t = t.substring(cut).trim();
    }
    v.addElement(t);
    String[] r = new String[v.size()];
    v.copyInto(r);
    return r;
  }

  void showExtro(String file) {
    hsCandidate = score;
    showText(file, Pic.BG_EXTRO, 1.0f, AFTER_CREDITS);
    music.playFile("t", -1);
  }
}
