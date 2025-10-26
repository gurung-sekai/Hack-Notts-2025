package tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline utility that reconstructs sprite atlas metadata from the pre-sliced PNGs stored in commit 820bc48.
 * The generated metadata is embedded into {@code util.SpriteSheetMetadata} so runtime code can crop the
 * original sprite sheets without tracking hundreds of binary files in git.
 */
public final class GenerateSpriteSheetMetadata {

    private static final String SOURCE_COMMIT = "820bc48";
    private static final Path PROJECT_ROOT = Path.of("The legend of Esran - Escape Unemployment");
    private static final Path RESOURCES_ROOT = PROJECT_ROOT.resolve("src/resources");

    private static final List<AtlasSource> SOURCES = List.of(
            new AtlasSource("/resources/bosses/Gollum.png", "bosses/Gollum/Idle"),
            new AtlasSource("/resources/bosses/Grim.png", "bosses/Grim/Idle"),
            new AtlasSource("/resources/bosses/fireFlinger.png", "bosses/FireFlinger/Idle"),
            new AtlasSource("/resources/bosses/goldMech.png", "bosses/GoldMech/Idle"),
            new AtlasSource("/resources/bosses/goldenKnight.png", "bosses/GoldenKnight/Idle"),
            new AtlasSource("/resources/bosses/purpleEmpress.png", "bosses/PurpleEmpress/Idle"),
            new AtlasSource("/resources/bosses/theWelch.png", "bosses/TheWelch/Idle"),
            new AtlasSource("/resources/bosses/toxicTree.png", "bosses/ToxicTree/Idle"),
            new AtlasSource("/resources/bosses/attacks/fireFlingerAttack1.png", "bosses/attacks/fireFlingerAttack1"),
            new AtlasSource("/resources/bosses/attacks/fireFlingerAttack2.png", "bosses/attacks/fireFlingerAttack2"),
            new AtlasSource("/resources/bosses/attacks/fireFlingerAttack3.png", "bosses/attacks/fireFlingerAttack3"),
            new AtlasSource("/resources/bosses/attacks/fireFlingerAttack4.png", "bosses/attacks/fireFlingerAttack4"),
            new AtlasSource("/resources/bosses/attacks/goldMechAttack1.png", "bosses/attacks/goldMechAttack1"),
            new AtlasSource("/resources/bosses/attacks/goldMechAttack2.png", "bosses/attacks/goldMechAttack2"),
            new AtlasSource("/resources/bosses/attacks/goldMechAttack3.png", "bosses/attacks/goldMechAttack3"),
            new AtlasSource("/resources/bosses/attacks/goldenKnightAttack1.png", "bosses/attacks/goldenKnightAttack1"),
            new AtlasSource("/resources/bosses/attacks/goldenKnightAttack2.png", "bosses/attacks/goldenKnightAttack2"),
            new AtlasSource("/resources/bosses/attacks/goldenKnightAttack3.png", "bosses/attacks/goldenKnightAttack3"),
            new AtlasSource("/resources/bosses/attacks/gollumAttack1.png", "bosses/attacks/gollumAttack1"),
            new AtlasSource("/resources/bosses/attacks/gollumAttack2.png", "bosses/attacks/gollumAttack2"),
            new AtlasSource("/resources/bosses/attacks/gollumAttack3.png", "bosses/attacks/gollumAttack3"),
            new AtlasSource("/resources/bosses/attacks/gollumAttack4.png", "bosses/attacks/gollumAttack4"),
            new AtlasSource("/resources/bosses/attacks/grimAttack1.png", "bosses/attacks/grimAttack1"),
            new AtlasSource("/resources/bosses/attacks/grimAttack2.png", "bosses/attacks/grimAttack2"),
            new AtlasSource("/resources/bosses/attacks/grimAttack3.png", "bosses/attacks/grimAttack3"),
            new AtlasSource("/resources/bosses/attacks/grimAttack4.png", "bosses/attacks/grimAttack4"),
            new AtlasSource("/resources/bosses/attacks/purpleEmpressAttack1.png", "bosses/attacks/purpleEmpressAttack1"),
            new AtlasSource("/resources/bosses/attacks/purpleEmpressAttack2.png", "bosses/attacks/purpleEmpressAttack2"),
            new AtlasSource("/resources/bosses/attacks/purpleEmpressAttack3.png", "bosses/attacks/purpleEmpressAttack3"),
            new AtlasSource("/resources/bosses/attacks/theWelchAttack1.png", "bosses/attacks/theWelchAttack1"),
            new AtlasSource("/resources/bosses/attacks/theWelchAttack2.png", "bosses/attacks/theWelchAttack2"),
            new AtlasSource("/resources/bosses/attacks/theWelchAttack3.png", "bosses/attacks/theWelchAttack3"),
            new AtlasSource("/resources/bosses/attacks/toxicTreeAttack1.png", "bosses/attacks/toxicTreeAttack1"),
            new AtlasSource("/resources/bosses/attacks/toxicTreeAttack2.png", "bosses/attacks/toxicTreeAttack2"),
            new AtlasSource("/resources/bosses/attacks/toxicTreeAttack3.png", "bosses/attacks/toxicTreeAttack3")
    );

