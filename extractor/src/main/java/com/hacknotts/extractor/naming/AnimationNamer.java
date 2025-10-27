package com.hacknotts.extractor.naming;

import com.hacknotts.extractor.analysis.AlphaMetrics;
import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.model.AnimationClip;
import com.hacknotts.extractor.model.FrameSlice;
import com.hacknotts.extractor.model.ProcessingDecision;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnimationNamer {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private final ExtractorConfig config;

    public AnimationNamer(ExtractorConfig config) {
        this.config = config;
    }

    public List<AnimationClip> nameClips(Path path,
                                         List<FrameSlice> frames,
                                         ProcessingDecision decision,
                                         AlphaMetrics metrics) {
        if (frames.isEmpty()) {
            return List.of();
        }
        String fileName = path.getFileName().toString();
        String clipName = resolveClipName(fileName, decision);
        boolean loop = clipName.toLowerCase(Locale.ROOT).contains("idle");
        AnimationClip clip = new AnimationClip(clipName, loop, ExtractorConfig.DEFAULT_FRAME_DURATION, frames);
        return List.of(clip);
    }

    private String resolveClipName(String fileName, ProcessingDecision decision) {
        String override = resolveOverride(fileName, config.getClipOverrides());
        if (override != null) {
            return override;
        }
        String base = stripExtension(fileName).toLowerCase(Locale.ROOT);
        if (base.contains("death")) {
            return "Death";
        }
        if (base.contains("idle")) {
            return "Idle";
        }
        if (base.contains("attack")) {
            Matcher matcher = NUMBER_PATTERN.matcher(base);
            if (matcher.find()) {
                return "Attack" + matcher.group(1);
            }
            return "Attack";
        }
        if (base.contains("cast") || base.contains("spell")) {
            return "CastSpell";
        }
        if (base.contains("hit") || base.contains("hurt")) {
            return "Hit";
        }
        if (decision == ProcessingDecision.WHOLE) {
            return "Whole";
        }
        return "Idle";
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
}
