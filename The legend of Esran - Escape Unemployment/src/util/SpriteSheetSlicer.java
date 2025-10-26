package util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility that slices irregular sprite sheets into individual frames without persisting binaries to the repo.
 * <p>
 * The slicer analyses a sheet for transparent separators and trims each frame so the animation is tight.  It also
 * treats the dominant border colour as background, making it suitable for sprite sheets that ship with a grey/white
 * matte instead of pre-multiplied alpha.  Results are cached in-memory so the operation only happens once per sheet.
 */
public final class SpriteSheetSlicer {

    /** Immutable configuration for a slicing run. */
    public record Options(int backgroundTolerance, int alphaThreshold, int padding, int minFrameArea) {
        public static final Options DEFAULT = new Options(48, 8, 2, 160);

        public Options {
            if (backgroundTolerance < 0) throw new IllegalArgumentException("backgroundTolerance must be >= 0");
            if (alphaThreshold < 0 || alphaThreshold > 255) throw new IllegalArgumentException("alphaThreshold 0-255");
            if (padding < 0) throw new IllegalArgumentException("padding must be >= 0");
            if (minFrameArea < 1) throw new IllegalArgumentException("minFrameArea must be >= 1");
        }

        public Options withPadding(int padding) { return new Options(backgroundTolerance, alphaThreshold, padding, minFrameArea); }
        public Options withTolerance(int tolerance) { return new Options(tolerance, alphaThreshold, padding, minFrameArea); }
        public Options withAlphaThreshold(int threshold) { return new Options(backgroundTolerance, threshold, padding, minFrameArea); }
        public Options withMinFrameArea(int area) { return new Options(backgroundTolerance, alphaThreshold, padding, area); }
    }

    private record CacheKey(String resourcePath, Options options) {}

    private static final Map<CacheKey, BufferedImage[]> CACHE = new HashMap<>();

    private SpriteSheetSlicer() { }

    /** Slice a sprite sheet referenced by a resource path. */
    public static BufferedImage[] slice(String resourcePath, Options options) throws IOException {
        Options opts = options == null ? Options.DEFAULT : options;
        CacheKey key = new CacheKey(Objects.requireNonNull(resourcePath, "resourcePath"), opts);
        BufferedImage[] cached = CACHE.get(key);
        if (cached != null) {
            return cached.clone();
        }
        BufferedImage sheet = ResourceLoader.image(resourcePath);
        int[][] atlas = SpriteSheetMetadata.lookup(resourcePath);
        BufferedImage[] frames = (atlas != null)
                ? sliceFromAtlas(sheet, atlas, opts)
                : autoSlice(sheet, opts);
        CACHE.put(key, frames);
        return frames.clone();
    }

    /** Slice a pre-loaded sheet via automatic detection. */
    public static BufferedImage[] autoSlice(BufferedImage sheet, Options options) {
        Objects.requireNonNull(sheet, "sheet");
        Options opts = options == null ? Options.DEFAULT : options;
        CleanSheet clean = cleanSheet(sheet, opts);
        int width = clean.width();
        int height = clean.height();
        if (width <= 0 || height <= 0) {
            return new BufferedImage[0];
        }

        int[] cleaned = clean.pixels();
        int[] colCounts = clean.colCounts().clone();
        boolean[] blankCol = new boolean[width];
        for (int x = 0; x < width; x++) blankCol[x] = colCounts[x] == 0;

        record FrameCandidate(int left, int top, BufferedImage image) {}

        List<FrameCandidate> frames = new ArrayList<>();
        for (int x = 0; x < width; ) {
            while (x < width && blankCol[x]) x++;
            if (x >= width) break;
            int x2 = x;
            while (x2 < width && !blankCol[x2]) x2++;

            int[] segmentRowCounts = new int[height];
            for (int xx = x; xx < x2; xx++) {
                int base = xx;
                for (int yy = 0; yy < height; yy++) {
                    if ((cleaned[yy * width + base] >>> 24) != 0) {
                        segmentRowCounts[yy]++;
                    }
                }
            }

            for (int y = 0; y < height; ) {
                while (y < height && segmentRowCounts[y] == 0) y++;
                if (y >= height) break;
                int y2 = y;
                while (y2 < height && segmentRowCounts[y2] != 0) y2++;

                // Tighten bounds to actual content
                int minX = x2, maxX = x - 1, minY = y2, maxY = y - 1;
                for (int yy = y; yy < y2; yy++) {
                    int rowIdx = yy * width;
                    for (int xx = x; xx < x2; xx++) {
                        int argb = cleaned[rowIdx + xx];
                        if ((argb >>> 24) != 0) {
                            if (xx < minX) minX = xx;
                            if (xx > maxX) maxX = xx;
                            if (yy < minY) minY = yy;
                            if (yy > maxY) maxY = yy;
                        }
                    }
                }

                if (maxX >= minX && maxY >= minY) {
                    int pad = opts.padding();
                    minX = Math.max(x, minX - pad);
                    minY = Math.max(y, minY - pad);
                    maxX = Math.min(x2 - 1, maxX + pad);
                    maxY = Math.min(y2 - 1, maxY + pad);

                    int frameW = maxX - minX + 1;
                    int frameH = maxY - minY + 1;
                    if (frameW * frameH >= opts.minFrameArea()) {
                        BufferedImage frame = new BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB);
                        int[] out = new int[frameW * frameH];
                        for (int yy = 0; yy < frameH; yy++) {
                            int srcPos = (minY + yy) * width + minX;
                            System.arraycopy(cleaned, srcPos, out, yy * frameW, frameW);
                        }
                        frame.setRGB(0, 0, frameW, frameH, out, 0, frameW);
                        frames.add(new FrameCandidate(minX, minY, frame));
                    }
                }
                y = y2;
            }
            x = x2;
        }

