package javax.microedition.lcdui;
import java.awt.image.BufferedImage;
public class Image {
  BufferedImage b;
  Image(BufferedImage b) { this.b = b; }
  // tests: a phone with little memory. Page loads (/tp/, /sp/) fail with OutOfMemoryError while the
  // pages Res holds take budgetKB or more; loads counts the page loads
  public static int budgetKB, loads;
  public static Image createImage(String name) throws java.io.IOException {
    if (name.startsWith("/tp/") || name.startsWith("/sp/")) {
      loads++;
      if (budgetKB > 0) {
        int kb = 0;
        try {
          java.lang.reflect.Method m = Class.forName("Res").getDeclaredMethod("pageKB", new Class[0]);
          m.setAccessible(true);
          kb = ((Integer) m.invoke(null, new Object[0])).intValue();
        }
        catch (Exception e) { }
        if (kb >= budgetKB) throw new OutOfMemoryError("test budget");
      }
    }
    java.io.InputStream in = Image.class.getResourceAsStream(name);
    if (in == null) throw new java.io.IOException("missing " + name);
    BufferedImage r = javax.imageio.ImageIO.read(in);
    BufferedImage a = new BufferedImage(r.getWidth(), r.getHeight(), BufferedImage.TYPE_INT_ARGB);
    a.getGraphics().drawImage(r, 0, 0, null);
    return new Image(a);
  }
  public static Image createRGBImage(int[] d, int w, int h, boolean a) { BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); b.setRGB(0, 0, w, h, d, 0, w); return new Image(b); }
  public static Image createImage(int w, int h) { return new Image(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)); }
  public int getWidth() { return b.getWidth(); }
  public int getHeight() { return b.getHeight(); }
  // like FreeJ2ME: the same Graphics every time, keeping its translation and clip
  private Graphics gfx;
  public Graphics getGraphics() { if (gfx == null) gfx = new Graphics(b.createGraphics()); return gfx; }
  public void getRGB(int[] d, int off, int scan, int x, int y, int w, int h) { b.getRGB(x, y, w, h, d, off, scan); }
}
