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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Provides textured tiles for the dungeon. We first try to load the handcrafted artwork that ships with the
 * project and only fall back to procedural placeholders when those resources cannot be located. This keeps the
 * visual style consistent with earlier builds while remaining resilient when resources are missing.
 */
public final class DungeonTextures {

    private static final String MODULE_ROOT = "The legend of Esran - Escape Unemployment";
    private static final String[] FLOOR_VARIANT_DIRS = {
            "resources/DungeonRooms/floor",
            "resources/DungeonRooms/Floor",
            "resources/DungeonRooms/floors",
            "resources/DungeonRooms/Floors",
            "resources/DungeonRooms/tiles/floor",
            "resources/DungeonRooms/tiles/Floor",
            "resources/tiles/floor"
    };
    private static final String[] WALL_VARIANT_DIRS = {
            "resources/DungeonRooms/wall",
            "resources/DungeonRooms/Walls",
            "resources/DungeonRooms/walls",
            "resources/DungeonRooms/tiles/wall",
            "resources/DungeonRooms/tiles/Walls",
            "resources/tiles/wall"
    };
    private static final String[] FLOOR_OVERLAY_DIRS = {
            "resources/DungeonRooms/overlays/floor",
            "resources/DungeonRooms/overlays/Floor",
            "resources/tiles/overlays/floor"
    };
    private static final String[] WALL_OVERLAY_DIRS = {
            "resources/DungeonRooms/overlays/wall",
            "resources/DungeonRooms/overlays/Walls",
            "resources/tiles/overlays/wall"
    };
    private static final String[] DOOR_TEXTURES = {
            "resources/DungeonRooms/door_floor.png",
            "resources/DungeonRooms/tiles/door_floor.png",
            "resources/tiles/floor/door_floor.png"
    };

    private static final int DEFAULT_TILE_SIZE = 36;
    private static volatile int targetTileSize = DEFAULT_TILE_SIZE;

    private static final String[] FLOOR_SHEETS = {
            "resources/tiles/atlas_floor-16x16.png",
            "resources/tiles/Set 1.png",
            "Sprites/Dungeon Gathering Free Version/Set 1.png",
            "AllSprites/Dungeon Gathering Free Version/Set 1.png"
    };

    private static final String[] WALL_SHEETS = {
            "resources/tiles/atlas_walls_low-16x16.png",
            "resources/tiles/Set 3.5.png",
            "Sprites/Background/atlas_walls_high-16x32.png",
            "AllSprites/Dungeon Gathering Free Version/Set 3.5.png"
    };

    private final BufferedImage[] floorCells;
    private final BufferedImage[] wallCells;
    private final BufferedImage doorFloor;
    private final BufferedImage[] doorFrames;
    private final BufferedImage[] floorOverlays;
    private final BufferedImage[] wallOverlays;

