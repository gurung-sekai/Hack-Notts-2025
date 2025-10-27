package com.hacknotts.extractor.processing;

import com.hacknotts.extractor.analysis.ComponentSegmentation;
import com.hacknotts.extractor.loader.SpriteSheet;
import com.hacknotts.extractor.ml.CoreVsFxClassifier;
import com.hacknotts.extractor.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.util.List;

public final class PivotEstimator {
    private final CoreVsFxClassifier classifier;

    public PivotEstimator(CoreVsFxClassifier classifier) {
        this.classifier = classifier;
    }

    public double[] estimate(SpriteSheet sheet, List<ComponentSegmentation.Component> components, int[] bounds) {
        return estimateWithinBounds(sheet, components, bounds);
    }

    public double[] estimateWithinBounds(SpriteSheet sheet,
                                         List<ComponentSegmentation.Component> components,
                                         int[] bounds) {
        double weightedX = 0.0;
        double weightedY = 0.0;
        double totalWeight = 0.0;
        for (ComponentSegmentation.Component component : components) {
            if (!intersects(bounds, component)) {
                continue;
            }
            double weight = classifier.score(component);
            weightedX += weight * (component.centroidX() - bounds[0]);
            weightedY += weight * (component.centroidY() - bounds[1]);
            totalWeight += weight;
        }
        if (totalWeight <= 0.0) {
            BufferedImage region = ImageUtils.copyRegion(sheet.image(), bounds[0], bounds[1], bounds[2], bounds[3]);
            double[] centroid = ImageUtils.computeCentroid(region);
            if (centroid[2] <= 0) {
                return new double[]{0.5, 0.5};
            }
            return new double[]{centroid[0] / region.getWidth(), centroid[1] / region.getHeight()};
        }
        double normalizedX = bounds[2] > 0 ? (weightedX / totalWeight) / bounds[2] : 0.5;
        double normalizedY = bounds[3] > 0 ? (weightedY / totalWeight) / bounds[3] : 0.5;
        return new double[]{clamp(normalizedX), clamp(normalizedY)};
    }

    private boolean intersects(int[] bounds, ComponentSegmentation.Component component) {
        int bx1 = bounds[0];
        int by1 = bounds[1];
        int bx2 = bounds[0] + bounds[2];
        int by2 = bounds[1] + bounds[3];
        int cx1 = component.left();
        int cy1 = component.top();
        int cx2 = component.left() + component.width();
        int cy2 = component.top() + component.height();
        return bx1 < cx2 && bx2 > cx1 && by1 < cy2 && by2 > cy1;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
