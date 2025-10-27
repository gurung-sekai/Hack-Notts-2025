package World.gfx;

import gfx.HiDpiScaler;
import util.ResourceLoader;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Provides textured tiles for the dungeon. We first try to load the handcrafted artwork that ships with the
 * project and only fall back to procedural placeholders when those resources cannot be located. This keeps the
 * visual style consistent with earlier builds while remaining resilient when resources are missing.
 */
public final class DungeonTextures {

    private static final String MODULE_ROOT = "The legend of Esran - Escape Unemployment";
    private static final String FLOOR_DIR = "resources/tiles/floor";
    private static final String WALL_DIR = "resources/tiles/wall";

    private static final int DEFAULT_TILE_SIZE = 36;
    private static volatile int targetTileSize = DEFAULT_TILE_SIZE;

    private static final String[] FLOOR_SHEETS = {
            "resources/tiles/atlas_floor-16x16.png",
            "resources/tiles/Set 1.png"
    };

    private static final String[] WALL_SHEETS = {
            "resources/tiles/atlas_walls_low-16x16.png",
            "resources/tiles/Set 3.5.png"
    };

    private final BufferedImage[] floorCells;
    private final BufferedImage[] wallCells;
    private final BufferedImage doorFloor;

    private DungeonTextures(BufferedImage[] floorCells, BufferedImage[] wallCells, BufferedImage doorFloor) {
        this.floorCells = floorCells;
        this.wallCells = wallCells;
        this.doorFloor = doorFloor;
    }

    /**
     * Load dungeon textures from the bundled assets when possible, falling back to procedural tiles if the assets
     * cannot be located. The method never returns {@code null}.
     */
    public static DungeonTextures load() {
        return load(DEFAULT_TILE_SIZE);
    }

    public static DungeonTextures load(int tileSize) {
        targetTileSize = Math.max(1, tileSize);
        DungeonTextures fromAssets = tryLoadAssets();
        return fromAssets != null ? fromAssets : procedural();
    }

    /**
     * Exposed for tests: returns a basic procedural palette so the game can run without external art.
     */
    public static DungeonTextures procedural() {
        BufferedImage[] floors = new BufferedImage[4];
        BufferedImage[] walls = new BufferedImage[3];
        for (int i = 0; i < floors.length; i++) {
            floors[i] = upscaleTile(makeTile(new Color(18 + i * 4, 64 + i * 3, 78 + i * 4), new Color(12, 42, 56)));
        }
        for (int i = 0; i < walls.length; i++) {
            walls[i] = upscaleTile(makeTile(new Color(46 + i * 6, 126 + i * 5, 146 + i * 4), new Color(28, 82, 96)));
        }
        BufferedImage door = upscaleTile(makeTile(new Color(38, 80, 92), new Color(24, 60, 70)));
        Graphics2D g = door.createGraphics();
        try {
            g.setColor(new Color(210, 178, 90));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(4, 4, door.getWidth() - 8, door.getHeight() - 8);
        } finally {
            g.dispose();
        }
        return new DungeonTextures(floors, walls, door);
    }

    public boolean isReady() {
        return floorCells.length > 0 && wallCells.length > 0;
    }

    public int floorVariants() {
        return floorCells.length;
    }

    public int wallVariants() {
        return wallCells.length;
    }

    public BufferedImage floorVariant(int index) {
        if (floorCells.length == 0) {
            throw new IllegalStateException("No floor textures loaded");
        }
        return floorCells[Math.floorMod(index, floorCells.length)];
    }

    public BufferedImage wallVariant(int index) {
        if (wallCells.length == 0) {
            throw new IllegalStateException("No wall textures loaded");
        }
        return wallCells[Math.floorMod(index, wallCells.length)];
    }

    public BufferedImage doorFloor() {
        return doorFloor != null ? doorFloor : floorVariant(0);
    }

