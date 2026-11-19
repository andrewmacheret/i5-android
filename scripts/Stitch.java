import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Lays images out in a grid, for README screenshots.
 *
 *   java scripts/Stitch.java out.png <columns> <scale> <crop x,y,w,h or full> in1.png in2.png ...
 */
public class Stitch {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int cols = Integer.parseInt(args[1]);
        double scale = Double.parseDouble(args[2]);
        String crop = args[3];
        int n = args.length - 4;
        BufferedImage[] imgs = new BufferedImage[n];
        for (int i = 0; i < n; i++) {
            BufferedImage img = ImageIO.read(new File(args[i + 4]));
            if (!crop.equals("full")) {
                String[] p = crop.split(",");
                img = img.getSubimage(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            }
            imgs[i] = img;
        }
        int w = (int) (imgs[0].getWidth() * scale);
        int h = (int) (imgs[0].getHeight() * scale);
        int gap = 24;
        int rows = (n + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * w + (cols - 1) * gap, rows * h + (rows - 1) * gap, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < n; i++) {
            g.drawImage(imgs[i], (i % cols) * (w + gap), (i / cols) * (h + gap), w, h, null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out);
    }
}
