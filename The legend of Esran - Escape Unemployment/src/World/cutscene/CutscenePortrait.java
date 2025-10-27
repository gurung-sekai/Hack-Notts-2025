package World.cutscene;

import gfx.HiDpiScaler;
import util.ResourceLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides lightly stylised portraits for cutscene characters.
 */
public enum CutscenePortrait {
    HERO("resources/sprites/Knight/Idle/knight_m_idle_anim_f0.png", "/resources/Cutscene/Hero", 200),
    GOLDEN_KNIGHT("resources/bosses/goldenKnight/GN7.png", "/resources/Cutscene/GoldenKnight", 240),
    PRINCESS(null, "/resources/Princess", 220),
    SHOPKEEPER("resources/sprites/Wizard/wizzard_m_idle_anim_f0.png", "/resources/Shop", 200);

    private static final Map<CutscenePortrait, BufferedImage> CACHE = new EnumMap<>(CutscenePortrait.class);

    private final String resourcePath;
    private final String directoryPath;
    private final int targetSize;

    CutscenePortrait(String resourcePath, String directoryPath, int targetSize) {
        this.resourcePath = resourcePath;
        this.directoryPath = directoryPath;
        this.targetSize = targetSize;
    }

    public BufferedImage image() {
        return CACHE.computeIfAbsent(this, CutscenePortrait::loadPortrait);
    }

    private static BufferedImage loadPortrait(CutscenePortrait portrait) {
        BufferedImage fromDirectory = loadFromDirectory(portrait.directoryPath, portrait.targetSize);
        if (fromDirectory != null) {
            return fromDirectory;
        }

        BufferedImage fromResource = loadFromResource(portrait.resourcePath, portrait.targetSize);
        if (fromResource != null) {
            return fromResource;
        }

        if (portrait == PRINCESS) {
            return generatePrincessPortrait(portrait.targetSize);
        }
        return generateFallback(portrait.name(), portrait.targetSize);
    }

    private static BufferedImage loadFromResource(String resourcePath, int targetSize) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        try (InputStream in = ResourceLoader.open(resourcePath)) {
            if (in == null) {
                return null;
            }
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) {
                return null;
            }
            return scalePortrait(raw, targetSize);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static BufferedImage loadFromDirectory(String directory, int targetSize) {
        List<String> candidates = listPortraitCandidates(directory);
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator
                .comparingInt(CutscenePortrait::portraitScore).reversed()
                .thenComparing(String::length)
                .thenComparing(String::compareTo));
        for (String path : candidates) {
            try {
                BufferedImage raw = ResourceLoader.image(path);
                if (raw != null) {
                    return scalePortrait(raw, targetSize);
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static List<String> listPortraitCandidates(String directory) {
        List<String> results = new ArrayList<>();
        if (directory == null || directory.isBlank()) {
            return results;
        }
        String normalized = ensureLeadingSlash(directory);
        results.addAll(ResourceLoader.listPng(normalized));
        for (String child : ResourceLoader.listDirectories(normalized)) {
            results.addAll(ResourceLoader.listPng(child));
        }
        return results;
    }

    private static String ensureLeadingSlash(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static int portraitScore(String path) {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int score = 0;
        if (value.contains("portrait")) score += 6;
        if (value.contains("face")) score += 4;
        if (value.contains("princess") || value.contains("hero")) score += 2;
        if (value.contains("shop")) score += 2;
        if (value.contains("idle")) score += 1;
        if (value.contains("background") || value.contains("bg") || value.contains("scene")) score -= 6;
        if (value.contains("full")) score -= 1;
        return score;
    }

    private static BufferedImage scalePortrait(BufferedImage raw, int targetSize) {
        if (raw == null) {
            return null;
        }
        int max = Math.max(raw.getWidth(), raw.getHeight());
        if (max <= 0) {
            return raw;
        }
        double scale = targetSize / (double) max;
        int width = Math.max(1, (int) Math.round(raw.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(raw.getHeight() * scale));
        BufferedImage scaled = HiDpiScaler.scale(raw, width, height);
        return scaled == null ? raw : scaled;
    }

    private static BufferedImage generatePrincessPortrait(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint dress = new GradientPaint(0, 0, new Color(255, 210, 230),
                    0, size, new Color(220, 160, 220));
            g.setPaint(dress);
            g.fillRoundRect(size / 6, size / 3, size * 2 / 3, size * 2 / 3, size / 5, size / 5);
            g.setColor(new Color(255, 235, 180));
            g.fillOval(size / 4, size / 6, size / 2, size / 2);
            g.setColor(new Color(150, 110, 40));
            g.fillArc(size / 4 - size / 10, size / 8, size * 7 / 10, size / 2, 0, 180);
            g.setColor(new Color(255, 240, 120));
            Polygon crown = new Polygon();
            crown.addPoint(size / 2, size / 10);
            crown.addPoint(size / 2 + size / 10, size / 5);
            crown.addPoint(size / 2 + size / 4, size / 5);
            crown.addPoint(size / 2, size / 3);
            crown.addPoint(size / 2 - size / 4, size / 5);
            crown.addPoint(size / 2 - size / 10, size / 5);
            g.fillPolygon(crown);
            g.setColor(new Color(255, 255, 255, 180));
            g.fillOval(size / 2 - size / 12, size / 2, size / 6, size / 6);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static BufferedImage generateFallback(String label, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(50, 60, 80));
            g.fillRoundRect(0, 0, size, size, size / 6, size / 6);
            g.setColor(new Color(240, 240, 255));
            g.setFont(g.getFont().deriveFont(Font.BOLD, size / 6f));
            String text = label.substring(0, Math.min(3, label.length()));
            FontMetrics fm = g.getFontMetrics();
            int tx = (size - fm.stringWidth(text)) / 2;
            int ty = (size + fm.getAscent()) / 2 - fm.getDescent();
            g.drawString(text, tx, ty);
        } finally {
            g.dispose();
        }
        return img;
    }
}
