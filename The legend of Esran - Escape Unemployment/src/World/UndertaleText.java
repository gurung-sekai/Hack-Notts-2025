package World;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * @deprecated kept for backwards compatibility. Prefer {@link DialogueText}.
 */
@Deprecated(forRemoval = false)
public final class UndertaleText {

    private UndertaleText() {
    }

    public static Font font(float size) {
        return DialogueText.font(size);
    }

    public static void apply(Graphics2D g, float size) {
        DialogueText.apply(g, size);
    }

    public static void paintFrame(Graphics2D g, Rectangle bounds, int arc) {
        DialogueText.paintFrame(g, bounds, arc);
    }

    public static void drawString(Graphics2D g, String text, int x, int y) {
        DialogueText.drawString(g, text, x, y);
    }

    public static int drawParagraph(Graphics2D g, String text, int x, int y, int width) {
        return DialogueText.drawParagraph(g, text, x, y, width);
    }
}
