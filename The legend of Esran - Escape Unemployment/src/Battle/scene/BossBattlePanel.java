package Battle.scene;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class BossBattlePanel extends JPanel {

    // -----------------------
    // Fighter definitions
    // -----------------------
    public enum BossKind { BIG_ZOMBIE, OGRE_WARLORD, SKELETON_LORD, PUMPKIN_KING, IMP_OVERLORD, WIZARD_ARCHON }

    public static BossBattlePanel create(BossKind kind, Runnable onEnd) {
        Fighter hero = new Fighter("Hero", 120, 30, Color.CYAN);
        Fighter boss;
        switch (kind) {
            case OGRE_WARLORD -> boss = new Fighter("Ogre Warlord", 180, 40, Color.RED);
            case SKELETON_LORD -> boss = new Fighter("Skeleton Lord", 180, 40, Color.WHITE);
            case PUMPKIN_KING -> boss = new Fighter("Pumpkin King", 180, 40, Color.ORANGE);
            case IMP_OVERLORD -> boss = new Fighter("Imp Overlord", 180, 40, Color.MAGENTA);
            case WIZARD_ARCHON -> boss = new Fighter("Archon", 180, 40, Color.BLUE);
            default -> boss = new Fighter("Dread Husk", 180, 40, Color.GRAY);
        }
        return new BossBattlePanel(hero, boss, onEnd);
    }

    public static class Fighter {
        public final String name;
        public int hp, hpMax;
        public int mp, mpMax;
        public Color color;
        public int spriteFrame = 0;
        public Fighter(String name, int hp, int mp, Color color) {
            this.name = name;
            this.hp = this.hpMax = hp;
            this.mp = this.mpMax = mp;
            this.color = color;
        }
    }

    private enum Phase { PLAYER_SELECT, PLAYER_ATTACK, ENEMY_ATTACK, WIN, GAME_OVER }
    private Phase phase = Phase.PLAYER_SELECT;

    private final Fighter hero;
    private final Fighter boss;
    private final Runnable onEnd;
    private final String[] cmds = {"Fight", "Guard", "Item"};
    private int cmdIndex = 0;
    private final List<String> dmgs = new ArrayList<>();
    private int animTick = 0;

    // -----------------------
    // Constructor
    // -----------------------
    public BossBattlePanel(Fighter hero, Fighter boss, Runnable onEnd) {
        this.hero = hero;
        this.boss = boss;
        this.onEnd = onEnd;

        setPreferredSize(new Dimension(800, 480));
        setBackground(Color.BLACK);

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (phase == Phase.PLAYER_SELECT) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> { cmdIndex = (cmdIndex + cmds.length - 1) % cmds.length; repaint(); }
                        case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> { cmdIndex = (cmdIndex + 1) % cmds.length; repaint(); }
                        case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> chooseCommand();
                    }
                } else if ((phase == Phase.GAME_OVER || phase == Phase.WIN) && onEnd != null) {
                    onEnd.run();
                }
            }
        });

        new javax.swing.Timer(1000 / 60, e -> tick()).start();
    }

    private void tick() {
        animTick++;
        hero.spriteFrame = (animTick / 10) % 4;
        boss.spriteFrame = (animTick / 15) % 4;
        repaint();
    }

    private void chooseCommand() {
        switch (cmdIndex) {
            case 0 -> playerAttack();
            case 1 -> guard();
            case 2 -> itemHeal();
        }
    }

    private void playerAttack() {
        phase = Phase.PLAYER_ATTACK;
        boss.hp = Math.max(0, boss.hp - 25);
        dmgs.add("-25");
        if (boss.hp <= 0) phase = Phase.WIN;
        else phase = Phase.ENEMY_ATTACK;
    }

    private void guard() {
        dmgs.add("Guard");
        phase = Phase.ENEMY_ATTACK;
    }

    private void itemHeal() {
        hero.hp = Math.min(hero.hpMax, hero.hp + 20);
        dmgs.add("+20");
        phase = Phase.ENEMY_ATTACK;
    }

    private void enemyTurn() {
        int dmg = 15;
        hero.hp = Math.max(0, hero.hp - dmg);
        dmgs.add("-" + dmg);
        if (hero.hp <= 0) phase = Phase.GAME_OVER;
        else phase = Phase.PLAYER_SELECT;
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // -----------------------
        // Draw arena (simple tiles)
        // -----------------------
        int tileSize = 40;
        g.setColor(Color.DARK_GRAY);
        for (int x = 0; x < getWidth(); x += tileSize) {
            for (int y = 0; y < getHeight(); y += tileSize) {
                g.fillRect(x, y, tileSize - 2, tileSize - 2);
            }
        }

        // -----------------------
        // Draw fighters
        // -----------------------
        drawFighter(g, hero, 100, getHeight() - 150);
        drawFighter(g, boss, getWidth() - 200, 100);

        // -----------------------
        // Draw UI: HP, commands
        // -----------------------
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString(hero.name + " HP: " + hero.hp + "/" + hero.hpMax, 20, getHeight() - 20);
        g.drawString(boss.name + " HP: " + boss.hp + "/" + boss.hpMax, getWidth() - 180, 40);

        g.drawString("Commands:", getWidth() - 300, getHeight() - 120);
        for (int i = 0; i < cmds.length; i++) {
            g.drawString((i == cmdIndex ? "> " : "") + cmds[i], getWidth() - 300, getHeight() - 100 + i * 20);
        }

        // Floating damage
        int y = 100;
        for (String d : dmgs) {
            g.drawString(d, getWidth() / 2, y);
            y += 20;
        }

        // Win / Lose messages
        if (phase == Phase.GAME_OVER) {
            g.setFont(g.getFont().deriveFont(36f));
            g.drawString("GAME OVER", getWidth() / 2 - 100, getHeight() / 2);
        } else if (phase == Phase.WIN) {
            g.setFont(g.getFont().deriveFont(36f));
            g.drawString("VICTORY!", getWidth() / 2 - 100, getHeight() / 2);
        }

        // Handle enemy attack animation
        if (phase == Phase.ENEMY_ATTACK) {
            enemyTurn();
        }
    }

    private void drawFighter(Graphics g, Fighter f, int x, int y) {
        // Animated rectangle to simulate a sprite
        g.setColor(f.color);
        int size = 50 + (f.spriteFrame % 2) * 5; // simple "bobbing" animation
        g.fillRect(x, y, size, size);
    }

    public static void main(String[] args) {
        JFrame f = new JFrame("Boss Battle");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(BossBattlePanel.create(BossKind.OGRE_WARLORD, () -> System.out.println("Battle ended")));
        f.pack();
        f.setVisible(true);
    }
}
