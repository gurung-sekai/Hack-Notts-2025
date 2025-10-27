package com.hacknotts.extractor.model;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public final class FrameSlice {
    private final BufferedImage image;
    private final int index;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final double pivotX;
    private final double pivotY;
    private Path exportedPath;

    public FrameSlice(BufferedImage image, int index, int x, int y, int width, int height, double pivotX, double pivotY) {
        this.image = image;
        this.index = index;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getIndex() {
        return index;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getPivotX() {
        return pivotX;
    }

    public double getPivotY() {
        return pivotY;
    }

    public Path getExportedPath() {
        return exportedPath;
    }

    public void setExportedPath(Path exportedPath) {
        this.exportedPath = exportedPath;
    }
}
