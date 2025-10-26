package util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
                          int aiIterations) {
        public static final Options DEFAULT = new Options(72, 32, 2, 160, 1, 12);

        public Options {
            if (backgroundTolerance < 0) throw new IllegalArgumentException("backgroundTolerance must be >= 0");
            if (alphaThreshold < 0 || alphaThreshold > 255) throw new IllegalArgumentException("alphaThreshold 0-255");
            if (padding < 0) throw new IllegalArgumentException("padding must be >= 0");
            if (minFrameArea < 1) throw new IllegalArgumentException("minFrameArea must be >= 1");
            if (joinGap < 0) throw new IllegalArgumentException("joinGap must be >= 0");
            if (aiIterations < 1) throw new IllegalArgumentException("aiIterations must be >= 1");
        }

        public Options withPadding(int padding) { return new Options(backgroundTolerance, alphaThreshold, padding, minFrameArea, joinGap, aiIterations); }
        public Options withTolerance(int tolerance) { return new Options(tolerance, alphaThreshold, padding, minFrameArea, joinGap, aiIterations); }
        public Options withAlphaThreshold(int threshold) { return new Options(backgroundTolerance, threshold, padding, minFrameArea, joinGap, aiIterations); }
        public Options withMinFrameArea(int area) { return new Options(backgroundTolerance, alphaThreshold, padding, area, joinGap, aiIterations); }
        public Options withJoinGap(int gap) { return new Options(backgroundTolerance, alphaThreshold, padding, minFrameArea, gap, aiIterations); }
        public Options withAiIterations(int iterations) { return new Options(backgroundTolerance, alphaThreshold, padding, minFrameArea, joinGap, iterations); }
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

        int[] cleaned = clean.pixels();
        boolean[] visited = new boolean[cleaned.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<FrameBounds> bounds = new ArrayList<>();

        for (int idx = 0; idx < cleaned.length; idx++) {
            if (visited[idx]) {
                continue;
            }
            visited[idx] = true;
            if ((cleaned[idx] >>> 24) == 0) {
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

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nx = cx + dx;
                        int ny = cy + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                            continue;
                        }
                        int nIdx = ny * width + nx;
                        if (visited[nIdx]) {
                            continue;
                        }
                        visited[nIdx] = true;
                        if ((cleaned[nIdx] >>> 24) != 0) {
                            queue.add(nIdx);
                        }
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                continue;
            }

            FrameBounds candidate = new FrameBounds(minX, minY, maxX, maxY, pixelCount);
            if (candidate.area() < opts.minFrameArea() && pixelCount < opts.minFrameArea()) {
                continue;
            }
            bounds.add(candidate);
        }

        if (bounds.isEmpty()) {
            BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            copy.setRGB(0, 0, width, height, cleaned, 0, width);
            return new BufferedImage[]{copy};
        }

        List<FrameBounds> merged = mergeBounds(bounds, opts);
        List<FrameBounds> refined = refineWithAi(merged, clean, opts);
        refined.sort((a, b) -> {
            int cmp = Integer.compare(a.minY(), b.minY());
            if (cmp == 0) {
                cmp = Integer.compare(a.minX(), b.minX());
            }
            return cmp;
        });

        BufferedImage[] arr = new BufferedImage[refined.size()];
        for (int i = 0; i < refined.size(); i++) {
            FrameBounds boundsWithPadding = refined.get(i).expand(opts.padding(), width, height);
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
            return new CleanSheet(width, height, new int[0], new int[0], new int[0]);
        }

        int[] src = sheet.getRGB(0, 0, width, height, null, 0, width);
        int background = estimateBackground(src, width, height);
        int tolSq = opts.backgroundTolerance() * opts.backgroundTolerance();

        int[] cleaned = new int[src.length];
        int[] colCounts = new int[width];
        int[] rowCounts = new int[height];

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
                rowCounts[y]++;
            }
        }

        return new CleanSheet(width, height, cleaned, colCounts, rowCounts);
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

    private record CleanSheet(int width, int height, int[] pixels, int[] colCounts, int[] rowCounts) {}

    private record Segment(int start, int end) {
        int size() { return Math.max(0, end - start); }
    }

    /**
     * Refine raw connected components with an unsupervised clustering pass.  We treat each column/row density as a
     * feature vector and run a lightweight k-means (k=2) to distinguish sprite pixels from separator gaps.  This keeps
     * the slicer dependency-free while still giving it "AI" behaviour that can recognise sprite grids without explicit
     * metadata.
     */
    private static List<FrameBounds> refineWithAi(List<FrameBounds> baseFrames, CleanSheet clean, Options opts) {
        if (baseFrames.isEmpty()) {
            return baseFrames;
        }
        List<FrameBounds> refined = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FrameBounds frame : baseFrames) {
            List<FrameBounds> splits = splitFrameWithAi(frame, clean, opts);
            if (splits.isEmpty()) {
                register(refined, seen, frame);
            } else {
                for (FrameBounds split : splits) {
                    register(refined, seen, split);
                }
            }
            if (countHigh > 0) {
                centroidHigh = sumHigh / countHigh;
            }
        }

        double separation = Math.abs(centroidHigh - centroidLow);
        if (separation < 1e-3) {
            return null;
        }
        return refined;
    }

    private static void register(List<FrameBounds> out, Set<String> seen, FrameBounds frame) {
        String key = frame.minX() + ":" + frame.minY() + ":" + frame.maxX() + ":" + frame.maxY();
        if (seen.add(key)) {
            out.add(frame);
        }
    }

    private static List<FrameBounds> splitFrameWithAi(FrameBounds frame, CleanSheet clean, Options opts) {
        int minX = frame.minX();
        int minY = frame.minY();
        int maxX = frame.maxX();
        int maxY = frame.maxY();

        int width = clean.width();
        int[] pixels = clean.pixels();

        int cellW = Math.max(1, maxX - minX + 1);
        int cellH = Math.max(1, maxY - minY + 1);

        double[] columnDensity = new double[cellW];
        double[] rowDensity = new double[cellH];
        int capacity = cellW * cellH;
        int[] px = new int[capacity];
        int[] py = new int[capacity];

        int pixelCount = 0;
        for (int y = minY; y <= maxY; y++) {
            int rowIndex = y * width;
            for (int x = minX; x <= maxX; x++) {
                int argb = pixels[rowIndex + x];
                if ((argb >>> 24) == 0) {
                    continue;
                }
                columnDensity[x - minX]++;
                rowDensity[y - minY]++;
                if (pixelCount < capacity) {
                    px[pixelCount] = x;
                    py[pixelCount] = y;
                }
                pixelCount++;
            }
        }

        if (pixelCount <= opts.minFrameArea() && frame.area() <= opts.minFrameArea()) {
            return List.of();
        }

        normalise(columnDensity, cellH);
        normalise(rowDensity, cellW);

        List<Segment> vertical = segmentAxis(columnDensity, minX, opts);
        List<Segment> horizontal = segmentAxis(rowDensity, minY, opts);

        if (vertical.size() <= 1 && horizontal.size() <= 1) {
            return List.of();
        }

        List<FrameBounds> splits = new ArrayList<>();
        for (Segment vx : vertical) {
            for (Segment hy : horizontal) {
                FrameBounds candidate = extractBounds(clean, vx, hy, opts);
                if (candidate != null) {
                    splits.add(candidate);
                }
            }
        }

        if (!splits.isEmpty()) {
            return mergeBounds(splits, opts.withJoinGap(0));
        }

        return clusterWithKMeans(frame, px, py, pixelCount, opts);
    }

    private static void normalise(double[] densities, int divisor) {
        double norm = divisor <= 0 ? 1.0 : divisor;
        for (int i = 0; i < densities.length; i++) {
            densities[i] = densities[i] / norm;
        }
    }

    private static List<Segment> segmentAxis(double[] densities, int offset, Options opts) {
        if (densities.length == 0) {
            return List.of();
        }
        boolean[] gapMask = classifyGaps(densities, opts);
        if (gapMask == null) {
            return List.of(new Segment(offset, offset + densities.length));
        }

        List<Segment> segments = new ArrayList<>();
        int minGap = Math.max(2, densities.length / 24);
        int start = offset;
        for (int idx = 0; idx < densities.length; ) {
            if (!gapMask[idx]) {
                idx++;
                continue;
            }
            int runStart = idx;
            while (idx < densities.length && gapMask[idx]) {
                idx++;
            }
            int runEnd = idx;
            if (runEnd - runStart >= minGap) {
                int segmentEnd = offset + runStart;
                if (segmentEnd > start) {
                    segments.add(new Segment(start, segmentEnd));
                }
                start = offset + runEnd;
            }
        }
        int finalEnd = offset + densities.length;
        if (finalEnd > start) {
            segments.add(new Segment(start, finalEnd));
        }

        if (segments.isEmpty()) {
            return List.of(new Segment(offset, offset + densities.length));
        }

        return segments;
    }

    private static boolean[] classifyGaps(double[] densities, Options opts) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : densities) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (!Double.isFinite(min) || !Double.isFinite(max) || Math.abs(max - min) < 1e-4) {
            return null;
        }

        double centroidLow = min;
        double centroidHigh = max;
        int iterations = Math.max(1, opts.aiIterations());
        boolean[] assignment = new boolean[densities.length];

        for (int iter = 0; iter < iterations; iter++) {
            double sumLow = 0;
            double sumHigh = 0;
            int countLow = 0;
            int countHigh = 0;

            for (int i = 0; i < densities.length; i++) {
                double value = densities[i];
                double distLow = Math.abs(value - centroidLow);
                double distHigh = Math.abs(value - centroidHigh);
                if (distLow <= distHigh) {
                    assignment[i] = true;
                    sumLow += value;
                    countLow++;
                } else {
                    assignment[i] = false;
                    sumHigh += value;
                    countHigh++;
                }
            }

            if (countLow > 0) {
                centroidLow = sumLow / countLow;
            }
            if (countHigh > 0) {
                centroidHigh = sumHigh / countHigh;
            }
        }

        double separation = Math.abs(centroidHigh - centroidLow);
        if (separation < 1e-3) {
            return null;
        }

        boolean gapIsLow = centroidLow < centroidHigh;
        boolean[] gapMask = new boolean[densities.length];
        for (int i = 0; i < densities.length; i++) {
            boolean assignedLow = assignment[i];
            gapMask[i] = gapIsLow ? assignedLow : !assignedLow;
        }
        return gapMask;
    }

    private static FrameBounds extractBounds(CleanSheet clean, Segment vx, Segment hy, Options opts) {
        int sheetWidth = clean.width();
        int[] pixels = clean.pixels();

        int minX = vx.end;
        int minY = hy.end;
        int maxX = vx.start - 1;
        int maxY = hy.start - 1;
        int pixelCount = 0;

        for (int y = hy.start; y < hy.end; y++) {
            int rowIndex = y * sheetWidth;
            for (int x = vx.start; x < vx.end; x++) {
                int argb = pixels[rowIndex + x];
                if ((argb >>> 24) == 0) {
                    continue;
                }
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                pixelCount++;
            }
        }

        if (pixelCount == 0) {
            return null;
        }

        FrameBounds bounds = new FrameBounds(minX, minY, maxX, maxY, pixelCount);
        if (bounds.area() < opts.minFrameArea() && pixelCount < opts.minFrameArea()) {
            return null;
        }
        return bounds;
    }

    private static List<FrameBounds> clusterWithKMeans(FrameBounds frame,
                                                       int[] pxRaw,
                                                       int[] pyRaw,
                                                       int pixelCount,
                                                       Options opts) {
        if (pixelCount <= 0) {
            return List.of();
        }
        int[] px = Arrays.copyOf(pxRaw, pixelCount);
        int[] py = Arrays.copyOf(pyRaw, pixelCount);
        int minArea = Math.max(1, opts.minFrameArea());
        if (pixelCount < minArea * 2 && frame.area() < minArea * 2) {
            return List.of();
        }

        int approx = Math.max(2, (int) Math.round(Math.sqrt(Math.max(1.0, pixelCount / (double) minArea))));
        int maxClusters = Math.min(Math.min(6, approx), pixelCount);
        if (maxClusters < 2) {
            return List.of();
        }

        long seed = (((long) frame.minX()) << 32) ^ (frame.minY() & 0xFFFFFFFFL) ^ (long) pixelCount;
        int iterations = Math.max(12, opts.aiIterations() * 4);
        ClusterSolution base = kMeans(px, py, pixelCount, 1, seed, iterations);
        if (base == null) {
            return List.of();
        }

        ClusterSolution best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int k = 2; k <= maxClusters; k++) {
            ClusterSolution candidate = kMeans(px, py, pixelCount, k, seed + 0x9E3779B97F4A7C15L * k, iterations);
            if (candidate == null) {
                continue;
            }
            if (candidate.score() > bestScore) {
                bestScore = candidate.score();
                best = candidate;
            }
        }

        if (best == null) {
            return List.of();
        }

        double ratio = base.totalSse() <= 1e-6 ? 0.0 : best.totalSse() / base.totalSse();
        if (ratio > 0.75 || best.score() < 1.2) {
            return List.of();
        }

        int clusters = best.k();
        int[] assignments = best.assignments();
        int[] counts = best.counts();
        int[] minX = new int[clusters];
        int[] minY = new int[clusters];
        int[] maxX = new int[clusters];
        int[] maxY = new int[clusters];
        Arrays.fill(minX, Integer.MAX_VALUE);
        Arrays.fill(minY, Integer.MAX_VALUE);
        Arrays.fill(maxX, Integer.MIN_VALUE);
        Arrays.fill(maxY, Integer.MIN_VALUE);

        for (int i = 0; i < pixelCount; i++) {
            int cluster = assignments[i];
            if (cluster < 0 || cluster >= clusters) {
                continue;
            }
            int x = px[i];
            int y = py[i];
            if (x < minX[cluster]) minX[cluster] = x;
            if (y < minY[cluster]) minY[cluster] = y;
            if (x > maxX[cluster]) maxX[cluster] = x;
            if (y > maxY[cluster]) maxY[cluster] = y;
        }

        List<FrameBounds> result = new ArrayList<>();
        for (int c = 0; c < clusters; c++) {
            if (counts[c] <= 0) {
                continue;
            }
            FrameBounds candidate = new FrameBounds(minX[c], minY[c], maxX[c], maxY[c], counts[c]);
            if (candidate.area() < minArea && counts[c] < minArea) {
                continue;
            }
            result.add(candidate);
        }

        if (result.size() <= 1) {
            return List.of();
        }

        return mergeBounds(result, opts.withJoinGap(0));
    }

    private static ClusterSolution kMeans(int[] px,
                                          int[] py,
                                          int count,
                                          int k,
                                          long seed,
                                          int iterations) {
        if (count <= 0 || k <= 0) {
            return null;
        }
        if (k == 1) {
            double meanX = 0;
            double meanY = 0;
            for (int i = 0; i < count; i++) {
                meanX += px[i];
                meanY += py[i];
            }
            meanX /= count;
            meanY /= count;
            double totalSse = 0;
            for (int i = 0; i < count; i++) {
                double dx = px[i] - meanX;
                double dy = py[i] - meanY;
                totalSse += dx * dx + dy * dy;
            }
            int[] assignments = new int[count];
            Arrays.fill(assignments, 0);
            return new ClusterSolution(1, assignments, new double[]{meanX}, new double[]{meanY}, new int[]{count}, totalSse, 0.0);
        }

        k = Math.min(k, count);
        PseudoRandom random = new PseudoRandom(seed);
        double[] centersX = new double[k];
        double[] centersY = new double[k];
        boolean[] used = new boolean[count];
        for (int c = 0; c < k; c++) {
            int idx = (int) (random.nextDouble() * count);
            int guard = 0;
            while (used[idx] && guard < count) {
                idx = (idx + 1) % count;
                guard++;
            }
            used[idx] = true;
            centersX[c] = px[idx];
            centersY[c] = py[idx];
        }

        int[] assignments = new int[count];
        Arrays.fill(assignments, -1);
        double[] sumX = new double[k];
        double[] sumY = new double[k];
        int[] clusterCounts = new int[k];
        double[] clusterSse = new double[k];

        for (int iter = 0; iter < iterations; iter++) {
            Arrays.fill(sumX, 0);
            Arrays.fill(sumY, 0);
            Arrays.fill(clusterCounts, 0);
            Arrays.fill(clusterSse, 0);
            boolean changed = false;

            for (int i = 0; i < count; i++) {
                double bestDist = Double.POSITIVE_INFINITY;
                int bestCluster = 0;
                for (int c = 0; c < k; c++) {
                    double dx = px[i] - centersX[c];
                    double dy = py[i] - centersY[c];
                    double dist = dx * dx + dy * dy;
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestCluster = c;
                    }
                }
                if (assignments[i] != bestCluster) {
                    assignments[i] = bestCluster;
                    changed = true;
                }
                sumX[bestCluster] += px[i];
                sumY[bestCluster] += py[i];
                clusterCounts[bestCluster]++;
                clusterSse[bestCluster] += bestDist;
            }

            boolean reseeded = false;
            for (int c = 0; c < k; c++) {
                if (clusterCounts[c] == 0) {
                    reseeded = true;
                    int idx = (int) (random.nextDouble() * count);
                    centersX[c] = px[idx];
                    centersY[c] = py[idx];
                    assignments[idx] = c;
                    clusterCounts[c] = 1;
                    sumX[c] = px[idx];
                    sumY[c] = py[idx];
                    clusterSse[c] = 0;
                } else {
                    double newCx = sumX[c] / clusterCounts[c];
                    double newCy = sumY[c] / clusterCounts[c];
                    if (Math.abs(newCx - centersX[c]) > 1e-6 || Math.abs(newCy - centersY[c]) > 1e-6) {
                        centersX[c] = newCx;
                        centersY[c] = newCy;
                        changed = true;
                    }
                }
            }

            if (!changed && !reseeded) {
                break;
            }
        }

        Arrays.fill(clusterCounts, 0);
        Arrays.fill(clusterSse, 0);
        double totalSse = 0;
        for (int i = 0; i < count; i++) {
            int cluster = assignments[i];
            if (cluster < 0 || cluster >= k) {
                return null;
            }
            clusterCounts[cluster]++;
            double dx = px[i] - centersX[cluster];
            double dy = py[i] - centersY[cluster];
            double dist = dx * dx + dy * dy;
            clusterSse[cluster] += dist;
            totalSse += dist;
        }
        for (int c = 0; c < k; c++) {
            if (clusterCounts[c] == 0) {
                return null;
            }
        }

        double meanX = 0;
        double meanY = 0;
        for (int i = 0; i < count; i++) {
            meanX += px[i];
            meanY += py[i];
        }
        meanX /= count;
        meanY /= count;

        double traceB = 0;
        for (int c = 0; c < k; c++) {
            double dx = centersX[c] - meanX;
            double dy = centersY[c] - meanY;
            traceB += clusterCounts[c] * (dx * dx + dy * dy);
        }

        double score;
        if (k == 1 || count == k) {
            score = traceB;
        } else if (totalSse <= 1e-6) {
            score = Double.POSITIVE_INFINITY;
        } else {
            score = (traceB / (k - 1)) / ((totalSse) / (count - k));
        }

        return new ClusterSolution(k, assignments.clone(), centersX, centersY, clusterCounts.clone(), totalSse, score);
    }

    private record ClusterSolution(int k,
                                   int[] assignments,
                                   double[] centersX,
                                   double[] centersY,
                                   int[] counts,
                                   double totalSse,
                                   double score) {}

    private static final class PseudoRandom {
        private long state;

        PseudoRandom(long seed) {
            if (seed == 0) {
                seed = 0x9E3779B97F4A7C15L;
            }
            this.state = seed;
        }

        double nextDouble() {
            state ^= (state >>> 12);
            state ^= (state << 25);
            state ^= (state >>> 27);
            long result = state * 2685821657736338717L;
            return ((result >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
        }
    }

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
