package javax.microedition.lcdui;
public abstract class Canvas extends Displayable {
  public static final int UP = 1, DOWN = 6, LEFT = 2, RIGHT = 5, FIRE = 8;
  public static final int KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51, KEY_NUM4 = 52,
      KEY_NUM5 = 53, KEY_NUM6 = 54, KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57, KEY_STAR = 42, KEY_POUND = 35;
  protected Canvas() {}
  public void setFullScreenMode(boolean b) {}
  public boolean isDoubleBuffered() { return true; }
  public int getGameAction(int code) {
    switch (code) { case -1: return UP; case -2: return DOWN; case -3: return LEFT; case -4: return RIGHT; case -5: return FIRE; }
    return 0;
  }
  public void keyPressed(int code) {}
  public void keyReleased(int code) {}
  public boolean hasPointerEvents() { return true; }
  public void pointerPressed(int x, int y) {} public void pointerDragged(int x, int y) {} public void pointerReleased(int x, int y) {}
  public void hideNotify() {} public void showNotify() {}
  public void repaint() {}
  public void serviceRepaints() {}
  protected void paint(Graphics g) {}
}
