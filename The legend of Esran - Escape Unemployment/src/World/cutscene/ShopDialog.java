package World.cutscene;

import World.DialogueText;
import World.UndertaleText;
import World.ui.UiPalette;
import gfx.HiDpiScaler;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Animated shop overlay that lets the player trade coins for healing items.
 */
public final class ShopDialog extends JDialog {

    private static final int VITALITY_STEP = 2;
    private static final int DUNGEON_STEP = 1;

    public record Result(int remainingCoins,
                         int resultingHp,
                         int vitalityLevel,
                         int dungeonLevel,
                         int resultingMaxHp,
                         String closingRemark) { }

    private int coins;
    private int hp;
    private final int baseMaxHp;
    private int vitalityLevel;
    private int dungeonLevel;
    private final int vitalityCap;
    private final int dungeonCap;
    private final JLabel coinsLabel = new JLabel();
    private final JLabel hpLabel = new JLabel();
    private final DialogueBubble dialogue = new DialogueBubble();
    private final Timer timer;
    private long tick = 0;
    private final double uiScale;
    private final Rectangle viewport;
    private String closingRemark;

    private ShopDialog(Window owner,
                       int coins,
                       int hp,
                       int baseMaxHp,
                       int vitalityLevel,
                       int dungeonLevel,
                       int vitalityCap,
                       int dungeonCap) {
        super(owner, "Shop", ModalityType.APPLICATION_MODAL);
        this.viewport = preferredBounds(owner);
        this.coins = coins;
        this.baseMaxHp = Math.max(1, baseMaxHp);
        this.vitalityLevel = Math.max(0, vitalityLevel);
        this.dungeonLevel = Math.max(0, dungeonLevel);
        this.vitalityCap = Math.max(0, vitalityCap);
        this.dungeonCap = Math.max(0, dungeonCap);
        this.hp = Math.min(currentMaxHp(), Math.max(0, hp));
        this.uiScale = computeUiScale(owner, viewport.getSize());
        this.closingRemark = "The shopkeeper nods appreciatively.";

        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(new Color(0, 0, 0, 0));
        ShopPanel panel = new ShopPanel(viewport.getSize());
        setContentPane(panel);
        pack();
        setBounds(viewport);
        getRootPane().setDefaultButton(panel.leaveButton);
        SwingUtilities.invokeLater(panel::focusFirstControl);

        getRootPane().registerKeyboardAction(this::closeShop,
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        timer = new Timer(1000 / 60, e -> {
            tick++;
            dialogue.advanceTick(tick);
            panel.repaint();
        });
        timer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                timer.stop();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                panel.focusFirstControl();
            }
        });
    }

    public static Result showShop(Window owner,
                                  int coins,
                                  int hp,
                                  int baseMaxHp,
                                  int vitalityLevel,
                                  int dungeonLevel,
                                  int vitalityCap,
                                  int dungeonCap) {
        ShopDialog dialog = new ShopDialog(owner, coins, hp, baseMaxHp, vitalityLevel, dungeonLevel, vitalityCap, dungeonCap);
        dialog.setVisible(true);
        return new Result(dialog.coins, dialog.hp, dialog.vitalityLevel, dialog.dungeonLevel, dialog.currentMaxHp(), dialog.closingRemark);
    }

    private class ShopPanel extends JPanel {
        private final AnimatedBackdrop background = CutsceneBackgrounds.shopGlow();
        private final Dimension preferredSize;
        private final JPanel content;
        private final java.util.List<JButton> purchaseButtons = new java.util.ArrayList<>();
        private final JButton leaveButton;

        ShopPanel(Dimension viewportSize) {
            setOpaque(false);
            this.preferredSize = viewportSize == null ? new Dimension(960, 640) : new Dimension(viewportSize);
            setPreferredSize(preferredSize);
            setMinimumSize(preferredSize);
            setMaximumSize(preferredSize);
            setLayout(new GridBagLayout());

            content = buildContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1;
            gbc.weighty = 1;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.CENTER;
            add(content, gbc);

            this.leaveButton = locateLeaveButton(content);
            updateLabels();
        }

        private JPanel buildContent() {
            JPanel wrapper = new JPanel(new BorderLayout(scaleToInt(32), scaleToInt(36)));
            wrapper.setOpaque(false);
            wrapper.setBorder(BorderFactory.createEmptyBorder(scaleToInt(56), scaleToInt(72),
                    scaleToInt(64), scaleToInt(72)));

            JPanel info = new JPanel(new GridLayout(1, 2, scaleToInt(32), 0));
            info.setOpaque(false);
            coinsLabel.setForeground(UiPalette.SHOP_COINS);
            coinsLabel.setFont(UndertaleText.font(scaledFont(22f)));
            coinsLabel.setHorizontalAlignment(SwingConstants.LEFT);
            hpLabel.setForeground(UiPalette.SHOP_HP);
            hpLabel.setFont(UndertaleText.font(scaledFont(22f)));
            hpLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            info.add(coinsLabel);
            info.add(hpLabel);
            wrapper.add(info, BorderLayout.NORTH);

            JPanel center = new JPanel(new BorderLayout(scaleToInt(28), scaleToInt(28)));
            center.setOpaque(false);
            center.add(buildPortrait(), BorderLayout.WEST);

            dialogue.setPreferredSize(new Dimension(scaleToInt(560), scaleToInt(240)));
            dialogue.setOpaque(false);
            updateDialogue("Welcome, traveler! Trade your coins for my restorative draughts.");
            center.add(dialogue, BorderLayout.CENTER);
            wrapper.add(center, BorderLayout.CENTER);

            JPanel actions = new JPanel();
            actions.setOpaque(false);
            actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
            for (ShopItem item : ShopItem.values()) {
                JButton button = new JButton(item.label());
                styleButton(button, scaledFont(19f));
                button.setAlignmentX(Component.LEFT_ALIGNMENT);
                button.addActionListener(e -> attemptPurchase(item));
                actions.add(button);
                actions.add(Box.createVerticalStrut(scaleToInt(14)));
                purchaseButtons.add(button);
            }
            if (actions.getComponentCount() > 0) {
                actions.remove(actions.getComponentCount() - 1);
            }
            actions.setBorder(BorderFactory.createEmptyBorder(scaleToInt(8), 0, 0, scaleToInt(12)));
            wrapper.add(actions, BorderLayout.EAST);

            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);

            JLabel hint = new JLabel("Press ESC to leave the shop");
            hint.setOpaque(false);
            hint.setForeground(UiPalette.SHOP_HINT);
            hint.setFont(UndertaleText.font(scaledFont(16f)));
            hint.setBorder(BorderFactory.createEmptyBorder(scaleToInt(12), 0, 0, 0));
            footer.add(hint, BorderLayout.WEST);

            JButton leave = new JButton("Return to the dungeon");
            styleButton(leave, scaledFont(19f));
            leave.addActionListener(ShopDialog.this::closeShop);
            leave.setAlignmentX(Component.RIGHT_ALIGNMENT);
            JPanel leaveWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, scaleToInt(16), 0));
            leaveWrapper.setOpaque(false);
            leaveWrapper.add(leave);
            footer.add(leaveWrapper, BorderLayout.EAST);

            wrapper.add(footer, BorderLayout.SOUTH);
            wrapper.setMaximumSize(new Dimension(scaleToInt(1200), scaleToInt(680)));
            return wrapper;
        }

        private JButton locateLeaveButton(Container container) {
            for (Component component : container.getComponents()) {
                if (component instanceof JButton button && "Return to the dungeon".equals(button.getText())) {
                    return button;
                }
                if (component instanceof Container nested) {
                    JButton nestedResult = locateLeaveButton(nested);
                    if (nestedResult != null) {
                        return nestedResult;
                    }
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                background.paint(g2, getWidth(), getHeight(), tick);
                Rectangle bounds = content.getBounds();
                int pad = scaleToInt(28);
                int arc = scaleToInt(52);
                Rectangle frame = new Rectangle(Math.max(0, bounds.x - pad),
                        Math.max(0, bounds.y - pad),
                        Math.min(getWidth(), bounds.width + pad * 2),
                        Math.min(getHeight(), bounds.height + pad * 2));
                g2.setComposite(AlphaComposite.SrcOver.derive(0.82f));
                g2.setColor(UiPalette.SHOP_PANEL_FILL);
                g2.fillRoundRect(frame.x, frame.y, frame.width, frame.height, arc, arc);
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(UiPalette.SHOP_PANEL_BORDER);
                g2.setStroke(new BasicStroke(Math.max(3f, (float) scaleToInt(3))));
                g2.drawRoundRect(frame.x, frame.y, frame.width, frame.height, arc, arc);
                drawVignette(g2);
            } finally {
                g2.dispose();
            }
        }

        void focusFirstControl() {
            if (!purchaseButtons.isEmpty()) {
                purchaseButtons.get(0).requestFocusInWindow();
            } else if (leaveButton != null) {
                leaveButton.requestFocusInWindow();
            }
        }

        private JLabel buildPortrait() {
            BufferedImage portraitImage = CutscenePortrait.SHOPKEEPER.image();
            int target = scaleToInt(260);
            if (portraitImage != null) {
                portraitImage = HiDpiScaler.scale(portraitImage, target, target);
            }
            JLabel portrait = new JLabel(portraitImage == null ? null : new ImageIcon(portraitImage));
            portrait.setOpaque(false);
            int pad = scaleToInt(16);
            portrait.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
            return portrait;
        }

        private void drawVignette(Graphics2D g2) {
            int margin = scaleToInt(36);
            int w = getWidth();
            int h = getHeight();
            Paint old = g2.getPaint();
            g2.setPaint(new GradientPaint(0, margin, UiPalette.SHOP_VIGNETTE_TOP, 0, h / 2f, UiPalette.SHOP_VIGNETTE_FADE));
            g2.fillRect(margin, margin, w - margin * 2, h / 2);
            g2.setPaint(new GradientPaint(0, h - margin, UiPalette.SHOP_VIGNETTE_BOTTOM, 0, h / 2f, UiPalette.SHOP_VIGNETTE_FADE));
            g2.fillRect(margin, h / 2, w - margin * 2, h / 2 - margin);
            g2.setPaint(old);
        }
    }

    private void closeShop(ActionEvent event) {
        updateDialogue("Safe travels, hero.");
        dispose();
    }

    private void attemptPurchase(ShopItem item) {
        if (item == null) {
            return;
        }
        int maxHp = currentMaxHp();
        switch (item.type) {
            case HEAL -> {
                if (coins < item.cost) {
                    updateDialogue("You'll need " + (item.cost - coins) + " more coins for that elixir.");
                    break;
                }
                int healAmount = item.magnitude >= maxHp ? maxHp : item.magnitude;
                if (hp >= maxHp && healAmount >= maxHp) {
                    updateDialogue("You're already brimming with vitality!");
                    break;
                }
                coins -= item.cost;
                hp = Math.min(maxHp, hp + healAmount);
                updateDialogue(item.response);
            }
            case VITALITY_UPGRADE -> {
                if (vitalityLevel >= vitalityCap) {
                    updateDialogue("Your spirit cannot channel any more sigils.");
                    break;
                }
                if (coins < item.cost) {
                    updateDialogue("You'll need " + (item.cost - coins) + " more coins for that sigil.");
                    break;
                }
                coins -= item.cost;
                vitalityLevel++;
                hp = currentMaxHp();
                updateDialogue(item.response);
            }
            case DUNGEON_UPGRADE -> {
                if (dungeonLevel >= dungeonCap) {
                    updateDialogue("Your armor cannot bear more wards.");
                    break;
                }
                if (coins < item.cost) {
                    updateDialogue("You'll need " + (item.cost - coins) + " more coins for that ward.");
                    break;
                }
                coins -= item.cost;
                dungeonLevel++;
                hp = currentMaxHp();
                updateDialogue(item.response);
            }
        }
        updateLabels();
    }

    private void updateLabels() {
        coinsLabel.setText(String.format("COINS: %d", coins).toUpperCase(Locale.ENGLISH));
        hpLabel.setText(String.format("HEALTH: %d / %d   VIT %d/%d   DUN %d/%d",
                hp,
                currentMaxHp(),
                vitalityLevel,
                Math.max(1, vitalityCap),
                dungeonLevel,
                Math.max(1, dungeonCap)).toUpperCase(Locale.ENGLISH));
    }

    private int currentMaxHp() {
        return baseMaxHp + vitalityLevel * VITALITY_STEP + dungeonLevel * DUNGEON_STEP;
    }

    private void updateDialogue(String text) {
        dialogue.setMessage(text);
        closingRemark = text;
    }

    private void styleButton(JButton button, float fontSize) {
        button.setUI(new DungeonButtonUI());
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setForeground(UiPalette.TEXT_PRIMARY);
        button.setFont(UndertaleText.font(fontSize));
        int pad = scaleToInt(10);
        button.setBorder(BorderFactory.createEmptyBorder(pad, pad * 2, pad, pad * 2));
        button.setHorizontalAlignment(SwingConstants.LEFT);
    }

    private int scaleToInt(int value) {
        return Math.max(1, (int) Math.round(value * uiScale));
    }

    private float scaledFont(float size) {
        return (float) (size * uiScale);
    }

    private enum ShopItemType { HEAL, VITALITY_UPGRADE, DUNGEON_UPGRADE }

    private enum ShopItem {
        SMALL("Small Flask (+2 HP) - 8 coins", 8, 2, ShopItemType.HEAL, "A gentle warmth rushes through you."),
        GRAND("Grand Brew (+4 HP) - 18 coins", 18, 4, ShopItemType.HEAL, "May your steps grow lighter."),
        ROYAL("Royal Panacea (Full heal) - 32 coins", 32, Integer.MAX_VALUE, ShopItemType.HEAL, "A royal remedy worthy of legends!"),
        VITALITY("Vitality Sigil (+2 Max HP) - 40 coins", 40, VITALITY_STEP, ShopItemType.VITALITY_UPGRADE, "Your heart swells with renewed vigor."),
        BULWARK("Bulwark Ward (+1 Dungeon Heart) - 24 coins", 24, DUNGEON_STEP, ShopItemType.DUNGEON_UPGRADE, "Stonebound resilience surrounds you.");

        private final String label;
        private final int cost;
        private final int magnitude;
        private final ShopItemType type;
        private final String response;

        ShopItem(String label, int cost, int magnitude, ShopItemType type, String response) {
            this.label = label;
            this.cost = cost;
            this.magnitude = magnitude;
            this.type = type;
            this.response = response;
        }

        public String label() {
            return label;
        }
    }

    private static double computeUiScale(Window owner, Dimension viewport) {
        GraphicsConfiguration config = owner == null ? null : owner.getGraphicsConfiguration();
        if (config != null) {
            AffineTransform tx = config.getDefaultTransform();
            double scale = Math.max(tx.getScaleX(), tx.getScaleY());
            if (Double.isFinite(scale) && scale > 1.05) {
                return Math.min(scale, 2.1);
            }
        }
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        double base = 1.0;
        double viewportScale = 1.0;
        if (viewport != null && viewport.height > 0) {
            viewportScale = Math.max(1.0, viewport.height / 720.0);
        }
        if (screen != null) {
            double height = screen.getHeight();
            if (height >= 2160) {
                base = 1.85;
            } else if (height >= 1600) {
                base = 1.6;
            } else if (height >= 1440) {
                base = 1.45;
            } else if (height >= 1200) {
                base = 1.25;
            } else if (height >= 1080) {
                base = 1.12;
            }
            try {
                int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
                if (dpi > 110) {
                    base = Math.max(base, Math.min(1.9, dpi / 96.0));
                }
            } catch (HeadlessException ignored) {
                // fall through
            }
        }
        return Math.max(base, Math.min(2.3, viewportScale));
    }

    private Rectangle preferredBounds(Window owner) {
        GraphicsConfiguration configuration = owner == null
                ? GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
                : owner.getGraphicsConfiguration();
        Rectangle screenBounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        int width = Math.max(900, screenBounds.width - insets.left - insets.right);
        int height = Math.max(600, screenBounds.height - insets.top - insets.bottom);
        return new Rectangle(screenBounds.x + insets.left,
                screenBounds.y + insets.top,
                width,
                height);
    }

    private final class DialogueBubble extends JComponent {
        private String message = "";
        private int revealedCharacters = 0;
        private boolean cursorVisible = false;
        private long lastAdvanceTick = 0;

        DialogueBubble() {
            setOpaque(false);
        }

        void setMessage(String text) {
            message = text == null ? "" : text.replace('\n', ' ').trim();
            revealedCharacters = 0;
            cursorVisible = false;
            lastAdvanceTick = 0;
            revalidate();
            repaint();
        }

        void advanceTick(long globalTick) {
            if (message.isEmpty()) {
                return;
            }
            if (revealedCharacters < message.length()) {
                if ((globalTick - lastAdvanceTick) >= 2) {
                    revealedCharacters = Math.min(message.length(), revealedCharacters + 1);
                    lastAdvanceTick = globalTick;
                    if (revealedCharacters == message.length()) {
                        cursorVisible = true;
                        lastAdvanceTick = globalTick;
                    }
                    repaint();
                }
            } else if ((globalTick - lastAdvanceTick) >= 20) {
                cursorVisible = !cursorVisible;
                lastAdvanceTick = globalTick;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                int pad = scaleToInt(12);
                Rectangle bounds = new Rectangle(pad, pad, Math.max(0, getWidth() - pad * 2),
                        Math.max(0, getHeight() - pad * 2));
                int arc = scaleToInt(32);
                UndertaleText.paintFrame(g2, bounds, arc);
                UndertaleText.apply(g2, scaledFont(18f));
                FontMetrics fm = g2.getFontMetrics();
                int textX = bounds.x + scaleToInt(18);
                int textY = bounds.y + fm.getAscent() + scaleToInt(12);
                int width = Math.max(0, bounds.width - scaleToInt(36));
                String display = revealedCharacters >= message.length()
                        ? message
                        : message.substring(0, Math.min(revealedCharacters, message.length()));
                UndertaleText.drawParagraph(g2, display, textX, textY, width);
                if (cursorVisible && revealedCharacters >= message.length()) {
                    int cursorSize = scaleToInt(10);
                    int cursorX = bounds.x + bounds.width - scaleToInt(28);
                    int cursorY = bounds.y + bounds.height - scaleToInt(18);
                    g2.setColor(UiPalette.SHOP_CURSOR);
                    int[] xs = {cursorX, cursorX + cursorSize, cursorX + cursorSize / 2};
                    int[] ys = {cursorY, cursorY, cursorY - cursorSize};
                    g2.fillPolygon(xs, ys, 3);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class DungeonButtonUI extends BasicButtonUI {
        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton button = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = c.getWidth();
                int h = c.getHeight();
                int arc = Math.max(16, h / 2);
                boolean pressed = button.getModel().isPressed();
                boolean hover = button.getModel().isRollover();
                Color base = pressed ? UiPalette.BUTTON_BASE_PRESSED : UiPalette.BUTTON_BASE;
                if (hover && !pressed) {
                    base = base.brighter();
                }
                g2.setColor(base);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                Color border = hover ? UiPalette.BUTTON_BORDER_GLOW_HOVER : UiPalette.BUTTON_BORDER_GLOW;
                g2.setColor(border);
                g2.setStroke(new BasicStroke(2.4f));
                g2.drawRoundRect(1, 1, w - 2, h - 2, arc - 2, arc - 2);
                g2.setColor(UiPalette.BUTTON_INNER_SHADOW);
                g2.drawRoundRect(2, 2, w - 4, h - 4, arc - 4, arc - 4);
            } finally {
                g2.dispose();
            }
            super.paint(g, c);
        }

        @Override
        protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                int baseline = textRect.y + g2.getFontMetrics().getAscent();
                AbstractButton button = (AbstractButton) c;
                boolean enabled = button.isEnabled();
                Color color = enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_MUTED;
                DialogueText.drawString(g2, text == null ? "" : text.toUpperCase(Locale.ENGLISH),
                        textRect.x, baseline, color);
            } finally {
                g2.dispose();
            }
        }
    }
}
