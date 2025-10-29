package World.trap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class SpriteLoader {
    private static final int DEFAULT_MAX_FRAMES = 100;

    private SpriteLoader() { }

    public static BufferedImage[] loadDefault(String classpathDirectory) {
        return loadSequential(classpathDirectory, "frame_", 0, DEFAULT_MAX_FRAMES);
    }

    public static BufferedImage[] loadSequential(String classpathDirectory,
                                                 String prefix,
                                                 int startIndex,
                                                 int maxFrames) {
        if (classpathDirectory == null || classpathDirectory.isBlank()) {
            return new BufferedImage[]{placeholder(32, 32, Color.MAGENTA)};
        }
        List<BufferedImage> frames = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        int max = Math.max(1, maxFrames);
        for (int i = 0; i < max; i++) {
            int idx = startIndex + i;
            String path = classpathDirectory + "/" + prefix + idx + ".png";
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in == null) {
                    break;
                }
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    frames.add(img);
                }
            } catch (IOException ex) {
                break;
            }
        }
        if (frames.isEmpty()) {
            frames.add(placeholder(32, 32, hashColour(classpathDirectory)));
        }
        return frames.toArray(new BufferedImage[0]);
    }

    private static BufferedImage placeholder(int w, int h, Color tint) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 180));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(tint.darker());
            g.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static Color hashColour(String input) {
        int hash = input.hashCode();
        int r = 64 + Math.abs(hash % 128);
        int g = 64 + Math.abs((hash / 31) % 128);
        int b = 64 + Math.abs((hash / 67) % 128);
        return new Color(r, g, b);
    }
}
