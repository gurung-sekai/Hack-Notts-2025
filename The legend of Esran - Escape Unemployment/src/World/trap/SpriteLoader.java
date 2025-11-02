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

    public static BufferedImage[] loadDefault(String classpathLocation) {
        if (classpathLocation == null || classpathLocation.isBlank()) {
            return new BufferedImage[]{placeholder(32, 32, Color.MAGENTA)};
        }
        if (isSpriteSheetPath(classpathLocation)) {
            BufferedImage[] frames = loadSpriteSheet(classpathLocation);
            if (frames.length > 0) {
                return frames;
            }
        }
        return loadSequential(classpathLocation, "frame_", 0, DEFAULT_MAX_FRAMES);
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

    private static boolean isSpriteSheetPath(String input) {
        return input != null && input.toLowerCase().endsWith(".png");
    }

    private static BufferedImage[] loadSpriteSheet(String pngPath) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(pngPath)) {
            if (in == null) {
                return new BufferedImage[0];
            }
            BufferedImage sheet = ImageIO.read(in);
            if (sheet == null) {
                return new BufferedImage[0];
            }
            SheetMetadata meta = readAsepriteMetadata(loader, deriveAsepritePath(pngPath));
            int frameWidth = meta != null ? meta.frameWidth() : sheet.getHeight();
            int frameHeight = meta != null ? meta.frameHeight() : sheet.getHeight();
            if (frameWidth <= 0 || frameHeight <= 0) {
                return new BufferedImage[]{sheet};
            }
            int expectedFrames = meta != null ? Math.max(1, meta.frameCount()) : Math.max(1, sheet.getWidth() / frameWidth);
            int columns = Math.max(1, sheet.getWidth() / frameWidth);
            int rows = Math.max(1, sheet.getHeight() / frameHeight);
            List<BufferedImage> frames = new ArrayList<>(expectedFrames);
            outer:
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    int x = col * frameWidth;
                    int y = row * frameHeight;
                    if (x + frameWidth > sheet.getWidth() || y + frameHeight > sheet.getHeight()) {
                        break;
                    }
                    frames.add(sheet.getSubimage(x, y, frameWidth, frameHeight));
                    if (frames.size() >= expectedFrames) {
                        break outer;
                    }
                }
            }
            if (frames.isEmpty()) {
                frames.add(sheet);
            }
            return frames.toArray(new BufferedImage[0]);
        } catch (IOException ex) {
            return new BufferedImage[0];
        }
    }

    private static String deriveAsepritePath(String pngPath) {
        if (pngPath == null || pngPath.isBlank()) {
            return null;
        }
        String normalised = pngPath.replace("\\", "/");
        if (!normalised.contains("/PNGs/")) {
            return null;
        }
        String ase = normalised.replace("/PNGs/", "/Aseprite/");
        if (ase.toLowerCase().endsWith(".png")) {
            ase = ase.substring(0, ase.length() - 4) + ".aseprite";
        }
        return ase;
    }

    private static SheetMetadata readAsepriteMetadata(ClassLoader loader, String asepritePath) {
        if (loader == null || asepritePath == null || asepritePath.isBlank()) {
            return null;
        }
        try (InputStream in = loader.getResourceAsStream(asepritePath)) {
            if (in == null) {
                return null;
            }
            // https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md
            readLittleEndianInt(in); // file size (unused)
            int magic = readLittleEndianShort(in);
            if (magic != 0xA5E0) {
                return null;
            }
            int frames = readLittleEndianShort(in);
            int width = readLittleEndianShort(in);
            int height = readLittleEndianShort(in);
            if (frames <= 0 || width <= 0 || height <= 0) {
                return null;
            }
            return new SheetMetadata(frames, width, height);
        } catch (IOException ex) {
            return null;
        }
    }

    private static int readLittleEndianShort(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) {
            throw new IOException("Unexpected end of stream");
        }
        return (b1 << 8) | b0;
    }

    private static void readLittleEndianInt(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw new IOException("Unexpected end of stream");
        }
        // ignore combined value, just advance the stream
    }

    private record SheetMetadata(int frameCount, int frameWidth, int frameHeight) {
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
