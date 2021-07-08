package ibb.api.image.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class ImageUtil {
    
    public static BufferedImage addWatermark(BufferedImage orig, String watermark) {
        Graphics2D g = (Graphics2D) orig.getGraphics();
        RenderingHints hints = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHints(hints);
        FontRenderContext fontRendContext = ((Graphics2D) g).getFontRenderContext();
        Font font = new Font("Sans-Serif", Font.BOLD, (orig.getHeight() / 32) > 12 ? orig.getHeight() / 32 : 12);
        g.setStroke(new BasicStroke(4));
        TextLayout text = new TextLayout(watermark, font, fontRendContext);

        Shape shape = text.getOutline(null);
        Rectangle rect = shape.getBounds();

        AffineTransform affineTransform = new AffineTransform();
        affineTransform = g.getTransform();
        affineTransform.translate((orig.getWidth() - rect.width - rect.width / 10), orig.getHeight() - 10);
        g.transform(affineTransform);
        g.setColor(Color.black);
        g.setClip(shape);
        g.setColor(Color.white);
        g.fill(shape);
        
        return orig;
    }

    public static BufferedImage scaleImage(BufferedImage origImage, int height) {

        int type = (origImage.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        /*
         * The uploaded image has to be passed to drawImage at the first
         * iteration only. For the remaining iterations, the newly scaled image
         * has to be passed instead. If you find this assignment a bit confusing
         * you can always assign it a null and then add an if statement similar
         * to the scratchImage below.
         */
        BufferedImage scaledImage = (BufferedImage) origImage;
        BufferedImage scratchImage = null;
        Graphics2D g2 = null;

        int w = origImage.getWidth();
        int h = origImage.getHeight();
        int prevW = scaledImage.getWidth();
        int prevH = scaledImage.getHeight();

        // the default with of the thumbnail is set to 200
        int targetHeight = height;
        double ratio = (double) height / h;
        int targetWidth = (int) (w * ratio);

        Object hint = RenderingHints.VALUE_INTERPOLATION_BILINEAR;

        do {
            if (w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            }
            if (h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            }
            if (scratchImage == null) {
                // Use a single scratch buffer for all iterations
                // and then copy to the final, correctly sized image
                // before returning
                scratchImage = new BufferedImage(w, h, type);
                g2 = scratchImage.createGraphics();
            }
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2.drawImage(scaledImage, 0, 0, w, h, 0, 0, prevW, prevH, null);
            prevW = w;
            prevH = h;
            scaledImage = scratchImage;
        } while (w != targetWidth || h != targetHeight);
        if (g2 != null) {
            g2.dispose();
        }
        // If we used a scratch buffer that is larger than our
        // target size, create an image of the right size and copy
        // the results into it
        if (targetWidth != scaledImage.getWidth()
                || targetHeight != scaledImage.getHeight()) {
            scratchImage = new BufferedImage(targetWidth, targetHeight, type);
            g2 = scratchImage.createGraphics();
            g2.drawImage(scaledImage, 0, 0, null);
            g2.dispose();
//            scaledImage = addWatermark(scratchImage, "foo");
            scaledImage = scratchImage;
        }

        return scaledImage;
    }
}
