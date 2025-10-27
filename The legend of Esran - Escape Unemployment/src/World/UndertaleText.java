package World;

import util.ResourceLoader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Utility helpers for rendering text and frames in an Undertale-inspired style.
 */
public final class UndertaleText {

    private static final Color BORDER = new Color(255, 255, 255);
    private static final Color FILL = new Color(0, 0, 0, 210);
    private static final Color TEXT = new Color(255, 255, 255);
    private static final Color SHADOW = new Color(0, 0, 0, 190);
    private static volatile Font baseFont;

    private UndertaleText() {
    }

    private static Font baseFont() {
        Font font = baseFont;
        if (font != null) {
            return font;
        }
        synchronized (UndertaleText.class) {
            if (baseFont != null) {
                return baseFont;
            }
            try (InputStream in = ResourceLoader.open("resources/Font/ThaleahFat.ttf")) {
                if (in != null) {
                    Font loaded = Font.createFont(Font.TRUETYPE_FONT, in);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
                    baseFont = loaded;
                }
            } catch (FontFormatException | IOException ignored) {
                // fall back to platform default
            }
            if (baseFont == null) {
                baseFont = new Font(Font.MONOSPACED, Font.BOLD, 18);
            }
            return baseFont;
        }
    }

    public static Font font(float size) {
        return baseFont().deriveFont(size);
    }

    public static void apply(Graphics2D g, float size) {
        if (g == null) {
            return;
        }
        g.setFont(font(size));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(TEXT);
    }

    public static void paintFrame(Graphics2D g, Rectangle bounds, int arc) {
        if (g == null || bounds == null) {
            return;
        }
        Color oldColour = g.getColor();
        java.awt.Stroke oldStroke = g.getStroke();
        g.setColor(FILL);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
        g.setColor(oldColour);
        g.setStroke(oldStroke);
    }

    public static void drawString(Graphics2D g, String text, int x, int y) {
        if (g == null || text == null || text.isBlank()) {
            return;
        }
        String rendered = text;
        Color old = g.getColor();
        g.setColor(SHADOW);
        g.drawString(rendered, x + 2, y + 2);
        g.setColor(TEXT);
        g.drawString(rendered, x, y);
        g.setColor(old);
    }

    public static int drawParagraph(Graphics2D g, String text, int x, int y, int width) {
        if (g == null || text == null || text.isBlank()) {
            return y;
        }
        FontMetrics fm = g.getFontMetrics();
        String remaining = text.replace('\r', ' ').trim();
        boolean first = true;
        int cursorY = y;
        while (!remaining.isEmpty()) {
            int available = width <= 0 ? Integer.MAX_VALUE : width - fm.stringWidth(first ? "* " : "  ");
            int len = remaining.length();
            while (len > 0 && fm.stringWidth(remaining.substring(0, len)) > available) {
                len--;
            }
            if (len <= 0) {
                break;
            }
            String line = remaining.substring(0, len).trim();
            if (len < remaining.length()) {
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace > 0) {
                    line = line.substring(0, lastSpace);
                    len = lastSpace + 1;
                }
            }
            String prefix = first ? "* " : "  ";
            drawString(g, (prefix + line).toUpperCase(Locale.ENGLISH), x, cursorY);
            cursorY += fm.getHeight();
            remaining = remaining.substring(Math.min(len, remaining.length())).trim();
            first = false;
        }
        return cursorY;
    }
}
