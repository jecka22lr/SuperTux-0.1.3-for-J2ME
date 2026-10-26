// Tux's fireballs: a port of 0.1.3's Bullet (special.cpp).
import javax.microedition.lcdui.Graphics;

final class Bullet extends Body {
  final Game game;
  int life = 3;
  boolean removable;

  Bullet(Game g, float px, float py, float xm, int dir) {
    game = g; w = 4; h = 4;
    if (dir == Player.RIGHT) { x = px + 32; vx = 6 + xm; } else { x = px; vx = -6 + xm; }
    y = py; vy = 0;
    syncOld();
  }

  void action(float fr) {
    Level level = game.level;
    fr *= 0.5f;
    float oy = y;
    x += vx * fr;
    y += vy * fr;            // vy here is base.ym (positive = down)
    sweptCollision(level);
    if (level.solid(x, y + 4) || level.solid(x, y)) {
      y = oy;
      vy = -vy;
      if (vy > 9) vy = 9; else if (vy < -9) vy = -9;
      life--;
    }
    vy += 0.5f * fr;
    if (x < game.scrollX || x > game.scrollX + game.viewW || y > 480
        || level.solid(x + 4, y + 2) || level.solid(x, y + 2) || life <= 0)
      removable = true;
  }

  void draw(Graphics g) {
    Res.drawSprite(g, Spr.BULLET, (int) (x - game.scrollX) >> 1, ((int) y + game.viewTop) >> 1,
        game.detail == Game.POTATO ? 0 : game.now);
  }
}
