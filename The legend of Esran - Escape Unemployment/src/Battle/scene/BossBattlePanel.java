package Battle.scene;

import Battle.core.DamageCalc;
import Battle.core.SimpleAi;
import Battle.domain.*;
import Battle.util.Rng;
import fx.Effect;
import fx.FXLibrary;
import gfx.AnimatedSprite;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import util.ResourceLoader;

/**
 * Boss battle swing panel featuring textured fighters, screen-space FX, and the shared battle ruleset.
 */
public class BossBattlePanel extends JPanel {

    private static final double BASE_WIDTH = 960.0;
    private static final double BASE_HEIGHT = 540.0;
    private static final double SPRITE_SCALE_BOOST = 1.5;
    private static final double MESSAGE_LIFETIME = 3.5;

    public enum BossKind {
        BIG_ZOMBIE,
        OGRE_WARLORD,
        SKELETON_LORD,
        PUMPKIN_KING,
        IMP_OVERLORD,
        WIZARD_ARCHON,
        GOLLUM,
        GRIM,
        FIRE_FLINGER,
        GOLD_MECH,
        GOLDEN_KNIGHT,
        PURPLE_EMPRESS,
        THE_WELCH,
        TOXIC_TREE
    }
    public enum Outcome { HERO_WIN, HERO_LOSS }

    public static BossBattlePanel create(BossKind kind, Consumer<Outcome> onEnd) {
        HeroDefinition hero = HeroDefinition.defaultHero();
        BossDefinition boss = BossDefinition.of(kind);
        return new BossBattlePanel(hero, boss, onEnd);
    }

    private enum Phase { PLAYER_SELECT, RESOLVING, WIN, GAME_OVER }

    private final Technique[] moves = BaseMoves.MOVES;
    private int cmdIndex = 0;
    private Phase phase = Phase.PLAYER_SELECT;
    private Outcome pendingOutcome;

    private final BattleEngine engine;
    private final FighterVisual heroVisual;
    private final FighterVisual bossVisual;
    private final List<Effect> effects = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<BattleMessage> battleMessages = new ArrayList<>();

    private final BufferedImage floorTile;

    private long lastTickNs = 0;
    private double resolveLock = 0.0;

