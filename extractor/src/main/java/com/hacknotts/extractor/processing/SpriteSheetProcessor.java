package com.hacknotts.extractor.processing;

import com.hacknotts.extractor.analysis.AlphaMetrics;
import com.hacknotts.extractor.analysis.ClusterFrameDetector;
import com.hacknotts.extractor.analysis.ComponentSegmentation;
import com.hacknotts.extractor.analysis.FrameCluster;
import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.ml.CoreVsFxClassifier;
import com.hacknotts.extractor.model.AnimationClip;
import com.hacknotts.extractor.model.FrameSlice;
import com.hacknotts.extractor.model.ProcessingDecision;
import com.hacknotts.extractor.model.SpriteSheetProcessingResult;
import com.hacknotts.extractor.naming.AnimationNamer;
import com.hacknotts.extractor.decision.DecisionModule;
import com.hacknotts.extractor.loader.SpriteSheet;
import com.hacknotts.extractor.loader.SpriteSheetLoader;
import com.hacknotts.extractor.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpriteSheetProcessor {
    private final ExtractorConfig config;
    private final CoreVsFxClassifier classifier;
    private final SpriteSheetLoader loader;
    private final ComponentSegmentation segmentation;
    private final ClusterFrameDetector clusterDetector;
    private final DecisionModule decisionModule;
    private final FrameRefiner refiner;
    private final PivotEstimator pivotEstimator;
    private final AnimationNamer animationNamer;

    public SpriteSheetProcessor(ExtractorConfig config, CoreVsFxClassifier classifier) {
        this.config = config;
        this.classifier = classifier;
        this.loader = new SpriteSheetLoader();
        this.segmentation = new ComponentSegmentation(config);
        this.clusterDetector = new ClusterFrameDetector(config);
        this.decisionModule = new DecisionModule(config);
        this.refiner = new FrameRefiner(config);
        this.pivotEstimator = new PivotEstimator(classifier);
        this.animationNamer = new AnimationNamer(config);
    }

    public SpriteSheetProcessingResult process(Path path) throws IOException {
        SpriteSheet sheet = loader.load(path);
        AlphaMetrics metrics = new AlphaMetrics(sheet, config);
        List<ComponentSegmentation.Component> components = segmentation.segment(sheet, metrics);
        List<FrameCluster> clusters = clusterDetector.cluster(components, metrics);
        ProcessingDecision decision = decisionModule.decide(path, components, clusters, metrics);

        List<FrameSlice> frames = switch (decision) {
            case WHOLE -> buildWholeFrames(sheet, metrics, components);
            case TWO -> buildTwoFrames(sheet, metrics, components, clusters);
            case MANY -> buildManyFrames(sheet, metrics, clusters);
        };

        classifier.learnFrom(sheet, components, frames);

        List<AnimationClip> clips = animationNamer.nameClips(path, frames, decision, metrics);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", frames.size());
        stats.put("alphaThreshold", config.getAlphaThreshold());
        stats.put("padding", config.getPadding());
        stats.put("decision", decision.name());
        stats.put("coverage", metrics.coverage());
        stats.put("clusters", clusters.size());

        return new SpriteSheetProcessingResult(path, decision, frames, clips, stats);
    }

    private List<FrameSlice> buildWholeFrames(SpriteSheet sheet, AlphaMetrics metrics, List<ComponentSegmentation.Component> components) {
        BufferedImage trimmed = ImageUtils.cropToOpaque(sheet.image(), config.getAlphaThreshold());
        List<FrameSlice> frames = new ArrayList<>();
        int[] bounds = ImageUtils.computeOpaqueBounds(sheet.image(), config.getAlphaThreshold());
        double[] pivot = pivotEstimator.estimate(sheet, components, bounds);
        frames.add(new FrameSlice(trimmed, 0, bounds[0], bounds[1], trimmed.getWidth(), trimmed.getHeight(), pivot[0], pivot[1]));
        return frames;
    }

    private List<FrameSlice> buildTwoFrames(SpriteSheet sheet,
                                           AlphaMetrics metrics,
                                           List<ComponentSegmentation.Component> components,
                                           List<FrameCluster> clusters) {
        int[][] splits = metrics.findBestBinarySplit();
        List<FrameSlice> frames = new ArrayList<>();
        if (splits.length == 0) {
            return buildWholeFrames(sheet, metrics, clusters.stream().flatMap(c -> c.components().stream()).toList());
        }
        int index = 0;
        for (int[] split : splits) {
            BufferedImage frameImg = ImageUtils.copyRegion(sheet.image(), split[0], split[1], split[2], split[3]);
            double[] pivot = pivotEstimator.estimateWithinBounds(sheet, components, split);
            frames.add(new FrameSlice(frameImg, index++, split[0], split[1], frameImg.getWidth(), frameImg.getHeight(), pivot[0], pivot[1]));
        }
        return refiner.refineFrames(sheet, frames);
    }

    private List<FrameSlice> buildManyFrames(SpriteSheet sheet, AlphaMetrics metrics, List<FrameCluster> clusters) {
        List<FrameSlice> frames = new ArrayList<>();
        int index = 0;
        for (FrameCluster cluster : clusters) {
            int[] bounds = cluster.bounds();
            BufferedImage region = ImageUtils.copyRegion(sheet.image(), bounds[0], bounds[1], bounds[2], bounds[3]);
            double[] pivot = pivotEstimator.estimateWithinBounds(sheet, cluster.components(), bounds);
            frames.add(new FrameSlice(region, index++, bounds[0], bounds[1], region.getWidth(), region.getHeight(), pivot[0], pivot[1]));
        }
        frames = refiner.refineFrames(sheet, frames);
        return frames;
    }
}
