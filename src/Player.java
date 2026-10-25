// Tux. A port of 0.1.3's player.cpp, in 0.1.3's own world units (32 per tile).
import javax.microedition.lcdui.Graphics;

final class Player extends Body {
  static final float MAX_WALK_XM = 2.3f, MAX_RUN_XM = 3.2f;
  static final float WALK_SPEED = 1.0f, WALK_ACCELERATION_X = 0.03f, RUN_ACCELERATION_X = 0.04f;
  static final float SKID_XM = 2.0f;
  static final int SKID_TIME = 200, TUX_SAFE_TIME = 1800, TUX_INVINCIBLE_TIME = 10000;
  static final int TUX_INVINCIBLE_TIME_WARNING = 2000, KICKING_TIME = 200;
  static final int LEFT = 0, RIGHT = 1;
  static final int SMALL = 0, BIG = 1;
  static final int DYING_NOT = 0, DYING_SQUISHED = 1;
  static final int KILL_SHRINK = 0, KILL_KILL = 1;

  final Game game;
  Level level;

  float prevX, prevY, prevH;          // previous_base
  int size = SMALL;
  boolean gotCoffee, duck, jumping, canJump = true, holding;
  int dir = RIGHT, oldDir = RIGHT;
  int dying = DYING_NOT;
  // input (true = pressed)
  boolean inLeft, inRight, inUp, inDown, inFire, oldUp, oldFire;
  // timers: 0 = stopped, else the end time
  long invincibleT, skiddingT, safeT, kickT, frameT;
  int frameMain;

  Player(Game g) { game = g; }

  boolean check(long t) { return t != 0 && game.now < t; }

  void init(Level l) {
    level = l;
    holding = false;
    w = 32; h = 32; size = SMALL; gotCoffee = false;
    x = l.startX; y = l.startY;
    syncOld(); prevX = x; prevY = y; prevH = h;
    dir = oldDir = RIGHT; duck = false;
    dying = DYING_NOT; jumping = false; canJump = true;
    frameMain = 0;
    inLeft = inRight = inUp = inDown = inFire = oldUp = oldFire = false;
    invincibleT = skiddingT = safeT = kickT = frameT = 0;
    resetPhysics();
  }

  // Player::level_begin (the title demo's wrap-around)
  void levelBegin() {
    x = 100; y = 170;
    resetPhysics();
    syncOld(); prevX = x; prevY = y; prevH = h;
    duck = false;
    dying = DYING_NOT;
    inLeft = inRight = inUp = inDown = inFire = oldUp = oldFire = false;
    invincibleT = skiddingT = safeT = kickT = frameT = 0;
  }

  boolean onGround() {
    return level.solid(x + w / 2, y + h) || level.solid(x + 1, y + h) || level.solid(x + w - 1, y + h);
  }

  boolean underSolid() {
    return level.solid(x + w / 2, y) || level.solid(x + 1, y) || level.solid(x + w - 1, y);
  }

