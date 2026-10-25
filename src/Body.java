// Shared moving-object state: 0.1.3's base_type + Physic, and the swept tile collision
// (collision_swept_object_map) that Tux, bad guys, power-ups and bullets all use.
class Body {
  float x, y, w = 32, h = 32;     // base
  float oldX, oldY;               // old_base (position only; sizes follow base)
  float vx, vy, ax, ay;           // physic, vy/ay stored inverted like 0.1.3 (positive = down)
  boolean gravity = true;

  final float getVY() { return -vy; }
  final void setVY(float v) { vy = -v; }
  final void setVelocity(float nvx, float nvy) { vx = nvx; vy = -nvy; }
  final void setAcceleration(float nax, float nay) { ax = nax; ay = -nay; }
  final void resetPhysics() { vx = vy = ax = ay = 0; gravity = true; }

  final void applyPhysics(float fr, float levelGravity) {
    float grav = gravity ? levelGravity / 100f : 0;
    x += vx * fr + ax * fr * fr;
    y += vy * fr + (ay + grav) * fr * fr;
    vx += ax * fr;
    vy += (ay + grav) * fr;
  }

  final void syncOld() { oldX = x; oldY = y; }

  // rectcollision
  final boolean overlaps(Body o) {
    return x >= o.x - w + 1 && x <= o.x + o.w - 1 && y >= o.y - h + 1 && y <= o.y + o.h - 1;
  }

  // collision_swept_object_map(&old_base, &base)
  final void sweptCollision(Level level) {
    float ox = oldX, oy = oldY;
    float lpath, xd, yd, temp, xt, yt;
    int mode;
    if (ox == x && oy == y) return;
    if (ox == x) {
      lpath = y - oy;
      if (lpath < 0) { yd = -1; lpath = -lpath; } else yd = 1;
      mode = 1; xd = 0;
    } else if (oy == y) {
      lpath = x - ox;
      if (lpath < 0) { xd = -1; lpath = -lpath; } else xd = 1;
      mode = 2; yd = 0;
    } else {
      lpath = x - ox;
      if (lpath < 0) lpath = -lpath;
      if (y - oy > lpath || oy - y > lpath) lpath = y - oy;
      if (lpath < 0) lpath = -lpath;
      mode = 3;
      xd = (x - ox) / lpath;
      yd = (y - oy) / lpath;
    }
    int steps = (int) (lpath / 16f);
    float origX = ox, origY = oy;
    ox += xd; oy += yd;
    for (float i = 0; i <= lpath; ox += xd, oy += yd, ++i) {
      if (steps > 0) { oy += yd * 16f; ox += xd * 16f; steps--; }
      if (level.hitsMap(ox, oy, w, h)) {
        if (mode == 1) {
          y = oy - yd;
          while (level.hitsMap(x, y, w, h)) y -= yd;
        } else if (mode == 2) {
          x = ox - xd;
          while (level.hitsMap(x, y, w, h)) x -= xd;
        } else {
          xt = x; yt = y;
          x = ox - xd; y = oy - yd;
          while (level.hitsMap(x, y, w, h)) { x -= xd; y -= yd; }
          temp = x; x = xt;
          if (!level.hitsMap(x, y, w, h)) break;
          x = temp; temp = y; y = yt;
          if (!level.hitsMap(x, y, w, h)) break;
          y = temp;
          while (!level.hitsMap(x, y, w, h)) y += yd;
          y -= yd;
        }
        break;
      }
    }
    if ((xd > 0 && x < origX) || (xd < 0 && x > origX)) x = origX;
    if ((yd > 0 && y < origY) || (yd < 0 && y > origY)) y = origY;
    oldX = x; oldY = y;
  }
}
