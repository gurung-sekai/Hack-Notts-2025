package World.cutscene;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
    private final JTextArea dialogue = new JTextArea();
    private final Timer timer;
    private long tick = 0;

    private ShopDialog(Window owner, int coins, int hp, int maxHp) {
        super(owner, "Shop", ModalityType.APPLICATION_MODAL);
        this.coins = coins;
        this.hp = hp;
        this.maxHp = maxHp;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        ShopPanel panel = new ShopPanel();
        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);

        timer = new Timer(1000 / 60, e -> {
            tick++;
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
        return new Result(dialog.coins, dialog.hp, dialog.dialogue.getText());
    }

    private class ShopPanel extends JPanel {
        ShopPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(560, 360));
            setLayout(new BorderLayout());

            JPanel info = new JPanel(new GridLayout(2, 1));
            info.setOpaque(false);
            coinsLabel.setForeground(new Color(255, 236, 160));
            coinsLabel.setFont(coinsLabel.getFont().deriveFont(Font.BOLD, 18f));
            hpLabel.setForeground(new Color(200, 230, 255));
            hpLabel.setFont(hpLabel.getFont().deriveFont(Font.BOLD, 18f));
            info.add(coinsLabel);
            info.add(hpLabel);
            add(info, BorderLayout.NORTH);

            JPanel shopKeeper = new JPanel(new BorderLayout());
            shopKeeper.setOpaque(false);
            JLabel portrait = new JLabel(new ImageIcon(CutscenePortrait.SHOPKEEPER.image()));
            shopKeeper.add(portrait, BorderLayout.WEST);

            dialogue.setLineWrap(true);
            dialogue.setWrapStyleWord(true);
            dialogue.setEditable(false);
            dialogue.setOpaque(false);
            dialogue.setFont(dialogue.getFont().deriveFont(16f));
            dialogue.setForeground(new Color(255, 250, 230));
            dialogue.setText("Welcome, traveler! Trade your coins for my restorative draughts.");
            shopKeeper.add(dialogue, BorderLayout.CENTER);
            add(shopKeeper, BorderLayout.CENTER);

            JPanel actions = new JPanel();
            actions.setOpaque(false);
            actions.setLayout(new GridLayout(0, 1, 8, 8));
            for (ShopItem item : ShopItem.values()) {
                JButton button = new JButton(item.label());
                button.setFocusPainted(false);
                button.setBackground(new Color(40, 60, 84));
                button.setForeground(new Color(240, 240, 255));
                button.addActionListener(e -> attemptPurchase(item));
                actions.add(button);
            }
            add(actions, BorderLayout.EAST);

            JButton leave = new JButton("Leave shop");
            leave.setFocusPainted(false);
            leave.addActionListener(e -> dispose());
            add(leave, BorderLayout.SOUTH);

            updateLabels();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                CutsceneBackgrounds.shopGlow().paint(g2, getWidth(), getHeight(), tick);
                g2.setColor(new Color(12, 22, 32, 160));
                g2.fillRoundRect(10, 10, getWidth() - 20, getHeight() - 20, 28, 28);
                g2.setColor(new Color(255, 220, 150, 200));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(10, 10, getWidth() - 20, getHeight() - 20, 28, 28);
            } finally {
                g2.dispose();
            }
        }
    }

    private void attemptPurchase(ShopItem item) {
        if (coins < item.cost) {
            dialogue.setText("You'll need " + (item.cost - coins) + " more coins for that elixir.");
            return;
        }
        coins -= item.cost;
        int healAmount = item.heal >= maxHp ? maxHp : item.heal;
        if (hp >= maxHp && healAmount >= maxHp) {
            dialogue.setText("You're already brimming with vitality!");
            coins += item.cost;
            updateLabels();
            return;
        }
        hp = Math.min(maxHp, hp + healAmount);
        dialogue.setText(item.response);
        updateLabels();
    }

    private void updateLabels() {
        coinsLabel.setText("Coins: " + coins);
        hpLabel.setText("Health: " + hp + " / " + maxHp);
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
}
