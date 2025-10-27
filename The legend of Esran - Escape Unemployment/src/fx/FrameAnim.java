package fx;

import util.ResourceLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Frame-by-frame animation helper (for VFX). */
public class FrameAnim {
    private final BufferedImage[] frames;
    private final double fps;
    private final boolean loop;
    private double t = 0;
    private int i = 0;

    private FrameAnim(BufferedImage[] fr, double fps, boolean loop) {
        this.frames = fr; this.fps = fps; this.loop = loop;
    }

    /** Load numbered sequence: base + "1..N" + ext (e.g., FX001_01.png..). */
    public static FrameAnim fromSequence1Based(String base, String ext, int count, double fps, boolean loop) {
        try {
            BufferedImage[] arr = new BufferedImage[count];
            for (int k = 1; k <= count; k++) {
                String num = (k < 10 && base.endsWith("_")) ? "0" + k : String.valueOf(k); // keep 01..08 format
                String path = base + num + ext;
                try (InputStream in = ResourceLoader.open(path)) {
                    if (in == null) {
                        throw new IOException("Missing frame: " + path);
                    }
                    arr[k - 1] = ImageIO.read(in);
                }
            }
            return new FrameAnim(arr, fps, loop);
        } catch (IOException e) {
            throw new RuntimeException("FX seq load failed: " + base + "*", e);
        }
    }

    /** Slice a sprite sheet into cols×rows frames in reading order. */
    public static FrameAnim fromSheet(BufferedImage sheet, int cols, int rows, double fps, boolean loop) {
        int w = sheet.getWidth() / cols, h = sheet.getHeight() / rows;
        BufferedImage[] arr = new BufferedImage[cols * rows];
        int idx = 0;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                arr[idx++] = sheet.getSubimage(c * w, r * h, w, h);
        return new FrameAnim(arr, fps, loop);
    }

    /** Build an animation directly from frames. */
    public static FrameAnim fromFrames(BufferedImage[] frames, double fps, boolean loop) {
        return new FrameAnim(frames.clone(), fps, loop);
    }

    public void update(double dt) {
        if (frames.length <= 1) return;
        t += dt; double fpf = 1.0 / fps;
        while (t >= fpf) { t -= fpf; i++; if (i >= frames.length) i = loop ? 0 : frames.length - 1; }
    }

    public BufferedImage frame() { return frames[Math.min(i, frames.length - 1)]; }
    public boolean finished() { return !loop && i == frames.length - 1 && t == 0; }
    public int w() { return frames[0].getWidth(); }
    public int h() { return frames[0].getHeight(); }
}
