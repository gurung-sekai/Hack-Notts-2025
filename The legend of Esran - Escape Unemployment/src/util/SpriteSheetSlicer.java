package util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility that slices irregular sprite sheets into individual frames without persisting binaries to the repo.
 * <p>
 * The slicer analyses a sheet for transparent separators and trims each frame so the animation is tight.  It also
 * treats the dominant border colour as background, making it suitable for sprite sheets that ship with a grey/white
 * matte instead of pre-multiplied alpha.  Results are cached in-memory so the operation only happens once per sheet.
 */
public final class SpriteSheetSlicer {

    /** Immutable configuration for a slicing run. */
    public record Options(int backgroundTolerance,
                          int alphaThreshold,
                          int padding,
                          int minFrameArea,
                          int joinGap,
                          int haloPasses) {
        public static final Options DEFAULT = new Options(64, 24, 2, 120, 1, 2);

        public Options {
            if (backgroundTolerance < 0) throw new IllegalArgumentException("backgroundTolerance must be >= 0");
            if (alphaThreshold < 0 || alphaThreshold > 255) throw new IllegalArgumentException("alphaThreshold 0-255");
            if (padding < 0) throw new IllegalArgumentException("padding must be >= 0");
            if (minFrameArea < 1) throw new IllegalArgumentException("minFrameArea must be >= 1");
            if (joinGap < 0) throw new IllegalArgumentException("joinGap must be >= 0");
            if (haloPasses < 0) throw new IllegalArgumentException("haloPasses must be >= 0");
        }

        public Options withPadding(int padding) { return new Options(backgroundTolerance, alphaThreshold, padding, minFrameArea, joinGap, haloPasses); }
        public Options withTolerance(int tolerance) { return new Options(tolerance, alphaThreshold, padding, minFrameArea, joinGap, haloPasses); }
        public Options withAlphaThreshold(int threshold) { return new Options(backgroundTolerance, threshold, padding, minFrameArea, joinGap, haloPasses); }
        public Options withMinFrameArea(int area) { return new Options(backgroundTolerance, alphaThreshold, padding, area, joinGap, haloPasses); }
        public Options withJoinGap(int gap) { return new Options(backgroundTolerance, alphaThreshold, padding, minFrameArea, gap, haloPasses); }
        public Options withHaloPasses(int passes) { return new Options(backgroundTolerance, alphaThreshold, padding, minFrameArea, joinGap, passes); }
    }

    private record CacheKey(String resourcePath, Options options) {}

    private static final Map<CacheKey, BufferedImage[]> CACHE = new ConcurrentHashMap<>();

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
        CACHE.put(key, frames.clone());
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

        List<FrameBounds> rawFrames = findFrames(clean, opts);
        if (rawFrames.isEmpty()) {
            BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            copy.setRGB(0, 0, width, height, clean.pixels(), 0, width);
            return new BufferedImage[]{copy};
        }

        List<FrameBounds> frames = mergeBounds(rawFrames, opts);
        frames.sort((a, b) -> {
            int cmp = Integer.compare(a.minY(), b.minY());
            if (cmp == 0) {
                cmp = Integer.compare(a.minX(), b.minX());
            }
            return cmp;
        });

