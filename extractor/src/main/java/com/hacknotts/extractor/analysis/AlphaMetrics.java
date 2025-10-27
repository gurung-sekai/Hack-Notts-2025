package com.hacknotts.extractor.analysis;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.loader.SpriteSheet;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class AlphaMetrics {
    private final SpriteSheet sheet;
    private final ExtractorConfig config;
    private final int[][] alpha;
    private final double[] rowSums;
    private final double[] colSums;
    private final int totalOpaquePixels;
    private final double coverage;

    public AlphaMetrics(SpriteSheet sheet, ExtractorConfig config) {
        this.sheet = sheet;
        this.config = config;
        this.alpha = extractAlpha(sheet.image());
        this.rowSums = computeRowSums(alpha);
        this.colSums = computeColSums(alpha);
        this.totalOpaquePixels = countOpaque(alpha, config.getAlphaThreshold());
        this.coverage = totalOpaquePixels / (double) (sheet.image().getWidth() * sheet.image().getHeight());
    }

    private int[][] extractAlpha(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] values = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                values[y][x] = (image.getRGB(x, y) >> 24) & 0xFF;
            }
        }
        return values;
    }

    private double[] computeRowSums(int[][] alpha) {
        double[] sums = new double[alpha.length];
        for (int y = 0; y < alpha.length; y++) {
            double sum = 0.0;
            for (int value : alpha[y]) {
                sum += value;
            }
            sums[y] = sum;
        }
        return sums;
    }

    private double[] computeColSums(int[][] alpha) {
        int height = alpha.length;
        int width = alpha[0].length;
        double[] sums = new double[width];
        for (int x = 0; x < width; x++) {
            double sum = 0.0;
            for (int y = 0; y < height; y++) {
                sum += alpha[y][x];
            }
            sums[x] = sum;
        }
        return sums;
    }

    private int countOpaque(int[][] alpha, int threshold) {
        int count = 0;
        for (int[] row : alpha) {
            for (int value : row) {
                if (value > threshold) {
                    count++;
                }
            }
        }
        return count;
    }

    public int[][] alpha() {
        return alpha;
    }

    public double coverage() {
        return coverage;
    }

    public double[] getRowSums() {
        return rowSums;
    }

    public double[] getColSums() {
        return colSums;
    }

    public int totalOpaquePixels() {
        return totalOpaquePixels;
    }

    public int[][] findBestBinarySplit() {
        double[] smoothRows = smooth(rowSums, config.getValleyWindow());
        double[] smoothCols = smooth(colSums, config.getValleyWindow());

        int rowValley = findValleyIndex(smoothRows);
        int colValley = findValleyIndex(smoothCols);

        int width = sheet.image().getWidth();
        int height = sheet.image().getHeight();

        int[][] horizontal = null;
        if (rowValley > 0 && rowValley < height - 1) {
            horizontal = new int[][]{
                    {0, 0, width, rowValley},
                    {0, rowValley, width, height - rowValley}
            };
        }
        int[][] vertical = null;
        if (colValley > 0 && colValley < width - 1) {
            vertical = new int[][]{
                    {0, 0, colValley, height},
                    {colValley, 0, width - colValley, height}
            };
        }

        if (horizontal == null && vertical == null) {
            return new int[0][];
        }
        if (horizontal != null && vertical != null) {
            return rowValleyScore(smoothRows) >= colValleyScore(smoothCols) ? horizontal : vertical;
        }
        return horizontal != null ? horizontal : vertical;
    }

    public double rowValleyScore() {
        return rowValleyScore(smooth(rowSums, config.getValleyWindow()));
    }

    public double colValleyScore() {
        return colValleyScore(smooth(colSums, config.getValleyWindow()));
    }

    private double rowValleyScore(double[] smoothRows) {
        return valleyScore(smoothRows);
    }

    private double colValleyScore(double[] smoothCols) {
        return valleyScore(smoothCols);
    }

    private double valleyScore(double[] data) {
        if (data.length == 0) {
            return 0.0;
        }
        double max = Arrays.stream(data).max().orElse(1.0);
        int valley = findValleyIndex(data);
        if (valley <= 0 || valley >= data.length - 1) {
            return 0.0;
        }
        double valleyValue = data[valley];
        return 1.0 - (valleyValue / max);
    }

    private double[] smooth(double[] data, int windowSize) {
        if (windowSize <= 1) {
            return data.clone();
        }
        double[] smooth = new double[data.length];
        int half = windowSize / 2;
        for (int i = 0; i < data.length; i++) {
            double sum = 0.0;
            int count = 0;
            for (int j = Math.max(0, i - half); j <= Math.min(data.length - 1, i + half); j++) {
                sum += data[j];
                count++;
            }
            smooth[i] = sum / Math.max(count, 1);
        }
        return smooth;
    }

    private int findValleyIndex(double[] data) {
        double min = Double.MAX_VALUE;
        int minIndex = -1;
        for (int i = 1; i < data.length - 1; i++) {
            if (data[i] < min) {
                min = data[i];
                minIndex = i;
            }
        }
        return minIndex;
    }
}
