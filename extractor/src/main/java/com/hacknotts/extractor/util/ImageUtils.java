package com.hacknotts.extractor.util;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class ImageUtils {
    private ImageUtils() {
    }

    public static BufferedImage copyRegion(BufferedImage source, int x, int y, int width, int height) {
        BufferedImage copy = new BufferedImage(Math.max(width, 1), Math.max(height, 1), BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = copy.createGraphics();
        g2d.setComposite(AlphaComposite.Src);
        g2d.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null);
        g2d.dispose();
        return copy;
    }

    public static BufferedImage cropToOpaque(BufferedImage source, int alphaThreshold) {
        int[] bounds = computeOpaqueBounds(source, alphaThreshold);
        if (bounds[2] <= 0 || bounds[3] <= 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        }
        return copyRegion(source, bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    public static int[] computeOpaqueBounds(BufferedImage source, int alphaThreshold) {
        int width = source.getWidth();
        int height = source.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (source.getRGB(x, y) >> 24) & 0xFF;
                if (alpha > alphaThreshold) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    public static BufferedImage addPadding(BufferedImage image, int left, int top, int right, int bottom) {
        int width = image.getWidth() + left + right;
        int height = image.getHeight() + top + bottom;
        BufferedImage padded = new BufferedImage(Math.max(width, 1), Math.max(height, 1), BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = padded.createGraphics();
        g2d.setComposite(AlphaComposite.Src);
        g2d.drawImage(image, left, top, null);
        g2d.dispose();
        return padded;
    }

    public static double[] computeCentroid(BufferedImage image) {
        double sumX = 0.0;
        double sumY = 0.0;
        double count = 0.0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha > 0) {
                    sumX += x;
                    sumY += y;
                    count += 1.0;
                }
            }
        }
        return new double[]{count > 0 ? sumX / count : 0.0, count > 0 ? sumY / count : 0.0, count};
    }
}
