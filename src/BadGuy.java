// Bad guys: a port of 0.1.3's badguy.cpp.
import java.util.Random;
import javax.microedition.lcdui.Graphics;

final class BadGuy extends Body {
  // kinds (the order matches tools/convert.py)
  static final int MRICEBLOCK = 0, JUMPY = 1, MRBOMB = 2, STALACTITE = 3, FLAME = 4, FISH = 5,
      BOUNCINGSNOWBALL = 6, FLYINGSNOWBALL = 7, SPIKY = 8, SNOWBALL = 9, BOMB = 10;
  // modes
  static final int NORMAL = 0, FLAT = 1, KICK = 2, HELD = 3, JUMPY_JUMP = 4, BOMB_TICKING = 5,
      BOMB_EXPLODE = 6, STALACTITE_SHAKING = 7, STALACTITE_FALL = 8, FISH_WAIT = 9, FLY_UP = 10, FLY_DOWN = 11;
  static final int DYING_NOT = 0, DYING_SQUISHED = 1, DYING_FALLING = 2;
  static final int LEFT = 0, RIGHT = 1;
  static final float WALK_SPEED = 0.8f;
  static final Random rnd = new Random();

  final Game game;
  final Level level;
  int kind, mode = NORMAL, dying = DYING_NOT, dir = LEFT;
  boolean stayOnPlatform, seen, removable;
  int squishcount;
  long timer;                   // end time, 0 = stopped
  int sprL = -1, sprR = -1;
  float homeX, homeY, angle;    // flame centre / stalactite home, flame angle

  BadGuy(Game g, float px, float py, int k, boolean stay) {
    game = g; level = g.level;
    x = px; y = py; kind = k; stayOnPlatform = stay;
    w = 32; h = 32;
    homeX = x; homeY = y;
    resetPhysics();
    switch (kind) {
      case MRBOMB: setVelocity(-WALK_SPEED, 0); setSprite(Spr.MRBOMB_LEFT, Spr.MRBOMB_RIGHT); break;
      case MRICEBLOCK: setVelocity(-WALK_SPEED, 0); setSprite(Spr.MRICEBLOCK_LEFT, Spr.MRICEBLOCK_RIGHT); break;
      case JUMPY: setSprite(Spr.JUMPY_LEFT_UP, Spr.JUMPY_LEFT_UP); break;
      case BOMB: setSprite(Spr.MRBOMB_TICKING_LEFT, Spr.MRBOMB_TICKING_RIGHT); dying = DYING_SQUISHED; break;
      case FLAME: gravity = false; setSprite(Spr.FLAME, Spr.FLAME); break;
      case BOUNCINGSNOWBALL: setVelocity(-1.3f, 0); setSprite(Spr.BOUNCINGSNOWBALL_LEFT, Spr.BOUNCINGSNOWBALL_RIGHT); break;
      case STALACTITE: gravity = false; setSprite(Spr.STALACTITE, Spr.STALACTITE); break;
      case FISH: setSprite(Spr.FISH, Spr.FISH); gravity = true; break;
      case FLYINGSNOWBALL: setSprite(Spr.FLYINGSNOWBALL, Spr.FLYINGSNOWBALL); gravity = false; break;
      case SPIKY: setVelocity(-WALK_SPEED, 0); setSprite(Spr.SPIKY_LEFT, Spr.SPIKY_RIGHT); break;
      default: setVelocity(-WALK_SPEED, 0); setSprite(Spr.SNOWBALL_LEFT, Spr.SNOWBALL_RIGHT); break;
    }
    if (kind != FLAME && kind != FISH)
      while (level.hitsMap(x, y, w, h)) --y;     // started in a wall
    syncOld();
  }

  void setSprite(int l, int r) { sprL = l; sprR = r; }
  boolean timerRunning() { return timer != 0 && game.now < timer; }
  void startTimer(int ms) { timer = game.now + ms; }
  boolean solid(float px, float py) { return level.solid(px, py); }

  void action(float fr) {
    if (x < game.scrollX - 256) { removable = true; return; }
    if (y > 640) { removable = true; return; }
    if (x <= game.scrollX + game.viewW + 256) seen = true;
    if (!seen) return;
    switch (kind) {
      case MRICEBLOCK: actionMriceblock(fr); break;
      case JUMPY: actionJumpy(fr); break;
      case MRBOMB: actionWalker(fr, true); break;
      case BOMB: actionBomb(fr); break;
      case STALACTITE: actionStalactite(fr); break;
      case FLAME: actionFlame(fr); break;
      case FISH: actionFish(fr); break;
      case BOUNCINGSNOWBALL: actionBouncing(fr); break;
      case FLYINGSNOWBALL: actionFlying(fr); break;
      default: actionWalker(fr, false); break;       // spiky, snowball
    }
  }

