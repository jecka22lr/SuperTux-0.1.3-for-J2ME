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
      CONTROLS = 8, LOADING = 9, EDITOR = 10, NOTICE = 12, ABOUT = 13, AUDIOINFO = 14;

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
  int fpsCount, worstFrame; long fpsT, lastDraw; String fpsText = "0.0";

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
  float lastX; long lastXT; boolean endStuck;
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
  // power/run. A key moved away stops doing its old job. Portrait and Landscape have a map each,
  // set separately. Landscape starts from the upright keys on a landscape screen (or a slid-out
  // keyboard) and from the turned keys when the picture is drawn sideways on an upright phone.
  static final int[] KEY_DEFAULT = { KEY_NUM4, KEY_NUM6, KEY_NUM2, KEY_NUM8, KEY_NUM0 };
  static final int[] KEY_DEFAULT_SIDE = { KEY_NUM2, KEY_NUM8, KEY_NUM6, KEY_NUM4, KEY_NUM0 };
  static final int[] KEY_BIT = { K_LEFT, K_RIGHT, K_UP, K_DOWN, K_FIRE };
  int[] keymapUp = { KEY_NUM4, KEY_NUM6, KEY_NUM2, KEY_NUM8, KEY_NUM0 };
  int[] keymapLand = { KEY_NUM4, KEY_NUM6, KEY_NUM2, KEY_NUM8, KEY_NUM0 };
  boolean landKeysSet;      // false: Landscape follows the defaults for how the screen is turned
  int[] keymap() { return landscape ? keymapLand : keymapUp; }
  int[] keyDefaults() { return landscape && rotate ? KEY_DEFAULT_SIDE : KEY_DEFAULT; }
  boolean bound(int code) { int[] k = keymap(); for (int i = 0; i < 5; i++) if (k[i] == code) return true; return false; }
  private int evHead, evTail;

  // menus (setup.cpp)
  static final int M_START = 1, M_CONTRIB = 2, M_OPTIONS = 3, M_CONTROLS = 4, M_CREDITS = 5, M_QUIT = 6,
      M_SOUND = 10, M_MUSIC = 11, M_FPS = 12, M_LANDSCAPE = 13, M_TOUCH = 14, M_CONTINUE = 20, M_ABORT = 21,
      M_WM_RETURN = 30, M_WM_QUIT = 31, M_YES = 41, M_NO = 40, M_EDITOR = 7, M_KEYS = 50,
      M_STORY = 999, M_DETAIL = 15, M_FPSCAP = 16, M_RAM = 17, M_OPTHELP = 18, M_DRAWCAP = 19, M_ABOUT = 8, M_SFXFMT = 22, M_PAUSEMUS = 23, M_AUDIOINFO = 9,
      M_DBG_INV = 70, M_DBG_TIME = 71, M_DBG_TUX = 72, M_DBG_LIVES = 75, M_DBG_KILL = 76,
      M_DBG_END = 77, M_DBG_FINISH = 78, M_DBG_WALK = 79, M_DBG_SOLVE = 80, M_DBG_SOLVEALL = 81;   // above any level number in a subset's level list
  Menu mainMenu, optionsMenu, loadGameMenu, contribMenu, gameMenu, worldmapMenu, confirmMenu, keysMenu,
      contribSubsetMenu;
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
  static final String[] DETAIL_MODES = { "Full", "Low", "Potato", "Max Potato" };
  static final int FULL = 0, LOW = 1, POTATO = 2, MAXPOTATO = 3;
  // Max Potato, for the slowest phones: Potato plus sprites with hard edges (no half-see-through
  // pixels to blend), a still title screen, and bad guys a screen or more away sleep until Tux comes
  // near. Options > Draw cap: draw fewer frames than the game runs, so the game keeps its speed
  static final String[] DRAWCAP_MODES = { "Max", "20", "15", "10" };
  static final int[] DRAWCAP_MS = { 0, 50, 66, 100 };
  int drawCap;
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
    sounds.music = music;
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
        if (b.length > 4 && b[4] >= 2 && b[4] <= 9) {
          java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(b, 5, b.length - 5));
          for (int i = 0; i < 5; i++) keymapUp[i] = in.readInt();
          hsScore = in.readInt(); hsName = in.readUTF();
          if (b[4] >= 3) for (int i = 0; i < 5; i++) keymapLand[i] = in.readInt();
          // before version 9 this was the sideways map: keep it only if it was changed
          landKeysSet = b[4] >= 3 && (b[4] >= 9 || !same(keymapLand, KEY_DEFAULT_SIDE));
          if (b[4] >= 4) showTouch = in.readBoolean();
          if (b[4] >= 5) detail = Math.max(FULL, Math.min(MAXPOTATO, in.readByte()));
          if (b[4] >= 6) { fpsCap = Math.max(0, Math.min(FPSCAP_MS.length - 1, in.readByte())); ramSaver = in.readBoolean(); }
          if (b[4] >= 7) drawCap = Math.max(0, Math.min(DRAWCAP_MS.length - 1, in.readByte()));
          if (b[4] >= 8) { int f = in.readByte(); if (f > 0 && f < Sound.FORMATS.length && getClass().getResourceAsStream("/a16/0.wav") != null && Sound.formatAvailable(f)) sounds.format = f; }   // test builds only
          if (b[4] >= 9) landKeysSet = in.readBoolean();
        }
      }
    } catch (Throwable e) { }
    Menu.noEffects = detail >= POTATO;
    setLayout(landscape);
  }

  // settings byte 2: 0 off, 1 normal (what older versions saved for off / on), 2 fast, 3 MIDI, 4 Nokia, 5 tones
  static int soundModeOf(byte b) {
    return b == 0 ? Sound.OFF : b == 2 ? Sound.FAST : b == 3 ? Sound.MIDI : b == 4 ? Sound.NOKIA : b == 5 ? Sound.TONES : Sound.NORMAL;
  }
  static int soundByte(int mode) {
    return mode == Sound.OFF ? 0 : mode == Sound.FAST ? 2 : mode == Sound.MIDI ? 3 : mode == Sound.NOKIA ? 4 : mode == Sound.TONES ? 5 : 1;
  }

  void saveSettings() {
    try {
      java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
      java.io.DataOutputStream out = new java.io.DataOutputStream(bo);
      out.writeByte(landscape ? 1 : 0); out.writeByte(!music.enabled ? 0 : music.midiOnly ? 2 : 1); out.writeByte(soundByte(sounds.mode));
      out.writeByte(showFps ? 1 : 0); out.writeByte(9);
      for (int i = 0; i < 5; i++) out.writeInt(keymapUp[i]);
      out.writeInt(hsScore); out.writeUTF(hsName);
      for (int i = 0; i < 5; i++) out.writeInt(keymapLand[i]);
      out.writeBoolean(showTouch);
      out.writeByte(detail);
      out.writeByte(fpsCap); out.writeBoolean(ramSaver);
      out.writeByte(drawCap);
      out.writeByte(sounds.format);
      out.writeBoolean(landKeysSet);
      out.close();
      byte[] b = bo.toByteArray();
      RecordStore rs = RecordStore.openRecordStore("stux", true);
      if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length); else rs.addRecord(b, 0, b.length);
      rs.closeRecordStore();
    } catch (Throwable e) { }
  }

  static boolean same(int[] a, int[] b) { for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false; return true; }

  // the screen turned (J2ME Loader rotating, a keyboard sliding out): work out sideways drawing again
  // on the game thread
  volatile boolean layoutDirty;
  protected void sizeChanged(int w, int h) { layoutDirty = true; }

  void setLayout(boolean land) {
    landscape = land;
    boolean deviceLandscape = getWidth() > getHeight();
    rotate = land && !deviceLandscape;
    if (!landKeysSet) System.arraycopy(land && rotate ? KEY_DEFAULT_SIDE : KEY_DEFAULT, 0, keymapLand, 0, 5);
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
    buildMainMenu();
    optionsMenu.add(Menu.LABEL, "Options", 0).add(Menu.HL, "", 0)
        .add(Menu.STRINGSELECT, "Sound     ", M_SOUND)
        .add(Menu.STRINGSELECT, "Music     ", M_MUSIC)
        .add(Menu.STRINGSELECT, "Detail    ", M_DETAIL)
        .add(Menu.STRINGSELECT, "FPS cap   ", M_FPSCAP)
        .add(Menu.STRINGSELECT, "Draw cap  ", M_DRAWCAP)
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
    optionsMenu.setList(M_DRAWCAP, DRAWCAP_MODES, drawCap);
    if (getClass().getResourceAsStream("/a16/0.wav") != null) {   // SFX format test build
      optionsMenu.insertAfter(M_SOUND, Menu.STRINGSELECT, "SFX type  ", M_SFXFMT);
      optionsMenu.setList(M_SFXFMT, Sound.FORMATS, sounds.format);
      optionsMenu.insertAfter(M_SFXFMT, Menu.TOGGLE, "Pause music during SFX", M_PAUSEMUS);
    }
    keysMenu.add(Menu.LABEL, "Key Setup", 0).add(Menu.HL, "", 0)
        .add(Menu.CONTROLFIELD, "Left move", M_KEYS).add(Menu.CONTROLFIELD, "Right move", M_KEYS + 1)
        .add(Menu.CONTROLFIELD, "Jump", M_KEYS + 2).add(Menu.CONTROLFIELD, "Duck", M_KEYS + 3)
        .add(Menu.CONTROLFIELD, "Power/Run", M_KEYS + 4).add(Menu.HL, "", 0)
        .add(Menu.ACTION, "Default Keys", M_KEYS + 9).add(Menu.BACK, "Back", 0);
    showKeys();
    contribSubsetMenu = new Menu();
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
    debugMenu = new Menu(); debugMapMenu = new Menu();
    debugMenu.add(Menu.LABEL, "Debug Mode", 0).add(Menu.HL, "", 0)
        .add(Menu.TOGGLE, "Invincible", M_DBG_INV).add(Menu.TOGGLE, "Freeze time", M_DBG_TIME)
        .add(Menu.STRINGSELECT, "Tux    ", M_DBG_TUX)
        .add(Menu.ACTION, "99 Lives", M_DBG_LIVES).add(Menu.ACTION, "Defeat Enemies", M_DBG_KILL)
        .add(Menu.ACTION, "Warp To End", M_DBG_END).add(Menu.ACTION, "Finish Level", M_DBG_FINISH)
        .add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
    debugMenu.setList(M_DBG_TUX, DBG_TUX, 0);
    debugMapMenu.add(Menu.LABEL, "Debug Mode", 0).add(Menu.HL, "", 0)
        .add(Menu.TOGGLE, "Walk past levels", M_DBG_WALK).add(Menu.ACTION, "Solve This Level", M_DBG_SOLVE)
        .add(Menu.ACTION, "Solve All Levels", M_DBG_SOLVEALL).add(Menu.ACTION, "99 Lives", M_DBG_LIVES)
        .add(Menu.HL, "", 0).add(Menu.BACK, "Back", 0);
  }

  // ---------------- Debug Mode ----------------
  // cheats, from the pause menu (level or world map): Up Up Down Down Left Right Left Right, or tap
  // the Pause title 7 times. They last until the title screen and are never saved.
  static final int[] DBG_COMBO = { K_UP, K_UP, K_DOWN, K_DOWN, K_LEFT, K_RIGHT, K_LEFT, K_RIGHT };
  static final String[] DBG_TUX = { "Small", "Big", "Fire" };
  Menu debugMenu, debugMapMenu;
  int dbgStep, dbgTaps; long dbgTapT;
  boolean dbgInvincible, dbgFreeze, dbgWalk;

  void dbgKeys(int ev) {
    int d = ev & (K_UP | K_DOWN | K_LEFT | K_RIGHT | K_SELECT | K_ESC);
    if (d == 0) return;
    if (d == DBG_COMBO[dbgStep]) {
      if (++dbgStep == DBG_COMBO.length) { dbgStep = 0; openDebug(); }
    } else dbgStep = d == K_UP ? (dbgStep == 2 ? 2 : 1) : 0;
  }

  // touch: taps on the pause menu's title, 7 within 3 seconds
  boolean dbgTap(Menu m, int x, int y) {
    if (m.itemY.length == 0 || x < m.boxX || x >= m.boxX + m.boxW || y < m.itemY[0] - 8 || y >= m.itemY[0] + 8) return false;
    long t = System.currentTimeMillis();
    if (t - dbgTapT > 3000) dbgTaps = 0;
    dbgTapT = t;
    if (++dbgTaps >= 7) { dbgTaps = 0; openDebug(); }
    return true;
  }

  void openDebug() {
    debugMenu.setToggled(M_DBG_INV, dbgInvincible); debugMenu.setToggled(M_DBG_TIME, dbgFreeze);
    debugMapMenu.setToggled(M_DBG_WALK, dbgWalk);
    debugMenu.setList(M_DBG_TUX, DBG_TUX, tuxForm());
    Menu.push(state == WORLDMAP ? debugMapMenu : debugMenu, now);
  }

  void resetDebug() { dbgInvincible = dbgFreeze = dbgWalk = false; dbgStep = dbgTaps = 0; }

  // returns true when the level was left
  boolean debugAction(int id) {
    Player t = tux;
    switch (id) {
      case M_DBG_INV: dbgInvincible = debugMenu.isToggled(M_DBG_INV); break;
      case M_DBG_TIME: dbgFreeze = debugMenu.isToggled(M_DBG_TIME); break;
      case M_DBG_LIVES: lives = MAX_LIVES; break;
      case M_DBG_KILL:
        for (int i = 0; i < badguys.size(); i++) {
          BadGuy b = (BadGuy) badguys.elementAt(i);
          if (b.dying == BadGuy.DYING_NOT) b.killMe(0);
        }
        Menu.setCurrent(null, now); break;
      case M_DBG_END:
        dbgPlace(level.width - 12); Menu.setCurrent(null, now); break;
      case M_DBG_FINISH: levelExit(WorldMap.ES_FINISHED); return true;
      case M_DBG_WALK: dbgWalk = debugMapMenu.isToggled(M_DBG_WALK); break;
      case M_DBG_SOLVE: {
        int l = worldmap.atLevel();
        if (l >= 0 && worldmap.lid[l].length() > 0) { worldmap.solved[l] = true; worldmap.save(); }
        break;
      }
      case M_DBG_SOLVEALL:
        for (int i = 0; i < worldmap.count; i++) if (worldmap.lid[i].length() > 0) worldmap.solved[i] = true;
        worldmap.save(); break;
    }
    return false;
  }

  int tuxForm() { return tux.size == Player.SMALL ? 0 : tux.gotCoffee ? 2 : 1; }

  // the Tux picker: Small, Big or Fire as soon as it changes
  void dbgTux() {
    int f = debugMenu.listOf(M_DBG_TUX);
    if (f == tuxForm()) return;
    Player t = tux;
    if (f == 0) { if (t.size == Player.BIG) { t.size = Player.SMALL; t.h = 32; t.duck = false; t.y += 32; t.syncOld(); } }
    else t.grow();
    t.gotCoffee = f == 2;
  }

  // puts Tux on the first ground at or after column c with room above him (Invincible's pit rescue,
  // Warp To End)
  void dbgPlace(int c) {
    Player t = tux;
    for (int x = Math.max(0, c); x < level.width; x++)
      for (int y = 2; y < Level.H; y++) {
        int cx = x * 32 + 16;
        if (level.solid(cx, y * 32 + 16) && !level.solid(cx, y * 32 - 16) && !level.solid(cx, y * 32 - 48)
            && (t.size == Player.SMALL || !level.solid(cx, y * 32 - 80))) {
          t.x = x * 32; t.y = y * 32 - t.h - 1; t.setVelocity(0, 0); t.syncOld();
          return;
        }
      }
    if (c > 0) dbgPlace(0);
  }

  // Potato and Max Potato leave the credit lines off the title screen and show them on an About
  // screen instead (the title then has no text to draw over the moving demo)
  void buildMainMenu() {
    int act = mainMenu.active;
    mainMenu.clear();
    mainMenu.add(Menu.GOTO, "Start Game", M_START, startTitle.length > 1 ? adventureMenu : loadGameMenu, false)
        .add(Menu.GOTO, "Bonus Levels", M_CONTRIB, contribMenu, false)
        .add(Menu.GOTO, "Options", M_OPTIONS, optionsMenu, false)
        .add(Menu.ACTION, "Controls", M_CONTROLS)
        .add(Menu.ACTION, "Level Editor", M_EDITOR);
    if (detail >= POTATO) mainMenu.add(Menu.ACTION, "About", M_ABOUT);
    if (getClass().getResourceAsStream("/a16/0.wav") != null) mainMenu.add(Menu.ACTION, "Audio info", M_AUDIOINFO);   // test builds
    mainMenu.add(Menu.ACTION, "Credits", M_CREDITS)
        .add(Menu.ACTION, "Quit", M_QUIT);
    mainMenu.active = Math.min(act, mainMenu.count - 1);
  }

  String[] creditLines() {
    String[] c = landscape
        ? new String[] { " SuperTux 0.1.3 - " + RELEASE, "Made by SuperTux Devel Team",
            "Originally ported by Lars 'Anotherguest'", "2026 J2ME port", "by Jecka22LR" }
        : new String[] { " SuperTux 0.1.3 - J2ME", RELEASE, "Made by SuperTux Devel Team",
            "Originally ported by", "Lars 'Anotherguest'", "2026 J2ME port by Jecka22LR" };
    if (titleNote == null) return c;
    String[] c2 = new String[c.length + 1];          // an edition's own line on top (x/g.txt T|...)
    c2[0] = " " + titleNote;
    System.arraycopy(c, 0, c2, 1, c.length);
    return c2;
  }

  void drawAbout(Graphics g) {
    g.setColor(0); g.fillRect(0, 0, sw, sh);
    int top = landscape ? 20 : 50;
    Res.textCentered(g, Res.BLUE, "- About -", sw / 2, top, 0);
    String[] c = creditLines();
    for (int i = 0; i < c.length; i++) Res.textCentered(g, Res.WHITE, c[i].trim(), sw / 2, top + 25 + i * 12, 0);
    centeredWrapped(g, Res.WHITE, "Free software under the GNU GPL version 2. See COPYING.", sw / 2, top + 40 + c.length * 12, 10);
    Res.text(g, Res.GOLD, showTouch ? "Tap or press any key" : "Press any key", 0, sh - 12, 0);
  }

  // test builds: what the phone says about its sound, to see why effects stall
  String[] audioInfo;
  void drawAudioInfo(Graphics g) {
    if (audioInfo == null) {
      Vector v = new Vector();
      String[] props = { "microedition.platform", "microedition.media.version", "supports.mixing",
          "supports.audio.capture", "audio.encodings" };
      for (int i = 0; i < props.length; i++) {
        String p = null;
        try { p = System.getProperty(props[i]); } catch (Throwable e) { }
        v.addElement(props[i] + ": " + p);
      }
      Runtime rt = Runtime.getRuntime();
      v.addElement("heap: " + rt.totalMemory() / 1024 + " KB, free " + rt.freeMemory() / 1024 + " KB");
      String types = "";
      try {
        String[] t = javax.microedition.media.Manager.getSupportedContentTypes(null);
        for (int i = 0; i < t.length; i++) if (t[i].startsWith("audio")) types += (types.length() > 0 ? ", " : "") + t[i];
      } catch (Throwable e) { types = "? " + e; }
      v.addElement("audio types: " + types);
      String fm = "";
      for (int i = 0; i < Sound.FORMATS.length; i++) if (Sound.formatAvailable(i)) fm += (fm.length() > 0 ? ", " : "") + Sound.FORMATS[i];
      v.addElement("SFX sets in this jar: " + fm);
      audioInfo = new String[v.size()];
      v.copyInto(audioInfo);
    }
    g.setColor(0); g.fillRect(0, 0, sw, sh);
    Res.textCentered(g, Res.BLUE, "- Audio info -", sw / 2, 4, 0);
    int y = 18, per = sw / 8 - 1;
    for (int i = 0; i < audioInfo.length && y < sh - 20; i++) {
      String[] l = wrap(audioInfo[i], per);
      for (int j = 0; j < l.length && y < sh - 20; j++, y += 10) Res.text(g, j == 0 ? Res.GOLD : Res.WHITE, l[j], 2, y, 0);
      y += 3;
    }
    Res.text(g, Res.WHITE, showTouch ? "Tap or press any key" : "Press any key", 0, sh - 11, 0);
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
      if (landscape) landKeysSet = false;
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
    if (ch) { if (landscape) landKeysSet = true; showKeys(); saveSettings(); }
  }

  void showKeys() {
    if (keysMenu == null) return;
    int[] k = keymap();
    for (int i = 0; i < 5; i++) keysMenu.setCode(M_KEYS + i, k[i]);
    keysMenu.text[0] = !landscape ? "Keys: Portrait" : rotate ? "Keys: Landscape (sideways)" : "Keys: Landscape";
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
      Menu.noEffects = detail >= POTATO;
      Res.setHardSprites(detail == MAXPOTATO);
      buildMainMenu();
      loadBackground();
      saveSettings();
    }
    if (optionsMenu.listOf(M_FPSCAP) != fpsCap) {
      fpsCap = optionsMenu.listOf(M_FPSCAP);
      saveSettings();
    }
    if (optionsMenu.find(M_SFXFMT) >= 0 && optionsMenu.listOf(M_SFXFMT) != sounds.format) {
      sounds.setFormat(optionsMenu.listOf(M_SFXFMT));
      sounds.play(Sound.DISTRO);
      saveSettings();
    }
    if (optionsMenu.listOf(M_DRAWCAP) != drawCap) {
      drawCap = optionsMenu.listOf(M_DRAWCAP);
      saveSettings();
    }
    if (optionsMenu.check() < 0) return;
    if (optionsMenu.check() == M_OPTHELP) { helpOpen = true; helpPage = 0; return; }

    if (ramSaver != optionsMenu.isToggled(M_RAM)) {
      ramSaver = !ramSaver;
      if (ramSaver) { tc = null; tcG = null; tcLevel = null; touchImg = null; hudImg = null; }
      loadBackground();
    }
    if (optionsMenu.find(M_PAUSEMUS) >= 0) sounds.pauseMusic = optionsMenu.isToggled(M_PAUSEMUS);

    showFps = optionsMenu.isToggled(M_FPS);
    if (showTouch != optionsMenu.isToggled(M_TOUCH)) { showTouch = !showTouch; setLayout(landscape); }
    if (landscape != optionsMenu.isToggled(M_LANDSCAPE)) setLayout(!landscape);
    saveSettings();
  }

  // ---------------- title screen ----------------
  void enterTitle() {
    resetDebug();
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
    if (detail == MAXPOTATO) {         // Max Potato: a still title screen (the music still plays)
      if (music.type != Music.LEVEL) music.play(Music.LEVEL);
      return;
    }
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
        case M_ABOUT: state = ABOUT; break;
        case M_AUDIOINFO: state = AUDIOINFO; audioInfo = null; break;
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
    darkBackground(g, 0xaa000000);
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
      // portrait has 18 characters left of room: "Key 4 / Joystick left" becomes "4 / Joy left"
      Res.text(g, Res.GOLD, landscape ? k[i] : cut(cut(k[i], "Key "), "stick"), keyX, y, 1);
    }
    if (showTouch) {                   // the on-screen buttons
      String[] t = wrap("Touch: the arrows walk and jump, FIRE runs and shoots, JUMP jumps, MENU pauses", sw / 8);
      for (int i = 0; i < t.length; i++) Res.text(g, Res.GOLD, t[i], 0, top + 140 + i * 10, 1);
    }
    String[] hint = wrap(rotate ? "Hold the phone keypad-right. Change keys in Options > Keyboard Setup"
        : "Change them in Options > Keyboard Setup", sw / 8);
    for (int i = 0; i < hint.length; i++) Res.text(g, Res.WHITE, hint[i], 0, top + 180 + i * 10, 1);
    Res.text(g, Res.WHITE, showTouch ? "Tap or press any key" : "Press any key", 0, top + 215, 1);
  }

  // s without the first occurrence of part (CLDC has no String.replace for strings)
  static String cut(String s, String part) {
    int i = s.indexOf(part);
    return i < 0 ? s : s.substring(0, i) + s.substring(i + part.length());
  }

  // ---------------- Options > Option Help ----------------
  // what each option does, a page per option like the Controls screen: name, then one line per choice
  static final String[][] HELP = {
    { "Sound",
      "Normal|Every sound effect, like the original.",
      "Fast|An effect still playing isn't restarted. Fewer stalls.",
      "MIDI|Effects as short MIDI notes. For phones that freeze when a sound plays.",
      "Tones|The same notes as simple beeps. Works on every phone; try it if MIDI is silent.",
      "Nokia|Effects through Nokia's own sound system. Try it on Nokia phones that freeze.",
      "Off|No sound effects." },
    { "Music",
      "Normal|MP3 recordings of the original songs (MIDI on phones without MP3).",
      "MIDI|Chiptune MIDI versions. Much lighter on slow phones.",
      "Off|No music." },
    { "Detail",
      "Full|Everything, like SuperTux 0.1.3.",
      "Low|Plain sky instead of the background picture, no snow or clouds. Faster.",
      "Potato|Also no animations or effects; defeated enemies vanish. Much faster.",
      "Max Potato|Also hard-edged sprites, a still title screen, and far-off enemies wait. Fastest. Music: MIDI helps too." },
    { "FPS cap and Draw cap",
      "FPS cap|How often the game runs, up to 33 times a second. Lower gives the phone spare time, which can smooth out sound and music.",
      "Draw cap|How often the screen is drawn. Lower is choppier but the game keeps its speed, and drawing is most of the work." },
    { "RAM saver",
      "On|Uses less memory and looks the same, but runs a bit slower. For phones that crash or lose sounds when memory runs out.",
      "Off|Keeps pictures ready in memory for speed." },
    { "Other options",
      "Show FPS|Frames per second under the score.",
      "Landscape|The 320x240 layout. On an upright phone it is turned sideways.",
      "Show touch|On-screen buttons and a MENU box for touch screens.",
      "Keyboard Setup|Pick the key for each action. Portrait and Landscape keep their own keys: set them in each." }
  };
  boolean helpOpen; int helpPage;

  // the screens the help takes on this layout: an option's choices that don't fit under each other
  // go on to a second screen (landscape is only 240 tall); each is { option, first choice, last + 1 }
  int[][] helpScreens; int helpScreensFor = -1;
  int[][] helpScreens() {
    int key = sw * 1000 + sh;
    if (helpScreens != null && helpScreensFor == key) return helpScreens;
    Vector v = new Vector();
    int per = sw / 8 - 2, top = landscape ? 0 : 20, bottom = sh - 26;
    for (int p = 0; p < HELP.length; p++) {
      int first = 1, y = top + 36;
      for (int i = 1; i < HELP[p].length; i++) {
        int h = 11 + wrap(HELP[p][i].substring(HELP[p][i].indexOf('|') + 1), per).length * 10;
        if (y + h > bottom && i > first) { v.addElement(new int[] { p, first, i }); first = i; y = top + 36; }
        y += h + 4;
      }
      v.addElement(new int[] { p, first, HELP[p].length });
    }
    helpScreens = new int[v.size()][];
    v.copyInto(helpScreens);
    helpScreensFor = key;
    return helpScreens;
  }

  void helpInput(int ev) {
    if ((ev & K_ESC) != 0) { helpOpen = false; return; }
    if ((ev & (K_LEFT | K_UP)) != 0) { if (helpPage > 0) helpPage--; return; }
    if ((ev & (K_SELECT | K_RIGHT | K_DOWN | K_FIRE)) != 0 && ++helpPage >= helpScreens().length) helpOpen = false;
  }

  // Potato and Max Potato: the editor help's black-to-white gradient instead of the darkened picture
  void darkBackground(Graphics g, int alpha) {
    if (detail >= POTATO) {
      for (int y = 0; y < sh; y += 4) { int v = y * 255 / sh; g.setColor((v << 16) | (v << 8) | v); g.fillRect(0, y, sw, 4); }
      return;
    }
    drawPicture(g, Pic.BG_TITLE);
    Res.fillAlpha(g, 0, 0, sw, sh, alpha);
  }

  void drawHelp(Graphics g) {
    darkBackground(g, 0xc8000000);
    int top = landscape ? 0 : 20, per = sw / 8 - 2;
    int[][] scr = helpScreens();
    if (helpPage >= scr.length) helpPage = scr.length - 1;
    int[] s = scr[helpPage];
    String[] p = HELP[s[0]];
    boolean more = (helpPage > 0 && scr[helpPage - 1][0] == s[0]) || (helpPage + 1 < scr.length && scr[helpPage + 1][0] == s[0]);
    Res.textCentered(g, Res.BLUE, "- " + p[0] + (more && s[1] > 1 ? " (more)" : "") + " -", sw / 2, top + 12, 2);
    int y = top + 36;
    for (int i = s[1]; i < s[2]; i++) {
      int bar = p[i].indexOf('|');
      Res.text(g, Res.GOLD, p[i].substring(0, bar), 5, y, 1);
      String[] l = wrap(p[i].substring(bar + 1), per);
      for (int j = 0; j < l.length; j++) Res.text(g, Res.WHITE, l[j], 13, y + 11 + j * 10, 1);
      y += 11 + l.length * 10 + 4;
    }
    Res.text(g, Res.WHITE, "Page " + (helpPage + 1) + "/" + scr.length, 0, sh - 22, 1);
    if (showTouch) {
      Res.fillAlpha(g, sw - 48, 0, 48, 16, 0x508080b4);
      Res.textCentered(g, Res.WHITE, "CLOSE", sw - 24, 3, 1);
      Res.text(g, Res.WHITE, landscape ? "Tap right: next page, left: back" : "Tap right: next, left: back", 0, sh - 11, 1);
    } else Res.text(g, Res.WHITE, landscape ? "5: next page   * or #: close" : "5 next page, * close", 0, sh - 11, 1);
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
      level = null; tcLevel = null; tc = null; tcG = null; touchImg = null; hudImg = null; gradImg = null; logoStrip = null;
      bgImage = null; bgTall = null;     // loadBackground loads it again (the sky colour stays)
      if (!demo) logoImg = null;
      textImg = null; textTall = null; textImgNo = -1;
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
    boolean keepBg = detail == FULL;          // Low and Potato only need its colours
    boolean wantTall = keepBg && !ramSaver;   // the portrait copy with a taller sky: RAM saver skips it
    setSkyColours();
    if (!keepBg && level.background != 255 && level.background < bgPotato.length && bgPotato[level.background] >= 0) {
      // the colours come from x/bgc.txt: the picture itself isn't loaded at all
      bgImage = null; bgTall = null; bgImageNo = level.background; skyColor = bgTop[level.background];
      return;
    }
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
    if (s == 0) return;               // Debug Mode's Defeat Enemies: no floating 0s
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
      if (a.dying != BadGuy.DYING_NOT || a.removable) continue;
      for (int j = i + 1; j < n; j++) {
        BadGuy b = (BadGuy) badguys.elementAt(j);
        if (b.dying != BadGuy.DYING_NOT || b.removable) continue;
        if (!a.seen && !b.seen) continue;      // two still waiting off screen can't have moved into each other
        // (a kicked Mr. Iceblock still knocks out one that hasn't woken up yet, as in 0.1.3)
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
    endSequence = kind; lastX = -1; lastXT = now; endStuck = false;
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
      // jump when stuck, judged over 50 ms rather than one frame: at a high frame rate Tux moves
      // less than a pixel a frame, looked stuck every other frame and kept making tiny hops
      if (now - lastXT >= 50 || now < lastXT) { endStuck = (int) lastX == (int) tux.x; lastX = tux.x; lastXT = now; }
      tux.inUp = endStuck;
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
  // the eight rectangles, worked out again only when the layout changes (no new arrays every frame)
  private final int[][] tRects = new int[8][4]; private int tRectsFor = -1;
  int[] touchRect(int i) {
    int key = (landscape ? 1 : 0) | (sw << 1) | (sh << 12);
    if (key != tRectsFor) {
      int h = landscape ? 40 : 48, y = sh - h;
      for (int k = 0; k < 8; k++) {
        int[] r = tRects[k];
        if (k < 6) { r[0] = (k % 3) * 40; r[1] = y + (k / 3) * (h / 2); r[2] = 40; r[3] = h / 2; }
        else { r[0] = sw - 120 + (k - 6) * 60; r[1] = y; r[2] = 60; r[3] = h; }
      }
      tRectsFor = key;
    }
    return tRects[i];
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
      int h = touchRect(6)[3], y0 = sh - h, sky = skyKey();
      int key = pressed ^ (sky << 8);
      if (touchImg == null || touchImg.getWidth() != sw || touchImg.getHeight() != h) { touchImg = Image.createImage(sw, h); touchImgKey = -1; }
      if (key != touchImgKey) {
        Graphics tg = Res.fresh(touchImg);
        fillSky(tg, 0, 0, sw, h, y0);
        tg.translate(0, -y0);
        drawButtons(tg, pressed, false);
        touchImgKey = key;
      }
      g.drawImage(touchImg, 0, y0, Graphics.TOP | Graphics.LEFT);
      return;
    }
    // drawn every frame: landscape below Full as outlines and labels only; portrait (RAM saver) the
    // same as the cached picture
    drawButtons(g, pressed, detail != FULL && landscape);
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
  static final int MENU_H = 14;
  int menuY() { return landscape ? 30 : 22; }   // landscape: under the lives icons
  boolean inMenuCorner(int x, int y) { return x >= sw - 48 && y >= menuY() - 4 && y < menuY() + MENU_H + 4; }
  void drawMenuCorner(Graphics g) {
    int my = menuY();
    if (detail >= POTATO) { g.setColor(0xffffff); g.drawRect(sw - 48, my, 47, MENU_H - 1); }
    else Res.fillAlpha(g, sw - 48, my, 48, MENU_H, 0x508080b4);
    Res.textCentered(g, Res.WHITE, "MENU", sw - 24, my + 3, detail >= POTATO ? 0 : 1);
  }

  // one finger: turns taps into the same presses and held keys the keypad gives
  int touch(int type, int x, int y) {
    lastTouchX = x; lastTouchY = y;
    if (helpOpen) {                    // CLOSE box top right; left half: page back; right half: next
      if (type != T_PRESS) return 0;
      if (x >= sw - 48 && y < 18) return K_ESC | K_ANY;
      return x < sw / 2 ? K_LEFT | K_ANY : K_SELECT | K_ANY;
    }
    Menu m = Menu.current;
    if (type == T_RELEASE) { touchKeys = 0; return 0; }
    if (m != null && state != NOTICE) {
      if (type != T_PRESS) return 0;
      if ((m == gameMenu || m == worldmapMenu) && dbgTap(m, x, y)) return 0;
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
        if (y >= 240 && !editor.help) { touchKeys = 0; editor.minimapTouch(x); return 0; }   // the minimap under the portrait editor
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
    if (detail == MAXPOTATO) Res.setHardSprites(true);
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
      // at least 10 ms a step (0.1.3's frame ratio 1): an emulator skipping sleeps ran the game
      // hundreds of times a second, where Tux's sub-pixel steps behave differently
      if (t0 - last < 10) { try { Thread.sleep(1); } catch (InterruptedException e) { } continue; }
      long dt = t0 - last; last = t0;
      if (dt > worstFrame) worstFrame = (int) dt;
      if (dt > 60) dt = 60;            // slow phone or a hiccup: don't jump too far
      if (dt < 1) dt = 1;
      try {
        frame(dt);
      } catch (Throwable e) {
        fail(e);
      }
      // Options > Draw cap: the game runs every frame, the screen is drawn at most this often
      if (DRAWCAP_MS[drawCap] == 0 || t0 - lastDraw >= DRAWCAP_MS[drawCap] - 5 || (state != PLAY && state != WORLDMAP)) {
        try { paintAll(screen); } catch (Throwable e) { fail(e); }
        flushGraphics();
        lastDraw = t0;
        fpsCount++;
      }
      if (t0 - fpsT >= 1000) {
        int f10 = (int) (fpsCount * 10000L / Math.max(1, t0 - fpsT));
        // with the slowest frame and the slowest sound start of that second: where the stalls are
        fpsText = (f10 / 10) + "." + (f10 % 10) + " max" + worstFrame + " sfx" + sounds.stallMax;
        worstFrame = 0; sounds.stallMax = 0;
        fpsCount = 0; fpsT = t0;
      }
      long spent = System.currentTimeMillis() - t0;
      int frameMs = FPSCAP_MS[fpsCap];
      // paused (or reading the help): nothing moves, so 10 frames a second are plenty
      // (once a menu has finished sliding in, so Full keeps its smooth slide)
      Menu mc = Menu.current;
      boolean sliding = mc != null && !Menu.noEffects && mc.effectEnd > System.currentTimeMillis() - 100;
      if (!sliding && (helpOpen || ((state == PLAY || state == WORLDMAP) && mc != null)
          || (state == TITLE && detail == MAXPOTATO) || state == CONTROLS || state == ABOUT || state == AUDIOINFO)) frameMs = Math.max(frameMs, 100);   // still screens too
      if (spent < frameMs) { try { Thread.sleep(frameMs - spent); } catch (InterruptedException e) { } }
      else Thread.yield();             // a slow phone that never sleeps still lets key and touch events in
    }
  }

  void fail(Throwable e) {
    if (state == ERROR) return;
    state = ERROR; message = "Error: " + e;
    e.printStackTrace();
  }

  void frame(long dt) {
    if (layoutDirty) { layoutDirty = false; setLayout(landscape); }
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
    if (state == PLAY) {
      Menu m = Menu.current;
      if (testMode && (ev & K_ESC) != 0) { levelExit(WorldMap.ES_ABORT); return; }
      if (m == null && (ev & K_ESC) != 0) { Menu.setCurrent(gameMenu, now); return; }
      if (m != null) {                 // game_menu: time stops
        if ((ev & K_ESC) != 0) { Menu.pop(now); return; }
        if (m == gameMenu) dbgKeys(ev);
        m.action(menuAction(ev), now);
        if (m == gameMenu && gameMenu.check() == M_ABORT) { levelExit(WorldMap.ES_ABORT); return; }
        if (m == debugMenu) { dbgTux(); if (debugAction(debugMenu.check())) return; }
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
          if (dbgFreeze) timeEnd = now + Math.max(1, (timeEnd - now + 999) / 1000) * 1000;   // the clock stands still
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
        if (m == worldmapMenu) dbgKeys(ev);
        m.action(menuAction(ev), now);
        if (m == worldmapMenu && worldmapMenu.check() == M_WM_QUIT) { enterTitle(); return; }
        if (m == debugMapMenu) debugAction(debugMapMenu.check());
        if (m == optionsMenu) optionsChanged();
        if (m == keysMenu) keysChanged();
      }
      if (music.type != Music.LEVEL) music.play(Music.LEVEL);
    } else if (state == TEXT) {
      textFrame(dt, ev);
    } else if (state == CONTROLS || state == ABOUT || state == AUDIOINFO) {
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
    if (state == TEXT) { drawText(g); return; }
    if (state == CONTROLS) { drawControls(g); return; }
    if (state == ABOUT) { drawAbout(g); return; }
    if (state == AUDIOINFO) { drawAudioInfo(g); return; }
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
        if (detail == MAXPOTATO) {     // no logo picture: a note that the still screen isn't frozen
          int ly = landscape ? 30 : 50;
          Res.textCentered(g, Res.GOLD, "SuperTux 0.1.3", sw / 2, ly, 0);
          Res.textCentered(g, Res.GOLD, "Max Potato mode ON", sw / 2, ly + 16, 0);
          Res.textCentered(g, Res.WHITE, "The title stands still", sw / 2, ly + 32, 0);
          Res.textCentered(g, Res.WHITE, "on purpose", sw / 2, ly + 42, 0);
        } else if (detail == POTATO) drawLogoStrip(g);
        else g.drawImage(logo(), landscape ? 80 : 40, landscape ? 8 : 24, Graphics.TOP | Graphics.LEFT);
        if (detail < POTATO) {         // Potato and Max Potato: on the About screen instead
          String[] c = creditLines();
          int y0 = (landscape ? 392 : 535) - (c.length - (landscape ? 5 : 6)) * 17;
          for (int i = 0; i < c.length; i++) Res.chars(g, Res.WHITE, c[i], 0, (y0 + 17 * i) / 2);
        }
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
    if (detail < POTATO && t >= 0 && t < 300) Res.fillAlpha(g, 0, 0, sw, sh, (int) (255 * (300 - t) / 300) << 24);
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

  // Potato: the logo drawn once onto a full-width strip of the sky colour, then copied without blending
  Image logoStrip; int logoStripKey = -1;
  void drawLogoStrip(Graphics g) {
    Image lo = logo();
    int lx = landscape ? 80 : 40, ly = landscape ? 8 : 24, h = lo.getHeight();
    int key = skySolid ^ (landscape ? 1 << 24 : 0);
    if (logoStrip == null || logoStrip.getWidth() != sw || logoStrip.getHeight() != h || key != logoStripKey) {
      logoStrip = Image.createImage(sw, h);
      Graphics lg = logoStrip.getGraphics();
      lg.setColor(skySolid); lg.fillRect(0, 0, sw, h);
      lg.drawImage(lo, lx, 0, Graphics.TOP | Graphics.LEFT);
      logoStripKey = key;
    }
    g.drawImage(logoStrip, 0, ly, Graphics.TOP | Graphics.LEFT);
  }

  Image logoImg;
  Image logo() {
    if (logoImg == null) { try { logoImg = Image.createImage("/lo.png"); } catch (Exception e) { logoImg = Image.createImage(1, 1); } }
    return logoImg;
  }

  void drawWorld(Graphics g) {
    int sx0 = (int) scrollX;
    int top = viewTop >> 1;
    reach();                           // pictures wider than a tile, starting left of the screen
    int col0 = (sx0 >> 5) - reachX, col1 = (sx0 + viewW) >> 5;
    int ox = -(sx0 >> 1);
    if (detail != FULL && !ramSaver) {
      // Low and Potato: the tiles come from the cache, which covers the level's rows, so only the
      // sky above and below it needs filling; Low draws its animated tiles (coins, water) on top
      int sky = skyKey();
      fillSky(g, 0, 0, sw, top, 0);
      int bot = top + (Level.H << 4);
      fillSky(g, 0, bot, sw, sh - bot, bot);
      drawCached(g, sx0, top, sky);
      if (detail == LOW) drawAnimated(g, col0, col1, ox, top);
    } else {
      background(g, scrollX);
      if (detail == FULL) particles(g, 0);
      // tall pictures in the bottom rows stop at the level's bottom edge (the screen's edge in
      // 0.1.3), as in the cached picture, instead of showing under the portrait touch buttons
      g.setClip(0, 0, sw, top + (Level.H << 4));
      drawLayer(g, level.bg, col0, col1, ox, top, false);
      drawLayer(g, level.ia, col0, col1, ox, top, true);
      g.setClip(0, 0, sw, sh);
    }
    if (detail < POTATO) for (int i = 0; i < NE; i++) if (bbOn[i])
      Res.drawTile(g, bbTile[i], ((int) bbX[i] - sx0) >> 1, ((int) (bbY[i] + bbOff[i]) + viewTop) >> 1, frameCounter);
    for (int i = 0; i < badguys.size(); i++) ((BadGuy) badguys.elementAt(i)).draw(g);
    tux.draw(g);
    for (int i = 0; i < bullets.size(); i++) ((Bullet) bullets.elementAt(i)).draw(g);
    if (detail < POTATO) for (int i = 0; i < NE; i++) if (fsEnd[i] != 0)
      Res.text(g, Res.GOLD, fsText[i], ((int) fsX[i] >> 1) + 8 - fsText[i].length() * 4, ((int) fsY[i] + viewTop) >> 1);
    for (int i = 0; i < items.size(); i++) ((Item) items.elementAt(i)).draw(g);
    if (detail < POTATO) for (int i = 0; i < NE; i++) {
      if (bdOn[i] && coinTile >= 0)
        Res.drawTile(g, coinTile, ((int) bdX[i] - sx0) >> 1, ((int) bdY[i] + viewTop) >> 1, 0);
      if (pbEnd[i] != 0) {
        int f = Res.tFrames[pbTile[i]].length > 0 ? Res.tFrames[pbTile[i]][0] : -1;
        if (f >= 0)
          g.drawRegion(Res.tileImg, Res.tfx[f] + (Math.abs(rnd.nextInt()) % 8), Res.tfy[f] + (Math.abs(rnd.nextInt()) % 8), 8, 8, 0,
              ((int) pbX[i] - sx0) >> 1, ((int) pbY[i] + viewTop) >> 1, Graphics.TOP | Graphics.LEFT);
      }
    }
    g.setClip(0, 0, sw, top + (Level.H << 4));
    drawLayer(g, level.fg, col0, col1, ox, top, false);
    g.setClip(0, 0, sw, sh);
    if (detail == FULL) particles(g, 1);
    g.setClip(0, 0, sw, sh);
  }

  Image bgTall;          // portrait: the background with a taller sky, filling the screen
  Image gradImg; int gradA, gradB;   // Full: a picture-less level's sky gradient, drawn once

  // the sky colour Low and Potato fill with (the top of the level's picture, or its gradient's top)
  int skyFill() { return bgImageNo >= 0 ? skyColor : level.topColor; }

  // ---- Low's gradient and Potato's solid sky, from the level's picture (x/bgc.txt, worked out by
  // convert.py) or from the level's own gradient when it has no picture ----
  static int[] bgPotato = new int[0], bgTop, bgBottom;
  int skyTop, skyBottom, skySolid;     // this level's

  void setSkyColours() {
    if (bgPotato.length == 0) {
      String[] l = Res.lines("/x/bgc.txt");
      int max = -1;
      for (int i = 0; i < l.length; i++) { String[] f = split(l[i], '|'); if (f.length >= 4) max = Math.max(max, Integer.parseInt(f[0])); }
      bgPotato = new int[max + 1]; bgTop = new int[max + 1]; bgBottom = new int[max + 1];
      for (int i = 0; i <= max; i++) bgPotato[i] = -1;
      for (int i = 0; i < l.length; i++) {
        String[] f = split(l[i], '|');
        if (f.length < 4) continue;
        int n = Integer.parseInt(f[0]);
        bgPotato[n] = Integer.parseInt(f[1], 16); bgTop[n] = Integer.parseInt(f[2], 16); bgBottom[n] = Integer.parseInt(f[3], 16);
      }
      if (max < 0) bgPotato = new int[] { -1 };
    }
    int b = level.background;
    if (b != 255 && b < bgPotato.length && bgPotato[b] >= 0) { skySolid = bgPotato[b]; skyTop = bgTop[b]; skyBottom = bgBottom[b]; }
    else { skyTop = level.topColor; skyBottom = level.bottomColor; skySolid = mixColour(skyTop, skyBottom, 128); }
  }

  static int mixColour(int a, int b, int t) {          // t of 256 toward b
    int r = ((a >> 16) * (256 - t) + (b >> 16) * t) >> 8;
    int g = (((a >> 8) & 0xff) * (256 - t) + ((b >> 8) & 0xff) * t) >> 8;
    int bl = ((a & 0xff) * (256 - t) + (b & 0xff) * t) >> 8;
    return (r << 16) | (g << 8) | bl;
  }

  // what identifies the sky the caches were drawn with
  int skyKey() { return detail == LOW ? (skyTop * 31 + skyBottom) * 31 + viewTop : skySolid; }

  // sky between rows y and y + h of g, whose row y is screen row sy: Low a soft top-to-bottom
  // gradient in 4-pixel bands, Potato and Max Potato one solid colour
  void fillSky(Graphics g, int x, int y, int w, int h, int sy) {
    if (h <= 0) return;
    if (detail != LOW) { g.setColor(skySolid); g.fillRect(x, y, w, h); return; }
    int off = sy - y;
    for (int yy = y; yy < y + h; ) {
      int s = yy + off, end = Math.min(y + h, ((s >> 2) + 1 << 2) - off);
      g.setColor(mixColour(skyTop, skyBottom, Math.max(0, Math.min(256, (s << 8) / sh))));
      g.fillRect(x, yy, w, end - yy);
      yy = end;
    }
  }

  // Low: the cached picture leaves out animated tiles (coins, water...). Each cell holding one is
  // drawn again from the sky up, clipped to the cell, with every tile reaching into it in the usual
  // order, so tiles drawn after the animated one (and see-through ones) still cover it correctly
  void drawAnimated(Graphics g, int col0, int col1, int ox, int top) {
    int w = level.width;
    if (col0 < 0) col0 = 0;
    if (col1 >= w) col1 = w - 1;
    reach();
    boolean clipped = false;
    for (int y = 0; y < Level.H; y++) {
      int row = y * w;
      for (int x = col0; x <= col1; x++) {
        int tb = level.bg[row + x] & 0xff, ti = level.ia[row + x] & 0xff;
        if (Res.tFrames[tb].length < 2 && (Res.tFrames[ti].length < 2 || bouncing(x, y))) continue;
        int px = ox + (x << 4), py = top + (y << 4);
        g.setClip(px, py, 16, 16); clipped = true;
        fillSky(g, px, py, 16, 16, py);
        for (int l = 0; l < 2; l++) {
          byte[] map = l == 0 ? level.bg : level.ia;
          for (int ry = Math.max(0, y - reachY); ry <= y; ry++)
            for (int rx = Math.max(0, x - reachX); rx <= x; rx++) {
              int t = map[ry * w + rx] & 0xff;
              if (t == 0 || (l == 1 && bouncing(rx, ry))) continue;
              short[] fr = Res.tFrames[t];
              if (fr.length == 0) continue;
              if (((x - rx) << 4) >= (Res.tfw[fr[0]] & 0xff) || ((y - ry) << 4) >= (Res.tfh[fr[0]] & 0xff)) continue;
              Res.drawTile(g, t, ox + (rx << 4), top + (ry << 4), frameCounter);
            }
        }
      }
    }
    if (clipped) g.setClip(0, 0, sw, sh);
  }

  // how far this level's biggest tile pictures reach right and down, in cells (most levels: 0)
  Level reachLevel; int reachX, reachY;
  void reach() {
    if (reachLevel == level) return;
    reachLevel = level; reachX = 0; reachY = 0;
    for (int l = 0; l < 3; l++) {
      byte[] map = l == 0 ? level.bg : l == 1 ? level.ia : level.fg;
      for (int i = 0; i < map.length; i++) {
        short[] fr = Res.tFrames[map[i] & 0xff];
        if (fr.length == 0) continue;
        reachX = Math.max(reachX, ((Res.tfw[fr[0]] & 0xff) - 1) >> 4);
        reachY = Math.max(reachY, ((Res.tfh[fr[0]] & 0xff) - 1) >> 4);
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
      // a coin, brick or bonus block changed: redraw just those columns (and as far right as the widest tile,
      // which a wide tile there could reach into)
      reach();
      int a = Math.max(level.chMin, tcFirst), b = Math.min(level.chMax + reachX, tcLast);
      for (int c = a; c <= b; c++) cacheColumn(c, top);
    }
    tcChanges = level.changes; level.chMin = Integer.MAX_VALUE; level.chMax = -1;
    for (int c = c0; c <= c1; c++)
      if (tcLast < 0 || c < tcFirst || c > tcLast) cacheColumn(c, top);
    tcFirst = c0; tcLast = c1;
    int slot = c0 % tcN, x = (c0 << 4) - (sx0 >> 1);
    int w1 = Math.min(n, tcN - slot);
    g.drawRegion(tc, slot << 4, 0, w1 << 4, h, 0, x, top, Graphics.TOP | Graphics.LEFT);
    if (w1 < n) g.drawRegion(tc, 0, 0, (n - w1) << 4, h, 0, x + (w1 << 4), top, Graphics.TOP | Graphics.LEFT);
  }

  // one column, drawn like drawLayer would (bg then ia, rows top to bottom) but clipped to the
  // column; tiles from as far left as the level's widest picture reaches can reach into it
  void cacheColumn(int c, int top) {
    int x = (c % tcN) << 4, h = Level.H << 4, w = level.width;
    tcG.setClip(x, 0, 16, h);
    fillSky(tcG, x, 0, 16, h, top);
    reach();
    int a = Math.max(0, c - reachX), b = Math.min(c, w - 1);
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
    if (detail != FULL) { fillSky(g, 0, 0, sw, sh, 0); return; }   // Low: gradient; Potato: solid
    if (bgImage == null && bgImageNo >= 0) {         // Full with the picture not loaded (yet)
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
      // drawn once into an image and copied each frame (RAM saver: drawn every frame)
      boolean cache = !ramSaver;
      if (cache && gradImg != null && gradImg.getWidth() == sw && gradImg.getHeight() == sh && gradA == a && gradB == b) {
        g.drawImage(gradImg, 0, 0, Graphics.TOP | Graphics.LEFT);
        return;
      }
      Graphics gg0 = g;
      if (cache) {
        if (gradImg == null || gradImg.getWidth() != sw || gradImg.getHeight() != sh) gradImg = Image.createImage(sw, sh);
        g = Res.fresh(gradImg); gradA = a; gradB = b;
      } else gradImg = null;
      for (int y = 0; y < sh; y += 4) {
        int r = ((a >> 16) * (sh - y) + (b >> 16) * y) / sh;
        int gg = (((a >> 8) & 0xff) * (sh - y) + ((b >> 8) & 0xff) * y) / sh;
        int bb = ((a & 0xff) * (sh - y) + (b & 0xff) * y) / sh;
        g.setColor((r << 16) | (gg << 8) | bb);
        g.fillRect(0, y, sw, 4);
      }
      if (cache) gg0.drawImage(gradImg, 0, 0, Graphics.TOP | Graphics.LEFT);
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

  // cloud tops for cloud bottoms on a level's top row (x/tops.txt): drawn above the level in Full
  // portrait, where 0.1.3's screen ended at that row and here sky shows instead of the rest
  static byte[] topOf;
  static void loadTops() {
    topOf = new byte[256];
    String[] l = Res.lines("/x/tops.txt");
    for (int i = 0; i < l.length; i++) {
      int sp = l[i].indexOf(' ');
      if (sp > 0) topOf[Integer.parseInt(l[i].substring(0, sp)) & 0xff] = (byte) Integer.parseInt(l[i].substring(sp + 1).trim());
    }
  }

  void drawLayer(Graphics g, byte[] map, int col0, int col1, int ox, int top, boolean ia) {
    int w = level.width;
    if (col0 < 0) col0 = 0;
    if (col1 >= w) col1 = w - 1;
    if (detail == FULL && top >= 16) {
      if (topOf == null) loadTops();
      for (int x = col0; x <= col1; x++) {
        int t = topOf[map[x] & 0xff] & 0xff;
        if (t != 0) Res.drawTile(g, t, ox + (x << 4), top - 16, frameCounter);
      }
    }
    for (int y = 0; y < Level.H; y++) {
      int row = y * w;
      for (int x = col0; x <= col1; x++) {
        int t = map[row + x] & 0xff;
        if (t == 0) continue;
        if (ia && detail < POTATO && bouncing(x, y)) continue;
        Res.drawTile(g, t, ox + (x << 4), top + (y << 4), detail >= POTATO ? 0 : frameCounter);
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
  void hud(Graphics g, int font, String s, int x, int y) { Res.text(g, font, s, x, y, detail == FULL ? 1 : 0); }   // shadow only in Full

  // Portrait below Full (without RAM saver): the HUD sits on the plain sky above the level, so it is
  // drawn into a small image and redrawn only when something on it changes (about once a second)
  Image hudImg; int hudSky = -1, hudScore = -1, hudTl = -1, hudCoins = -1, hudLives = -1, hudFlags = -1; String hudFps;

  void status(Graphics g) {
    int hh = showFps ? 32 : 22;          // the lives Tux reaches 2 px under the second row
    if (landscape || detail == FULL || ramSaver || testMode || (viewTop >> 1) < hh) { statusDraw(g); return; }
    int tl = (int) ((timeEnd - now) / 1000);
    if (tl < 0) tl = 0;
    int sky = skyKey();
    boolean blink = endSequence == 0 && timeEnd - now <= TIME_WARNING && (frameCounter % 10) >= 5;
    int flags = (showFps ? 1 : 0) | (blink ? 2 : 0) | (endSequence == 0 && now >= timeEnd ? 4 : 0) | (detail << 3);
    if (hudImg == null || hudImg.getWidth() != sw || hudImg.getHeight() != hh) { hudImg = Image.createImage(sw, hh); hudFlags = -1; }
    if (sky != hudSky || score != hudScore || tl != hudTl || distros != hudCoins || lives != hudLives || flags != hudFlags
        || (showFps && fpsText != hudFps)) {
      Graphics hg = Res.fresh(hudImg);
      fillSky(hg, 0, 0, sw, hh, 0);
      statusDraw(hg);
      hudSky = sky; hudScore = score; hudTl = tl; hudCoins = distros; hudLives = lives; hudFlags = flags; hudFps = fpsText;
    }
    g.drawImage(hudImg, 0, 0, Graphics.TOP | Graphics.LEFT);
  }

  void statusDraw(Graphics g) {
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
      hud(g, Res.GOLD, s, lx + 66 - s.length() * 8, y);   // clear of LIVES even at x99
      Res.drawSpriteTL(g, Spr.TUX_LIFE, lx + 65, y);
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
    }
  }

  // centred text broken at spaces to fit the screen; returns the number of lines
  int centeredWrapped(Graphics g, int font, String t, int cx, int y, int lineH) {
    String[] l = wrap(t, sw / 8 - 1);
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
    showText(file, Pic.BG_EXTRO, 1.0f, AFTER_CREDITS);
    music.playFile("t", -1);
  }
}
