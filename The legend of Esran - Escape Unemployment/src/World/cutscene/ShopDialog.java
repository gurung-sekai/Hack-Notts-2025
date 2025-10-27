package World.cutscene;

import World.UndertaleText;
import gfx.HiDpiScaler;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Random;

/**
 * Animated shop overlay that lets the player trade coins for healing items.
 */
public final class ShopDialog extends JDialog {

    public record Result(int remainingCoins, int resultingHp, String closingRemark) { }

    private int coins;
    private int hp;
    private final int maxHp;
    private final JLabel coinsLabel = new JLabel();
    private final JLabel hpLabel = new JLabel();
    private final DialogueBubble dialogue = new DialogueBubble();
    private final Timer timer;
    private long tick = 0;
    private final double uiScale;
    private String closingRemark;

    private ShopDialog(Window owner, int coins, int hp, int maxHp) {
        super(owner, "Shop", ModalityType.APPLICATION_MODAL);
        this.coins = coins;
        this.hp = hp;
        this.maxHp = maxHp;
        this.uiScale = computeUiScale(owner);
        this.closingRemark = "The shopkeeper nods appreciatively.";

        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(new Color(0, 0, 0, 0));
        ShopPanel panel = new ShopPanel(owner);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);

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
        });
    }

    public static Result showShop(Window owner, int coins, int hp, int maxHp) {
        ShopDialog dialog = new ShopDialog(owner, coins, hp, maxHp);
        dialog.setVisible(true);
        return new Result(dialog.coins, dialog.hp, dialog.closingRemark);
    }

    private class ShopPanel extends JPanel {
        private final AnimatedBackdrop background = CutsceneBackgrounds.shopGlow();
        private final int preferredWidth;
        private final int preferredHeight;

        ShopPanel(Window owner) {
            setOpaque(false);
            Dimension size = scaledSize(owner);
            this.preferredWidth = size.width;
            this.preferredHeight = size.height;
            setPreferredSize(size);
            setLayout(new BorderLayout());

            JPanel info = new JPanel(new GridLayout(2, 1));
            info.setOpaque(false);
            coinsLabel.setForeground(new Color(255, 236, 160));
            coinsLabel.setFont(UndertaleText.font(scaledFont(18f)));
            hpLabel.setForeground(new Color(200, 230, 255));
            hpLabel.setFont(UndertaleText.font(scaledFont(18f)));
            info.add(coinsLabel);
            info.add(hpLabel);
            add(info, BorderLayout.NORTH);

            JPanel shopKeeper = new JPanel(new BorderLayout());
            shopKeeper.setOpaque(false);
            shopKeeper.add(buildPortrait(), BorderLayout.WEST);

            dialogue.setPreferredSize(new Dimension(scaleToInt(320), scaleToInt(160)));
            dialogue.setOpaque(false);
            updateDialogue("Welcome, traveler! Trade your coins for my restorative draughts.");
            shopKeeper.add(dialogue, BorderLayout.CENTER);
            add(shopKeeper, BorderLayout.CENTER);

            JPanel actions = new JPanel();
            actions.setOpaque(false);
            actions.setLayout(new GridLayout(0, 1, scaleToInt(8), scaleToInt(8)));
            for (ShopItem item : ShopItem.values()) {
                JButton button = new JButton(item.label());
                styleButton(button, scaledFont(15f));
                button.addActionListener(e -> attemptPurchase(item));
                actions.add(button);
            }
            add(actions, BorderLayout.EAST);

            JButton leave = new JButton("Return to the dungeon");
            styleButton(leave, scaledFont(15f));
            leave.addActionListener(ShopDialog.this::closeShop);
            add(leave, BorderLayout.SOUTH);

            updateLabels();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                background.paint(g2, getWidth(), getHeight(), tick);
                int margin = scaleToInt(12);
                int arc = scaleToInt(32);
                g2.setColor(new Color(8, 16, 26, 150));
                g2.fillRoundRect(margin, margin, getWidth() - margin * 2, getHeight() - margin * 2, arc, arc);
                g2.setColor(new Color(255, 220, 150, 190));
                g2.setStroke(new BasicStroke(Math.max(2f, (float) scaleToInt(2))));
                g2.drawRoundRect(margin, margin, getWidth() - margin * 2, getHeight() - margin * 2, arc, arc);
                drawVignette(g2);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(preferredWidth, preferredHeight);
        }

        private JLabel buildPortrait() {
            BufferedImage portraitImage = CutscenePortrait.SHOPKEEPER.image();
            int target = scaleToInt(200);
            if (portraitImage != null) {
                portraitImage = HiDpiScaler.scale(portraitImage, target, target);
            }
            JLabel portrait = new JLabel(portraitImage == null ? null : new ImageIcon(portraitImage));
            portrait.setOpaque(false);
            int pad = scaleToInt(12);
            portrait.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
            return portrait;
        }

        private void drawVignette(Graphics2D g2) {
            int margin = scaleToInt(18);
            int w = getWidth();
            int h = getHeight();
            Paint old = g2.getPaint();
            g2.setPaint(new GradientPaint(0, margin, new Color(0, 0, 0, 140), 0, h / 2f, new Color(0, 0, 0, 20)));
            g2.fillRect(margin, margin, w - margin * 2, h / 2);
            g2.setPaint(new GradientPaint(0, h - margin, new Color(0, 0, 0, 180), 0, h / 2f, new Color(0, 0, 0, 0)));
            g2.fillRect(margin, h / 2, w - margin * 2, h / 2 - margin);
            g2.setPaint(old);
        }

        private Dimension scaledSize(Window owner) {
            double scale = uiScale;
            if (owner != null) {
                Dimension ownerSize = owner.getSize();
                if (ownerSize != null && ownerSize.height > 0) {
                    double ownerScale = Math.max(1.0, ownerSize.height / 720.0);
                    scale = Math.max(scale, Math.min(ownerScale, uiScale * 1.3));
                }
            }
            int width = Math.max(1, (int) Math.round(580 * scale));
            int height = Math.max(1, (int) Math.round(380 * scale));
            return new Dimension(width, height);
        }
    }

    private void closeShop(ActionEvent event) {
        updateDialogue("Safe travels, hero.");
        dispose();
    }

    private void attemptPurchase(ShopItem item) {
        if (coins < item.cost) {
            updateDialogue("You'll need " + (item.cost - coins) + " more coins for that elixir.");
            return;
        }
        coins -= item.cost;
        int healAmount = item.heal >= maxHp ? maxHp : item.heal;
        if (hp >= maxHp && healAmount >= maxHp) {
            updateDialogue("You're already brimming with vitality!");
            coins += item.cost;
            updateLabels();
            return;
        }
        hp = Math.min(maxHp, hp + healAmount);
        updateDialogue(item.response);
        updateLabels();
    }

    private void updateLabels() {
        coinsLabel.setText(String.format("COINS: %d", coins).toUpperCase(Locale.ENGLISH));
        hpLabel.setText(String.format("HEALTH: %d / %d", hp, maxHp).toUpperCase(Locale.ENGLISH));
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
        button.setForeground(new Color(250, 240, 220));
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

    private enum ShopItem {
        SMALL("Small Flask (+2 HP) - 8 coins", 8, 2, "A gentle warmth rushes through you."),
        GRAND("Grand Brew (+4 HP) - 18 coins", 18, 4, "May your steps grow lighter."),
        ROYAL("Royal Panacea (Full heal) - 32 coins", 32, Integer.MAX_VALUE, "A royal remedy worthy of legends!");

        private final String label;
        private final int cost;
        private final int heal;
        private final String response;

        ShopItem(String label, int cost, int heal, String response) {
            this.label = label;
            this.cost = cost;
            this.heal = heal;
            this.response = response;
        }

        public String label() {
            return label;
        }
    }

    private static double computeUiScale(Window owner) {
        GraphicsConfiguration config = owner == null ? null : owner.getGraphicsConfiguration();
        if (config != null) {
            AffineTransform tx = config.getDefaultTransform();
            double scale = Math.max(tx.getScaleX(), tx.getScaleY());
            if (Double.isFinite(scale) && scale > 1.05) {
                return Math.min(scale, 1.9);
            }
        }
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        double base = 1.0;
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
        return base;
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
                    g2.setColor(new Color(255, 255, 255, 230));
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
                Color base = pressed ? new Color(18, 18, 28) : new Color(26, 26, 40);
                if (hover && !pressed) {
                    base = base.brighter();
                }
                g2.setColor(base);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.setColor(new Color(255, 255, 255, hover ? 250 : 220));
                g2.setStroke(new BasicStroke(2.4f));
                g2.drawRoundRect(1, 1, w - 2, h - 2, arc - 2, arc - 2);
                g2.setColor(new Color(0, 0, 0, 140));
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
                UndertaleText.drawString(g2, text == null ? "" : text.toUpperCase(Locale.ENGLISH),
                        textRect.x, baseline);
            } finally {
                g2.dispose();
            }
        }
    }
}
