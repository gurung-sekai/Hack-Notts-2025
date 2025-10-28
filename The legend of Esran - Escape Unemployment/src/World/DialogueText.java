package World;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Utility helpers for rendering text and frames in an Undertale-inspired style.
 */
public final class DialogueText {

    private DialogueText() {
    }

    public static Font font(float size) {
        return UndertaleText.font(size);
    }

    public static void apply(Graphics2D g, float size) {
        UndertaleText.apply(g, size);
    }

    public static void paintFrame(Graphics2D g, Rectangle bounds, int arc) {
        UndertaleText.paintFrame(g, bounds, arc);
    }

    public static void drawString(Graphics2D g, String text, int x, int y) {
        UndertaleText.drawString(g, text, x, y);
    }

    public static int drawParagraph(Graphics2D g, String text, int x, int y, int width) {
        return UndertaleText.drawParagraph(g, text, x, y, width);
    }
}