        if (frames.isEmpty()) {
            BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            copy.setRGB(0, 0, width, height, cleaned, 0, width);
            return new BufferedImage[]{copy};
        }

        frames.sort((a, b) -> {
            int cmp = Integer.compare(a.top(), b.top());
            if (cmp == 0) cmp = Integer.compare(a.left(), b.left());
            return cmp;
        });

        BufferedImage[] arr = new BufferedImage[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            arr[i] = frames.get(i).image();
        }
        return arr;
    }

    private static BufferedImage[] sliceFromAtlas(BufferedImage sheet, int[][] atlas, Options opts) {
        CleanSheet clean = cleanSheet(sheet, opts);
        int width = clean.width();
        int[] pixels = clean.pixels();
        BufferedImage[] frames = new BufferedImage[atlas.length];
        for (int i = 0; i < atlas.length; i++) {
            int[] rect = atlas[i];
            int x = Math.max(0, Math.min(width - 1, rect[0]));
            int y = Math.max(0, Math.min(clean.height() - 1, rect[1]));
            int w = Math.max(1, Math.min(rect[2], width - x));
            int h = Math.max(1, Math.min(rect[3], clean.height() - y));
            BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] out = new int[w * h];
            for (int row = 0; row < h; row++) {
                int srcPos = (y + row) * width + x;
                System.arraycopy(pixels, srcPos, out, row * w, w);
            }
            frame.setRGB(0, 0, w, h, out, 0, w);
            frames[i] = frame;
        }
        return frames;
    }

    private static CleanSheet cleanSheet(BufferedImage sheet, Options opts) {
        int width = sheet.getWidth();
        int height = sheet.getHeight();
        if (width <= 0 || height <= 0) {
            return new CleanSheet(width, height, new int[0], new int[0]);
        }

        int[] src = sheet.getRGB(0, 0, width, height, null, 0, width);
        int background = estimateBackground(src, width, height);
        int tolSq = opts.backgroundTolerance() * opts.backgroundTolerance();

        int[] cleaned = new int[src.length];
        int[] colCounts = new int[width];

        for (int y = 0; y < height; y++) {
            int rowIndex = y * width;
            for (int x = 0; x < width; x++) {
                int idx = rowIndex + x;
                int argb = src[idx];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= opts.alphaThreshold()) {
                    cleaned[idx] = 0;
                    continue;
                }
                int rgb = argb & 0x00FFFFFF;
                if (colourDistanceSq(rgb, background) <= tolSq) {
                    cleaned[idx] = 0;
                    continue;
                }
                cleaned[idx] = argb;
                if (((argb >>> 24) & 0xFF) == 0) {
                    cleaned[idx] = argb | 0xFF000000;
                }
                colCounts[x]++;
            }
        }

        return new CleanSheet(width, height, cleaned, colCounts);
    }

    private record CleanSheet(int width, int height, int[] pixels, int[] colCounts) {}

    private static int estimateBackground(int[] pixels, int width, int height) {
        Map<Integer, Integer> counts = new HashMap<>();
        int stepX = Math.max(1, width / 12);
        int stepY = Math.max(1, height / 12);

        for (int x = 0; x < width; x += stepX) {
            sample(pixels, width, height, counts, x, 0);
            sample(pixels, width, height, counts, x, height - 1);
        }
        for (int y = 0; y < height; y += stepY) {
            sample(pixels, width, height, counts, 0, y);
            sample(pixels, width, height, counts, width - 1, y);
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseGet(() -> pixels.length == 0 ? 0 : pixels[0] & 0x00FFFFFF);
    }

    private static void sample(int[] pixels, int width, int height, Map<Integer, Integer> counts, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int argb = pixels[y * width + x];
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 16) return;
        int rgb = argb & 0x00FFFFFF;
        counts.merge(rgb, 1, Integer::sum);
    }

    private static int colourDistanceSq(int rgb, int otherRgb) {
        int r1 = (rgb >>> 16) & 0xFF;
        int g1 = (rgb >>> 8) & 0xFF;
        int b1 = rgb & 0xFF;
        int r2 = (otherRgb >>> 16) & 0xFF;
        int g2 = (otherRgb >>> 8) & 0xFF;
        int b2 = otherRgb & 0xFF;
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return dr * dr + dg * dg + db * db;
    }
}
