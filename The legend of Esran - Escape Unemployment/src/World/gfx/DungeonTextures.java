package World.gfx;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Minimal texture helper for ZeldaRooms.
 * - Call DungeonTextures.autoDetect() to try several common resource paths.
 * - Or call DungeonTextures.from(path, floorRect, wallRect) to specify exactly.
 * If no textures load, isReady()==false and callers should draw fallback colors.
 */
public final class DungeonTextures {

    // Defaults that match your original code
    private static final String DEFAULT_PATH = "/dungeon_sheet.png";
    private static final Rectangle DEFAULT_FLOOR_SRC = new Rectangle(224, 64, 32, 32);
    private static final Rectangle DEFAULT_WALL_SRC  = new Rectangle(  0,  0, 32, 32);

    private final boolean ready;
    private final BufferedImage floorSprite;
    private final BufferedImage wallSprite;

    private DungeonTextures(boolean ready, BufferedImage floorSprite, BufferedImage wallSprite) {
        this.ready = ready;
        this.floorSprite = floorSprite;
        this.wallSprite = wallSprite;
    }

    /** Try a few likely locations/atlases. */
    public static DungeonTextures autoDetect() {
        // 1) Exact defaults (your original)
        DungeonTextures dt = from(DEFAULT_PATH, DEFAULT_FLOOR_SRC, DEFAULT_WALL_SRC);
        if (dt.isReady()) return dt;

        // 2) Same file under a tiles/ folder (common layout)
        dt = from("/tiles/dungeon_sheet.png", DEFAULT_FLOOR_SRC, DEFAULT_WALL_SRC);
        if (dt.isReady()) return dt;

        // 3) Separate atlases (pick the first cell from each)
        BufferedImage floorAtlas = loadImage("/tiles/atlas_floor-16x16.png");
        BufferedImage wallAtlas16 = loadImage("/tiles/atlas_walls_low-16x16.png");
        if (floorAtlas != null && wallAtlas16 != null) {
            BufferedImage f = safeSub(floorAtlas, new Rectangle(0, 0,
                    Math.min(16, floorAtlas.getWidth()), Math.min(16, floorAtlas.getHeight())));
            BufferedImage w = safeSub(wallAtlas16, new Rectangle(0, 0,
                    Math.min(16, wallAtlas16.getWidth()), Math.min(16, wallAtlas16.getHeight())));
            return new DungeonTextures(true, f, w);
        }

        // 4) If you prefer the 16x32 high walls atlas, fallback to that
        BufferedImage wallAtlas32 = loadImage("/tiles/atlas_walls_high-16x32.png");
        if (floorAtlas != null && wallAtlas32 != null) {
            BufferedImage f = safeSub(floorAtlas, new Rectangle(0, 0,
                    Math.min(16, floorAtlas.getWidth()), Math.min(16, floorAtlas.getHeight())));
            BufferedImage w = safeSub(wallAtlas32, new Rectangle(0, 0,
                    Math.min(16, wallAtlas32.getWidth()), Math.min(32, wallAtlas32.getHeight())));
            return new DungeonTextures(true, f, w);
        }

        // 5) Some of your "Set X.Y.png" files (first cell fallback).
        //    Note: spaces in resource names are allowed if they exist in the JAR/classpath.
        String[] sets = {
                "/tiles/Set 1.0.png", "/tiles/Set 1.1.png", "/tiles/Set 1.2.png",
                "/tiles/Set 1.3.png", "/tiles/Set 1.png",   "/tiles/Set 3.5.png",
                "/tiles/Set 4.5.png"
        };
        for (String p : sets) {
            BufferedImage s = loadImage(p);
            if (s != null) {
                // Take two different-looking tiles from the sheet heuristically
                BufferedImage f = safeSub(s, new Rectangle(0, 0,
                        Math.min(16, s.getWidth()), Math.min(16, s.getHeight())));
                BufferedImage w = safeSub(s, new Rectangle(Math.min(16, Math.max(0, s.getWidth()-1)), 0,
                        Math.min(16, Math.max(1, s.getWidth()-16)), Math.min(16, s.getHeight())));
                return new DungeonTextures(true, f, w);
            }
        }

        // Nothing found -> disabled
        return disabled();
    }

    /** Load explicitly from one PNG and two sub-rectangles. */
    public static DungeonTextures from(String path, Rectangle floorRect, Rectangle wallRect) {
        BufferedImage sheet = loadImage(path);
        if (sheet == null) return disabled();
        BufferedImage f = safeSub(sheet, floorRect);
        BufferedImage w = safeSub(sheet, wallRect);
        if (f == null || w == null) return disabled();
        return new DungeonTextures(true, f, w);
    }

    /** Disabled instance -> no textures, draw fallback colors. */
    public static DungeonTextures disabled() {
        return new DungeonTextures(false, null, null);
    }

    public boolean isReady() { return ready; }
    public BufferedImage floor() { return floorSprite; }
    public BufferedImage wall() { return wallSprite; }

    // ---- helpers ----
    private static BufferedImage loadImage(String path) {
        InputStream in = null;
        try {
            in = DungeonTextures.class.getResourceAsStream(path);
            if (in == null) {
                // Also try context class loader (some build tools place resources differently)
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl != null) {
                    in = cl.getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
                }
            }
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException ignored) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static BufferedImage safeSub(BufferedImage src, Rectangle r) {
        if (src == null || r == null) return null;
        int x = Math.max(0, r.x);
        int y = Math.max(0, r.y);
        int w = Math.max(1, Math.min(r.width,  src.getWidth()  - x));
        int h = Math.max(1, Math.min(r.height, src.getHeight() - y));
        if (w <= 0 || h <= 0) return null;
        try {
            return src.getSubimage(x, y, w, h);
        } catch (RasterFormatException ex) {
            return null;
        }
    }
}
