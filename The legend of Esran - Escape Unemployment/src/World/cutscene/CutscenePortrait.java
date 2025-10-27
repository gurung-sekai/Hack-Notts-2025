package World.cutscene;

import gfx.HiDpiScaler;
import util.ResourceLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Provides lightly stylised portraits for cutscene characters.
 */
public enum CutscenePortrait {
    HERO("resources/sprites/Knight/Idle/knight_m_idle_anim_f0.png", 180),
    GOLDEN_KNIGHT("resources/bosses/goldenKnight/GN7.png", 220),
    PRINCESS(null, 180),
    SHOPKEEPER("resources/sprites/Wizard/wizzard_m_idle_anim_f0.png", 180);

    private static final Map<CutscenePortrait, BufferedImage> CACHE = new EnumMap<>(CutscenePortrait.class);

    private final String resourcePath;
    private final int targetSize;

    CutscenePortrait(String resourcePath, int targetSize) {
        this.resourcePath = resourcePath;
        this.targetSize = targetSize;
    }

    public BufferedImage image() {
        return CACHE.computeIfAbsent(this, CutscenePortrait::loadPortrait);
    }

    private static BufferedImage loadPortrait(CutscenePortrait portrait) {
        if (portrait.resourcePath == null) {
            return generatePrincessPortrait(portrait.targetSize);
        }
        try (InputStream in = ResourceLoader.open(portrait.resourcePath)) {
            if (in != null) {
                BufferedImage raw = ImageIO.read(in);
                if (raw != null) {
                    return HiDpiScaler.scale(raw, portrait.targetSize, portrait.targetSize);
                }
            }
        } catch (IOException ignored) {
        }
        return generateFallback(portrait.name(), portrait.targetSize);
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
