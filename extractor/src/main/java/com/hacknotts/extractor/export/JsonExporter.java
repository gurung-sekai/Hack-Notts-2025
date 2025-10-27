package com.hacknotts.extractor.export;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.model.AnimationClip;
import com.hacknotts.extractor.model.FrameSlice;
import com.hacknotts.extractor.model.SpriteSheetProcessingResult;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JsonExporter {
    private final ExtractorConfig config;

    public JsonExporter(ExtractorConfig config) {
        this.config = config;
    }

    public void export(SpriteSheetProcessingResult result) {
        String character = resolveCharacter(result.getSource().getFileName().toString());
        for (AnimationClip clip : result.getClips()) {
            Path clipDir = config.getOutputDir().resolve(character).resolve(clip.getName());
            try {
                Files.createDirectories(clipDir);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create output directory " + clipDir, ex);
            }

            List<String> frameEntries = new ArrayList<>();
            List<Integer> order = new ArrayList<>();
            int index = 0;
            for (FrameSlice slice : clip.getFrames()) {
                String fileName = String.format(Locale.ROOT, "frame_%03d.png", index);
                Path imagePath = clipDir.resolve(fileName);
                try {
                    ImageIO.write(slice.getImage(), "png", imagePath.toFile());
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to write frame " + imagePath, ex);
                }
                slice.setExportedPath(config.getOutputDir().relativize(imagePath));
                StringBuilder frameBuilder = new StringBuilder();
                frameBuilder.append("    {\n");
                frameBuilder.append("      \"file\": \"").append(fileName).append("\",\n");
                frameBuilder.append("      \"w\": ").append(slice.getWidth()).append(",\n");
                frameBuilder.append("      \"h\": ").append(slice.getHeight()).append(",\n");
                frameBuilder.append(String.format(Locale.ROOT, "      \"pivot\": [%.4f, %.4f],\n", slice.getPivotX(), slice.getPivotY()));
                frameBuilder.append("      \"sourceRect\": [")
                        .append(slice.getX()).append(',')
                        .append(slice.getY()).append(',')
                        .append(slice.getWidth()).append(',')
                        .append(slice.getHeight()).append("]\n");
                frameBuilder.append("    }");
                frameEntries.add(frameBuilder.toString());
                order.add(index);
                index++;
            }
            Path metadataPath = clipDir.resolve("metadata.json");
            try (Writer writer = Files.newBufferedWriter(metadataPath)) {
                writer.write(buildMetadata(character, clip, frameEntries, order, result));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to write metadata for " + metadataPath, ex);
            }
        }
    }

    private String buildMetadata(String character,
                                 AnimationClip clip,
                                 List<String> frameEntries,
                                 List<Integer> order,
                                 SpriteSheetProcessingResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"character\": \"").append(character).append("\",\n");
        json.append("  \"animation\": \"").append(clip.getName()).append("\",\n");
        json.append("  \"frames\": [\n");
        for (int i = 0; i < frameEntries.size(); i++) {
            json.append(frameEntries.get(i));
            if (i < frameEntries.size() - 1) {
                json.append(',');
            }
            json.append("\n");
        }
        json.append("  ],\n");
        json.append("  \"order\": [");
        for (int i = 0; i < order.size(); i++) {
            json.append(order.get(i));
            if (i < order.size() - 1) {
                json.append(',');
            }
        }
        json.append("],\n");
        json.append(String.format(Locale.ROOT, "  \"frameDuration\": %.4f,\n", clip.getFrameDuration()));
        json.append("  \"decision\": \"").append(result.getDecision().name()).append("\",\n");
        json.append("  \"stats\": {");
        int index = 0;
        for (Map.Entry<String, Object> entry : result.getStats().entrySet()) {
            json.append("\"").append(entry.getKey()).append("\": ");
            json.append(formatValue(entry.getValue()));
            if (index < result.getStats().size() - 1) {
                json.append(',');
            }
            json.append(' ');
            index++;
        }
        if (!result.getStats().isEmpty()) {
            json.setLength(json.length() - 1);
        }
        json.append("}\n");
        json.append("}\n");
        return json.toString();
    }

    private String formatValue(Object value) {
        if (value instanceof Number number) {
            if (number instanceof Double || number instanceof Float) {
                return String.format(Locale.ROOT, "%.4f", number.doubleValue());
            }
            return number.toString();
        }
        if (value instanceof Boolean bool) {
            return Boolean.toString(bool);
        }
        return "\"" + value + "\"";
    }

    private String resolveCharacter(String fileName) {
        String override = resolveOverride(fileName, config.getCharacterOverrides());
        if (override != null) {
            return override;
        }
        String base = stripExtension(fileName);
        base = base.replaceAll("(?i)attack.*", "");
        base = base.replaceAll("(?i)idle.*", "");
        base = base.replaceAll("(?i)cast.*", "");
        base = base.replaceAll("(?i)death.*", "");
        if (base.isBlank()) {
            base = stripExtension(fileName);
        }
        return toTitleCase(base);
    }

    private String resolveOverride(String fileName, Map<String, String> overrides) {
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (matches(fileName, entry.getKey())) {
                return entry.getValue();
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
            String fragment = pattern.substring(1, pattern.length() - 1).toLowerCase(Locale.ROOT);
            return fileName.toLowerCase(Locale.ROOT).contains(fragment);
        }
        if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1).toLowerCase(Locale.ROOT);
            return fileName.toLowerCase(Locale.ROOT).endsWith(suffix);
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1).toLowerCase(Locale.ROOT);
            return fileName.toLowerCase(Locale.ROOT).startsWith(prefix);
        }
        return fileName.equalsIgnoreCase(pattern);
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(0, index) : fileName;
    }

    private String toTitleCase(String text) {
        if (text.isBlank()) {
            return "Unknown";
        }
        String[] parts = text.split("[_-]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
            builder.append(' ');
        }
        if (builder.length() > 0) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }
}
