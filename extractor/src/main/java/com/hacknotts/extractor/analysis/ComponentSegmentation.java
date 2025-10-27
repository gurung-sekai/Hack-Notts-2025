package com.hacknotts.extractor.analysis;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.loader.SpriteSheet;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class ComponentSegmentation {
    private final ExtractorConfig config;

    public ComponentSegmentation(ExtractorConfig config) {
        this.config = config;
    }

    public List<Component> segment(SpriteSheet sheet, AlphaMetrics metrics) {
        boolean[][] binary = threshold(metrics.alpha(), config.getAlphaThreshold());
        boolean[][] backgroundCleaned = removeEdgeBackgroundIfNecessary(sheet.image(), binary);
        boolean[][] filtered = morphologicalOpen(morphologicalClose(backgroundCleaned));
        return extractComponents(sheet.image(), filtered);
    }

    private boolean[][] threshold(int[][] alpha, int threshold) {
        int height = alpha.length;
        int width = alpha[0].length;
        boolean[][] mask = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                mask[y][x] = alpha[y][x] > threshold;
            }
        }
        return mask;
    }

    private boolean[][] morphologicalClose(boolean[][] mask) {
        return erode(dilate(mask));
    }

    private boolean[][] morphologicalOpen(boolean[][] mask) {
        return dilate(erode(mask));
    }

    private boolean[][] dilate(boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        boolean[][] result = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y][x]) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y + dy;
                        int nx = x + dx;
                        if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                            result[ny][nx] = true;
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean[][] removeEdgeBackgroundIfNecessary(BufferedImage image, boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        double coverage = coverage(mask);
        if (coverage < 0.85) {
            return mask;
        }

        boolean[][] result = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(mask[y], 0, result[y], 0, width);
        }

        double[] background = estimateBackgroundColor(image);
        boolean[][] visited = new boolean[height][width];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            enqueueIfBackground(image, result, visited, queue, background, x, 0);
            enqueueIfBackground(image, result, visited, queue, background, x, height - 1);
        }
        for (int y = 0; y < height; y++) {
            enqueueIfBackground(image, result, visited, queue, background, 0, y);
            enqueueIfBackground(image, result, visited, queue, background, width - 1, y);
        }

        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};

        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            int px = point[0];
            int py = point[1];
            result[py][px] = false;
            for (int dir = 0; dir < dx.length; dir++) {
                int nx = px + dx[dir];
                int ny = py + dy[dir];
                enqueueIfBackground(image, result, visited, queue, background, nx, ny);
            }
        }

        return result;
    }

    private void enqueueIfBackground(BufferedImage image,
                                     boolean[][] mask,
                                     boolean[][] visited,
                                     Queue<int[]> queue,
                                     double[] background,
                                     int x,
                                     int y) {
        int height = mask.length;
        int width = mask[0].length;
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        if (visited[y][x]) {
            return;
        }
        visited[y][x] = true;
        int argb = image.getRGB(x, y);
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha <= config.getAlphaThreshold()) {
            mask[y][x] = false;
            queue.add(new int[]{x, y});
            return;
        }
        if (isBackgroundColor(argb, background)) {
            mask[y][x] = false;
            queue.add(new int[]{x, y});
        }
    }

    private boolean isBackgroundColor(int argb, double[] background) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double dr = r - background[0];
        double dg = g - background[1];
        double db = b - background[2];
        double distance = Math.sqrt(dr * dr + dg * dg + db * db);
        return distance < 32.0;
    }

    private double[] estimateBackgroundColor(BufferedImage image) {
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        long count = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        for (int x = 0; x < width; x++) {
            int top = image.getRGB(x, 0);
            int bottom = image.getRGB(x, height - 1);
            sumR += (top >> 16) & 0xFF;
            sumG += (top >> 8) & 0xFF;
            sumB += top & 0xFF;
            sumR += (bottom >> 16) & 0xFF;
            sumG += (bottom >> 8) & 0xFF;
            sumB += bottom & 0xFF;
            count += 2;
        }
        for (int y = 0; y < height; y++) {
            int left = image.getRGB(0, y);
            int right = image.getRGB(width - 1, y);
            sumR += (left >> 16) & 0xFF;
            sumG += (left >> 8) & 0xFF;
            sumB += left & 0xFF;
            sumR += (right >> 16) & 0xFF;
            sumG += (right >> 8) & 0xFF;
            sumB += right & 0xFF;
            count += 2;
        }
        if (count == 0) {
            return new double[]{0.0, 0.0, 0.0};
        }
        return new double[]{sumR / (double) count, sumG / (double) count, sumB / (double) count};
    }

    private double coverage(boolean[][] mask) {
        long count = 0;
        long total = (long) mask.length * mask[0].length;
        for (boolean[] booleans : mask) {
            for (boolean value : booleans) {
                if (value) {
                    count++;
                }
            }
        }
        return total == 0 ? 0.0 : count / (double) total;
    }

    private boolean[][] erode(boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        boolean[][] result = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean keep = true;
                for (int dy = -1; dy <= 1 && keep; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y + dy;
                        int nx = x + dx;
                        if (ny < 0 || ny >= height || nx < 0 || nx >= width || !mask[ny][nx]) {
                            keep = false;
                            break;
                        }
                    }
                }
                result[y][x] = keep;
            }
        }
        return result;
    }

    private List<Component> extractComponents(BufferedImage image, boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        int[][] labels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                labels[y][x] = -1;
            }
        }

        List<Component> components = new ArrayList<>();
        int label = 0;
        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y][x] || labels[y][x] != -1) {
                    continue;
                }
                int left = x;
                int right = x;
                int top = y;
                int bottom = y;
                double sumX = 0.0;
                double sumY = 0.0;
                int area = 0;
                double colorSum = 0.0;
                double colorSq = 0.0;
                Queue<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                labels[y][x] = label;
                while (!queue.isEmpty()) {
                    int[] point = queue.poll();
                    int px = point[0];
                    int py = point[1];
                    left = Math.min(left, px);
                    right = Math.max(right, px);
                    top = Math.min(top, py);
                    bottom = Math.max(bottom, py);
                    area++;
                    sumX += px;
                    sumY += py;
                    int argb = image.getRGB(px, py);
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    double intensity = (r + g + b) / 3.0;
                    colorSum += intensity;
                    colorSq += intensity * intensity;
                    for (int dir = 0; dir < dx.length; dir++) {
                        int nxVal = px + dx[dir];
                        int nyVal = py + dy[dir];
                        if (nxVal >= 0 && nxVal < width && nyVal >= 0 && nyVal < height) {
                            if (mask[nyVal][nxVal] && labels[nyVal][nxVal] == -1) {
                                labels[nyVal][nxVal] = label;
                                queue.add(new int[]{nxVal, nyVal});
                            }
                        }
                    }
                }
                int compWidth = right - left + 1;
                int compHeight = bottom - top + 1;
                double centroidX = sumX / area;
                double centroidY = sumY / area;
                double density = area / (double) (compWidth * compHeight);
                double mean = colorSum / area;
                double variance = Math.max(0.0, (colorSq / area) - (mean * mean));
                double solidity = density; // approximation without convex hull
                if (area >= config.getMinArea()) {
                    components.add(new Component(label, left, top, compWidth, compHeight, area, solidity, centroidX, centroidY, variance, density));
                }
                label++;
            }
        }
        return components;
    }

    public record Component(int label, int left, int top, int width, int height,
                             double area, double solidity, double centroidX, double centroidY,
                             double colorVariance, double density) {
    }
}