  void action(float fr) {
    boolean jumpedInSolid = false;
    if (!inFire) holding = false;
    prevX = x; prevY = y; prevH = h;

    if (dying == DYING_NOT) handleInput();
    applyPhysics(fr, level.gravity);

    if (dying == DYING_NOT) {
      float targetX = x;
      sweptCollision(level);
      if (targetX != x) vx = 0;   // don't accelerate against a wall

      // drift out when stuck under tiles after ducking
      if (!duck && onGround() && oldX == x && oldY == y && level.hitsMap(x, y, w, h)) {
        x += fr * WALK_SPEED * (dir == RIGHT ? 1 : -1);
        syncOld(); prevX = x; prevY = y;
      }

      if (!onGround()) {
        gravity = true;
        if (underSolid()) { setVY(0); jumpedInSolid = true; }
      } else {
        if (getVY() < 0) {
          y = (int) (((int) y / 32) * 32);
          setVY(0);
        }
        gravity = false;
        game.scoreMultiplier = 1;
      }

      if (jumpedInSolid) {
        int f = level.flagsAt(x, y);
        if ((f & (Res.BRICK | Res.FULLBOX)) != 0) {
          game.tryGrabDistro(x, y - 32, true);
          game.tryBumpBadguy(x, y - 64);
          game.tryBreakBrick(x, y, size == SMALL, RIGHT);
          game.bumpBrick(x, y);
          game.tryEmptyBox(x, y, RIGHT);
        }
        f = level.flagsAt(x + 31, y);
        if ((f & (Res.BRICK | Res.FULLBOX)) != 0) {
          game.tryGrabDistro(x + 31, y - 32, true);
          game.tryBumpBadguy(x + 31, y - 64);
          if (size == BIG) game.tryBreakBrick(x + 31, y, size == SMALL, LEFT);
          game.bumpBrick(x + 31, y);
          game.tryEmptyBox(x + 31, y, LEFT);
        }
      }

      grabDistros();

      if (jumpedInSolid) {
        ++y; ++oldY;
        if (onGround()) jumping = false;
      }
    }
    if (skiddingT != 0 && game.now >= skiddingT) skiddingT = 0;
    if (invincibleT != 0 && game.now >= invincibleT) invincibleT = 0;
    if (safeT != 0 && game.now >= safeT) safeT = 0;
    if (kickT != 0 && game.now >= kickT) kickT = 0;
  }

  void handleHorizontalInput() {
    float nvx = vx, nvy = getVY(), nax = ax, nay = -ay;
    float dirsign = 0;
    if (inLeft && !inRight && (!duck || getVY() != 0)) { oldDir = dir; dir = LEFT; dirsign = -1; }
    else if (!inLeft && inRight && (!duck || getVY() != 0)) { oldDir = dir; dir = RIGHT; dirsign = 1; }

    if (!inFire) {
      nax = dirsign * WALK_ACCELERATION_X;
      if (nvx >= MAX_WALK_XM && dirsign > 0) { nvx = MAX_WALK_XM; nax = 0; }
      else if (nvx <= -MAX_WALK_XM && dirsign < 0) { nvx = -MAX_WALK_XM; nax = 0; }
    } else {
      nax = dirsign * RUN_ACCELERATION_X;
      if (nvx >= MAX_RUN_XM && dirsign > 0) { nvx = MAX_RUN_XM; nax = 0; }
      else if (nvx <= -MAX_RUN_XM && dirsign < 0) { nvx = -MAX_RUN_XM; nax = 0; }
    }
    if (dirsign != 0 && Math.abs(nvx) < WALK_SPEED) nvx = dirsign * WALK_SPEED;

    if (onGround() && ((nvx < 0 && dirsign > 0) || (nvx > 0 && dirsign < 0))) {
      if (Math.abs(nvx) > SKID_XM && !check(skiddingT)) {
        skiddingT = game.now + SKID_TIME;
        game.sound(Game.SND_SKID);
        nax *= 2.5f;
      } else nax *= 2;
    }
    if (dirsign == 0) {
      if (Math.abs(nvx) < WALK_SPEED) { nvx = 0; nax = 0; }
      else if (nvx < 0) nax = WALK_ACCELERATION_X * 1.5f;
      else nax = WALK_ACCELERATION_X * -1.5f;
    }
    if ((level.flagsAt(x, y + h) & Res.ICE) != 0) {
      if (nax != 0) nax *= 1 / Math.abs(nvx);
    }
    setVelocity(nvx, nvy);
    setAcceleration(nax, nay);
  }

  void handleVerticalInput() {
    if (inUp && canJump) {
      if (onGround()) {
        if (Math.abs(vx) > MAX_WALK_XM) setVY(5.8f); else setVY(5.2f);
        --y;
        jumping = true; canJump = false;
        game.sound(size == SMALL ? Game.SND_JUMP : Game.SND_BIGJUMP);
      }
    } else if (!inUp && jumping) {
      jumping = false;
      if (getVY() > 0) setVY(0);
    }
    if ((level.solid(x + w / 2, y + h + 64) || level.solid(x + 1, y + h + 64) || level.solid(x + w - 1, y + h + 64))
        && !jumping && !canJump && inUp && !oldUp)
      canJump = true;
    oldUp = inUp;
  }

