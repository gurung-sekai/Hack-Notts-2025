package com.hacknotts.extractor.export;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.model.AnimationClip;
import com.hacknotts.extractor.model.FrameSlice;
import com.hacknotts.extractor.model.SpriteSheetProcessingResult;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class UnityExporter {
    private final ExtractorConfig config;

    public UnityExporter(ExtractorConfig config) {
        this.config = config;
    }

    public void export(SpriteSheetProcessingResult result) {
        String character = resolveCharacter(result.getSource().getFileName().toString());
        for (AnimationClip clip : result.getClips()) {
            Path clipDir = config.getOutputDir().resolve(character).resolve(clip.getName());
            try {
                Files.createDirectories(clipDir);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create Unity export directory " + clipDir, ex);
            }
            writeMetaFiles(clipDir, clip.getFrames());
            writeSpriteAtlas(clipDir, clip);
            writeAnimationClip(clipDir, clip);
        }
    }

    private void writeMetaFiles(Path clipDir, List<FrameSlice> frames) {
        for (FrameSlice slice : frames) {
            if (slice.getExportedPath() == null) {
                throw new IllegalStateException("Frame has not been written before Unity export");
            }
            Path imagePath = config.getOutputDir().resolve(slice.getExportedPath());
            Path metaPath = imagePath.resolveSibling(imagePath.getFileName().toString() + ".meta");
            String guid = UUID.nameUUIDFromBytes(imagePath.toString().getBytes()).toString();
            String content = "fileFormatVersion: 2\n" +
                    "guid: " + guid + "\n" +
                    "TextureImporter:\n" +
                    "  externalObjects: {}\n" +
                    "  spriteMode: 1\n" +
                    "  pixelsPerUnit: " + config.getPixelsPerUnit() + "\n" +
                    "  spriteAlignment: 9\n" +
                    String.format(Locale.ROOT, "  spritePivot: {x: %.4f, y: %.4f}\n", slice.getPivotX(), slice.getPivotY());
            try {
                Files.writeString(metaPath, content);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to write Unity meta file " + metaPath, ex);
            }
        }
    }

    private void writeSpriteAtlas(Path clipDir, AnimationClip clip) {
        Path atlasPath = clipDir.resolve(clip.getName() + ".spriteatlas");
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"name\": \"").append(clip.getName()).append("\",\n");
        builder.append("  \"sprites\": [\n");
        for (int i = 0; i < clip.getFrames().size(); i++) {
            FrameSlice slice = clip.getFrames().get(i);
            builder.append("    \"").append(slice.getExportedPath().toString().replace('\\', '/')).append("\"");
            if (i < clip.getFrames().size() - 1) {
                builder.append(',');
            }
            builder.append("\n");
        }
        builder.append("  ],\n");
        builder.append("  \"settings\": {\n");
        builder.append("    \"allowRotation\": false,\n");
        builder.append("    \"tightPacking\": true\n");
        builder.append("  }\n");
        builder.append("}\n");
        try (Writer writer = Files.newBufferedWriter(atlasPath)) {
            writer.write(builder.toString());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write Unity sprite atlas " + atlasPath, ex);
        }
    }

    private void writeAnimationClip(Path clipDir, AnimationClip clip) {
        Path animPath = clipDir.resolve(clip.getName() + ".anim.json");
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"name\": \"").append(clip.getName()).append("\",\n");
        builder.append("  \"loop\": ").append(clip.isLoop()).append(",\n");
        builder.append("  \"frameDuration\": ").append(clip.getFrameDuration()).append(",\n");
        builder.append("  \"frames\": [\n");
        for (int i = 0; i < clip.getFrames().size(); i++) {
            FrameSlice slice = clip.getFrames().get(i);
            builder.append("    {\n");
            builder.append("      \"path\": \"").append(slice.getExportedPath().toString().replace('\\', '/')).append("\",\n");
            builder.append(String.format(Locale.ROOT, "      \"pivot\": [%.4f, %.4f]\n", slice.getPivotX(), slice.getPivotY()));
            builder.append("    }");
            if (i < clip.getFrames().size() - 1) {
                builder.append(',');
            }
            builder.append("\n");
        }
        builder.append("  ]\n");
        builder.append("}\n");
        try (Writer writer = Files.newBufferedWriter(animPath)) {
            writer.write(builder.toString());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write Unity animation clip " + animPath, ex);
        }
    }

    private String resolveCharacter(String fileName) {
        String override = resolveOverride(fileName, config.getCharacterOverrides());
        if (override != null) {
            return override;
        }
        int index = fileName.lastIndexOf('.')
;
        String base = index >= 0 ? fileName.substring(0, index) : fileName;
        base = base.replaceAll("(?i)attack.*", "");
        base = base.replaceAll("(?i)idle.*", "");
        base = base.replaceAll("(?i)cast.*", "");
        base = base.replaceAll("(?i)death.*", "");
        if (base.isBlank()) {
            base = index >= 0 ? fileName.substring(0, index) : fileName;
        }
        return base;
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
}