    private DungeonTextures(BufferedImage[] floorCells, BufferedImage[] wallCells, BufferedImage doorFloor,
                            BufferedImage[] doorFrames, BufferedImage[] floorOverlays, BufferedImage[] wallOverlays) {
        this.floorCells = floorCells;
        this.wallCells = wallCells;
        this.doorFloor = doorFloor;
        this.doorFrames = doorFrames == null ? new BufferedImage[0] : doorFrames;
        this.floorOverlays = floorOverlays == null ? new BufferedImage[0] : floorOverlays;
        this.wallOverlays = wallOverlays == null ? new BufferedImage[0] : wallOverlays;
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
        BufferedImage[] proceduralDoors = proceduralDoorAnimation();
        return new DungeonTextures(floors, walls, door, proceduralDoors, new BufferedImage[0], new BufferedImage[0]);
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

    public boolean hasDoorAnimation() {
        return doorFrames.length > 0;
    }

    public int doorFrameCount() {
        return doorFrames.length;
    }

    public BufferedImage doorFrame(int index) {
        if (doorFrames.length == 0) {
            return null;
        }
        return doorFrames[Math.floorMod(index, doorFrames.length)];
    }

    public boolean hasFloorOverlays() {
        return floorOverlays.length > 0;
    }

    public boolean hasWallOverlays() {
        return wallOverlays.length > 0;
    }

    public int floorOverlayCount() {
        return floorOverlays.length;
    }

    public int wallOverlayCount() {
        return wallOverlays.length;
    }

    public BufferedImage floorOverlay(int index) {
        if (floorOverlays.length == 0) {
            return null;
        }
        return floorOverlays[Math.floorMod(index, floorOverlays.length)];
    }

    public BufferedImage wallOverlay(int index) {
        if (wallOverlays.length == 0) {
            return null;
        }
        return wallOverlays[Math.floorMod(index, wallOverlays.length)];
    }

    private static DungeonTextures tryLoadAssets() {
        try {
            BufferedImage[] floorOverlays = loadOverlayVariants(FLOOR_OVERLAY_DIRS);
            BufferedImage[] wallOverlays = loadOverlayVariants(WALL_OVERLAY_DIRS);
            BufferedImage door = loadFirstExistingImage(DOOR_TEXTURES);
            BufferedImage doorTile = door == null ? null : upscaleTile(door);
            BufferedImage[] doorFrames = loadDoorFrames();

            BufferedImage[] folderFloors = loadFirstAvailableFolderVariants(FLOOR_VARIANT_DIRS,
                    new String[]{"floor_", "Floor_", "tile_floor_"});
            BufferedImage[] folderWalls = loadFirstAvailableFolderVariants(WALL_VARIANT_DIRS,
                    new String[]{"wall_", "Wall_", "tile_wall_"});

            if (folderFloors.length > 0 && folderWalls.length > 0) {
                folderFloors = ensureVariety(folderFloors, 6, 0.06f);
                folderWalls = ensureVariety(folderWalls, 6, 0.05f);
                return new DungeonTextures(folderFloors, folderWalls, doorTile,
                        doorFrames, floorOverlays, wallOverlays);
            }

            BufferedImage floorSheet = loadFirstExistingImage(FLOOR_SHEETS);
            BufferedImage wallSheet = loadFirstExistingImage(WALL_SHEETS);
            if (floorSheet != null && wallSheet != null) {
                BufferedImage[] sheetFloors = upscaleTiles(sliceSheet(floorSheet, 16));
                BufferedImage[] sheetWalls = upscaleTiles(sliceSheet(wallSheet, 16));
                if (sheetFloors.length > 0 && sheetWalls.length > 0) {
                    sheetFloors = ensureVariety(sheetFloors, 6, 0.05f);
                    sheetWalls = ensureVariety(sheetWalls, 6, 0.04f);
                    return new DungeonTextures(sheetFloors, sheetWalls, doorTile,
                            doorFrames, floorOverlays, wallOverlays);
                }
            }
        } catch (IOException ex) {
            System.err.println("Failed to load dungeon textures: " + ex.getMessage());
        }
        return null;
    }

    private static BufferedImage[] loadFolderVariants(String relativeDir, String prefix) throws IOException {
        if (relativeDir == null || relativeDir.isBlank()) {
            return new BufferedImage[0];
        }
        String trimmedPrefix = prefix == null ? null : prefix.trim();
        boolean requirePrefix = trimmedPrefix != null && !trimmedPrefix.isEmpty();
        String lowerPrefix = requirePrefix ? trimmedPrefix.toLowerCase(Locale.ROOT) : null;

        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        for (Path dir : locateDirectories(relativeDir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".png") && (!requirePrefix || name.startsWith(lowerPrefix));
                        })
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> uniqueFiles.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT), p));
            }
        }

        Map<String, String> resourceFiles = new LinkedHashMap<>();
        for (String resource : ResourceLoader.listPng(relativeDir)) {
            String name = resource.substring(resource.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (requirePrefix && !name.startsWith(lowerPrefix)) {
                continue;
            }
            resourceFiles.putIfAbsent(name, resource);
        }

        List<BufferedImage> images = new ArrayList<>();
        for (Path path : uniqueFiles.values()) {
            try (InputStream in = Files.newInputStream(path)) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    images.add(upscaleTile(img));
                }
            }
        }
        for (Map.Entry<String, String> entry : resourceFiles.entrySet()) {
            if (uniqueFiles.containsKey(entry.getKey())) {
                continue;
            }
            try {
                BufferedImage img = ResourceLoader.image(entry.getValue());
                if (img != null) {
                    images.add(upscaleTile(img));
                }
            } catch (IOException ex) {
                System.err.println("Failed to load tile variant: " + entry.getValue() + " -> " + ex.getMessage());
            }
        }
        return images.toArray(new BufferedImage[0]);
    }

    private static BufferedImage[] loadFirstAvailableFolderVariants(String[] directories, String[] prefixes) throws IOException {
        if (directories == null || directories.length == 0) {
            return new BufferedImage[0];
        }
        String[] searchPrefixes = (prefixes == null || prefixes.length == 0)
                ? new String[]{null}
                : prefixes;
        for (String prefix : searchPrefixes) {
            for (String dir : directories) {
                BufferedImage[] attempt = loadFolderVariants(dir, prefix);
                if (attempt.length > 0) {
                    return attempt;
                }
            }
        }
        boolean hasNull = false;
        for (String prefix : searchPrefixes) {
            if (prefix == null) {
                hasNull = true;
                break;
            }
        }
        if (!hasNull) {
            for (String dir : directories) {
                BufferedImage[] attempt = loadFolderVariants(dir, null);
                if (attempt.length > 0) {
                    return attempt;
                }
            }
        }
        return new BufferedImage[0];
    }

    private static BufferedImage[] loadOverlayVariants(String[] directories) throws IOException {
        if (directories == null || directories.length == 0) {
            return new BufferedImage[0];
        }
        Map<String, Path> fileBacked = new LinkedHashMap<>();
        Map<String, String> resourceBacked = new LinkedHashMap<>();

        for (String dir : directories) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            for (Path folder : locateDirectories(dir)) {
                try (Stream<Path> stream = Files.list(folder)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .forEach(p -> fileBacked.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT), p));
                }
            }
            for (String resource : ResourceLoader.listPng(dir)) {
                String name = resource.substring(resource.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (!fileBacked.containsKey(name)) {
                    resourceBacked.putIfAbsent(name, resource);
                }
            }
        }

        List<BufferedImage> overlays = new ArrayList<>();
        for (Path path : fileBacked.values()) {
            try (InputStream in = Files.newInputStream(path)) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    overlays.add(upscaleTile(img));
                }
            }
        }
        for (String resource : resourceBacked.values()) {
            try {
                BufferedImage img = ResourceLoader.image(resource);
                if (img != null) {
                    overlays.add(upscaleTile(img));
                }
            } catch (IOException ex) {
                System.err.println("Failed to load overlay resource: " + resource + " -> " + ex.getMessage());
            }
        }
        return overlays.toArray(new BufferedImage[0]);
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

    private static BufferedImage[] ensureVariety(BufferedImage[] base, int targetVariants, float hueStep) {
        if (base == null || base.length == 0 || base.length >= targetVariants) {
            return base == null ? new BufferedImage[0] : base;
        }
        List<BufferedImage> enriched = new ArrayList<>(Arrays.asList(base));
        int original = base.length;
        for (int i = 0; enriched.size() < targetVariants; i++) {
            BufferedImage source = base[i % original];
            float shift = hueStep * (i + 1);
            float satScale = 0.92f + (i % 3) * 0.06f;
            float brightScale = 0.9f + ((i % 2 == 0) ? 0.08f : -0.04f);
            enriched.add(tintTile(source, shift, satScale, brightScale));
        }
        return enriched.toArray(new BufferedImage[0]);
    }

    private static BufferedImage tintTile(BufferedImage img, float hueShift, float satScale, float brightScale) {
        if (img == null) {
            return null;
        }
        BufferedImage tinted = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) {
                    tinted.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                hsb[0] = wrapHue(hsb[0] + hueShift);
                hsb[1] = clamp01(hsb[1] * satScale);
                hsb[2] = clamp01(hsb[2] * brightScale);
                int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x00ffffff;
                tinted.setRGB(x, y, (alpha << 24) | rgb);
            }
        }
        return tinted;
    }

    private static float wrapHue(float hue) {
        hue %= 1f;
        if (hue < 0f) {
            hue += 1f;
        }
        return hue;
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
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

    private static BufferedImage[] loadDoorFrames() {
        List<String> directories = List.of(
                "/resources/DungeonRooms/door",
                "/resources/DungeonRooms/doors",
                "/resources/DungeonRooms/Door",
                "/resources/DungeonRooms/Doors",
                "/resources/tiles/wall",
                "/resources/tiles/door",
                "/resources/Shop/door"
        );
        for (String dir : directories) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            List<String> entries = ResourceLoader.list(dir, name -> name.toLowerCase(Locale.ROOT).startsWith("door")
                    && name.toLowerCase(Locale.ROOT).contains("f")
                    && name.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (entries.isEmpty()) {
                continue;
            }
            TreeMap<Integer, String> ordered = new TreeMap<>();
            for (String path : entries) {
                int idx = frameIndexFromName(path);
                if (idx >= 0 && idx <= 4) {
                    ordered.putIfAbsent(idx, path);
                }
            }
            if (!ordered.isEmpty()) {
                List<BufferedImage> frames = new ArrayList<>();
                for (String path : ordered.values()) {
                    try {
                        BufferedImage raw = ResourceLoader.image(path);
                        if (raw != null) {
                            frames.add(upscaleTile(raw));
                        }
                    } catch (IOException ignored) {
                        // continue with remaining frames
                    }
                    if (frames.size() >= 5) {
                        break;
                    }
                }
                if (!frames.isEmpty()) {
                    return frames.toArray(new BufferedImage[0]);
                }
            }
        }
        return proceduralDoorAnimation();
    }

    private static BufferedImage[] proceduralDoorAnimation() {
        BufferedImage base = upscaleTile(makeTile(new Color(46, 58, 72), new Color(28, 36, 48)));
        BufferedImage[] frames = new BufferedImage[5];
        for (int i = 0; i < frames.length; i++) {
            BufferedImage frame = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = frame.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(base, 0, 0, null);
                g.setColor(new Color(58, 42, 28));
                int inset = 4;
                g.fillRoundRect(inset, inset, frame.getWidth() - inset * 2, frame.getHeight() - inset * 2, 6, 6);
                g.setColor(new Color(182, 142, 78));
                int slatHeight = Math.max(4, frame.getHeight() / 6);
                int glow = i * 3;
                g.fillRoundRect(inset + 2, inset + slatHeight + glow / 3,
                        frame.getWidth() - (inset + 2) * 2, frame.getHeight() - inset * 2 - slatHeight - glow / 2,
                        4, 4);
                g.setColor(new Color(250, 220, 160, 120));
                g.fillRoundRect(inset + 4, inset + slatHeight + glow / 3 + 2,
                        frame.getWidth() - (inset + 4) * 2, frame.getHeight() - inset * 2 - slatHeight - glow / 2 - 4,
                        4, 4);
                g.setColor(new Color(120, 88, 44));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(inset, inset, frame.getWidth() - inset * 2, frame.getHeight() - inset * 2, 6, 6);
            } finally {
                g.dispose();
            }
            frames[i] = frame;
        }
        return frames;
    }

    private static int frameIndexFromName(String path) {
        if (path == null || path.isBlank()) {
            return -1;
        }
        int slash = path.lastIndexOf('/') + 1;
        String name = slash >= 0 && slash < path.length() ? path.substring(slash) : path;
        int marker = name.toLowerCase(Locale.ROOT).lastIndexOf('f');
        if (marker < 0 || marker + 1 >= name.length()) {
            return -1;
        }
        int end = marker + 1;
        while (end < name.length() && Character.isDigit(name.charAt(end))) {
            end++;
        }
        if (end == marker + 1) {
            return -1;
        }
        try {
            return Integer.parseInt(name.substring(marker + 1, end));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
