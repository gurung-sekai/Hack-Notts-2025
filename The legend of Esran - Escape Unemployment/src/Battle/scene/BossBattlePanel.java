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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import util.ResourceLoader;

/**
 * Boss battle swing panel featuring textured fighters, screen-space FX, and the shared battle ruleset.
 */
public class BossBattlePanel extends JPanel {

    public enum BossKind { BIG_ZOMBIE, OGRE_WARLORD, SKELETON_LORD, PUMPKIN_KING, IMP_OVERLORD, WIZARD_ARCHON }
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
    private final Deque<String> logLines = new ArrayDeque<>();

    private final BufferedImage floorTile;

    private long lastTickNs = 0;
    private double resolveLock = 0.0;

    private BossBattlePanel(HeroDefinition heroDef, BossDefinition bossDef, Consumer<Outcome> onEnd) {
        Objects.requireNonNull(heroDef, "hero");
        Objects.requireNonNull(bossDef, "boss");

        setPreferredSize(new Dimension(960, 540));
        setBackground(Color.BLACK);
        setFocusable(true);

        this.engine = new BattleEngine(heroDef.toFighter(), bossDef.toFighter(), onEnd);
        this.heroVisual = new FighterVisual(engine.hero, heroDef.spritePrefix, 140, 260, 3.0);
        this.bossVisual = new FighterVisual(engine.boss, bossDef.spritePrefix, 540, 140, bossDef.scale);
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

        for (Event event : outcome.events) {
            switch (event.type) {
                case DAMAGE -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = target.center();
                        floatingTexts.add(FloatingText.damage("-" + event.value, p.x, p.y, event.critical));
                    }
                    spawnEffect(event);
                }
                case DOT -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = target.center();
                        floatingTexts.add(FloatingText.dot("-" + event.value, p.x, p.y));
                    }
                }
                case HEAL -> {
                    FighterVisual actor = visualFor(event.actor);
                    if (actor != null) {
                        Point p = actor.center();
                        floatingTexts.add(FloatingText.heal("+" + event.value, p.x, p.y));
                    }
                }
                case MISS -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = target.center();
                        floatingTexts.add(FloatingText.miss(p.x, p.y));
                    }
                }
                case STATUS -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = target.center();
                        floatingTexts.add(FloatingText.buff(event.message, p.x, p.y));
                    }
                }
                case GUARD -> {
                    FighterVisual actor = visualFor(event.actor);
                    if (actor != null) {
                        Point p = actor.center();
                        floatingTexts.add(FloatingText.buff(event.message, p.x, p.y));
                    }
                }
                case INTERRUPT -> {
                    FighterVisual target = visualFor(event.target);
                    if (target != null) {
                        Point p = target.center();
                        floatingTexts.add(FloatingText.buff("Interrupted!", p.x, p.y));
                    }
                    spawnEffect(event);
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

    private void spawnEffect(Event event) {
        FighterVisual actor = visualFor(event.actor);
        FighterVisual target = visualFor(event.target);
        Technique tech = event.technique;
        if (tech == null) return;

        switch (tech.name) {
            case "Flame Lash" -> {
                if (actor != null && target != null) {
                    Point a = actor.center();
                    Point t = target.center();
                    double dir = (actor == heroVisual) ? 1 : -1;
                    effects.add(FXLibrary.fireBreath(a.x, a.y - 20, dir, 0));
                    effects.add(FXLibrary.fireHit(t.x, t.y - 30));
                }
            }
            case "Thorn Bind" -> {
                if (target != null) {
                    Point t = target.center();
                    effects.add(FXLibrary.smokeLarge(t.x - 40, t.y - 40));
                }
            }
            case "Brace" -> {
                if (actor != null) {
                    Point a = actor.center();
                    effects.add(FXLibrary.shieldBlockScreen(a.x, a.y));
                }
            }
            case "Disrupt Bolt" -> {
                if (target != null) {
                    Point t = target.center();
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
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        drawFloor(g2);
        drawEffectsBehind(g2);
        drawFighter(g2, heroVisual, false);
        drawFighter(g2, bossVisual, true);
        drawEffectsFront(g2);
        drawFloatingTexts(g2);
        drawHud(g2);
    }

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

    private void drawFloatingTexts(Graphics2D g2) {
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        for (FloatingText ft : floatingTexts) {
            g2.setColor(ft.color);
            g2.drawString(ft.text, (int) ft.x, (int) ft.y);
        }
    }

    private void drawFighter(Graphics2D g2, FighterVisual vis, boolean flip) {
        BufferedImage frame = vis.sprite.frame();
        if (frame == null) return;
        int w = (int) Math.round(frame.getWidth() * vis.scale);
        int h = (int) Math.round(frame.getHeight() * vis.scale);
        int x1 = vis.x;
        int y1 = vis.y;
        if (flip) {
            g2.drawImage(frame, x1 + w, y1, x1, y1 + h, 0, 0, frame.getWidth(), frame.getHeight(), null);
        } else {
            g2.drawImage(frame, x1, y1, x1 + w, y1 + h, 0, 0, frame.getWidth(), frame.getHeight(), null);
        }
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(20, 20, 300, 120, 16, 16);
        g2.fillRoundRect(getWidth() - 320, 20, 300, 120, 16, 16);
        g2.fillRoundRect(20, getHeight() - 190, getWidth() - 40, 170, 16, 16);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        drawFighterHud(g2, heroVisual, 40, 50);
        drawFighterHud(g2, bossVisual, getWidth() - 300, 50);
        drawCommands(g2);
        drawLog(g2);
    }

    private void drawFighterHud(Graphics2D g2, FighterVisual vis, int x, int y) {
        Fighter f = vis.fighter;
        g2.drawString(f.name, x, y);
        int barWidth = 240;
        double hpRatio = Math.max(0, (double) f.hp / f.base.hp);
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(x, y + 10, barWidth, 16);
        g2.setColor(new Color(190, 50, 50));
        g2.fillRect(x, y + 10, (int) (barWidth * hpRatio), 16);
        g2.setColor(Color.WHITE);
        g2.drawRect(x, y + 10, barWidth, 16);
        g2.drawString(f.hp + " / " + f.base.hp, x, y + 40);
        g2.drawString("Status: " + f.status, x, y + 60);
    }

    private void drawCommands(Graphics2D g2) {
        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        int x = 60;
        int y = getHeight() - 150;
        for (int i = 0; i < moves.length; i++) {
            Technique t = moves[i];
            boolean selected = (i == cmdIndex);
            int cooldown = engine.cooldownRemaining(engine.hero, t);
            String label = t.name + " (" + t.affinity + ")";
            if (cooldown > 0) label += " — CD " + cooldown;
            if (selected) {
                g2.setColor(new Color(255, 215, 0));
            } else {
                g2.setColor(Color.LIGHT_GRAY);
            }
            g2.drawString((selected ? "> " : "  ") + label, x, y + i * 28);
        }
        g2.setColor(Color.WHITE);
        g2.drawString("Momentum: " + engine.momentum, getWidth() - 220, getHeight() - 150);
        if (phase != Phase.PLAYER_SELECT) {
            g2.drawString("Resolving...", getWidth() - 220, getHeight() - 120);
        }
    }

    private void drawLog(Graphics2D g2) {
        g2.setFont(new Font("Monospaced", Font.PLAIN, 16));
        int x = 60;
        int y = getHeight() - 90;
        for (String line : logLines) {
            g2.setColor(Color.WHITE);
            g2.drawString(line, x, y);
            y += 20;
        }
    }

    private void addMessage(String msg) {
        if (msg == null || msg.isBlank()) return;
        logLines.addLast(msg);
        while (logLines.size() > 6) {
            logLines.removeFirst();
        }
    }

    private void addMessages(List<String> msgs) {
        if (msgs == null) return;
        for (String s : msgs) addMessage(s);
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
        final int x, y;
        final double scale;

        FighterVisual(Fighter fighter, String spritePrefix, int x, int y, double scale) {
            this.fighter = fighter;
            this.x = x;
            this.y = y;
            this.scale = scale;
            sprite.addFromPrefix(AnimatedSprite.State.IDLE, spritePrefix);
            sprite.setState(AnimatedSprite.State.IDLE);
            sprite.setFps(6.0);
        }

        void update(double dt) {
            sprite.update(dt);
        }

        Point center() {
            BufferedImage frame = sprite.frame();
            if (frame == null) return new Point(x, y);
            int w = (int) Math.round(frame.getWidth() * scale);
            int h = (int) Math.round(frame.getHeight() * scale);
            return new Point(x + w / 2, y + h / 2);
        }
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

    // ---------------------------------------------------------------------
    // Hero / Boss definitions
    // ---------------------------------------------------------------------
    private record HeroDefinition(String name, Affinity affinity, Stats stats, String spritePrefix) {
        Fighter toFighter() {
            return new Fighter(name, affinity, stats.copy());
        }

        static HeroDefinition defaultHero() {
            return new HeroDefinition("Sir Rowan", Affinity.EMBER, new Stats(160, 24, 16, 14),
                    "/resources/sprites/Knight/Idle/knight_m_idle_anim_f");
        }
    }

    private record BossDefinition(String displayName, Affinity affinity, Stats stats, String spritePrefix, double scale) {
        Fighter toFighter() {
            return new Fighter(displayName, affinity, stats.copy());
        }

        static BossDefinition of(BossKind kind) {
            return switch (kind) {
                case OGRE_WARLORD -> new BossDefinition("Ogre Warlord", Affinity.STONE,
                        new Stats(210, 22, 18, 10), "/resources/sprites/Ogre/ogre_idle_anim_f", 3.5);
                case SKELETON_LORD -> new BossDefinition("Skeleton Lord", Affinity.VERDANT,
                        new Stats(190, 20, 14, 16), "/resources/sprites/Skeleton/skelet_idle_anim_f", 3.3);
                case PUMPKIN_KING -> new BossDefinition("Pumpkin King", Affinity.EMBER,
                        new Stats(200, 21, 15, 15), "/resources/sprites/Pumpkin/pumpkin_dude_idle_anim_f", 3.4);
                case IMP_OVERLORD -> new BossDefinition("Imp Overlord", Affinity.STORM,
                        new Stats(175, 18, 12, 18), "/resources/sprites/Imp/imp_idle_anim_f", 3.6);
                case WIZARD_ARCHON -> new BossDefinition("Wizard Archon", Affinity.STORM,
                        new Stats(185, 24, 12, 17), "/resources/sprites/Wizard/wizzard_m_idle_anim_f", 3.6);
                case BIG_ZOMBIE -> new BossDefinition("Dread Husk", Affinity.STONE,
                        new Stats(230, 20, 20, 8), "/resources/sprites/Bigzombie/big_zombie_idle_anim_f", 3.8);
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
        private int momentum = 0;
        private boolean over = false;

        private BattleEngine(Fighter hero, Fighter boss, Consumer<Outcome> onEnd) {
            this.hero = hero;
            this.boss = boss;
            this.onEnd = onEnd;
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