    private BossBattlePanel(HeroDefinition heroDef, BossDefinition bossDef, Consumer<Outcome> onEnd) {
        Objects.requireNonNull(heroDef, "hero");
        Objects.requireNonNull(bossDef, "boss");

        setPreferredSize(new Dimension(960, 540));
        setBackground(Color.BLACK);
        setFocusable(true);

        int initialMomentum = Math.max(-2, Math.min(2, heroDef.openingMomentum - bossDef.momentumEdge));
        this.engine = new BattleEngine(heroDef.toFighter(), bossDef.toFighter(), initialMomentum, onEnd);
        this.heroVisual = new FighterVisual(engine.hero, heroDef.sprite, 280, 470, 3.2);
        this.bossVisual = new FighterVisual(engine.boss, bossDef.sprite, 700, 320, bossDef.scale);
        this.floorTile = loadImage("/resources/tiles/floor/floor_5.png");

        addMessage("A wild " + bossDef.displayName + " appears!");

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (phase != Phase.PLAYER_SELECT) return;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> {
                        cmdIndex = (cmdIndex + moves.length - 1) % moves.length;
                        repaint();
                    }
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> {
                        cmdIndex = (cmdIndex + 1) % moves.length;
                        repaint();
                    }
                    case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> chooseCommand();
                }
            }
        });

        new javax.swing.Timer(1000 / 60, e -> tick()).start();
    }

    private void chooseCommand() {
        RoundOutcome outcome = engine.resolve(cmdIndex);
        if (outcome == null) return;

        if (outcome.moveRefused) {
            Toolkit.getDefaultToolkit().beep();
            addMessage(outcome.refusalReason);
            return;
        }

        applyOutcome(outcome);
    }

    private void applyOutcome(RoundOutcome outcome) {
        addMessages(outcome.messages);

        LayoutMetrics metrics = layoutMetrics();

        for (Event event : outcome.events) {
            switch (event.type) {
                case DAMAGE -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = centerOf(target, metrics);
                        floatingTexts.add(FloatingText.damage("-" + event.value, p.x, p.y, event.critical));
                    }
                    spawnEffect(event, metrics);
                }
                case DOT -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = centerOf(target, metrics);
                        floatingTexts.add(FloatingText.dot("-" + event.value, p.x, p.y));
                    }
                }
                case HEAL -> {
                    FighterVisual actor = visualFor(event.actor);
                    if (actor != null) {
                        Point p = centerOf(actor, metrics);
                        floatingTexts.add(FloatingText.heal("+" + event.value, p.x, p.y));
                    }
                }
                case MISS -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = centerOf(target, metrics);
                        floatingTexts.add(FloatingText.miss(p.x, p.y));
                    }
                }
                case STATUS -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = centerOf(target, metrics);
                        floatingTexts.add(FloatingText.buff(event.message, p.x, p.y));
                    }
                }
                case GUARD -> {
                    FighterVisual actor = visualFor(event.actor);
                    if (actor != null) {
                        Point p = centerOf(actor, metrics);
                        floatingTexts.add(FloatingText.buff(event.message, p.x, p.y));
                    }
                }
                case INTERRUPT -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = centerOf(target, metrics);
                        floatingTexts.add(FloatingText.buff("Interrupted!", p.x, p.y));
                    }
                    spawnEffect(event, metrics);
                }
            }
        }

        if (engine.heroWon()) {
            phase = Phase.WIN;
            addMessage("Victory!");
            queueOutcome(Outcome.HERO_WIN);
        } else if (engine.heroLost()) {
            phase = Phase.GAME_OVER;
            addMessage("The hero has fallen...");
            queueOutcome(Outcome.HERO_LOSS);
        } else {
            phase = Phase.RESOLVING;
            resolveLock = 0.75;
        }
    }

    private void spawnEffect(Event event, LayoutMetrics metrics) {
        FighterVisual actor = visualFor(event.actor);
        FighterVisual target = visualFor(event.target);
        Technique tech = event.technique;
        if (tech == null) return;

        switch (tech.name) {
            case "Flame Lash" -> {
                if (actor != null && target != null) {
                    Point a = centerOf(actor, metrics);
                    Point t = centerOf(target, metrics);
                    double dir = (actor == heroVisual) ? 1 : -1;
                    effects.add(FXLibrary.fireBreath(a.x, a.y - 20, dir, 0));
                    effects.add(FXLibrary.fireHit(t.x, t.y - 30));
                }
            }
            case "Thorn Bind" -> {
                if (target != null) {
                    Point t = centerOf(target, metrics);
                    effects.add(FXLibrary.smokeLarge(t.x - 40, t.y - 40));
                }
            }
            case "Brace" -> {
                if (actor != null) {
                    Point a = centerOf(actor, metrics);
                    effects.add(FXLibrary.shieldBlockScreen(a.x, a.y));
                }
            }
            case "Disrupt Bolt" -> {
                if (target != null) {
                    Point t = centerOf(target, metrics);
                    effects.add(FXLibrary.thunderStrike(t.x - 40, t.y - 120));
                    effects.add(FXLibrary.thunderSplash(t.x - 60, t.y - 60));
                }
            }
        }
    }

    private FighterVisual visualFor(Fighter fighter) {
        if (fighter == null) return null;
        if (fighter == engine.hero) return heroVisual;
        if (fighter == engine.boss) return bossVisual;
        return null;
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = (lastTickNs == 0) ? 1 / 60.0 : (now - lastTickNs) / 1_000_000_000.0;
        lastTickNs = now;

        heroVisual.update(dt);
        bossVisual.update(dt);

        effects.forEach(fx -> fx.update(dt));
        effects.removeIf(Effect::dead);

        floatingTexts.forEach(ft -> ft.update(dt));
        floatingTexts.removeIf(FloatingText::dead);

        for (Iterator<BattleMessage> it = battleMessages.iterator(); it.hasNext(); ) {
            BattleMessage msg = it.next();
            msg.life -= dt;
            if (msg.life <= 0) {
                it.remove();
            }
        }

        if (phase == Phase.RESOLVING) {
            resolveLock -= dt;
            if (resolveLock <= 0) {
                phase = Phase.PLAYER_SELECT;
            }
        }

        repaint();

        if (pendingOutcome != null && engine.onEnd != null) {
            Outcome out = pendingOutcome;
            pendingOutcome = null;
            SwingUtilities.invokeLater(() -> engine.onEnd.accept(out));
        }
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        LayoutMetrics metrics = layoutMetrics();

        drawFloor(g2);
        drawEffectsBehind(g2);
        drawFighter(g2, heroVisual, false, metrics);
        drawFighter(g2, bossVisual, true, metrics);
        drawEffectsFront(g2);
        drawFloatingTexts(g2, metrics);
        drawHud(g2, metrics);
    }

    private LayoutMetrics layoutMetrics() {
        double scale = Math.min(getWidth() / BASE_WIDTH, getHeight() / BASE_HEIGHT);
        if (Double.isNaN(scale) || scale <= 0) {
            scale = 1.0;
        }
        double offsetX = (getWidth() - BASE_WIDTH * scale) / 2.0;
        double offsetY = (getHeight() - BASE_HEIGHT * scale) / 2.0;
        return new LayoutMetrics(scale, offsetX, offsetY);
    }

    private record LayoutMetrics(double scale, double offsetX, double offsetY) { }

    private void drawFloor(Graphics2D g2) {
        if (floorTile == null) return;
        int tileW = floorTile.getWidth();
        int tileH = floorTile.getHeight();
        for (int x = 0; x < getWidth(); x += tileW) {
            for (int y = 0; y < getHeight(); y += tileH) {
                g2.drawImage(floorTile, x, y, null);
            }
        }
    }

    private void drawEffectsBehind(Graphics2D g2) {
        for (Effect fx : effects) {
            fx.render(g2, 0, 0);
        }
    }

    private void drawEffectsFront(Graphics2D g2) {
        // world-space effects already rendered in drawEffectsBehind; we keep hook for layering if needed.
    }

    private void drawFloatingTexts(Graphics2D g2, LayoutMetrics metrics) {
        float fontSize = (float) Math.max(18.0, 18.0 * metrics.scale());
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
        for (FloatingText ft : floatingTexts) {
            g2.setColor(ft.color);
            g2.drawString(ft.text, (int) ft.x, (int) ft.y);
        }
    }

    private void drawFighter(Graphics2D g2, FighterVisual vis, boolean flip, LayoutMetrics metrics) {
        BufferedImage frame = vis.sprite.frame();
        Rectangle bounds = layoutFighter(vis, frame, metrics);
        if (frame == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        if (flip) {
            g2.drawImage(frame, bounds.x + bounds.width, bounds.y, bounds.x, bounds.y + bounds.height,
                    0, 0, frame.getWidth(), frame.getHeight(), null);
        } else {
            g2.drawImage(frame, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height,
                    0, 0, frame.getWidth(), frame.getHeight(), null);
        }
    }

    private Rectangle layoutFighter(FighterVisual vis, BufferedImage frame, LayoutMetrics metrics) {
        double scale = metrics.scale();
        double offsetX = metrics.offsetX();
        double offsetY = metrics.offsetY();
        int cx = (int) Math.round(offsetX + vis.footX * scale);
        int cy = (int) Math.round(offsetY + vis.footY * scale);
        if (frame == null) {
            vis.bounds.setBounds(cx, cy, 0, 0);
            return vis.bounds;
        }
        double spriteScale = vis.baseScale * scale * SPRITE_SCALE_BOOST;
        int w = Math.max(1, (int) Math.round(frame.getWidth() * spriteScale));
        int h = Math.max(1, (int) Math.round(frame.getHeight() * spriteScale));
        vis.bounds.setBounds(cx - w / 2, cy - h, w, h);
        return vis.bounds;
    }

    private Point centerOf(FighterVisual vis, LayoutMetrics metrics) {
        Rectangle bounds = layoutFighter(vis, vis.sprite.frame(), metrics);
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    private Point centerOf(FighterVisual vis) {
        return centerOf(vis, layoutMetrics());
    }

    private void drawHud(Graphics2D g2, LayoutMetrics metrics) {
        double scale = metrics.scale();
        int radius = (int) Math.round(18 * scale);
        Rectangle heroBox = new Rectangle(
                (int) Math.round(metrics.offsetX() + 32 * scale),
                (int) Math.round(metrics.offsetY() + 36 * scale),
                (int) Math.round(320 * scale),
                (int) Math.round(150 * scale));
        Rectangle bossBox = new Rectangle(
                getWidth() - heroBox.x - heroBox.width,
                heroBox.y,
                heroBox.width,
                heroBox.height);
        int maxAvailable = (int) Math.round(getWidth() - 2.0 * heroBox.x);
        if (maxAvailable <= 0) {
            maxAvailable = (int) Math.round(getWidth() * 0.9);
        }
        int commandsWidth = Math.max((int) Math.round(420 * scale),
                Math.min((int) Math.round(720 * scale), maxAvailable));
        int commandsHeight = (int) Math.max(120, Math.round(160 * scale));
        int commandsY = Math.max(heroBox.y + heroBox.height + radius,
                (int) Math.round(getHeight() - metrics.offsetY() - 60 * scale - commandsHeight));
        commandsY = Math.min(commandsY, getHeight() - commandsHeight - (int) Math.round(24 * scale));
        commandsY = Math.max(commandsY, heroBox.y + heroBox.height + radius);
        Rectangle commandsBox = new Rectangle(
                Math.max(0, (getWidth() - commandsWidth) / 2),
                commandsY,
                commandsWidth,
                commandsHeight);

        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(heroBox.x, heroBox.y, heroBox.width, heroBox.height, radius, radius);
        g2.fillRoundRect(bossBox.x, bossBox.y, bossBox.width, bossBox.height, radius, radius);
        g2.fillRoundRect(commandsBox.x, commandsBox.y, commandsBox.width, commandsBox.height, radius, radius);

        Font baseFont = g2.getFont();
        Font titleFont = baseFont.deriveFont(Font.BOLD, (float) Math.max(20.0, 22.0 * scale));
        Font infoFont = baseFont.deriveFont(Font.PLAIN, (float) Math.max(16.0, 18.0 * scale));
        Font commandFont = baseFont.deriveFont(Font.PLAIN, (float) Math.max(18.0, 20.0 * scale));
        Font smallFont = baseFont.deriveFont(Font.PLAIN, (float) Math.max(16.0, 17.0 * scale));

        drawFighterHud(g2, heroVisual, heroBox, scale, titleFont, infoFont);
        drawFighterHud(g2, bossVisual, bossBox, scale, titleFont, infoFont);
        drawCommands(g2, commandsBox, scale, commandFont, smallFont);
        drawMessages(g2, commandsBox, metrics);
    }

    private void drawFighterHud(Graphics2D g2, FighterVisual vis, Rectangle box, double scale,
                                Font titleFont, Font infoFont) {
        Fighter f = vis.fighter;
        int padding = (int) Math.round(18 * scale);
        g2.setColor(Color.WHITE);
        g2.setFont(titleFont);
        FontMetrics titleMetrics = g2.getFontMetrics();
        int nameBaseline = box.y + padding + titleMetrics.getAscent();
        g2.drawString(f.name, box.x + padding, nameBaseline);

        g2.setFont(infoFont);
        FontMetrics infoMetrics = g2.getFontMetrics();
        int barY = nameBaseline + infoMetrics.getDescent() + (int) Math.round(6 * scale);
        int barWidth = box.width - padding * 2;
        int barHeight = (int) Math.max(12, Math.round(20 * scale));
        double hpRatio = Math.max(0, (double) f.hp / f.base.hp);
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(box.x + padding, barY, barWidth, barHeight);
        g2.setColor(new Color(190, 50, 50));
        g2.fillRect(box.x + padding, barY, (int) Math.round(barWidth * hpRatio), barHeight);
        g2.setColor(Color.WHITE);
        g2.drawRect(box.x + padding, barY, barWidth, barHeight);

        int statsBaseline = barY + barHeight + infoMetrics.getAscent() + (int) Math.round(6 * scale);
        g2.drawString(f.hp + " / " + f.base.hp, box.x + padding, statsBaseline);
        g2.drawString("Status: " + f.status, box.x + padding, statsBaseline + infoMetrics.getHeight());
    }

    private void drawCommands(Graphics2D g2, Rectangle box, double scale, Font commandFont, Font smallFont) {
        int padding = (int) Math.round(20 * scale);
        g2.setFont(commandFont);
        FontMetrics commandMetrics = g2.getFontMetrics();
        int lineHeight = commandMetrics.getHeight();
        int y = box.y + padding + commandMetrics.getAscent();
        int x = box.x + padding;
        for (int i = 0; i < moves.length; i++) {
            Technique t = moves[i];
            boolean selected = (i == cmdIndex);
            int cooldown = engine.cooldownRemaining(engine.hero, t);
            String label = t.name + " (" + t.affinity + ")";
            if (cooldown > 0) {
                label += " — CD " + cooldown;
            }
            g2.setColor(selected ? new Color(255, 215, 0) : Color.LIGHT_GRAY);
            g2.drawString((selected ? "> " : "  ") + label, x, y + i * lineHeight);
        }

        g2.setFont(smallFont);
        g2.setColor(Color.WHITE);
        int infoY = box.y + box.height - padding;
        String momentum = "Momentum: " + engine.momentum;
        int momentumWidth = g2.getFontMetrics().stringWidth(momentum);
        g2.drawString(momentum, box.x + box.width - padding - momentumWidth, infoY);
        if (phase != Phase.PLAYER_SELECT) {
            g2.drawString("Resolving...", box.x + padding, infoY);
        }
    }

    private void drawMessages(Graphics2D g2, Rectangle commandsBox, LayoutMetrics metrics) {
        if (battleMessages.isEmpty()) {
            return;
        }
        double scale = metrics.scale();
        Font messageFont = g2.getFont().deriveFont(Font.BOLD, (float) Math.max(18.0, 20.0 * scale));
        g2.setFont(messageFont);
        FontMetrics fm = g2.getFontMetrics();
        int padding = (int) Math.round(16 * scale);
        int lineHeight = fm.getHeight();
        int width = battleMessages.stream()
                .mapToInt(msg -> fm.stringWidth(msg.text))
                .max()
                .orElse(0) + padding * 2;
        width = Math.min(width, Math.min((int) Math.round(700 * scale), getWidth() - padding * 2));
        int height = battleMessages.size() * lineHeight + padding * 2;
        int x = (getWidth() - width) / 2;
        int y = Math.max((int) Math.round(metrics.offsetY() + 24 * scale),
                commandsBox.y - height - (int) Math.round(20 * scale));

        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(x, y, width, height, (int) Math.round(24 * scale), (int) Math.round(24 * scale));

        int textY = y + padding + fm.getAscent();
        for (BattleMessage message : battleMessages) {
            float alpha = (float) Math.max(0.0, Math.min(1.0, message.life / message.totalLife));
            g2.setColor(new Color(255, 255, 255, Math.round(alpha * 255)));
            int textX = x + (width - fm.stringWidth(message.text)) / 2;
            g2.drawString(message.text, textX, textY);
            textY += lineHeight;
        }
    }

    private void addMessage(String msg) {
        if (msg == null) {
            return;
        }
        String trimmed = msg.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        battleMessages.add(new BattleMessage(trimmed, MESSAGE_LIFETIME));
        while (battleMessages.size() > 5) {
            battleMessages.remove(0);
        }
    }

    private void addMessages(List<String> msgs) {
        if (msgs == null) {
            return;
        }
        for (String s : msgs) {
            addMessage(s);
        }
    }

    private void queueOutcome(Outcome outcome) {
        if (pendingOutcome == null) {
            pendingOutcome = outcome;
        }
    }

    private static BufferedImage loadImage(String path) {
        try {
            return ResourceLoader.image(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load image: " + path, e);
        }
    }

    // ---------------------------------------------------------------------
    // Fighter visual wrapper
    // ---------------------------------------------------------------------
    private static class FighterVisual {
        final Fighter fighter;
        final AnimatedSprite sprite = new AnimatedSprite(0, 0);
        final double footX;
        final double footY;
        final double baseScale;
        final Rectangle bounds = new Rectangle();

        FighterVisual(Fighter fighter, SpriteSource spriteSource, double footX, double footY, double baseScale) {
            this.fighter = fighter;
            this.footX = footX;
            this.footY = footY;
            this.baseScale = baseScale;
            Objects.requireNonNull(spriteSource, "spriteSource").loadInto(sprite);
            sprite.setState(AnimatedSprite.State.IDLE);
            sprite.setFps(6.0);
        }

        void update(double dt) {
            sprite.update(dt);
        }
    }

    private interface SpriteSource {
        void loadInto(AnimatedSprite sprite);
    }

    private static SpriteSource prefixSource(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        return sprite -> sprite.addFromPrefix(AnimatedSprite.State.IDLE, prefix);
    }

    private static SpriteSource sheetSource(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        return sprite -> sprite.addFromSheet(AnimatedSprite.State.IDLE, resourcePath);
    }

    // ---------------------------------------------------------------------
    // Floating text
    // ---------------------------------------------------------------------
    private static class FloatingText {
        final String text;
        final Color color;
        double x, y;
        double life;
        final double vy;

        private FloatingText(String text, Color color, double x, double y, double vy, double life) {
            this.text = text;
            this.color = color;
            this.x = x;
            this.y = y;
            this.vy = vy;
            this.life = life;
        }

        static FloatingText damage(String text, double x, double y, boolean crit) {
            return new FloatingText(text, crit ? new Color(255, 100, 40) : new Color(240, 60, 60), x, y, -40, 1.0);
        }

        static FloatingText dot(String text, double x, double y) {
            return new FloatingText(text, new Color(255, 140, 0), x, y, -25, 1.0);
        }

        static FloatingText heal(String text, double x, double y) {
            return new FloatingText(text, new Color(80, 220, 120), x, y, -35, 1.0);
        }

        static FloatingText miss(double x, double y) {
            return new FloatingText("MISS", Color.WHITE, x, y, -30, 0.8);
        }

        static FloatingText buff(String text, double x, double y) {
            return new FloatingText(text, new Color(150, 180, 255), x, y, -20, 1.2);
        }

        void update(double dt) {
            life -= dt;
            y += vy * dt;
        }

        boolean dead() {
            return life <= 0;
        }
    }

    private static class BattleMessage {
        final String text;
        final double totalLife;
        double life;

        BattleMessage(String text, double lifetime) {
            this.text = text;
            this.life = lifetime;
            this.totalLife = lifetime;
        }
    }

    // ---------------------------------------------------------------------
    // Hero / Boss definitions
    // ---------------------------------------------------------------------
    private record HeroDefinition(String name, Affinity affinity, Stats stats, SpriteSource sprite,
                                  double offenseMod, double defenseMod, int openingMomentum) {
        Fighter toFighter() {
            return new Fighter(name, affinity, stats.copy(), offenseMod, defenseMod);
        }

        static HeroDefinition defaultHero() {
            return new HeroDefinition("Sir Rowan", Affinity.EMBER, new Stats(240, 28, 20, 18),
                    prefixSource("/resources/sprites/Knight/Idle/knight_m_idle_anim_f"), 1.12, 1.18, 2);
        }
    }

    private record BossDefinition(String displayName, Affinity affinity, Stats stats, SpriteSource sprite, double scale,
                                  double offenseMod, double defenseMod, int momentumEdge) {
        Fighter toFighter() {
            return new Fighter(displayName, affinity, stats.copy(), offenseMod, defenseMod);
        }

        static BossDefinition of(BossKind kind) {
            return switch (kind) {
                case OGRE_WARLORD -> new BossDefinition("Ogre Warlord", Affinity.STONE,
                        new Stats(230, 21, 15, 11), prefixSource("/resources/sprites/Ogre/ogre_idle_anim_f"), 3.5, 0.96, 1.02, 1);
                case SKELETON_LORD -> new BossDefinition("Skeleton Lord", Affinity.VERDANT,
                        new Stats(195, 18, 12, 22), prefixSource("/resources/sprites/Skeleton/skelet_idle_anim_f"), 3.3, 0.92, 0.94, 0);
                case PUMPKIN_KING -> new BossDefinition("Pumpkin King", Affinity.EMBER,
                        new Stats(210, 19, 17, 15), prefixSource("/resources/sprites/Pumpkin/pumpkin_dude_idle_anim_f"), 3.4, 0.94, 1.02, 0);
                case IMP_OVERLORD -> new BossDefinition("Imp Overlord", Affinity.STORM,
                        new Stats(185, 20, 11, 24), prefixSource("/resources/sprites/Imp/imp_idle_anim_f"), 3.6, 0.98, 0.9, 0);
                case WIZARD_ARCHON -> new BossDefinition("Wizard Archon", Affinity.STORM,
                        new Stats(200, 22, 12, 19), prefixSource("/resources/sprites/Wizard/wizzard_m_idle_anim_f"), 3.6, 0.99, 0.92, 1);
                case BIG_ZOMBIE -> new BossDefinition("Dread Husk", Affinity.STONE,
                        new Stats(265, 17, 21, 9), prefixSource("/resources/sprites/Bigzombie/big_zombie_idle_anim_f"), 3.8, 0.88, 1.12, -1);
                case GOLLUM -> new BossDefinition("Gollum", Affinity.STORM,
                        new Stats(205, 23, 13, 27), sheetSource("/resources/bosses/gollum.png"), 1.6, 1.05, 0.92, 1);
                case GRIM -> new BossDefinition("Grim", Affinity.STONE,
                        new Stats(245, 26, 18, 18), sheetSource("/resources/bosses/grim.png"), 1.5, 1.1, 1.05, 0);
                case FIRE_FLINGER -> new BossDefinition("Fire Flinger", Affinity.EMBER,
                        new Stats(215, 24, 14, 23), sheetSource("/resources/bosses/fireFlinger.png"), 1.1, 1.08, 0.95, 1);
                case GOLD_MECH -> new BossDefinition("Gold Mech", Affinity.STONE,
                        new Stats(320, 28, 25, 12), sheetSource("/resources/bosses/goldMech.png"), 0.6, 1.05, 1.15, -1);
                case GOLDEN_KNIGHT -> new BossDefinition("Golden Knight", Affinity.STONE,
                        new Stats(260, 27, 22, 16), sheetSource("/resources/bosses/goldenKnight.png"), 1.3, 1.1, 1.08, 0);
                case PURPLE_EMPRESS -> new BossDefinition("Purple Empress", Affinity.STORM,
                        new Stats(210, 25, 15, 25), sheetSource("/resources/bosses/purpleEmpress.png"), 0.85, 1.12, 0.96, 1);
                case THE_WELCH -> new BossDefinition("The Welch", Affinity.STORM,
                        new Stats(285, 22, 20, 18), sheetSource("/resources/bosses/theWelch.png"), 0.55, 1.0, 1.05, 0);
                case TOXIC_TREE -> new BossDefinition("Toxic Tree", Affinity.VERDANT,
                        new Stats(270, 24, 19, 14), sheetSource("/resources/bosses/toxicTree.png"), 1.45, 1.06, 1.1, -1);
            };
        }
    }

    // ---------------------------------------------------------------------
    // Battle resolution engine for the panel
    // ---------------------------------------------------------------------
    private static class BattleEngine {
        private final Fighter hero;
        private final Fighter boss;
        private final Consumer<Outcome> onEnd;
        private int momentum;
        private boolean over = false;
        private boolean heroSecondWindAvailable = true;

        private BattleEngine(Fighter hero, Fighter boss, int initialMomentum, Consumer<Outcome> onEnd) {
            this.hero = hero;
            this.boss = boss;
            this.onEnd = onEnd;
            this.momentum = Math.max(-2, Math.min(2, initialMomentum));
        }

        RoundOutcome resolve(int heroIndex) {
            if (over) {
                RoundOutcome out = new RoundOutcome();
                out.moveRefused = true;
                out.refusalReason = "The battle is already decided.";
                return out;
            }

            heroIndex = Math.max(0, Math.min(BaseMoves.MOVES.length - 1, heroIndex));
            Technique heroTech = BaseMoves.MOVES[heroIndex];
            if (cooldownRemaining(hero, heroTech) > 0) {
                RoundOutcome out = new RoundOutcome();
                out.moveRefused = true;
                out.refusalReason = "That move is still recharging.";
                return out;
            }

            Technique bossTech = SimpleAi.choose(boss, hero, momentum);
            RoundOutcome outcome = new RoundOutcome();

            Act first = decideOrder(heroTech, bossTech);
            Act second = (first.user == hero) ? new Act(boss, hero, bossTech) : new Act(hero, boss, heroTech);

            resolveAct(first, outcome);
            if (!hero.isDown() && !boss.isDown()) {
                resolveAct(second, outcome);
            }

            endOfRound(hero, outcome);
            endOfRound(boss, outcome);

            if (hero.isDown() || boss.isDown()) {
                over = true;
            }

            return outcome;
        }

        private Act decideOrder(Technique heroTech, Technique bossTech) {
            double biasHero = (momentum > 0 ? 0.3 : 0.0) + (hero.status == Status.SHOCKED ? -0.2 : 0);
            double biasBoss = (momentum < 0 ? 0.3 : 0.0) + (boss.status == Status.SHOCKED ? -0.2 : 0);
            double orderHero = heroTech.priority + hero.base.speed / 10.0 + biasHero + (hero.charging != null ? 0.5 : 0);
            double orderBoss = bossTech.priority + boss.base.speed / 10.0 + biasBoss + (boss.charging != null ? 0.5 : 0);
            boolean heroFirst = (orderHero == orderBoss) ? Math.random() < 0.5 : orderHero > orderBoss;
            return heroFirst ? new Act(hero, boss, heroTech) : new Act(boss, hero, bossTech);
        }

        private void resolveAct(Act act, RoundOutcome out) {
            Fighter user = act.user;
            Fighter target = act.target;
            Technique tech = act.technique;

            if (user.isDown()) return;

            if (user.charging != null) {
                tech = user.charging;
                user.charging = null;
                out.messages.add(user.name + " unleashes " + tech.name + "!");
            } else {
                out.messages.add(user.name + " uses " + tech.name + "!");
            }

            if (user.status == Status.ROOTED && tech.tag == Tag.CHARGE) {
                out.messages.add(user.name + " is rooted and cannot charge!");
                return;
            }

            if (!Rng.roll(tech.accuracy)) {
                out.messages.add("It misses!");
                out.events.add(Event.miss(user, target, tech));
                setCooldown(user, tech);
                return;
            }

            if (tech.tag == Tag.CHARGE) {
                user.charging = tech;
                out.messages.add(user.name + " gathers power...");
                setCooldown(user, tech);
                return;
            }

            if (tech.tag == Tag.INTERRUPT && target.charging != null) {
                target.charging = null;
                out.messages.add("Interrupt! " + target.name + " loses their charge.");
                out.events.add(Event.interrupt(user, target, tech));
                momentum += (user == hero ? 1 : -1);
            }

            if (tech.isUtility()) {
                applyOnHit(tech, user, target, out);
            } else {
                DamageCalc.Result res = DamageCalc.compute(user, target, tech, (user == hero ? momentum : -momentum));
                int damage = res.damage;
                if (tech.tag == Tag.GUARD) {
                    damage = (int) Math.ceil(damage * 0.5);
                }
                target.hp = Math.max(0, target.hp - damage);
                out.events.add(Event.damage(user, target, tech, damage, res.crit, res.mult));
                out.messages.add(target.name + " takes " + damage + " damage.");
                if (res.crit) out.messages.add("Critical strike!");
                if (res.mult > 1.0) out.messages.add("It resonates strongly!");
                else if (res.mult < 1.0) out.messages.add("Resisted.");
                momentum += (user == hero ? tech.momentumDelta : -tech.momentumDelta);
                momentum = Math.max(-3, Math.min(3, momentum));
                applyOnHit(tech, user, target, out);
            }

            setCooldown(user, tech);
        }

        private void applyOnHit(Technique tech, Fighter user, Fighter target, RoundOutcome out) {
            if (tech.onHit == null) return;
            Status beforeUser = user.status;
            Status beforeTarget = target.status;
            int guardBefore = user.base.guard;
            tech.onHit.accept(user, target);
            if (target.status != beforeTarget) {
                out.events.add(Event.status(target, "Status: " + target.status));
                out.messages.add(target.name + " is now " + target.status + "!");
            }
            if (user.status != beforeUser) {
                out.events.add(Event.status(user, "Status: " + user.status));
                out.messages.add(user.name + " becomes " + user.status + ".");
            }
            if (user.base.guard != guardBefore) {
                out.events.add(Event.guard(user, "Guard ↑"));
                out.messages.add(user.name + " braces, Guard rises!");
            }
        }

        private void endOfRound(Fighter fighter, RoundOutcome out) {
            if (fighter.status == Status.IGNITED) {
                int burn = Math.max(1, fighter.base.hp / 20);
                fighter.hp = Math.max(0, fighter.hp - burn);
                out.messages.add(fighter.name + " suffers " + burn + " burn damage.");
                out.events.add(Event.dot(null, fighter, burn));
            }
            if (fighter.status == Status.SHOCKED && Rng.roll(25)) {
                fighter.status = Status.OK;
                out.messages.add(fighter.name + " recovers from shock.");
                out.events.add(Event.status(fighter, "Recovered"));
            }
            if (fighter == hero && heroSecondWindAvailable && fighter.hp > 0) {
                int threshold = (int) Math.ceil(fighter.base.hp * 0.35);
                if (fighter.hp <= threshold) {
                    heroSecondWindAvailable = false;
                    int before = fighter.hp;
                    int heal = Math.max(15, fighter.base.hp / 5);
                    fighter.hp = Math.min(fighter.base.hp, fighter.hp + heal);
                    fighter.base.guard += 1;
                    int healed = fighter.hp - before;
                    out.messages.add(fighter.name + " rallies with a second wind!");
                    if (healed > 0) {
                        out.events.add(Event.heal(fighter, fighter, healed));
                    }
                    out.events.add(Event.guard(fighter, "Guard ↑"));
                }
            }
            List<Technique> cooldowns = new ArrayList<>(fighter.cd.keySet());
            for (Technique t : cooldowns) {
                int left = fighter.cd.get(t);
                if (left > 0) fighter.cd.put(t, left - 1);
            }
        }

        int cooldownRemaining(Fighter fighter, Technique t) {
            return fighter.cd.getOrDefault(t, 0);
        }

        private void setCooldown(Fighter fighter, Technique tech) {
            if (tech.cooldown > 0) {
                fighter.cd.put(tech, tech.cooldown);
            }
        }

        boolean heroWon() { return over && !hero.isDown() && boss.isDown(); }
        boolean heroLost() { return over && hero.isDown(); }
    }

    private static class Act {
        final Fighter user, target;
        final Technique technique;
        Act(Fighter user, Fighter target, Technique technique) { this.user = user; this.target = target; this.technique = technique; }
    }

    // ---------------------------------------------------------------------
    // Round outcome structure
    // ---------------------------------------------------------------------
    private static class Event {
        enum Type { DAMAGE, DOT, HEAL, MISS, STATUS, GUARD, INTERRUPT }
        final Type type;
        final Fighter actor;
        final Fighter target;
        final Technique technique;
        final int value;
        final boolean critical;
        final double mult;
        final String message;

        private Event(Type type, Fighter actor, Fighter target, Technique technique, int value, boolean critical, double mult, String message) {
            this.type = type;
            this.actor = actor;
            this.target = target;
            this.technique = technique;
            this.value = value;
            this.critical = critical;
            this.mult = mult;
            this.message = message;
        }

        static Event damage(Fighter actor, Fighter target, Technique tech, int value, boolean crit, double mult) {
            return new Event(Type.DAMAGE, actor, target, tech, value, crit, mult, null);
        }

        static Event dot(Fighter actor, Fighter target, int value) {
            return new Event(Type.DOT, actor, target, null, value, false, 1.0, null);
        }

        static Event heal(Fighter actor, Fighter target, int value) {
            return new Event(Type.HEAL, actor, target, null, value, false, 1.0, null);
        }

        static Event miss(Fighter actor, Fighter target, Technique tech) {
            return new Event(Type.MISS, actor, target, tech, 0, false, 1.0, null);
        }

        static Event status(Fighter target, String message) {
            return new Event(Type.STATUS, target, target, null, 0, false, 1.0, message);
        }

        static Event guard(Fighter actor, String message) {
            return new Event(Type.GUARD, actor, actor, null, 0, false, 1.0, message);
        }

        static Event interrupt(Fighter actor, Fighter target, Technique tech) {
            return new Event(Type.INTERRUPT, actor, target, tech, 0, false, 1.0, null);
        }
    }

    private static class RoundOutcome {
        final List<String> messages = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        boolean moveRefused = false;
        String refusalReason = "";
    }
}
