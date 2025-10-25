package Battle.swing;

import Battle.core.*;
import Battle.domain.*;
import Battle.util.Rng;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class BattlePanel extends JPanel {
    // Model
    private final Fighter hero;
    private final Fighter foe;
    private final BattleSwingController ctrl;

    // UI state
    private enum Phase { MENU, ANIM, MESSAGE, OVER }
    private Phase phase = Phase.MENU;
    private int selected = 0;               // 0..3
    private String message = "A duel begins!";
    private int animTick = 0;               // for tweening/shake

    // Sprites (can be images later)
    private final Sprite heroSprite = Sprite.placeholder(new Color(60,170,255), 96, 96);
    private final Sprite foeSprite  = Sprite.placeholder(new Color(255,110,120), 96, 96);

    private final Timer loop;

    // When battle ends, invoke this callback (winnerName)
    private final Consumer<String> onEnd;

    public BattlePanel(Fighter hero, Fighter foe, Consumer<String> onEnd) {
        this.hero = hero;
        this.foe = foe;
        this.ctrl = new BattleSwingController(hero, foe);
        this.onEnd = onEnd;

        setPreferredSize(new Dimension(720, 420));
        setBackground(new Color(14, 44, 56));

        setFocusable(true);
        addKeyListener(new Input());

        // 60 FPS game loop
        loop = new Timer(1000 / 60, e -> tick());
        loop.start();
    }

    private void tick() {
        if (phase == Phase.ANIM) {
            animTick++;
            if (animTick >= 28) { // animation length
                animTick = 0;
                phase = Phase.MESSAGE;
            }
        }
        repaint();
    }

    // ---- Input handling ----
    private class Input extends KeyAdapter {
        @Override public void keyPressed(KeyEvent e) {
            if (phase == Phase.MENU) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> selected = (selected + 3) % 4;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> selected = (selected + 1) % 4;
                    case KeyEvent.VK_UP, KeyEvent.VK_W -> selected = (selected + 2) % 4;   // swap rows
                    case KeyEvent.VK_DOWN, KeyEvent.VK_S -> selected = (selected + 2) % 4;
                    case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> beginRound();
                }
            } else if (phase == Phase.MESSAGE) {
                // any key continues after message
                if (!ctrl.isOver()) { phase = Phase.MENU; message = "Choose a technique."; }
                else {
                    phase = Phase.OVER;
                    if (onEnd != null) onEnd.accept(ctrl.getWinnerName());
                }
            } else if (phase == Phase.OVER) {
                // ESC or Enter to exit battle scene
                if (onEnd != null) onEnd.accept(ctrl.getWinnerName());
            }
        }
    }

    private void beginRound() {
        // resolve a full round and return battle log text
        message = ctrl.playerTurn(selected);
        phase = Phase.ANIM;   // play a quick tween before showing log text
        animTick = 0;
    }

    // ---- Rendering ----
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D gg = (Graphics2D) g;
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background "arena"
        drawArena(gg);

        // positions
        int heroX = 120, heroY = 240;
        int foeX  = getWidth() - 120 - 96, foeY = 120;

        // animations: slight lunge/shake
        if (phase == Phase.ANIM) {
            double t = animTick / 28.0;
            int lunge = (int)(Math.sin(Math.PI * t) * 20);
            // crude: attacker lunges; alternate to add variety
            if (Rng.roll(50)) heroX += lunge; else foeX -= lunge;
            int shake = (animTick % 4 < 2) ? 2 : -2;
            foeY += shake; heroY -= shake;
        }

        // sprites
        heroSprite.draw(gg, heroX, heroY);
        foeSprite.draw(gg,  foeX,  foeY);

        // HUDs
        drawHud(gg);

        // Menu box or message box
        if (phase == Phase.MENU) drawMenu(gg);
        else drawMessage(gg);
    }

    private void drawArena(Graphics2D gg) {
        int w = getWidth(), h = getHeight();
        // gradient sky
        var grad = new GradientPaint(0,0, new Color(24,72,88), 0,h, new Color(10,30,38));
        gg.setPaint(grad);
        gg.fillRect(0,0,w,h);
        // ground ellipses
        gg.setColor(new Color(20,60,70));
        gg.fillOval(60, 280, 240, 60);
        gg.fillOval(w-300, 160, 240, 60);
    }

    private void drawHud(Graphics2D gg) {
        // foe HUD (top-right)
        drawHpBox(gg, foe.name, foe.hp, foe.base.hp, ctrl.getMomentum() < 0 ? "-" + Math.abs(ctrl.getMomentum()) : "", getWidth() - 320, 20);
        // hero HUD (bottom-left)
        drawHpBox(gg, hero.name, hero.hp, hero.base.hp, ctrl.getMomentum() > 0 ? "+" + ctrl.getMomentum() : "", 20, getHeight() - 160);
    }

    private void drawHpBox(Graphics2D gg, String name, int hp, int max, String mom, int x, int y) {
        gg.setColor(new Color(8,26,32));
        gg.fillRoundRect(x, y, 300, 90, 16, 16);
        gg.setColor(new Color(64,164,180));
        gg.drawRoundRect(x, y, 300, 90, 16, 16);
        gg.setFont(getFont().deriveFont(Font.BOLD, 16f));
        gg.drawString(name, x+12, y+22);
        if (!mom.isEmpty()) gg.drawString("Momentum " + mom, x+180, y+22);
        // HP bar
        int bw = 260, bh = 12;
        gg.setColor(new Color(48, 80, 90));
        gg.fillRect(x+20, y+40, bw, bh);
        double p = Math.max(0, Math.min(1.0, (double)hp / Math.max(1, max)));
        gg.setColor(new Color(120, 230, 120));
        gg.fillRect(x+20, y+40, (int)(bw*p), bh);
        gg.setColor(new Color(220, 255, 220));
        gg.setFont(getFont().deriveFont(12f));
        gg.drawString(hp + " / " + max, x+20, y+64);
    }

    private void drawMenu(Graphics2D gg) {
        int x = getWidth() - 360, y = getHeight() - 140, w = 340, h = 120;
        gg.setColor(new Color(8,26,32));
        gg.fillRoundRect(x, y, w, h, 18, 18);
        gg.setColor(new Color(64,164,180));
        gg.drawRoundRect(x, y, w, h, 18, 18);

        gg.setFont(getFont().deriveFont(Font.BOLD, 16f));
        String[] names = {
                Battle.domain.BaseMoves.MOVES[0].name,
                Battle.domain.BaseMoves.MOVES[1].name,
                Battle.domain.BaseMoves.MOVES[2].name,
                Battle.domain.BaseMoves.MOVES[3].name
        };
        for (int i=0;i<4;i++) {
            int cx = x + 20 + (i%2)*160;
            int cy = y + 32 + (i/2)*40;
            if (i == selected) {
                gg.setColor(new Color(40,120,140));
                gg.fillRoundRect(cx-8, cy-18, 150, 28, 10,10);
                gg.setColor(Color.WHITE);
            } else {
                gg.setColor(new Color(210, 240, 245));
            }
            gg.drawString((i+1)+") "+names[i], cx, cy);
        }
        gg.setFont(getFont().deriveFont(12f));
        gg.setColor(new Color(200,230,235));
        gg.drawString("←/→/↑/↓ to choose, Enter to confirm", x+20, y+h-14);
    }

    private void drawMessage(Graphics2D gg) {
        int x = 20, y = getHeight() - 140, w = 660, h = 120;
        gg.setColor(new Color(8,26,32));
        gg.fillRoundRect(x, y, w, h, 18, 18);
        gg.setColor(new Color(64,164,180));
        gg.drawRoundRect(x, y, w, h, 18, 18);
        gg.setFont(getFont().deriveFont(Font.PLAIN, 16f));
        gg.setColor(new Color(220,240,245));
        drawWrapped(gg, message, x+16, y+28, w-32, 18);
        gg.setFont(getFont().deriveFont(12f));
        gg.drawString(ctrl.isOver()? "Battle over. Press any key." : "Press any key…", x+16, y+h-12);
    }

    private void drawWrapped(Graphics2D gg, String text, int x, int y, int wrap, int lh) {
        String[] words = text.split("\\s+");
        String line = "";
        for (String w : words) {
            String tryLine = line.isEmpty()? w : line + " " + w;
            if (gg.getFontMetrics().stringWidth(tryLine) > wrap) {
                gg.drawString(line, x, y); y += lh; line = w;
            } else line = tryLine;
        }
        if (!line.isEmpty()) gg.drawString(line, x, y);
    }
}
