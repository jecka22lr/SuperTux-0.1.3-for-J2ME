package javax.microedition.lcdui;
import java.awt.Graphics2D;
public class Graphics {
  public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;
  Graphics2D g; Font font = Font.getDefaultFont(); int cx, cy, cw = 10000, ch = 10000, tx, ty;
  public Graphics(Graphics2D g) { this.g = g; }
  public void setColor(int c) { g.setColor(new java.awt.Color(c & 0xffffff)); }
  public void setColor(int r, int gg, int b) { setColor((r << 16) | (gg << 8) | b); }
  public void fillRect(int x, int y, int w, int h) { g.fillRect(x, y, w, h); }
  public void drawRect(int x, int y, int w, int h) { g.drawRect(x, y, w, h); }
  public void drawLine(int a, int b, int c, int d) { g.drawLine(a, b, c, d); }
  public void setClip(int x, int y, int w, int h) { g.setClip(x, y, w, h); cx = x; cy = y; cw = w; ch = h; }
  public void clipRect(int x, int y, int w, int h) { g.clipRect(x, y, w, h); }
  public int getClipX() { return cx; } public int getClipY() { return cy; }
  public int getClipWidth() { return cw; } public int getClipHeight() { return ch; }
  public void setFont(Font f) { font = f; g.setFont(f.f); }
  public Font getFont() { return font; }
  public void translate(int x, int y) { g.translate(x, y); tx += x; ty += y; }
  public int getTranslateX() { return tx; } public int getTranslateY() { return ty; }
  int ax(int x, int w, int a) { return (a & HCENTER) != 0 ? x - w / 2 : (a & RIGHT) != 0 ? x - w : x; }
  int ay(int y, int h, int a) { return (a & VCENTER) != 0 ? y - h / 2 : (a & BOTTOM) != 0 ? y - h : y; }
  public void drawImage(Image i, int x, int y, int a) { g.drawImage(i.b, ax(x, i.getWidth(), a), ay(y, i.getHeight(), a), null); }
  public void drawRegion(Image i, int sx, int sy, int w, int h, int t, int x, int y, int a) {
    if (t == 5 || t == 6) {                                    // TRANS_ROT90 / TRANS_ROT270
      x = ax(x, h, a); y = ay(y, w, a);
      java.awt.geom.AffineTransform old = g.getTransform();
      if (t == 5) { g.translate(x + h, y); g.rotate(Math.PI / 2); }
      else { g.translate(x, y + w); g.rotate(-Math.PI / 2); }
      g.drawImage(i.b, 0, 0, w, h, sx, sy, sx + w, sy + h, null);
      g.setTransform(old);
      return;
    }
    x = ax(x, w, a); y = ay(y, h, a);
    if (t == 2) g.drawImage(i.b, x + w, y, x, y + h, sx, sy, sx + w, sy + h, null);        // TRANS_MIRROR
    else g.drawImage(i.b, x, y, x + w, y + h, sx, sy, sx + w, sy + h, null);
  }
  public void drawString(String s, int x, int y, int a) {
    java.awt.FontMetrics m = g.getFontMetrics(font.f);
    int w = m.stringWidth(s);
    x = ax(x, w, a);
    if ((a & BASELINE) == 0) y = ay(y, m.getHeight(), a) + m.getAscent();
    g.drawString(s, x, y);
  }
  public void drawRGB(int[] d, int off, int scan, int x, int y, int w, int h, boolean alpha) {
    java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    b.setRGB(0, 0, w, h, d, off, scan);
    g.drawImage(b, x, y, null);
  }
  public void drawChar(char c, int x, int y, int a) { drawString(String.valueOf(c), x, y, a); }
}
