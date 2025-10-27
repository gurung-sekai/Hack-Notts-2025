package com.hacknotts.extractor.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SpriteSheetProcessingResult {
    private final Path source;
    private final ProcessingDecision decision;
    private final List<FrameSlice> frames;
    private final List<AnimationClip> clips;
    private final Map<String, Object> stats;

    public SpriteSheetProcessingResult(Path source,
                                       ProcessingDecision decision,
                                       List<FrameSlice> frames,
                                       List<AnimationClip> clips,
                                       Map<String, Object> stats) {
        this.source = source;
        this.decision = decision;
        this.frames = List.copyOf(frames);
        this.clips = List.copyOf(clips);
        this.stats = Collections.unmodifiableMap(stats);
    }

    public Path getSource() {
        return source;
    }

    public ProcessingDecision getDecision() {
        return decision;
    }

    public List<FrameSlice> getFrames() {
        return frames;
    }

    public List<AnimationClip> getClips() {
        return clips;
    }

    public Map<String, Object> getStats() {
        return stats;
    }
}
