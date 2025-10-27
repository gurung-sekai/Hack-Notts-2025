package com.hacknotts.extractor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnimationClip {
    private final String name;
    private final boolean loop;
    private final double frameDuration;
    private final List<FrameSlice> frames;

    public AnimationClip(String name, boolean loop, double frameDuration, List<FrameSlice> frames) {
        this.name = name;
        this.loop = loop;
        this.frameDuration = frameDuration;
        this.frames = Collections.unmodifiableList(new ArrayList<>(frames));
    }

    public String getName() {
        return name;
    }

    public boolean isLoop() {
        return loop;
    }

    public double getFrameDuration() {
        return frameDuration;
    }

    public List<FrameSlice> getFrames() {
        return frames;
    }
}
