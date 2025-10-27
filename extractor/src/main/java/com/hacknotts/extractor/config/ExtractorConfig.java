package com.hacknotts.extractor.config;

import com.hacknotts.extractor.model.ProcessingDecision;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class ExtractorConfig {
    public static final double DEFAULT_FRAME_DURATION = 0.08;

    private final Path inputDir;
    private final Path outputDir;
    private final int alphaThreshold;
    private final int padding;
    private final int minArea;
    private final double eps;
    private final int minSamples;
    private final int valleyWindow;
    private final double wholeCoverage;
    private final double twoGapIouMax;
    private final int pixelsPerUnit;
    private final boolean visualize;
    private final Map<String, String> clipOverrides;
    private final Map<String, String> decisionOverrides;
    private final Map<String, String> characterOverrides;
    private final Path singleFile;

    private ExtractorConfig(Builder builder) {
        this.inputDir = builder.inputDir;
        this.outputDir = builder.outputDir;
        this.alphaThreshold = builder.alphaThreshold;
        this.padding = builder.padding;
        this.minArea = builder.minArea;
        this.eps = builder.eps;
        this.minSamples = builder.minSamples;
        this.valleyWindow = builder.valleyWindow;
        this.wholeCoverage = builder.wholeCoverage;
        this.twoGapIouMax = builder.twoGapIouMax;
        this.pixelsPerUnit = builder.pixelsPerUnit;
        this.visualize = builder.visualize;
        this.clipOverrides = Map.copyOf(builder.clipOverrides);
        this.decisionOverrides = Map.copyOf(builder.decisionOverrides);
        this.characterOverrides = Map.copyOf(builder.characterOverrides);
        this.singleFile = builder.singleFile;
    }

    public Path getInputDir() {
        return inputDir;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public int getAlphaThreshold() {
        return alphaThreshold;
    }

    public int getPadding() {
        return padding;
    }

    public int getMinArea() {
        return minArea;
    }

    public double getEps() {
        return eps;
    }

    public int getMinSamples() {
        return minSamples;
    }

    public int getValleyWindow() {
        return valleyWindow;
    }

    public double getWholeCoverage() {
        return wholeCoverage;
    }

    public double getTwoGapIouMax() {
        return twoGapIouMax;
    }

    public int getPixelsPerUnit() {
        return pixelsPerUnit;
    }

    public boolean isVisualize() {
        return visualize;
    }

    public Map<String, String> getClipOverrides() {
        return clipOverrides;
    }

    public Map<String, String> getDecisionOverrides() {
        return decisionOverrides;
    }

    public Map<String, String> getCharacterOverrides() {
        return characterOverrides;
    }

    public Path getSingleFile() {
        return singleFile;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path inputDir = Paths.get("./sprites");
        private Path outputDir = Paths.get("./out");
        private int alphaThreshold = 8;
        private int padding = 2;
        private int minArea = 40;
        private double eps = 26.0;
        private int minSamples = 3;
        private int valleyWindow = 7;
        private double wholeCoverage = 0.90;
        private double twoGapIouMax = 0.05;
        private int pixelsPerUnit = 100;
        private boolean visualize = true;
        private final Map<String, String> clipOverrides = new HashMap<>();
        private final Map<String, String> decisionOverrides = new HashMap<>();
        private final Map<String, String> characterOverrides = new HashMap<>();
        private Path singleFile = null;

        public Builder inputDir(Path inputDir) {
            this.inputDir = inputDir;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder alphaThreshold(int alphaThreshold) {
            this.alphaThreshold = alphaThreshold;
            return this;
        }

        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }

        public Builder minArea(int minArea) {
            this.minArea = minArea;
            return this;
        }

        public Builder eps(double eps) {
            this.eps = eps;
            return this;
        }

        public Builder minSamples(int minSamples) {
            this.minSamples = minSamples;
            return this;
        }

        public Builder valleyWindow(int valleyWindow) {
            this.valleyWindow = valleyWindow;
            return this;
        }

        public Builder wholeCoverage(double wholeCoverage) {
            this.wholeCoverage = wholeCoverage;
            return this;
        }

        public Builder twoGapIouMax(double twoGapIouMax) {
            this.twoGapIouMax = twoGapIouMax;
            return this;
        }

        public Builder pixelsPerUnit(int pixelsPerUnit) {
            this.pixelsPerUnit = pixelsPerUnit;
            return this;
        }

        public Builder visualize(boolean visualize) {
            this.visualize = visualize;
            return this;
        }

        public Builder singleFile(Path singleFile) {
            this.singleFile = singleFile;
            return this;
        }

        public Builder putClipOverride(String pattern, String clip) {
            this.clipOverrides.put(pattern, clip);
            return this;
        }

        public Builder putDecisionOverride(String pattern, String decision) {
            this.decisionOverrides.put(pattern, decision);
            return this;
        }

        public Builder putCharacterOverride(String pattern, String name) {
            this.characterOverrides.put(pattern, name);
            return this;
        }

        public ExtractorConfig build() {
            decisionOverrides.putIfAbsent("theWelchAttack3.png", ProcessingDecision.WHOLE.name());
            decisionOverrides.putIfAbsent("purpleEmpressAttack3.png", ProcessingDecision.TWO.name());
            clipOverrides.putIfAbsent("goldMechAttack3.png", "Attack3");
            return new ExtractorConfig(this);
        }
    }
}
