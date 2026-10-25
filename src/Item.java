// Power-ups coming out of bonus boxes: a port of 0.1.3's Upgrade (special.cpp).
import javax.microedition.lcdui.Graphics;

final class Item extends Body {
  static final int GROWUP = 0, ICEFLOWER = 1, HERRING = 2, ONEUP = 3;
  static final int LEFT = 0, RIGHT = 1;
  static final float GROWUP_SPEED = 1.0f;
  final Game game;
  int kind, dir;
  boolean removable;

  Item(Game g, float px, float py, int d, int k) {
    game = g; kind = k; dir = d;
    x = px; y = py; w = 32; h = 0;
    resetPhysics();
    gravity = false;
    if (kind == ONEUP || kind == HERRING) { setVelocity(dir == LEFT ? -1 : 1, 4); gravity = true; h = 32; }
    else if (kind == GROWUP) setVelocity(dir == LEFT ? -GROWUP_SPEED : GROWUP_SPEED, 0);
    syncOld();
  }

  void action(float fr) {
    Level level = game.level;
    if (kind == ICEFLOWER || kind == GROWUP) {
      if (h < 32) {                          // rise out of the box
        h += 0.7f * fr;
        if (h > 32) h = 32;
        return;
      }
    }
    if (x < game.scrollX - 256 || y > 640) { removable = true; return; }
    applyPhysics(fr, level.gravity);
    boolean walker = kind == GROWUP || kind == HERRING;
    if (walker) sweptCollision(level);
    if (walker) {
      if (getVY() != 0) {
        if (level.solid(x, y + h)) {
          y = (int) (y / 32) * 32;
          syncOld();
          if (kind == GROWUP) { gravity = false; setVelocity(dir == LEFT ? -GROWUP_SPEED : GROWUP_SPEED, 0); }
          else setVelocity(dir == LEFT ? -2 : 2, 3);
        }
      } else if ((vx < 0 && !level.solid(x + w, y + h)) || (vx > 0 && !level.solid(x, y + h))) gravity = true;
      if ((vx < 0 && level.solid(x, (int) y + h / 2)) || (vx > 0 && level.solid(x + w, (int) y + h / 2))) {
        setVelocity(-vx, getVY());
        dir = dir == LEFT ? RIGHT : LEFT;
      }
    }
  }

  int sprite() {
    switch (kind) {
      case GROWUP: return Spr.EGG;
      case ICEFLOWER: return Spr.ICEFLOWER;
      case HERRING: return Spr.STAR;
      default: return Spr.ONEUP;
    }
  }

  void draw(Graphics g) {
    int sx = (int) (x - game.scrollX) >> 1;
    if (h < 32) {
      // rising: only the part above the box shows
      int top = ((int) y + game.viewTop) >> 1;
      int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
      int vis = (int) h >> 1;
      g.clipRect(sx - 8, top + 16 - vis, 32, vis);
      Res.drawSprite(g, sprite(), sx, top + 16 - vis, game.now);
      g.setClip(cx, cy, cw, ch);
    } else Res.drawSprite(g, sprite(), sx, ((int) y + game.viewTop) >> 1, game.now);
  }

  void bump() {
    if (kind != GROWUP) return;
    game.sound(Game.SND_BUMP_UPGRADE);
    setVelocity(-vx, 3);
    dir = dir == LEFT ? RIGHT : LEFT;
    gravity = true;
  }

  void collect(Player p) {
    if (kind == GROWUP) { game.sound(Game.SND_EXCELLENT); p.grow(); }
    else if (kind == ICEFLOWER) { game.sound(Game.SND_COFFEE); p.grow(); p.gotCoffee = true; }
    else if (kind == HERRING) {
      game.sound(Game.SND_HERRING);
      p.invincibleT = game.now + Player.TUX_INVINCIBLE_TIME;
      game.music.play(Music.HERRING);
    } else if (game.lives < Game.MAX_LIVES) { game.lives++; game.sound(Game.SND_LIFEUP); }
    removable = true;
  }
}