    private static DungeonTextures tryLoadAssets() {
        try {
            BufferedImage[] folderFloors = loadFolderVariants(FLOOR_DIR, "floor_");
            BufferedImage[] folderWalls = loadFolderVariants(WALL_DIR, "wall_");
            BufferedImage door = loadImageIfExists(FLOOR_DIR + "/door_floor.png");

            if (folderFloors.length > 0 && folderWalls.length > 0) {
                return new DungeonTextures(folderFloors, folderWalls, upscaleTile(door));
            }

            BufferedImage floorSheet = loadFirstExistingImage(FLOOR_SHEETS);
            BufferedImage wallSheet = loadFirstExistingImage(WALL_SHEETS);
            if (floorSheet != null && wallSheet != null) {
                BufferedImage[] sheetFloors = upscaleTiles(sliceSheet(floorSheet, 16));
                BufferedImage[] sheetWalls = upscaleTiles(sliceSheet(wallSheet, 16));
                if (sheetFloors.length > 0 && sheetWalls.length > 0) {
                    return new DungeonTextures(sheetFloors, sheetWalls, upscaleTile(door));
                }
            }
        } catch (IOException ex) {
            System.err.println("Failed to load dungeon textures: " + ex.getMessage());
        }
        return null;
    }

    private static BufferedImage[] loadFolderVariants(String relativeDir, String prefix) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        for (Path dir : locateDirectories(relativeDir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                        .filter(p -> p.getFileName().toString().startsWith(prefix))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> uniqueFiles.putIfAbsent(p.getFileName().toString(), p));
            }
        }

        for (Path path : uniqueFiles.values()) {
            try (InputStream in = Files.newInputStream(path)) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    images.add(upscaleTile(img));
                }
            }
        }
        return images.toArray(new BufferedImage[0]);
    }

    private static BufferedImage loadFirstExistingImage(String[] candidates) throws IOException {
        for (String candidate : candidates) {
            BufferedImage img = loadImageIfExists(candidate);
            if (img != null) {
                return img;
            }
        }
        return null;
    }

    private static BufferedImage loadImageIfExists(String relativePath) throws IOException {
        try (InputStream fromResource = ResourceLoader.open(relativePath)) {
            if (fromResource != null) {
                BufferedImage image = ImageIO.read(fromResource);
                if (image != null) {
                    return image;
                }
            }
        }

        for (Path base : candidateRoots()) {
            Path candidate = base.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                try (InputStream in = Files.newInputStream(candidate)) {
                    BufferedImage image = ImageIO.read(in);
                    if (image != null) {
                        return image;
                    }
                }
            }
        }
        return null;
    }

    private static BufferedImage[] upscaleTiles(BufferedImage[] source) {
        if (source == null) {
            return new BufferedImage[0];
        }
        BufferedImage[] scaled = new BufferedImage[source.length];
        for (int i = 0; i < source.length; i++) {
            scaled[i] = upscaleTile(source[i]);
        }
        return scaled;
    }

    private static BufferedImage upscaleTile(BufferedImage img) {
        if (img == null) {
            return null;
        }
        int target = Math.max(1, targetTileSize);
        return HiDpiScaler.scale(img, target, target);
    }

    private static List<Path> locateDirectories(String relativePath) {
        Set<Path> dirs = new LinkedHashSet<>();
        for (Path base : candidateRoots()) {
            Path candidate = base.resolve(relativePath).normalize();
            if (Files.isDirectory(candidate)) {
                dirs.add(candidate);
            }
        }
        return new ArrayList<>(dirs);
    }

    private static List<Path> candidateRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        for (Path cursor = cwd; cursor != null; cursor = cursor.getParent()) {
            roots.add(cursor);
            roots.add(cursor.resolve("src"));
            roots.add(cursor.resolve(MODULE_ROOT));
            roots.add(cursor.resolve(MODULE_ROOT).resolve("src"));
        }
        return new ArrayList<>(roots);
    }

    private static BufferedImage[] sliceSheet(BufferedImage sheet, int cell) {
        if (sheet == null || cell <= 0) {
            return new BufferedImage[0];
        }
        int cols = Math.max(1, sheet.getWidth() / cell);
        int rows = Math.max(1, sheet.getHeight() / cell);
        List<BufferedImage> slices = new ArrayList<>(cols * rows);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int px = x * cell;
                int py = y * cell;
                int w = Math.min(cell, sheet.getWidth() - px);
                int h = Math.min(cell, sheet.getHeight() - py);
                if (w > 0 && h > 0) {
                    slices.add(sheet.getSubimage(px, py, w, h));
                }
            }
        }
        return slices.toArray(new BufferedImage[0]);
    }

    private static BufferedImage makeTile(Color base, Color accent) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(base);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(accent);
            g.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);
            g.drawLine(0, img.getHeight() / 2, img.getWidth(), img.getHeight() / 2);
            g.drawLine(img.getWidth() / 2, 0, img.getWidth() / 2, img.getHeight());
        } finally {
            g.dispose();
        }
        return img;
    }
}