  void actionMriceblock(float fr) {
    Player tux = game.tux;
    if (mode != HELD) fall();
    if (mode != HELD) {
      applyPhysics(fr, level.gravity);
      if (dying != DYING_FALLING) sweptCollision(level);
    } else {
      dir = tux.dir;
      x = dir == RIGHT ? tux.x + 16 : tux.x - 16;
      y = tux.y + tux.h / 1.5f - h;
      if (level.hitsMap(x, y, w, h)) { x = tux.x; y = tux.y + tux.h / 1.5f - h; }
      if (!tux.inFire) {             // shoot!
        if (dir == LEFT) x -= 24; else x += 24;
        syncOld();
        mode = KICK;
        tux.kickT = game.now + Player.KICKING_TIME;
        setSprite(Spr.MRICEBLOCK_FLAT_LEFT, Spr.MRICEBLOCK_FLAT_RIGHT);
        vx = dir == LEFT ? -3.5f : 3.5f;
        game.sound(Game.SND_KICK);
      }
    }
    if (dying == DYING_NOT) {
      int changed = dir;
      checkHorizontalBump(false);
      if (mode == KICK && changed != dir) game.sound(Game.SND_RICOCHET);
    }
    if (mode == FLAT && !timerRunning()) {
      mode = NORMAL;
      setSprite(Spr.MRICEBLOCK_LEFT, Spr.MRICEBLOCK_RIGHT);
      setVelocity(dir == LEFT ? -0.8f : 0.8f, 0);
    }
  }

  void checkHorizontalBump(boolean checkCliff) {
    float half = h / 2;
    if (dir == LEFT && solid(x, (int) y + half)) {
      if (kind == MRICEBLOCK && mode == KICK) game.tryBreakBrick(x, y + half, false, dir);
      dir = RIGHT; setVelocity(-vx, getVY()); return;
    }
    if (dir == RIGHT && solid(x + w, (int) y + half)) {
      if (kind == MRICEBLOCK && mode == KICK) game.tryBreakBrick(x + w, (int) y + half, false, dir);
      dir = LEFT; setVelocity(-vx, getVY()); return;
    }
    if (!checkCliff) return;
    if (!solid(x + w / 2, y + h)) return;
    if (dir == LEFT && !solid(x, (int) y + h + half)) { dir = RIGHT; setVelocity(-vx, getVY()); return; }
    if (dir == RIGHT && !solid(x + w, (int) y + h + half)) { dir = LEFT; setVelocity(-vx, getVY()); }
  }

  void fall() {
    if (dying != DYING_FALLING) {
      if (!solid(x + w / 2, y + h)) gravity = true;
      else {
        if (getVY() < 0) { y = (int) ((y + h) / 32) * 32 - h; setVY(0); }
        gravity = false;
        if (stayOnPlatform && mode == NORMAL) {
          if (!solid(x + (dir == LEFT ? 0 : w), y + h)) {
            if (dir == LEFT) { dir = RIGHT; vx = Math.abs(vx); }
            else { dir = LEFT; vx = -Math.abs(vx); }
          }
        }
      }
    } else gravity = true;
  }

  void actionJumpy(float fr) {
    float v = Math.abs(getVY());
    if (v > 5.6f) setSprite(Spr.JUMPY_LEFT_DOWN, Spr.JUMPY_LEFT_DOWN);
    else if (v > 5.3f) setSprite(Spr.JUMPY_LEFT_MIDDLE, Spr.JUMPY_LEFT_MIDDLE);
    else setSprite(Spr.JUMPY_LEFT_UP, Spr.JUMPY_LEFT_UP);
    fall();
    if (dying == DYING_NOT && solid(x, y + 32)) { setVY(6); gravity = true; mode = JUMPY_JUMP; }
    else if (mode == JUMPY_JUMP) mode = NORMAL;
    dir = game.tux.x > x ? RIGHT : LEFT;
    applyPhysics(fr, level.gravity);
    if (dying == DYING_NOT) sweptCollision(level);
  }

  // mrbomb (cliff check), spiky and snowball
  void actionWalker(float fr, boolean cliff) {
    if (dying == DYING_NOT) checkHorizontalBump(cliff);
    fall();
    applyPhysics(fr, level.gravity);
    if (dying != DYING_FALLING) sweptCollision(level);
  }

  void actionBomb(float fr) {
    fall();
    if (mode == NORMAL) { mode = BOMB_TICKING; startTimer(1000); }
    else if (!timerRunning()) {
      if (mode == BOMB_TICKING) {
        mode = BOMB_EXPLODE;
        setSprite(Spr.MRBOMB_EXPLOSION, Spr.MRBOMB_EXPLOSION);
        dying = DYING_NOT;       // now the bomb hurts
        startTimer(1000);
        game.sound(Game.SND_EXPLODE);
      } else if (mode == BOMB_EXPLODE) { removable = true; return; }
    }
    applyPhysics(fr, level.gravity);
    sweptCollision(level);
  }

