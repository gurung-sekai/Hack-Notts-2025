package com.hacknotts.extractor.ml;

import com.hacknotts.extractor.analysis.ComponentSegmentation;
import com.hacknotts.extractor.loader.SpriteSheet;
import com.hacknotts.extractor.model.FrameSlice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CoreVsFxClassifier {
    private static final double LEARNING_RATE = 0.05;

    private final Path modelPath;
    private final double[] weights;
    private double bias;

    private CoreVsFxClassifier(Path modelPath, double[] weights, double bias) {
        this.modelPath = modelPath;
        this.weights = weights;
        this.bias = bias;
    }

    public static CoreVsFxClassifier loadOrCreate(Path modelPath) {
        if (Files.exists(modelPath)) {
            try (BufferedReader reader = Files.newBufferedReader(modelPath)) {
                String biasLine = reader.readLine();
                String weightLine = reader.readLine();
                double bias = biasLine != null ? Double.parseDouble(biasLine.trim()) : 0.0;
                double[] weights = parseWeights(weightLine);
                return new CoreVsFxClassifier(modelPath, weights, bias);
            } catch (IOException | NumberFormatException ex) {
                throw new IllegalStateException("Failed to read classifier model", ex);
            }
        }
        return new CoreVsFxClassifier(modelPath, new double[]{0.6, 0.2, -0.1, -0.05}, 0.0);
    }

    private static double[] parseWeights(String line) {
        if (line == null || line.isBlank()) {
            return new double[]{0.6, 0.2, -0.1, -0.05};
        }
        String[] parts = line.split(",");
        double[] weights = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            weights[i] = Double.parseDouble(parts[i].trim());
        }
        return weights;
    }

    public void save() {
        try {
            Files.createDirectories(modelPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(modelPath)) {
                writer.write(Double.toString(bias));
                writer.newLine();
                for (int i = 0; i < weights.length; i++) {
                    writer.write(Double.toString(weights[i]));
                    if (i < weights.length - 1) {
                        writer.write(",");
                    }
                }
                writer.newLine();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save classifier", ex);
        }
    }

    public double score(ComponentSegmentation.Component component) {
        double[] features = features(component);
        double z = bias;
        for (int i = 0; i < weights.length; i++) {
            z += weights[i] * features[i];
        }
        return sigmoid(z);
    }

    public void learnFrom(SpriteSheet sheet,
                           List<ComponentSegmentation.Component> components,
                           List<FrameSlice> frames) {
        if (components.isEmpty()) {
            return;
        }
        double maxArea = components.stream().mapToDouble(ComponentSegmentation.Component::area).max().orElse(1.0);
        double avgArea = components.stream().mapToDouble(ComponentSegmentation.Component::area).average().orElse(maxArea);
        for (ComponentSegmentation.Component component : components) {
            double label = deriveLabel(component, maxArea, avgArea, frames);
            if (label < 0) {
                continue;
            }
            double prediction = score(component);
            double error = label - prediction;
            double[] features = features(component);
            for (int i = 0; i < weights.length; i++) {
                weights[i] += LEARNING_RATE * error * features[i];
            }
            bias += LEARNING_RATE * error;
        }
    }

    private double deriveLabel(ComponentSegmentation.Component component,
                                double maxArea,
                                double avgArea,
                                List<FrameSlice> frames) {
        double area = component.area();
        if (area >= maxArea * 0.4) {
            return 1.0;
        }
        if (area <= avgArea * 0.15 && component.colorVariance() > 1500 && component.density() < 0.4) {
            return 0.0;
        }
        boolean intersects = frames.stream().anyMatch(frame -> intersects(frame, component));
        if (intersects) {
            return 1.0;
        }
        return -1.0;
    }

    private boolean intersects(FrameSlice slice, ComponentSegmentation.Component component) {
        int sx1 = slice.getX();
        int sy1 = slice.getY();
        int sx2 = slice.getX() + slice.getWidth();
        int sy2 = slice.getY() + slice.getHeight();
        int cx1 = component.left();
        int cy1 = component.top();
        int cx2 = component.left() + component.width();
        int cy2 = component.top() + component.height();
        return sx1 < cx2 && sx2 > cx1 && sy1 < cy2 && sy2 > cy1;
    }

    private double[] features(ComponentSegmentation.Component component) {
        double area = component.area();
        double density = component.density();
        double solidity = component.solidity();
        double variance = component.colorVariance();
        double normArea = Math.min(1.0, area / 10000.0);
        double normVariance = Math.min(1.0, variance / 5000.0);
        return new double[]{normArea, density, solidity, normVariance};
    }

    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }
}
