package World.cutscene;

import java.awt.Graphics2D;

/**
 * Simple interface for animated cutscene backdrops.
 */
@FunctionalInterface
public interface AnimatedBackdrop {
    /**
     * Paint the backdrop.
     *
     * @param g graphics context pre-translated to the backdrop area.
     * @param width available width in pixels.
     * @param height available height in pixels.
     * @param tick frame counter incremented at ~60 Hz.
     */
    void paint(Graphics2D g, int width, int height, long tick);
}
