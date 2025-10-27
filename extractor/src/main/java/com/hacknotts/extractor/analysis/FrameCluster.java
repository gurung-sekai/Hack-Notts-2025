package com.hacknotts.extractor.analysis;

import java.util.List;

public record FrameCluster(int label, int[] bounds, List<ComponentSegmentation.Component> components) {
}
