package World.trap;

import java.awt.image.BufferedImage;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Simple frame-based animation that advances using delta time supplied by the game loop.
 */
public final class Animation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final BufferedImage[] frames;
    private final double frameDuration;
    private boolean loop = true;
    private double accumulator = 0.0;
    private int index = 0;
    private final int maxWidth;
    private final int maxHeight;

    public Animation(BufferedImage[] frames, double frameDurationSeconds) {
        if (frames == null || frames.length == 0) {
            throw new IllegalArgumentException("Animation requires at least one frame");
        }
        if (!Double.isFinite(frameDurationSeconds) || frameDurationSeconds <= 0) {
            throw new IllegalArgumentException("Frame duration must be a positive finite number");
        }
        this.frames = frames;
        this.frameDuration = frameDurationSeconds;
        int width = 0;
        int height = 0;
        for (BufferedImage frame : frames) {
            if (frame == null) {
                continue;
            }
            width = Math.max(width, frame.getWidth());
            height = Math.max(height, frame.getHeight());
        }
        this.maxWidth = Math.max(1, width);
        this.maxHeight = Math.max(1, height);
    }

    public BufferedImage getFrame() {
        return frames[index];
    }

    public int getWidth() {
        return maxWidth;
    }

    public int getHeight() {
        return maxHeight;
    }

    public int getFrameCount() {
        return frames.length;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void reset() {
        accumulator = 0.0;
        index = 0;
    }

    public void update(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0) {
            return;
        }
        accumulator += deltaSeconds;
        while (accumulator >= frameDuration) {
            accumulator -= frameDuration;
            index++;
            if (index >= frames.length) {
                if (loop) {
                    index = 0;
                } else {
                    index = frames.length - 1;
                    break;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "Animation{" +
                "frames=" + frames.length +
                ", frameDuration=" + frameDuration +
                ", loop=" + loop +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(frameDuration, loop, frames.length);
    }
}
