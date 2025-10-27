package com.hacknotts.extractor.processing;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.loader.SpriteSheet;
import com.hacknotts.extractor.model.FrameSlice;
import com.hacknotts.extractor.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class FrameRefiner {
    private final ExtractorConfig config;

    public FrameRefiner(ExtractorConfig config) {
        this.config = config;
    }

    public List<FrameSlice> refineFrames(SpriteSheet sheet, List<FrameSlice> frames) {
        List<FrameSlice> refined = new ArrayList<>();
        int alphaThreshold = config.getAlphaThreshold();
        for (FrameSlice frame : frames) {
            int[] localBounds = ImageUtils.computeOpaqueBounds(frame.getImage(), alphaThreshold);
            if (localBounds[2] <= 0 || localBounds[3] <= 0) {
                continue;
            }
            BufferedImage trimmed = ImageUtils.copyRegion(frame.getImage(), localBounds[0], localBounds[1], localBounds[2], localBounds[3]);

            int padLeft = Math.max(0, Math.min(config.getPadding(), frame.getX() + localBounds[0]));
            int padTop = Math.max(0, Math.min(config.getPadding(), frame.getY() + localBounds[1]));
            int padRight = Math.max(0, Math.min(config.getPadding(), sheet.image().getWidth() - (frame.getX() + localBounds[0] + localBounds[2])));
            int padBottom = Math.max(0, Math.min(config.getPadding(), sheet.image().getHeight() - (frame.getY() + localBounds[1] + localBounds[3])));

            BufferedImage padded = ImageUtils.addPadding(trimmed, padLeft, padTop, padRight, padBottom);
            int newX = frame.getX() + localBounds[0] - padLeft;
            int newY = frame.getY() + localBounds[1] - padTop;

            double absolutePivotX = frame.getPivotX() * frame.getWidth();
            double absolutePivotY = frame.getPivotY() * frame.getHeight();
            absolutePivotX = absolutePivotX - localBounds[0] + padLeft;
            absolutePivotY = absolutePivotY - localBounds[1] + padTop;
            double newPivotX = padded.getWidth() > 0 ? clamp(absolutePivotX / padded.getWidth()) : 0.5;
            double newPivotY = padded.getHeight() > 0 ? clamp(absolutePivotY / padded.getHeight()) : 0.5;

            FrameSlice refinedSlice = new FrameSlice(padded,
                    refined.size(),
                    newX,
                    newY,
                    padded.getWidth(),
                    padded.getHeight(),
                    newPivotX,
                    newPivotY);
            refined.add(refinedSlice);
        }
        return refined;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