  void actionStalactite(float fr) {
    Player tux = game.tux;
    if (mode == NORMAL) {
      if (tux.x + 32 > x - 40 && tux.x < x + 32 + 40 && tux.y + tux.h > y) {
        startTimer(800); mode = STALACTITE_SHAKING;
      }
    }
    if (mode == STALACTITE_SHAKING) {
      // Potato: a small left/right step every 100 ms instead of a random jitter every frame
      if (game.detail == Game.POTATO) x = homeX + (((game.frameCounter >> 2) & 1) == 0 ? -2 : 2);
      else x = homeX + (rnd.nextInt() % 3);
      if (!timerRunning()) mode = STALACTITE_FALL;
    } else if (mode == STALACTITE_FALL) {
      fall();
      if (solid(x + w / 2, y + h)) {
        startTimer(2000); dying = DYING_SQUISHED; mode = FLAT;
        if (game.detail == Game.POTATO) { removable = true; return; }   // no broken stalactite
        setSprite(Spr.STALACTITE_BROKEN, Spr.STALACTITE_BROKEN);
      }
    } else if (mode == FLAT) fall();
    applyPhysics(fr, level.gravity);
    if (dying == DYING_SQUISHED && !timerRunning()) removable = true;
  }

  void actionFlame(float fr) {
    x = homeX + (float) Math.cos(angle) * 100;
    y = homeY + (float) Math.sin(angle) * 100;
    angle += fr * 0.02f;
    if (angle > 6.2831855f) angle -= 6.2831855f;
  }

  void actionFish(float fr) {
    if (dying == DYING_NOT && (level.flagsAt(x, y + h) & Res.WATER) != 0 && getVY() <= 0 && mode == NORMAL) {
      mode = FISH_WAIT; setSprite(-1, -1); setVelocity(0, 0); gravity = false; startTimer(1000);
    } else if (mode == FISH_WAIT && !timerRunning()) {
      setSprite(Spr.FISH, Spr.FISH); mode = NORMAL; setVelocity(0, 6); gravity = true;
    }
    applyPhysics(fr, level.gravity);
    if (dying == DYING_NOT) sweptCollision(level);
    if (getVY() < 0 && sprL != -1) setSprite(Spr.FISH_DOWN, Spr.FISH_DOWN);
  }

  void actionBouncing(float fr) {
    fall();
    if (dying == DYING_NOT && solid(x, y + 32)) { setVY(4.5f); gravity = true; }
    else mode = NORMAL;
    checkHorizontalBump(false);
    applyPhysics(fr, level.gravity);
    if (dying == DYING_NOT) sweptCollision(level);
    if (dying == DYING_SQUISHED && !timerRunning()) removable = true;
  }

  void actionFlying(float fr) {
    if (dying == DYING_NOT && mode == NORMAL) { mode = FLY_UP; setVY(1); startTimer(500); }
    if (dying == DYING_NOT && !timerRunning()) {
      if (mode == FLY_UP) { mode = FLY_DOWN; setVY(-1); }
      else if (mode == FLY_DOWN) { mode = FLY_UP; setVY(1); }
      startTimer(1000);
    }
    if (dying != DYING_NOT) gravity = true;
    applyPhysics(fr, level.gravity);
    if (dying == DYING_NOT || dying == DYING_SQUISHED) sweptCollision(level);
    if (dying == DYING_SQUISHED && !timerRunning()) removable = true;
  }

  void draw(Graphics g) {
    if (x <= game.scrollX - w || x >= game.scrollX + game.viewW) return;
    int s = dir == LEFT ? sprL : sprR;
    if (s < 0) return;
    Res.drawSprite(g, s, (int) (x - game.scrollX) >> 1, ((int) y + game.viewTop) >> 1,
        game.detail == Game.POTATO ? 0 : game.now);   // Potato: first frame only, enemies just slide
  }

  void bump() {
    if (kind == FLAME || kind == BOMB || kind == FISH || kind == FLYINGSNOWBALL) return;
    setVY(3);
    killMe(25);
  }

  void squishMe(Player p) {
    p.jumpOfBadguy(this);
    game.addScore(x - game.scrollX, y, 50 * game.scoreMultiplier);
    game.sound(Game.SND_SQUISH);
    game.scoreMultiplier++;
    dying = DYING_SQUISHED;
    startTimer(2000);
    setVelocity(0, 0);
    if (game.detail == Game.POTATO) removable = true;   // Potato: gone, no squished sprite
  }