  void handleInput() {
    handleHorizontalInput();
    if (onGround() && !inUp) canJump = true;
    if (inUp || (!inUp && jumping)) handleVerticalInput();

    if (inFire && !oldFire && gotCoffee) {
      game.addBullet(x, y, vx, dir);
      oldFire = true;
    }

    if (!check(frameT)) {
      frameT = game.now + 25;
      if (!inRight && !inLeft) frameMain = 1;
      else if ((inFire && (game.frameCounter % 2) == 0) || (game.frameCounter % 4) == 0)
        frameMain = (frameMain + 1) % 4;
    }

    if (inDown && size == BIG && !duck && getVY() == 0 && onGround()) {
      duck = true; h = 32; y += 32;
      syncOld(); prevX = x; prevY = y; prevH = h;
    } else if (!inDown && size == BIG && duck && getVY() == 0 && onGround()) {
      duck = false; y -= 32; h = 64;
      syncOld(); prevX = x; prevY = y; prevH = h;
    }
  }

  void grow() {
    if (size == BIG) return;
    size = BIG; h = 64; y -= 32;
    syncOld(); prevX = x; prevY = y; prevH = h;
  }

  void jumpOfBadguy(BadGuy b) {
    if (inUp) setVY(5.2f); else setVY(2.0f);
    y = b.y - h - 2;
  }

  void grabDistros() {
    if (dying == DYING_NOT) {
      game.tryGrabDistro(x, y, false);
      game.tryGrabDistro(x + 31, y, false);
      game.tryGrabDistro(x, y + h, false);
      game.tryGrabDistro(x + 31, y + h, false);
      if (size == BIG) {
        game.tryGrabDistro(x, y + h / 2, false);
        game.tryGrabDistro(x + 31, y + h / 2, false);
      }
    }
    if (game.distros >= Game.DISTROS_LIFEUP) {
      game.distros -= Game.DISTROS_LIFEUP;
      if (game.lives < Game.MAX_LIVES) ++game.lives;
      game.sound(Game.SND_LIFEUP);
    }
  }

  // Player::collision with a bad guy (CO_BADGUY)
  void collideBadguy(BadGuy b) {
    if (b.dying == BadGuy.DYING_NOT && dying == DYING_NOT && !check(safeT) && b.mode != BadGuy.HELD) {
      if (b.mode == BadGuy.FLAT && inFire && !holding) {
        holding = true;
        b.mode = BadGuy.HELD;
        b.y -= 8;
      } else if (b.mode == BadGuy.FLAT) {
        // kicking a flat bad guy doesn't hurt
      } else if (b.mode == BadGuy.KICK) {
        if (!check(invincibleT)) kill(KILL_SHRINK);
        else {
          b.dying = BadGuy.DYING_FALLING;
          game.sound(Game.SND_FALL);
          game.addScore(b.x - game.scrollX, b.y, 25 * game.scoreMultiplier);
        }
      } else {
        if (!check(invincibleT)) kill(KILL_SHRINK);
        else b.killMe(25);
      }
      game.scoreMultiplier++;
    }
  }

  void kill(int mode) {
    game.sound(Game.SND_HURT);
    vx = 0;
    if (mode == KILL_SHRINK && size == BIG) {
      if (gotCoffee) gotCoffee = false;
      else { size = SMALL; h = 32; duck = false; }
      safeT = game.now + TUX_SAFE_TIME;
    } else {
      gravity = true;
      setAcceleration(0, 0);
      setVelocity(0, 7);
      if (dying != DYING_SQUISHED) --game.lives;
      dying = DYING_SQUISHED;
    }
  }

  boolean isDead() {
    return y > 640 || x < game.scrollX - 300;
  }

  void checkBounds() {
    if (x < 0) x = 0;
    if (y > 640) kill(KILL_KILL);
    if (x < game.scrollX && (!level.backScrolling || level.autoscroll != 0)) x = game.scrollX;
    if (level.autoscroll != 0) {
      if (x == game.scrollX)
        if ((level.solid(x + 32, y) || (size != SMALL && !duck && level.solid(x + 32, y + 32))) && dying == DYING_NOT)
          kill(KILL_KILL);
      if (x + w > game.scrollX + game.viewW) x = game.scrollX + game.viewW - w;
    }
  }