        BufferedImage[] arr = new BufferedImage[frames.size()];
        int[] cleaned = clean.pixels();
        for (int i = 0; i < frames.size(); i++) {
            FrameBounds boundsWithPadding = frames.get(i).expand(opts.padding(), width, height);
            int frameW = boundsWithPadding.width();
            int frameH = boundsWithPadding.height();
            BufferedImage frame = new BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB);
            int[] out = new int[frameW * frameH];
            for (int row = 0; row < frameH; row++) {
                int srcPos = (boundsWithPadding.minY() + row) * width + boundsWithPadding.minX();
                System.arraycopy(cleaned, srcPos, out, row * frameW, frameW);
            }
            frame.setRGB(0, 0, frameW, frameH, out, 0, frameW);
            arr[i] = frame;
        }
        return arr;
    }

    private static BufferedImage[] sliceFromAtlas(BufferedImage sheet, int[][] atlas, Options opts) {
        CleanSheet clean = cleanSheet(sheet, opts);
        int width = clean.width();
        int height = clean.height();
        int[] pixels = clean.pixels();
        BufferedImage[] frames = new BufferedImage[atlas.length];
        for (int i = 0; i < atlas.length; i++) {
            int[] rect = atlas[i];
            int x = Math.max(0, Math.min(width - 1, rect[0]));
            int y = Math.max(0, Math.min(height - 1, rect[1]));
            int w = Math.max(1, Math.min(rect[2], width - x));
            int h = Math.max(1, Math.min(rect[3], height - y));
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
            return new CleanSheet(width, height, new int[0]);
        }

        int[] src = sheet.getRGB(0, 0, width, height, null, 0, width);
        int background = estimateBackground(src, width, height);
        boolean[] backgroundMask = floodBackground(src, width, height, background, opts);
        int[] cleaned = Arrays.copyOf(src, src.length);

        for (int i = 0; i < cleaned.length; i++) {
            int argb = cleaned[i];
            int alpha = (argb >>> 24) & 0xFF;
            if (backgroundMask[i] || alpha <= opts.alphaThreshold()) {
                cleaned[i] = 0;
            } else if (alpha < 255) {
                cleaned[i] = argb | 0xFF000000;
            }
        }

        if (opts.haloPasses() > 0) {
            peelHalos(cleaned, backgroundMask, width, height, background, opts);
        }

        return new CleanSheet(width, height, cleaned);
    }

    private static boolean[] floodBackground(int[] src, int width, int height, int backgroundRgb, Options opts) {
        int tolSq = opts.backgroundTolerance() * opts.backgroundTolerance();
        boolean[] mask = new boolean[src.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            enqueueIfBackground(queue, mask, src, width, height, x, 0, backgroundRgb, tolSq, opts.alphaThreshold());
            enqueueIfBackground(queue, mask, src, width, height, x, height - 1, backgroundRgb, tolSq, opts.alphaThreshold());
        }
        for (int y = 0; y < height; y++) {
            enqueueIfBackground(queue, mask, src, width, height, 0, y, backgroundRgb, tolSq, opts.alphaThreshold());
            enqueueIfBackground(queue, mask, src, width, height, width - 1, y, backgroundRgb, tolSq, opts.alphaThreshold());
        }

        while (!queue.isEmpty()) {
            int idx = queue.removeFirst();
            int cx = idx % width;
            int cy = idx / width;
            if (cx > 0) enqueueIfBackground(queue, mask, src, width, height, cx - 1, cy, backgroundRgb, tolSq, opts.alphaThreshold());
            if (cx + 1 < width) enqueueIfBackground(queue, mask, src, width, height, cx + 1, cy, backgroundRgb, tolSq, opts.alphaThreshold());
            if (cy > 0) enqueueIfBackground(queue, mask, src, width, height, cx, cy - 1, backgroundRgb, tolSq, opts.alphaThreshold());
            if (cy + 1 < height) enqueueIfBackground(queue, mask, src, width, height, cx, cy + 1, backgroundRgb, tolSq, opts.alphaThreshold());
        }
        return mask;
    }

    private static void enqueueIfBackground(ArrayDeque<Integer> queue,
                                            boolean[] mask,
                                            int[] src,
                                            int width,
                                            int height,
                                            int x,
                                            int y,
                                            int backgroundRgb,
                                            int tolSq,
                                            int alphaThreshold) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int idx = y * width + x;
        if (mask[idx]) {
            return;
        }
        int argb = src[idx];
        if (isBackgroundPixel(argb, backgroundRgb, tolSq, alphaThreshold)) {
            mask[idx] = true;
            queue.add(idx);
        }
    }

    private static void peelHalos(int[] cleaned,
                                  boolean[] backgroundMask,
                                  int width,
                                  int height,
                                  int backgroundRgb,
                                  Options opts) {
        int tolSq = opts.backgroundTolerance() * opts.backgroundTolerance();
        int passes = Math.max(1, opts.haloPasses());
        for (int pass = 0; pass < passes; pass++) {
            boolean changed = false;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;
                    if (backgroundMask[idx]) {
                        continue;
                    }
                    int argb = cleaned[idx];
                    if ((argb >>> 24) == 0) {
                        backgroundMask[idx] = true;
                        continue;
                    }
                    int rgb = argb & 0x00FFFFFF;
                    if (colourDistanceSq(rgb, backgroundRgb) > tolSq) {
                        continue;
                    }
                    if (touchesStrongColour(cleaned, backgroundMask, width, height, x, y, tolSq, backgroundRgb)) {
                        continue;
                    }
                    cleaned[idx] = 0;
                    backgroundMask[idx] = true;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    private static boolean touchesStrongColour(int[] cleaned,
                                               boolean[] backgroundMask,
                                               int width,
                                               int height,
                                               int x,
                                               int y,
                                               int tolSq,
                                               int backgroundRgb) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int nIdx = ny * width + nx;
                if (backgroundMask[nIdx]) continue;
                int argb = cleaned[nIdx];
                if ((argb >>> 24) == 0) continue;
                int rgb = argb & 0x00FFFFFF;
                if (colourDistanceSq(rgb, backgroundRgb) > tolSq) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isBackgroundPixel(int argb, int backgroundRgb, int tolSq, int alphaThreshold) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha <= alphaThreshold) {
            return true;
        }
        if (alpha == 0) {
            return true;
        }
        int rgb = argb & 0x00FFFFFF;
        int dist = colourDistanceSq(rgb, backgroundRgb);
        if (dist <= tolSq) {
            return true;
        }
        if (isLowSaturation(rgb) && dist <= tolSq * 4L) {
            return true;
        }
        return false;
    }

    private static boolean isLowSaturation(int rgb) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max == 0) {
            return true;
        }
        int sat = (max - min) * 255 / max;
        return sat < 28;
    }

    private static List<FrameBounds> findFrames(CleanSheet clean, Options opts) {
        int width = clean.width();
        int height = clean.height();
        int[] pixels = clean.pixels();
        boolean[] visited = new boolean[pixels.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<FrameBounds> frames = new ArrayList<>();

        for (int idx = 0; idx < pixels.length; idx++) {
            if (visited[idx]) {
                continue;
            }
            visited[idx] = true;
            if ((pixels[idx] >>> 24) == 0) {
                continue;
            }

            queue.clear();
            queue.add(idx);

            int minX = width, minY = height, maxX = -1, maxY = -1;
            int pixelCount = 0;

            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int cx = current % width;
                int cy = current / width;

                if (cx < minX) minX = cx;
                if (cy < minY) minY = cy;
                if (cx > maxX) maxX = cx;
                if (cy > maxY) maxY = cy;
                pixelCount++;

                if (cx > 0) tryVisit(queue, visited, pixels, width, current - 1);
                if (cx + 1 < width) tryVisit(queue, visited, pixels, width, current + 1);
                if (cy > 0) tryVisit(queue, visited, pixels, width, current - width);
                if (cy + 1 < height) tryVisit(queue, visited, pixels, width, current + width);
            }

            if (maxX < minX || maxY < minY) {
                continue;
            }

            FrameBounds candidate = new FrameBounds(minX, minY, maxX, maxY, pixelCount);
            if (candidate.area() < opts.minFrameArea() && pixelCount < opts.minFrameArea()) {
                continue;
            }
            frames.add(candidate);
        }

        return frames;
    }

    private static void tryVisit(ArrayDeque<Integer> queue,
                                 boolean[] visited,
                                 int[] pixels,
                                 int width,
                                 int nextIdx) {
        if (visited[nextIdx]) {
            return;
        }
        visited[nextIdx] = true;
        if ((pixels[nextIdx] >>> 24) != 0) {
            queue.add(nextIdx);
        }
    }

    private record FrameBounds(int minX, int minY, int maxX, int maxY, int pixelCount) {
        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
        int area() { return width() * height(); }

        FrameBounds expand(int padding, int sheetWidth, int sheetHeight) {
            if (padding <= 0) {
                return this;
            }
            int newMinX = Math.max(0, minX - padding);
            int newMinY = Math.max(0, minY - padding);
            int newMaxX = Math.min(sheetWidth - 1, maxX + padding);
            int newMaxY = Math.min(sheetHeight - 1, maxY + padding);
            return new FrameBounds(newMinX, newMinY, newMaxX, newMaxY, pixelCount);
        }

        FrameBounds merge(FrameBounds other) {
            int newMinX = Math.min(this.minX, other.minX);
            int newMinY = Math.min(this.minY, other.minY);
            int newMaxX = Math.max(this.maxX, other.maxX);
            int newMaxY = Math.max(this.maxY, other.maxY);
            return new FrameBounds(newMinX, newMinY, newMaxX, newMaxY, this.pixelCount + other.pixelCount);
        }

        boolean touches(FrameBounds other, int gap) {
            if (gap < 0) {
                gap = 0;
            }
            int dx = 0;
            if (other.minX > this.maxX + 1) {
                dx = other.minX - this.maxX - 1;
            } else if (this.minX > other.maxX + 1) {
                dx = this.minX - other.maxX - 1;
            }
            int dy = 0;
            if (other.minY > this.maxY + 1) {
                dy = other.minY - this.maxY - 1;
            } else if (this.minY > other.maxY + 1) {
                dy = this.minY - other.maxY - 1;
            }
            return dx <= gap && dy <= gap;
        }
    }

    private static List<FrameBounds> mergeBounds(List<FrameBounds> frames, Options opts) {
        if (frames.size() <= 1) {
            return frames;
        }
        int gap = opts.joinGap();
        if (gap <= 0) {
            return frames;
        }

        List<FrameBounds> working = new ArrayList<>(frames);
        boolean merged;
        do {
            merged = false;
            outer:
            for (int i = 0; i < working.size(); i++) {
                FrameBounds a = working.get(i);
                for (int j = i + 1; j < working.size(); j++) {
                    FrameBounds b = working.get(j);
                    if (a.touches(b, gap)) {
                        working.set(i, a.merge(b));
                        working.remove(j);
                        merged = true;
                        break outer;
                    }
                }
            }
        } while (merged);
        return working;
    }

    private record CleanSheet(int width, int height, int[] pixels) {}

    private static int estimateBackground(int[] pixels, int width, int height) {
        java.util.HashMap<Integer, Integer> counts = new java.util.HashMap<>();
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
