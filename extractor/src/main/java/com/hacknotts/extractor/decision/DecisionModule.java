package com.hacknotts.extractor.decision;

import com.hacknotts.extractor.analysis.AlphaMetrics;
import com.hacknotts.extractor.analysis.FrameCluster;
import com.hacknotts.extractor.analysis.ComponentSegmentation;
import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.model.ProcessingDecision;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DecisionModule {
    private final ExtractorConfig config;

    public DecisionModule(ExtractorConfig config) {
        this.config = config;
    }

    public ProcessingDecision decide(Path path,
                                     List<ComponentSegmentation.Component> components,
                                     List<FrameCluster> clusters,
                                     AlphaMetrics metrics) {
        ProcessingDecision override = resolveOverride(path.getFileName().toString(), config.getDecisionOverrides());
        if (override != null) {
            return override;
        }
        if (components.isEmpty()) {
            return ProcessingDecision.WHOLE;
        }

        double totalArea = components.stream().mapToDouble(ComponentSegmentation.Component::area).sum();
        double largestArea = components.stream().mapToDouble(ComponentSegmentation.Component::area).max().orElse(0.0);
        if (totalArea <= 0.0) {
            return ProcessingDecision.WHOLE;
        }

        if (largestArea / totalArea >= config.getWholeCoverage() && clusters.size() <= 1) {
            return ProcessingDecision.WHOLE;
        }

        if (shouldSplitInTwo(clusters, metrics)) {
            return ProcessingDecision.TWO;
        }

        if (clusters.size() <= 1 && components.size() > 1) {
            return ProcessingDecision.MANY;
        }

        return ProcessingDecision.MANY;
    }

    private boolean shouldSplitInTwo(List<FrameCluster> clusters, AlphaMetrics metrics) {
        if (clusters.size() < 2) {
            return false;
        }
        clusters.sort(Comparator.comparingInt(c -> -c.components().size()));
        FrameCluster a = clusters.get(0);
        FrameCluster b = clusters.get(1);
        double areaA = area(a.bounds());
        double areaB = area(b.bounds());
        double total = areaA + areaB;
        if (total <= 0) {
            return false;
        }
        double iou = intersectionOverUnion(a.bounds(), b.bounds());
        double share = total / clusters.stream().mapToDouble(c -> area(c.bounds())).sum();
        boolean clearValley = metrics.rowValleyScore() > 0.35 || metrics.colValleyScore() > 0.35;
        return share > 0.75 && iou < config.getTwoGapIouMax() && clearValley;
    }

    private double area(int[] bounds) {
        return Math.max(bounds[2], 0) * Math.max(bounds[3], 0);
    }

    private double intersectionOverUnion(int[] a, int[] b) {
        int ax1 = a[0];
        int ay1 = a[1];
        int ax2 = a[0] + a[2];
        int ay2 = a[1] + a[3];

        int bx1 = b[0];
        int by1 = b[1];
        int bx2 = b[0] + b[2];
        int by2 = b[1] + b[3];

        int ix1 = Math.max(ax1, bx1);
        int iy1 = Math.max(ay1, by1);
        int ix2 = Math.min(ax2, bx2);
        int iy2 = Math.min(ay2, by2);

        int iw = Math.max(0, ix2 - ix1);
        int ih = Math.max(0, iy2 - iy1);
        double intersection = iw * (double) ih;
        double union = area(a) + area(b) - intersection;
        if (union <= 0) {
            return 0.0;
        }
        return intersection / union;
    }

    private ProcessingDecision resolveOverride(String fileName, Map<String, String> overrides) {
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (matches(fileName, entry.getKey())) {
                try {
                    return ProcessingDecision.valueOf(entry.getValue().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    private boolean matches(String fileName, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            String fragment = pattern.substring(1, pattern.length() - 1);
            return fileName.contains(fragment);
        }
        if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1);
            return fileName.endsWith(suffix);
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return fileName.startsWith(prefix);
        }
        return fileName.equalsIgnoreCase(pattern);
    }
}
