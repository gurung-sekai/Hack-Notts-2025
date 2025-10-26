package gfx;

import util.ResourceLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility that slices irregularly spaced sprite sheets by detecting transparent guide columns/rows.
 */
public final class SpriteSheetSlicer {

    private SpriteSheetSlicer() {
    }

    /**
     * Load a sprite sheet from the resource path and slice it into tight frames.
     *
     * @param resourcePath classpath-style resource location
     * @return array of frames ordered left-to-right, top-to-bottom
     */
    public static BufferedImage[] sliceFromResource(String resourcePath) {
        try (InputStream stream = ResourceLoader.open(resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing sprite sheet: " + resourcePath);
            }
            BufferedImage sheet = ImageIO.read(stream);
            if (sheet == null) {
                throw new IOException("Unsupported sprite sheet format: " + resourcePath);
            }
            return slice(sheet);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to slice sprite sheet: " + resourcePath, ex);
        }
    }

    /**
     * Slice the given sheet using fully-transparent columns/rows as separators and trimming extra padding.
     */
    public static BufferedImage[] slice(BufferedImage sheet) {
        if (sheet == null) {
            return new BufferedImage[0];
        }

        int width = sheet.getWidth();
        int height = sheet.getHeight();
        boolean[] solidCols = new boolean[width];
        boolean[] solidRows = new boolean[height];

        // Pre-compute which columns contain any visible pixel.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (opaque(sheet.getRGB(x, y))) {
                    solidCols[x] = true;
                    break;
                }
            }
        }

        List<BufferedImage> frames = new ArrayList<>();
        int x = 0;
        while (x < width) {
            while (x < width && !solidCols[x]) {
                x++;
            }
            if (x >= width) {
                break;
            }
            int startX = x;
            while (x < width && solidCols[x]) {
                x++;
            }
            int endX = x; // exclusive

            // Determine vertical bands for this horizontal slice.
            java.util.Arrays.fill(solidRows, false);
            for (int yy = 0; yy < height; yy++) {
                for (int xx = startX; xx < endX; xx++) {
                    if (opaque(sheet.getRGB(xx, yy))) {
                        solidRows[yy] = true;
                        break;
                    }
                }
            }

            int y = 0;
            while (y < height) {
                while (y < height && !solidRows[y]) {
                    y++;
                }
                if (y >= height) {
                    break;
                }
                int startY = y;
                while (y < height && solidRows[y]) {
                    y++;
                }
                int endY = y; // exclusive

                int minX = endX;
                int maxX = startX - 1;
                int minY = endY;
                int maxY = startY - 1;

                for (int yy = startY; yy < endY; yy++) {
                    for (int xx = startX; xx < endX; xx++) {
                        if (opaque(sheet.getRGB(xx, yy))) {
                            if (xx < minX) {
                                minX = xx;
                            }
                            if (xx > maxX) {
                                maxX = xx;
                            }
                            if (yy < minY) {
                                minY = yy;
                            }
                            if (yy > maxY) {
                                maxY = yy;
                            }
                        }
                    }
                }

                if (maxX >= minX && maxY >= minY) {
                    int frameW = maxX - minX + 1;
                    int frameH = maxY - minY + 1;
                    frames.add(sheet.getSubimage(minX, minY, frameW, frameH));
                }
            }
        }

        if (frames.isEmpty()) {
            frames.add(sheet);
        }

        return frames.toArray(new BufferedImage[0]);
    }

    private static boolean opaque(int argb) {
        return ((argb >>> 24) & 0xFF) != 0;
    }
}