    private GenerateSpriteSheetMetadata() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, List<int[]>> atlas = new LinkedHashMap<>();
        for (AtlasSource source : SOURCES) {
            System.out.println("Processing " + source.resourcePath());
            BufferedImage sheet = loadSheet(source.resourcePath());
            List<BufferedImage> frames = loadFrames(source.treePath());
            atlas.put(source.resourcePath(), locate(sheet, frames));
        }

        writeJava(atlas);
    }

    private static BufferedImage loadSheet(String resourcePath) throws IOException {
        if (!resourcePath.startsWith("/resources/")) {
            throw new IllegalArgumentException("resourcePath must start with /resources/: " + resourcePath);
        }
        String relative = resourcePath.substring("/resources/".length());
        Path path = RESOURCES_ROOT.resolve(relative);
        return ImageIO.read(path.toFile());
    }

    private static List<BufferedImage> loadFrames(String relativeDir) throws Exception {
        String treePath = "The legend of Esran - Escape Unemployment/src/resources/" + relativeDir;
        List<FrameEntry> entries = listFrames(treePath);
        List<BufferedImage> frames = new ArrayList<>(entries.size());
        for (FrameEntry entry : entries) {
            byte[] data = readBlob(entry.hash());
            try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                frames.add(ImageIO.read(in));
            }
        }
        return frames;
    }

    private static List<FrameEntry> listFrames(String treePath) throws Exception {
        Process process = new ProcessBuilder("git", "ls-tree", SOURCE_COMMIT + ":" + treePath)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        List<FrameEntry> entries = new ArrayList<>();
        try (InputStream in = process.getInputStream()) {
            String output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : output.split("\n")) {
                if (line.isBlank()) continue;
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String[] parts = line.substring(0, tab).split(" ");
                if (parts.length < 3) continue;
                String hash = parts[2];
                String name = line.substring(tab + 1).trim();
                entries.add(new FrameEntry(name, hash));
            }
        }
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git ls-tree failed for " + treePath);
        }
        entries.sort(Comparator.comparing(FrameEntry::name));
        return entries;
    }

    private static byte[] readBlob(String hash) throws Exception {
        Process process = new ProcessBuilder("git", "cat-file", "blob", hash)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        byte[] data;
        try (InputStream in = process.getInputStream()) {
            data = in.readAllBytes();
        }
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git cat-file failed for " + hash);
        }
        return data;
    }

    private static List<int[]> locate(BufferedImage sheet, List<BufferedImage> frames) {
        int sheetW = sheet.getWidth();
        int sheetH = sheet.getHeight();
        boolean[][] used = new boolean[sheetW][sheetH];
        int[][] sheetPixels = new int[sheetW][];
        for (int x = 0; x < sheetW; x++) {
            sheetPixels[x] = sheet.getRGB(x, 0, 1, sheetH, null, 0, 1);
        }
        Map<Integer, List<int[]>> colorIndex = buildColorIndex(sheetPixels);

        List<int[]> rects = new ArrayList<>(frames.size());
        for (BufferedImage frame : frames) {
            int[] rect = locateFrame(sheetPixels, colorIndex, frame, used);
            rects.add(rect);
            markUsed(used, rect);
        }
        return rects;
    }

    private static void markUsed(boolean[][] used, int[] rect) {
        int x = rect[0], y = rect[1], w = rect[2], h = rect[3];
        for (int xx = x; xx < x + w; xx++) {
            boolean[] column = used[xx];
            for (int yy = y; yy < y + h; yy++) {
                column[yy] = true;
            }
        }
    }

    private static int[] locateFrame(int[][] sheetPixels, Map<Integer, List<int[]>> colorIndex,
                                     BufferedImage frame, boolean[][] used) {
        int sheetW = sheetPixels.length;
        int sheetH = sheetPixels[0].length;
        int frameW = frame.getWidth();
        int frameH = frame.getHeight();

        int refX = -1, refY = -1;
        int refColor = 0;
        outer:
        for (int y = 0; y < frameH; y++) {
            for (int x = 0; x < frameW; x++) {
                int argb = frame.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) > 0) {
                    refX = x;
                    refY = y;
                    refColor = argb & 0xFFFFFF;
                    break outer;
                }
            }
        }
        if (refX < 0) {
            throw new IllegalStateException("Frame has no opaque pixels");
        }

        List<int[]> candidates = colorIndex.getOrDefault(refColor, List.of());
        for (int[] pos : candidates) {
            int sx = pos[0];
            int sy = pos[1];
            int originX = sx - refX;
            int originY = sy - refY;
            if (originX < 0 || originY < 0 || originX + frameW > sheetW || originY + frameH > sheetH) {
                continue;
            }
            if (overlapsUsed(used, originX, originY, frameW, frameH)) {
                continue;
            }
            if (matches(sheetPixels, originX, originY, frame)) {
                return new int[]{originX, originY, frameW, frameH};
            }
        }
        throw new IllegalStateException("Unable to locate frame");
    }

    private static Map<Integer, List<int[]>> buildColorIndex(int[][] sheetPixels) {
        Map<Integer, List<int[]>> index = new LinkedHashMap<>();
        int width = sheetPixels.length;
        int height = sheetPixels[0].length;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int colour = sheetPixels[x][y] & 0xFFFFFF;
                index.computeIfAbsent(colour, c -> new ArrayList<>()).add(new int[]{x, y});
            }
        }
        return index;
    }

    private static boolean overlapsUsed(boolean[][] used, int x, int y, int w, int h) {
        for (int xx = x; xx < x + w; xx++) {
            boolean[] column = used[xx];
            for (int yy = y; yy < y + h; yy++) {
                if (column[yy]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matches(int[][] sheetPixels, int originX, int originY, BufferedImage frame) {
        int frameW = frame.getWidth();
        int frameH = frame.getHeight();
        for (int y = 0; y < frameH; y++) {
            int sheetY = originY + y;
            for (int x = 0; x < frameW; x++) {
                int argb = frame.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;
                int sheetX = originX + x;
                int sheetColour = sheetPixels[sheetX][sheetY] & 0xFFFFFF;
                if (sheetColour != (argb & 0xFFFFFF)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void writeJava(Map<String, List<int[]>> atlas) throws IOException {
        Path output = PROJECT_ROOT.resolve("src/util/SpriteSheetMetadata.java");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(output))) {
            out.println("package util;");
            out.println();
            out.println("import java.util.HashMap;");
            out.println("import java.util.Map;");
            out.println();
            out.println("final class SpriteSheetMetadata {");
            out.println("    private static final Map<String, int[][]> DATA = build();");
            out.println();
            out.println("    private SpriteSheetMetadata() { }");
            out.println();
            out.println("    private static Map<String, int[][]> build() {");
            out.println("        Map<String, int[][]> map = new HashMap<>();");
            for (Map.Entry<String, List<int[]>> entry : atlas.entrySet()) {
                out.println("        map.put(\"" + entry.getKey() + "\", new int[][] {");
                List<int[]> rects = entry.getValue();
                for (int i = 0; i < rects.size(); i++) {
                    int[] r = rects.get(i);
                    out.printf("                new int[] {%d, %d, %d, %d}%s%n",
                            r[0], r[1], r[2], r[3], (i + 1 == rects.size()) ? "" : ",");
                }
                out.println("        });");
            }
            out.println("        return map;");
            out.println("    }");
            out.println();
            out.println("    static int[][] lookup(String resourcePath) {");
            out.println("        int[][] arr = DATA.get(resourcePath);");
            out.println("        if (arr == null) return null;");
            out.println("        int[][] copy = new int[arr.length][];");
            out.println("        for (int i = 0; i < arr.length; i++) {");
            out.println("            copy[i] = arr[i].clone();");
            out.println("        }");
            out.println("        return copy;");
            out.println("    }");
            out.println("}");
        }
    }

    private record FrameEntry(String name, String hash) {}
    private record AtlasSource(String resourcePath, String treePath) {}
}