  void squish(Player p) {
    if (kind == MRBOMB) {
      game.addBadGuy(x, y, BOMB, false);
      p.jumpOfBadguy(this);
      game.addScore(x - game.scrollX, y, 50 * game.scoreMultiplier);
      game.sound(Game.SND_SQUISH);
      game.scoreMultiplier++;
      removable = true;
    } else if (kind == MRICEBLOCK) {
      if (mode == NORMAL || mode == KICK) {
        game.sound(Game.SND_STOMP);
        mode = FLAT;
        setSprite(Spr.MRICEBLOCK_FLAT_LEFT, Spr.MRICEBLOCK_FLAT_RIGHT);
        vx = 0;
        startTimer(4000);
      } else if (mode == FLAT) {
        game.sound(Game.SND_KICK);
        if (p.x < x + w / 2) { vx = 5; dir = RIGHT; } else { vx = -5; dir = LEFT; }
        mode = KICK;
        p.kickT = game.now + Player.KICKING_TIME;
        setSprite(Spr.MRICEBLOCK_FLAT_LEFT, Spr.MRICEBLOCK_FLAT_RIGHT);
      }
      p.jumpOfBadguy(this);
      game.scoreMultiplier++;
      if (++squishcount >= 10) killMe(50);
    } else if (kind == FISH) {
      if (getVY() >= 0) return;
      p.jumpOfBadguy(this);
      game.addScore(x - game.scrollX, y, 25 * game.scoreMultiplier);
      game.scoreMultiplier++;
      removable = true;
    } else if (kind == BOUNCINGSNOWBALL) {
      squishMe(p); setSprite(Spr.BOUNCINGSNOWBALL_SQUISHED, Spr.BOUNCINGSNOWBALL_SQUISHED);
    } else if (kind == FLYINGSNOWBALL) {
      squishMe(p); setSprite(Spr.FLYINGSNOWBALL_SQUISHED, Spr.FLYINGSNOWBALL_SQUISHED);
    } else if (kind == SNOWBALL) {
      squishMe(p); setSprite(Spr.SNOWBALL_SQUISHED_LEFT, Spr.SNOWBALL_SQUISHED_RIGHT);
    }
  }

  void killMe(int score) {
    if (kind == BOMB || kind == STALACTITE || kind == FLAME) return;
    dying = DYING_FALLING;
    if (kind == MRICEBLOCK) {
      setSprite(Spr.MRICEBLOCK_FALLING_LEFT, Spr.MRICEBLOCK_FALLING_RIGHT);
      if (mode == HELD) { mode = NORMAL; game.tux.holding = false; }
    }
    gravity = true;
    game.addScore(x - game.scrollX, y, score * game.scoreMultiplier);
    game.sound(Game.SND_FALL);
    if (game.detail == Game.POTATO) removable = true;   // Potato: gone instead of falling off screen
  }

  void explode(BadGuy b) {
    game.addBadGuy(b.x, b.y, BOMB, false);
    b.removable = true;
  }

  // collision with another bad guy (CO_BADGUY)
  void collideBadguy(BadGuy o) {
    if (kind == MRICEBLOCK && mode == KICK) o.killMe(25);
    else if (kind == MRICEBLOCK && mode == HELD) { o.killMe(25); killMe(0); }
    else if (kind == BOMB && dying == DYING_NOT) {
      if (o.kind == MRBOMB) explode(o); else o.killMe(50);
    } else if (kind == STALACTITE && dying == DYING_NOT) {
      if (o.kind == MRBOMB) explode(o); else o.killMe(50);
    } else {
      if (o.kind == JUMPY || o.kind == FLAME || o.kind == STALACTITE || o.kind == FISH) return;
      if (y + h < o.y + o.h) {
        if (o.dir == LEFT) { dir = RIGHT; setVelocity(Math.abs(vx), 2); }
        else { dir = LEFT; setVelocity(-Math.abs(vx), 2); }
        return;
      } else if (y + h > o.y + o.h) return;
      if (dir == LEFT) { dir = RIGHT; vx = Math.abs(vx); }
      else { dir = LEFT; vx = -Math.abs(vx); }
    }
  }

  // touched by Tux without squishing (CO_PLAYER, COLLISION_NORMAL)
  void collidePlayer(Player p) {
    if (mode == FLAT && dying == DYING_NOT) {
      game.sound(Game.SND_KICK);
      if (p.x < x) { vx = 5; dir = RIGHT; } else { vx = -5; dir = LEFT; }
      mode = KICK;
      p.kickT = game.now + Player.KICKING_TIME;
      setSprite(Spr.MRICEBLOCK_FLAT_LEFT, Spr.MRICEBLOCK_FLAT_RIGHT);
    }
  }
}
