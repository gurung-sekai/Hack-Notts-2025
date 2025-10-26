package World.gfx;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DungeonTextures {
    private boolean ready;
    private BufferedImage floorSprite;
    private BufferedImage wallSprite;
    private BufferedImage[] floorCells; // loaded from folder if available
    private BufferedImage[] wallCells;  // loaded from folder if available
    private BufferedImage doorFloor;    // optional door floor tile if provided

    private DungeonTextures() {}

    public static DungeonTextures autoDetect() {
        DungeonTextures dt = new DungeonTextures();
        // 1) Try folder-based variants first
        dt.floorCells = loadFolderVariants("src/resources/tiles/floor", "floor_");
        dt.wallCells  = loadFolderVariants("src/resources/tiles/wall",  "wall_");
        dt.doorFloor  = tryReadFS("src/resources/tiles/floor/door_floor.png");

        if ((dt.floorCells != null && dt.floorCells.length > 0) &&
            (dt.wallCells != null && dt.wallCells.length > 0)) {
            dt.ready = true;
            return dt;
        }

        // 2) Fallback to single images/atlases
        dt.floorSprite = tryReadFS("src/resources/tiles/atlas_floor-16x16.png");
        if (dt.floorSprite == null) dt.floorSprite = tryReadFS("src/resources/tiles/Set 1.png");
        dt.wallSprite  = tryReadFS("src/resources/tiles/atlas_walls_low-16x16.png");
        if (dt.wallSprite == null) dt.wallSprite = tryReadFS("src/resources/tiles/Set 3.5.png");
        dt.ready = (dt.floorSprite != null && dt.wallSprite != null);
        return dt;
    }

    public boolean isReady() { return ready; }
    public BufferedImage floor() { return floorSprite; }
    public BufferedImage wall() { return wallSprite; }
    public BufferedImage doorFloor() { return doorFloor; }

    // 16x16 cell slicing helpers
    public int floorVariants() {
        if (floorCells != null && floorCells.length > 0) return floorCells.length;
        return variantsFor(floorSprite, 16);
    }
    public int wallVariants()  {
        if (wallCells != null && wallCells.length > 0) return wallCells.length;
        return variantsFor(wallSprite, 16);
    }

    public BufferedImage floorVariant(int index) {
        if (floorCells != null && floorCells.length > 0) return floorCells[Math.floorMod(index, floorCells.length)];
        return subcell(floorSprite, 16, index);
    }
    public BufferedImage wallVariant(int index)  {
        if (wallCells != null && wallCells.length > 0) return wallCells[Math.floorMod(index, wallCells.length)];
        return subcell(wallSprite, 16, index);
    }

    private static int variantsFor(BufferedImage sheet, int cell) {
        if (sheet == null || cell <= 0) return 0;
        int cols = sheet.getWidth() / cell;
        int rows = sheet.getHeight() / cell;
        int n = cols * rows;
        return n > 0 ? n : 0;
    }

    private static BufferedImage subcell(BufferedImage sheet, int cell, int index) {
        if (sheet == null || cell <= 0) return null;
        int cols = Math.max(1, sheet.getWidth() / cell);
        int rows = Math.max(1, sheet.getHeight() / cell);
        int n = cols * rows;
        if (n <= 0) return sheet;
        int i = Math.floorMod(index, n);
        int cx = i % cols;
        int cy = i / cols;
        int x = cx * cell;
        int y = cy * cell;
        int w = Math.min(cell, sheet.getWidth() - x);
        int h = Math.min(cell, sheet.getHeight() - y);
        return sheet.getSubimage(x, y, w, h);
    }

    private static BufferedImage tryReadFS(String path) {
        try {
            File f = new File(path);
            if (f.exists()) return ImageIO.read(f);
        } catch (Exception ignored) {}
        return null;
    }

    private static BufferedImage[] loadFolderVariants(String folderPath, String prefix) {
        try {
            File dir = new File(folderPath);
            if (!dir.exists() || !dir.isDirectory()) return null;
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png") && name.startsWith(prefix));
            if (files == null || files.length == 0) return null;
            Arrays.sort(files, Comparator.comparing(File::getName));
            List<BufferedImage> imgs = new ArrayList<>();
            for (File f : files) {
                try { imgs.add(ImageIO.read(f)); } catch (Exception ignored) {}
            }
            return imgs.isEmpty() ? null : imgs.toArray(new BufferedImage[0]);
        } catch (Exception ignored) {
            return null;
        }
    }
}
