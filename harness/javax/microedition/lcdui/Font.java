package javax.microedition.lcdui;
public class Font {
  public static final int FACE_SYSTEM = 0, STYLE_PLAIN = 0, STYLE_BOLD = 1, SIZE_SMALL = 8, SIZE_MEDIUM = 0;
  java.awt.Font f;
  Font(java.awt.Font f) { this.f = f; }
  public static Font getFont(int face, int style, int size) {
    return new Font(new java.awt.Font(java.awt.Font.SANS_SERIF, style == STYLE_BOLD ? java.awt.Font.BOLD : 0, size == SIZE_SMALL ? 12 : 14));
  }
  public static Font getDefaultFont() { return getFont(0, 0, 0); }
  java.awt.FontMetrics fm() { return new java.awt.image.BufferedImage(1,1,1).getGraphics().getFontMetrics(f); }
  public int getHeight() { return fm().getHeight(); }
  public int stringWidth(String s) { return fm().stringWidth(s); }
  public int getBaselinePosition() { return fm().getAscent(); }
}
