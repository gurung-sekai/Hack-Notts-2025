package com.hacknotts.extractor.export;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.model.AnimationClip;
import com.hacknotts.extractor.model.FrameSlice;
import com.hacknotts.extractor.model.SpriteSheetProcessingResult;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class GodotExporter {
    private final ExtractorConfig config;

    public GodotExporter(ExtractorConfig config) {
        this.config = config;
    }

    public void export(SpriteSheetProcessingResult result) {
        String character = resolveCharacter(result.getSource().getFileName().toString());
        for (AnimationClip clip : result.getClips()) {
            Path clipDir = config.getOutputDir().resolve(character).resolve(clip.getName());
            try {
                Files.createDirectories(clipDir);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create Godot export directory " + clipDir, ex);
            }
            writeSpriteFrames(clipDir, clip);
        }
    }

    private void writeSpriteFrames(Path clipDir, AnimationClip clip) {
        Path tresPath = clipDir.resolve(clip.getName() + ".tres");
        StringBuilder builder = new StringBuilder();
        builder.append("[gd_resource type=\"SpriteFrames\" load_steps=2 format=3]\n\n");
        builder.append("[resource]\n");
        builder.append("animations/").append(clip.getName()).append("/loop = ").append(clip.isLoop()).append('\n');
        double speed = clip.getFrameDuration() > 0 ? 1.0 / clip.getFrameDuration() : 12.0;
        builder.append(String.format(Locale.ROOT, "animations/%s/speed = %.4f\n", clip.getName(), speed));
        builder.append("animations/").append(clip.getName()).append("/frames = [\n");
        for (int i = 0; i < clip.getFrames().size(); i++) {
            FrameSlice slice = clip.getFrames().get(i);
            if (slice.getExportedPath() == null) {
                throw new IllegalStateException("Frame has not been exported prior to Godot export");
            }
            String path = "res://" + slice.getExportedPath().toString().replace('\\', '/');
            builder.append("    \"").append(path).append("\"");
            if (i < clip.getFrames().size() - 1) {
                builder.append(',');
            }
            builder.append("\n");
        }
        builder.append("]\n");
        try (Writer writer = Files.newBufferedWriter(tresPath)) {
            writer.write(builder.toString());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write Godot SpriteFrames " + tresPath, ex);
        }
    }

    private String resolveCharacter(String fileName) {
        String override = resolveOverride(fileName, config.getCharacterOverrides());
        if (override != null) {
            return override;
        }
        int index = fileName.lastIndexOf('.');
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
