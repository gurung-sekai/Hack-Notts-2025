package com.hacknotts.extractor.analysis;

import com.hacknotts.extractor.config.ExtractorConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClusterFrameDetector {
    private final ExtractorConfig config;

    public ClusterFrameDetector(ExtractorConfig config) {
        this.config = config;
    }

    public List<FrameCluster> cluster(List<ComponentSegmentation.Component> components, AlphaMetrics metrics) {
        List<FrameCluster> clusters = new ArrayList<>();
        double epsSquared = config.getEps() * config.getEps();
        int total = components.size();
        boolean[] visited = new boolean[total];
        int label = 0;

        for (int i = 0; i < total; i++) {
            if (visited[i]) {
                continue;
            }
            List<ComponentSegmentation.Component> group = new ArrayList<>();
            List<Integer> frontier = new ArrayList<>();
            frontier.add(i);
            visited[i] = true;

            for (int index = 0; index < frontier.size(); index++) {
                int currentIndex = frontier.get(index);
                ComponentSegmentation.Component current = components.get(currentIndex);
                group.add(current);
                for (int candidate = 0; candidate < total; candidate++) {
                    if (visited[candidate]) {
                        continue;
                    }
                    ComponentSegmentation.Component other = components.get(candidate);
                    double dx = current.centroidX() - other.centroidX();
                    double dy = current.centroidY() - other.centroidY();
                    double distanceSquared = dx * dx + dy * dy;
                    if (distanceSquared <= epsSquared) {
                        visited[candidate] = true;
                        frontier.add(candidate);
                    }
                }
            }

            if (group.size() < config.getMinSamples()) {
                for (ComponentSegmentation.Component component : group) {
                    clusters.add(new FrameCluster(label++, bounds(List.of(component)), List.of(component)));
                }
            } else {
                clusters.add(new FrameCluster(label++, bounds(group), List.copyOf(group)));
            }
        }

        if (clusters.isEmpty() || clusters.size() == 1) {
            int[][] splits = metrics.findBestBinarySplit();
            if (splits.length >= 2) {
                List<FrameCluster> splitClusters = new ArrayList<>();
                for (int[] split : splits) {
                    splitClusters.add(new FrameCluster(label++, split, List.of()));
                }
                return splitClusters;
            }
        }

        clusters.sort(Comparator.comparingInt(c -> c.bounds()[1]));
        return clusters;
    }

    private int[] bounds(List<ComponentSegmentation.Component> components) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (ComponentSegmentation.Component component : components) {
            left = Math.min(left, component.left());
            top = Math.min(top, component.top());
            right = Math.max(right, component.left() + component.width());
            bottom = Math.max(bottom, component.top() + component.height());
        }
        return new int[]{left, top, right - left, bottom - top};
    }

}