  void draw(Graphics g) {
    if (check(safeT) && (game.frameCounter % 2) != 0) return;
    int sx = (int) (x - game.scrollX) >> 1, sy = ((int) y + game.viewTop) >> 1;
    if (dying == DYING_SQUISHED) { Res.drawSprite(g, Spr.SMALLTUX_GAMEOVER, sx, sy, game.now); return; }
    boolean r = dir == RIGHT;
    int s;
    if (size == SMALL) {
      if (skiddingT != 0) s = r ? Spr.SMALLTUX_SKID_RIGHT : Spr.SMALLTUX_SKID_LEFT;
      else if (kickT != 0) s = r ? Spr.SMALLTUX_KICK_RIGHT : Spr.SMALLTUX_KICK_LEFT;
      else if (getVY() != 0) s = r ? Spr.SMALLTUX_JUMP_RIGHT : Spr.SMALLTUX_JUMP_LEFT;
      else if (Math.abs(vx) < 1.0f) s = r ? Spr.SMALLTUX_STAND_RIGHT : Spr.SMALLTUX_STAND_LEFT;
      else s = r ? Spr.SMALLTUX_WALK_RIGHT : Spr.SMALLTUX_WALK_LEFT;
    } else if (gotCoffee) {
      if (duck) s = r ? Spr.FIRETUX_DUCK_RIGHT : Spr.FIRETUX_DUCK_LEFT;
      else if (skiddingT != 0) s = r ? Spr.FIRETUX_SKID_RIGHT : Spr.FIRETUX_SKID_LEFT;
      else if (kickT != 0) s = r ? Spr.FIRETUX_KICK_RIGHT : Spr.FIRETUX_KICK_LEFT;
      else if (getVY() != 0) s = r ? Spr.FIRETUX_JUMP_RIGHT : Spr.FIRETUX_JUMP_LEFT;
      else if (Math.abs(vx) < 1.0f) s = r ? Spr.FIRETUX_STAND_RIGHT : Spr.FIRETUX_STAND_LEFT;
      else s = r ? Spr.FIRETUX_WALK_RIGHT : Spr.FIRETUX_WALK_LEFT;
    } else {
      if (duck) s = r ? Spr.LARGETUX_DUCK_RIGHT : Spr.LARGETUX_DUCK_LEFT;
      else if (skiddingT != 0) s = r ? Spr.LARGETUX_SKID_RIGHT : Spr.LARGETUX_SKID_LEFT;
      else if (kickT != 0) s = r ? Spr.LARGETUX_KICK_RIGHT : Spr.LARGETUX_KICK_LEFT;
      else if (getVY() != 0) s = r ? Spr.LARGETUX_JUMP_RIGHT : Spr.LARGETUX_JUMP_LEFT;
      else if (Math.abs(vx) < 1.0f) s = r ? Spr.LARGETUX_STAND_RIGHT : Spr.LARGETUX_STAND_LEFT;
      else s = r ? Spr.LARGETUX_WALK_RIGHT : Spr.LARGETUX_WALK_LEFT;
    }
    Res.drawSprite(g, s, sx, sy, game.now);
    if (holding && getVY() == 0 && !duck) {
      int gs = size == SMALL ? (r ? Spr.SMALLTUX_GRAB_RIGHT : Spr.SMALLTUX_GRAB_LEFT)
          : gotCoffee ? (r ? Spr.FIRETUX_GRAB_RIGHT : Spr.FIRETUX_GRAB_LEFT)
          : (r ? Spr.LARGETUX_GRAB_RIGHT : Spr.LARGETUX_GRAB_LEFT);
      Res.drawSprite(g, gs, sx, sy, game.now);
    }
    if (invincibleT != 0 && (invincibleT - game.now > TUX_INVINCIBLE_TIME_WARNING || game.frameCounter % 3 != 0))
      Res.drawSprite(g, size == SMALL || duck ? Spr.SMALLTUX_STAR : Spr.LARGETUX_STAR, sx, sy, game.now);
  }
}
