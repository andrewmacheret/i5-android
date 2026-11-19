import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the launcher icon as a PNG for the README, using the same shapes as
 * res/drawable/ic_launcher_foreground.xml.
 *
 *   java scripts/Icon.java docs/app-icon.png
 */
public class Icon {
    public static void main(String[] args) throws Exception {
        int size = 512;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0x0F2338));
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.45, size * 0.45));

        // Adaptive icons show the middle 72 of the 108 unit canvas
        double s = size / 72.0;
        g.translate(-18 * s, -18 * s);
        g.scale(s, s);
        g.setColor(new Color(0x34A853));
        g.fill(path(42, 30, 56, 48, 47, 48, 47, 78, 37, 78, 37, 48, 28, 48));
        g.setColor(new Color(0x4C8DF6));
        g.fill(path(66, 78, 80, 60, 71, 60, 71, 30, 61, 30, 61, 60, 52, 60));
        g.dispose();
        ImageIO.write(img, "png", new File(args[0]));
    }

    private static Path2D path(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        return p;
    }
}
