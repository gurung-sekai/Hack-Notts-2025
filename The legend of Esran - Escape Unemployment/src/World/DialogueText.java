package World;

import World.ui.UiPalette;
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
import java.awt.Stroke;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility helpers for rendering text and frames in an Undertale-inspired style.
 */
public final class DialogueText {

    private static final String FONT_RESOURCE = "resources/Font/ThaleahFat.ttf";
    private static final Stroke FRAME_STROKE = new BasicStroke(2.6f);

    private static volatile Font baseFont;
    private static final Font FALLBACK_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 18);

    private DialogueText() {
    }

    public static Font font(float size) {
        Font font = ensureBaseFont();
        return font.deriveFont(size);
    }

    public static void apply(Graphics2D g, float size) {
        if (g == null) {
            return;
        }
        Font derived = font(size);
        g.setFont(derived);
        g.setColor(UiPalette.TEXT_PRIMARY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

    public static void paintFrame(Graphics2D g, Rectangle bounds, int arc) {
        if (g == null || bounds == null) {
            return;
        }

        Color originalColor = g.getColor();
        Stroke originalStroke = g.getStroke();

        int arcSize = Math.max(0, arc);
        int width = Math.max(0, bounds.width);
        int height = Math.max(0, bounds.height);

        g.setColor(UiPalette.FRAME_BACKGROUND);
        g.fillRoundRect(bounds.x, bounds.y, width, height, arcSize, arcSize);

        g.setColor(UiPalette.FRAME_BORDER_OUTER);
        g.setStroke(FRAME_STROKE);
        g.drawRoundRect(bounds.x + 1, bounds.y + 1,
                Math.max(0, width - 2), Math.max(0, height - 2),
                Math.max(0, arcSize - 2), Math.max(0, arcSize - 2));

        g.setColor(UiPalette.FRAME_BORDER_INNER);
        g.drawRoundRect(bounds.x + 2, bounds.y + 2,
                Math.max(0, width - 4), Math.max(0, height - 4),
                Math.max(0, arcSize - 4), Math.max(0, arcSize - 4));

        g.setStroke(originalStroke);
        g.setColor(originalColor);
    }

    public static void drawString(Graphics2D g, String text, int x, int y) {
        if (g == null || text == null || text.isEmpty()) {
            return;
        }
        drawString(g, text, x, y, UiPalette.TEXT_PRIMARY);
    }

    public static int drawParagraph(Graphics2D g, String text, int x, int y, int width) {
        if (g == null || text == null || text.isBlank()) {
            return y;
        }

        FontMetrics metrics = g.getFontMetrics();
        if (metrics == null) {
            return y;
        }

        int lineHeight = metrics.getHeight();
        int baseline = y;
        boolean firstLine = true;
        int bulletWidth = metrics.stringWidth("• ");
        if (bulletWidth <= 0) {
            bulletWidth = Math.max(metrics.charWidth(' ') * 2, metrics.getHeight() / 2);
        }

        String remaining = text.trim();
        while (!remaining.isEmpty()) {
            int available = Math.max(0, width - bulletWidth);

            int breakPos = computeBreakPosition(metrics, remaining, available);
            String lineText = remaining.substring(0, breakPos).stripTrailing();
            boolean drew = false;
            if (!lineText.isEmpty()) {
                if (firstLine) {
                    drawString(g, "•", x, baseline, UiPalette.TEXT_ACCENT);
                }
                drawString(g, lineText, x + bulletWidth, baseline, UiPalette.TEXT_PRIMARY);
                drew = true;
            }
            baseline += lineHeight;
            if (drew) {
                firstLine = false;
            }
            remaining = remaining.substring(breakPos).stripLeading();
        }

        return baseline;
    }

    public static void drawString(Graphics2D g, String text, int x, int y, Color color) {
        if (g == null || text == null || text.isEmpty()) {
            return;
        }
        Color original = g.getColor();
        g.setColor(UiPalette.TEXT_SHADOW);
        g.drawString(text, x + 2, y + 2);
        g.setColor(color == null ? UiPalette.TEXT_PRIMARY : color);
        g.drawString(text, x, y);
        g.setColor(original);
    }

    private static int computeBreakPosition(FontMetrics metrics, String text, int availableWidth) {
        if (text.isBlank()) {
            return text.length();
        }

        int newline = text.indexOf('\n');
        if (newline == 0) {
            return 1;
        } else if (newline > 0) {
            return newline;
        }

        if (availableWidth <= 0 || metrics.stringWidth(text) <= availableWidth) {
            return text.length();
        }

        int breakPos = text.length();
        int lastWhitespace = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                lastWhitespace = i;
            }
            int width = metrics.stringWidth(text.substring(0, i + 1));
            if (width > availableWidth) {
                breakPos = lastWhitespace >= 0 ? lastWhitespace : i;
                break;
            }
        }

        if (breakPos <= 0) {
            breakPos = Math.min(text.length(), 1);
        }

        while (breakPos < text.length() && Character.isWhitespace(text.charAt(breakPos))) {
            breakPos++;
        }

        return Math.min(breakPos, text.length());
    }

    private static Font ensureBaseFont() {
        Font cached = baseFont;
        if (cached != null) {
            return cached;
        }

        synchronized (DialogueText.class) {
            if (baseFont == null) {
                baseFont = loadBaseFont();
            }
            return baseFont;
        }
    }

    private static Font loadBaseFont() {
        try (InputStream in = ResourceLoader.open(FONT_RESOURCE)) {
            if (in != null) {
                Font loaded = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
                return loaded;
            }
        } catch (FontFormatException | IOException ex) {
            // Fall through to the fallback font below.
        }
        return FALLBACK_FONT;
    }
}
