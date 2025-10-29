package World.ui;

import java.awt.Color;

/**
 * Centralised colour palette for high-contrast UI text and chrome.
 */
public final class UiPalette {

    public static final Color TEXT_PRIMARY = new Color(252, 248, 244);
    public static final Color TEXT_MUTED = new Color(210, 214, 228);
    public static final Color TEXT_ACCENT = new Color(255, 214, 120);
    public static final Color TEXT_SHADOW = new Color(20, 18, 30, 210);

    public static final Color FRAME_BACKGROUND = new Color(18, 22, 38, 235);
    public static final Color FRAME_BORDER_OUTER = new Color(255, 222, 160, 240);
    public static final Color FRAME_BORDER_INNER = new Color(0, 0, 0, 170);

    public static final Color CUTSCENE_BOX_FILL = new Color(14, 18, 32, 230);
    public static final Color CUTSCENE_BORDER = new Color(255, 225, 170, 225);
    public static final Color CUTSCENE_NAME = new Color(255, 236, 190);

    public static final Color SHOP_PANEL_FILL = new Color(10, 16, 28, 228);
    public static final Color SHOP_PANEL_BORDER = new Color(255, 222, 180, 220);
    public static final Color SHOP_VIGNETTE_TOP = new Color(0, 0, 0, 180);
    public static final Color SHOP_VIGNETTE_BOTTOM = new Color(0, 0, 0, 210);
    public static final Color SHOP_VIGNETTE_FADE = new Color(0, 0, 0, 40);
    public static final Color SHOP_HINT = new Color(222, 232, 255, 220);
    public static final Color SHOP_COINS = new Color(255, 222, 128);
    public static final Color SHOP_HP = new Color(190, 240, 255);
    public static final Color SHOP_CURSOR = new Color(255, 240, 210, 235);

    public static final Color BUTTON_BASE = new Color(36, 32, 56);
    public static final Color BUTTON_BASE_PRESSED = new Color(26, 24, 42);
    public static final Color BUTTON_BORDER_GLOW = new Color(255, 244, 220, 235);
    public static final Color BUTTON_BORDER_GLOW_HOVER = new Color(255, 255, 255, 250);
    public static final Color BUTTON_INNER_SHADOW = new Color(0, 0, 0, 155);

    private UiPalette() {
    }
}
