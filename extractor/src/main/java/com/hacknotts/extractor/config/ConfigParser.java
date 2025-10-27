package com.hacknotts.extractor.config;

import com.hacknotts.extractor.model.ProcessingDecision;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

public final class ConfigParser {
    private ConfigParser() {
    }

    public static ExtractorConfig parse(String[] args) {
        ExtractorConfig.Builder builder = ExtractorConfig.builder();
        String[] normalised = mergeSplitArguments(args);
        for (String arg : normalised) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String[] parts = arg.split("=", 2);
            String key = parts[0].trim();
            String value = parts.length > 1 ? parts[1].trim() : "true";
            switch (key) {
                case "inDir" -> builder.inputDir(normalizeDir(value));
                case "outDir" -> builder.outputDir(Paths.get(value));
                case "alphaThreshold" -> builder.alphaThreshold(Integer.parseInt(value));
                case "padding" -> builder.padding(Integer.parseInt(value));
                case "minArea" -> builder.minArea(Integer.parseInt(value));
                case "eps" -> builder.eps(Double.parseDouble(value));
                case "minSamples" -> builder.minSamples(Integer.parseInt(value));
                case "valleyWindow" -> builder.valleyWindow(Integer.parseInt(value));
                case "wholeCoverage" -> builder.wholeCoverage(Double.parseDouble(value));
                case "twoGapIouMax" -> builder.twoGapIouMax(Double.parseDouble(value));
                case "pixelsPerUnit" -> builder.pixelsPerUnit(Integer.parseInt(value));
                case "visualize" -> builder.visualize(Boolean.parseBoolean(value));
                case "file", "--file" -> builder.singleFile(Paths.get(value));
                case "--forceWhole" -> builder.putDecisionOverride("*", ProcessingDecision.WHOLE.name());
                case "--forceTwo" -> builder.putDecisionOverride("*", ProcessingDecision.TWO.name());
                case "--rows", "--cols" -> {
                    // manual grid hints stored in stats map by processor; not directly used in config yet
                    // keep placeholder for future extension
                }
                case "--name" -> builder.putCharacterOverride("*", value);
                case "--clip" -> builder.putClipOverride("clip", value);
                default -> {
                    if (key.startsWith("override.")) {
                        String pattern = key.substring("override.".length());
                        builder.putClipOverride(pattern, value);
                    } else if (key.startsWith("decision.")) {
                        String pattern = key.substring("decision.".length());
                        builder.putDecisionOverride(pattern, value.toUpperCase(Locale.ROOT));
                    }
                }
            }
        }

        // Hard-coded overrides demanded in the specification
        builder.putDecisionOverride("theWelchAttack3.png", ProcessingDecision.WHOLE.name());
        builder.putDecisionOverride("purpleEmpressAttack3.png", ProcessingDecision.TWO.name());
        builder.putClipOverride("goldMechAttack3.png", "Attack3");

        ExtractorConfig config = builder.build();
        ensureDirectory(config.getOutputDir());
        return config;
    }

    private static String[] mergeSplitArguments(String[] args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        String[] buffer = new String[args.length];
        int count = 0;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            boolean hasEquals = arg.contains("=");
            boolean isFlag = arg.startsWith("--");
            if ((hasEquals || isFlag) || count == 0) {
                buffer[count++] = arg;
            } else {
                buffer[count - 1] = buffer[count - 1] + " " + arg;
            }
        }
        String[] result = new String[count];
        System.arraycopy(buffer, 0, result, 0, count);
        return result;
    }

    private static Path normalizeDir(String value) {
        Path dir = Paths.get(value);
        ensureDirectory(dir);
        return dir;
    }

    private static void ensureDirectory(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to create directory " + dir, ex);
            }
        }
    }
}
