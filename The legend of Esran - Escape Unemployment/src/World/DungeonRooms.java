package World;

import World.gfx.DungeonTextures;
import Battle.scene.BossBattlePanel;
import Battle.scene.BossBattlePanel.Outcome;
import launcher.ControlAction;
import launcher.ControlsProfile;
import launcher.GameLauncher;
import launcher.GameSettings;
import launcher.LanguageBundle;
import security.GameSecurity;
import util.ResourceLoader;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Point2D;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Top-down dungeon crawler panel with persistent rooms and procedural generation.
 * Player movement, combat, and interactions obey the control bindings supplied by {@link ControlsProfile}.
 */
public class DungeonRooms extends JPanel implements ActionListener, KeyListener {

    // ----- Tunables -----
    static final int TILE = 36;           // pixels per tile
    static final int COLS = 21;           // room width (odd looks nice)
    static final int ROWS = 13;           // room height (odd looks nice)
    static final int FPS  = 60;
    static final int PLAYER_SIZE = (int)(TILE * 0.6);
    static final int PLAYER_SPEED = 4;    // px per tick
    static final Color BG = new Color(6, 24, 32);
    static final int PLAYER_PROJECTILE_RADIUS = 6;
    static final int ENEMY_PROJECTILE_RADIUS = 5;

    private static final int MINIMAP_MARGIN = 16;
    private static final int MINIMAP_CELL_MIN = 10;
    private static final int MINIMAP_CELL_MAX = 26;
    private static final int MINIMAP_MAX_WIDTH = 220;
    private static final int MINIMAP_MAX_HEIGHT = 220;
    private static final int MINIMAP_HEADER = 26;
    private static final int MINIMAP_FOOTER = 36;
    private static final int MINIMAP_HORIZONTAL_PADDING = 12;

    enum T { VOID, FLOOR, WALL, DOOR }
    enum Dir { N, S, W, E }

    static class Enemy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int x, y;
        int size = (int)(TILE * 0.6);
        int cd = 0;
        boolean alive = true;
        EnemySpawn spawn;
    }

    static class Bullet implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        double x, y;
        double vx, vy;
        int r = ENEMY_PROJECTILE_RADIUS;
        boolean alive = true;
    }

    static class Explosion implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        double x, y;
        int age = 0;
        int life = 18;
        int maxR = 22;
    }

    static class KeyPickup implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int x, y;
        int r = 12;
    }

    static class EnemySpawn implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int x, y;
        boolean defeated = false;
    }

    static class BossEncounter implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        BossBattlePanel.BossKind kind;
        boolean defeated = false;
        boolean rewardClaimed = false;
    }

    static class Room implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        T[][] g = new T[COLS][ROWS];
        Set<Dir> doors = EnumSet.noneOf(Dir.class);
        List<Enemy> enemies = new ArrayList<>();
        List<KeyPickup> keyPickups = new ArrayList<>();
        List<EnemySpawn> enemySpawns = new ArrayList<>();
        EnumSet<Dir> lockedDoors = EnumSet.noneOf(Dir.class);
        boolean cleared = false;
        boolean spawnsPrepared = false;
        Room() {
            for (int x = 0; x < COLS; x++)
                for (int y = 0; y < ROWS; y++)
                    g[x][y] = T.VOID;
        }
    }

    private static final int MESSAGE_SECONDS = 3;
    private static final String PLAYER_IDLE_PREFIX = "resources/sprites/Knight/Idle/knight_m_idle_anim_f";
    private static final String ENEMY_IDLE_PREFIX = "resources/sprites/Imp/imp_idle_anim_f";
    private static final String BOSS_IDLE_PREFIX = "resources/sprites/Bigzombie/big_zombie_idle_anim_f";

    private final GameSettings settings;
    private final ControlsProfile controls;
    private final LanguageBundle texts;
    private final Consumer<DungeonRoomsSnapshot> saveHandler;
    private final Runnable exitHandler;
    private final BossBattleHost bossBattleHost;

    private final Timer timer;
    private final int messageDurationTicks;

    private Random rng = new Random();
    private SecureRandom secureRandom = GameSecurity.secureRandom();

    // Persistent world: integer-grid of rooms using world coordinates
    private Map<Point, Room> world = new HashMap<>();
    private Map<Point, BossEncounter> bossEncounters = new HashMap<>();
    private Set<Point> visited = new HashSet<>();
    private List<BossBattlePanel.BossKind> bossPool = new ArrayList<>();
    private Point worldPos = new Point(0, 0);   // current room coordinate
    private int roomsVisited = 1;

    private Room room;                 // current room
    private Rectangle player;          // player rectangle in pixels
    private boolean up, down, left, right;

    private DungeonTextures textures;
    private BufferedImage[] playerIdleFrames;
    private BufferedImage[] enemyIdleFrames;
    private BufferedImage[] bossIdleFrames;
    private BufferedImage playerShotTexture;
    private BufferedImage enemyShotTexture;
    private int animTick = 0;
    private int mouseX = COLS * TILE / 2, mouseY = ROWS * TILE / 2;
    private final List<Bullet> bullets = new ArrayList<>();       // enemy bullets
    private final List<Bullet> playerBullets = new ArrayList<>(); // player bullets
    private final List<Explosion> explosions = new ArrayList<>();
    private int playerHP = 5;
    private int iFrames = 0;
    private int keysHeld = 0;
    private String statusMessage = "";
    private int statusTicks = 0;
    private volatile boolean inBoss = false;
    private boolean paused;
    private Dimension renderSize;
    private double scaleX = 1.0;
    private double scaleY = 1.0;

    public DungeonRooms(GameSettings settings,
                        ControlsProfile controls,
                        LanguageBundle texts,
                        Consumer<DungeonRoomsSnapshot> saveHandler,
                        Runnable exitHandler,
                        DungeonRoomsSnapshot snapshot,
                        BossBattleHost bossBattleHost) {
        GameSecurity.verifyIntegrity();
        this.settings = settings == null ? new GameSettings() : new GameSettings(settings);
        this.controls = controls == null ? new ControlsProfile() : new ControlsProfile(controls);
        this.texts = texts == null ? new LanguageBundle(this.settings.language()) : texts;
        this.saveHandler = saveHandler == null ? snapshotIgnored -> { } : saveHandler;
        this.exitHandler = exitHandler == null ? () -> { } : exitHandler;
        this.bossBattleHost = bossBattleHost;
        this.timer = new Timer(1000 / Math.max(30, this.settings.refreshRate()), this);
        this.messageDurationTicks = Math.max(1, this.settings.refreshRate() * MESSAGE_SECONDS);
        this.renderSize = this.settings.resolution();

        setPreferredSize(new Dimension(renderSize));
        setBackground(BG);
        setFocusable(true);
        addKeyListener(this);
        updateScale();

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                Point p = toGameCoords(e.getX(), e.getY());
                mouseX = p.x;
                mouseY = p.y;
            }

            @Override public void mouseDragged(MouseEvent e) {
                Point p = toGameCoords(e.getX(), e.getY());
                mouseX = p.x;
                mouseY = p.y;
            }
        });

        if (snapshot != null) {
            restoreFromSnapshot(snapshot);
        } else {
            initializeNewRun();
        }

        timer.start();
    }

    private void initializeNewRun() {
        rng = new Random();
        secureRandom = GameSecurity.secureRandom();
        world = new HashMap<>();
        bossEncounters = new HashMap<>();
        visited = new HashSet<>();
        bossPool = new ArrayList<>();
        worldPos = new Point(0, 0);
        roomsVisited = 1;
        bullets.clear();
        playerBullets.clear();
        explosions.clear();
        up = down = left = right = false;
        playerHP = 5;
        iFrames = 0;
        keysHeld = 0;
        statusMessage = "";
        statusTicks = 0;
        inBoss = false;
        paused = false;
        refreshArtAssets();
        initializeBossPool();
        room = makeOrGetRoom(worldPos, null);
        spawnEnemiesIfNeeded(worldPos, room);
        placePlayerAtCenter();
        visited.add(new Point(worldPos));
        showMessage(texts.text("intro"));
    }

    private void restoreFromSnapshot(DungeonRoomsSnapshot snapshot) {
        refreshArtAssets();

        world = snapshot.world();
        bossEncounters = snapshot.bossEncounters();
        visited = snapshot.visited();
        bossPool = snapshot.bossPool();
        worldPos = snapshot.worldPos();
        roomsVisited = snapshot.roomsVisited();
        room = snapshot.currentRoom();
        player = snapshot.playerRect();
        if (player == null) {
            placePlayerAtCenter();
        }
        up = snapshot.moveUp();
        down = snapshot.moveDown();
        left = snapshot.moveLeft();
        right = snapshot.moveRight();
        bullets.clear();
        bullets.addAll(snapshot.enemyBullets());
        playerBullets.clear();
        playerBullets.addAll(snapshot.playerBullets());
        ensureProjectileDefaults(bullets, ENEMY_PROJECTILE_RADIUS);
        ensureProjectileDefaults(playerBullets, PLAYER_PROJECTILE_RADIUS);
        explosions.clear();
        explosions.addAll(snapshot.explosions());
        playerHP = snapshot.playerHP();
        iFrames = snapshot.iFrames();
        keysHeld = snapshot.keysHeld();
        statusMessage = snapshot.statusMessage() == null ? "" : snapshot.statusMessage();
        statusTicks = snapshot.statusTicks();
        inBoss = snapshot.inBoss();
        animTick = snapshot.animTick();
        mouseX = snapshot.mouseX();
        mouseY = snapshot.mouseY();
        rng = snapshot.rng();
        secureRandom = snapshot.secureRandom();
        paused = false;
    }

    private void refreshArtAssets() {
        textures = DungeonTextures.load();
        playerIdleFrames = loadSpriteSequence(PLAYER_IDLE_PREFIX, 0, 3);
        if (playerIdleFrames == null) {
            playerIdleFrames = fallbackIdleFrames(new Color(255, 214, 102), new Color(40, 30, 10));
        }
        enemyIdleFrames = loadSpriteSequence(ENEMY_IDLE_PREFIX, 0, 3);
        if (enemyIdleFrames == null) {
            enemyIdleFrames = fallbackIdleFrames(new Color(198, 72, 72), new Color(38, 20, 20));
        }
        bossIdleFrames = loadSpriteSequence(BOSS_IDLE_PREFIX, 0, 3);
        if (bossIdleFrames == null) {
            bossIdleFrames = fallbackIdleFrames(new Color(120, 210, 150), new Color(32, 60, 40));
        }
        playerShotTexture = createProjectileTexture(
                new Color(212, 247, 255, 255),
                new Color(112, 206, 255, 220),
                new Color(24, 110, 196, 170));
        enemyShotTexture = createProjectileTexture(
                new Color(255, 205, 150, 255),
                new Color(232, 118, 62, 225),
                new Color(132, 36, 20, 180));
    }

    private void ensureProjectileDefaults(List<Bullet> projectiles, int desiredRadius) {
        if (projectiles == null) {
            return;
        }
        for (Bullet b : projectiles) {
            if (b == null) {
                continue;
            }
            b.r = desiredRadius;
        }
    }

    private void updateScale() {
        scaleX = renderSize.getWidth() / (double) (COLS * TILE);
        scaleY = renderSize.getHeight() / (double) (ROWS * TILE);
        if (Double.isNaN(scaleX) || scaleX <= 0) {
            scaleX = 1.0;
        }
        if (Double.isNaN(scaleY) || scaleY <= 0) {
            scaleY = 1.0;
        }
    }

    private Point toGameCoords(int x, int y) {
        int gx = (int) Math.round(x / scaleX);
        int gy = (int) Math.round(y / scaleY);
        return new Point(Math.max(0, Math.min(COLS * TILE, gx)), Math.max(0, Math.min(ROWS * TILE, gy)));
    }

    private BufferedImage[] loadSpriteSequence(String prefix, int from, int toInclusive) {
        List<BufferedImage> frames = new ArrayList<>();
        for (int i = from; i <= toInclusive; i++) {
            String resource = prefix + i + ".png";
            try (InputStream in = ResourceLoader.open(resource)) {
                if (in == null) {
                    continue;
                }
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    frames.add(img);
                }
            } catch (IOException ex) {
                System.err.println("Failed to load sprite frame: " + resource + " -> " + ex.getMessage());
            }
        }
        return frames.isEmpty() ? null : frames.toArray(new BufferedImage[0]);
    }

    private BufferedImage[] fallbackIdleFrames(Color base, Color outline) {
        BufferedImage[] frames = new BufferedImage[4];
        for (int i = 0; i < frames.length; i++) {
            BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(base);
                int wobble = i % 2;
                g.fillOval(4, 6 + wobble, 24, 24 - wobble);
                g.setColor(outline);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(4, 6 + wobble, 24, 24 - wobble);
                g.setColor(new Color(255, 255, 255, 120));
                g.fillOval(10, 10 + wobble, 8, 8);
            } finally {
                g.dispose();
            }
            frames[i] = img;
        }
        return frames;
    }

    private BufferedImage createProjectileTexture(Color core, Color mid, Color edge) {
        int size = 48;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Point2D center = new Point2D.Float(size / 2f, size / 2f);
            RadialGradientPaint paint = new RadialGradientPaint(center, size / 2f,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{core, mid, new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(255, edge.getAlpha() + 30))});
            g.setPaint(paint);
            g.fillOval(0, 0, size, size);
            g.setComposite(AlphaComposite.SrcOver.derive(0.8f));
            g.setColor(new Color(255, 255, 255, 190));
            int highlightW = size / 4;
            int highlightH = size / 5;
            g.fillOval(size / 2 - highlightW, size / 2 - highlightH - 4, highlightW, highlightH);
            g.setComposite(AlphaComposite.SrcOver);
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 200));
            g.drawOval(2, 2, size - 4, size - 4);
        } finally {
            g.dispose();
        }
        return img;
    }

    private void drawProjectile(Graphics2D g, Bullet bullet, BufferedImage texture, Color fallbackColour) {
        if (bullet == null || !bullet.alive) {
            return;
        }
        int diameter = Math.max(4, bullet.r * 2);
        int drawX = (int) Math.round(bullet.x - diameter / 2.0);
        int drawY = (int) Math.round(bullet.y - diameter / 2.0);
        if (texture != null) {
            g.drawImage(texture, drawX, drawY, diameter, diameter, null);
        } else {
            Color original = g.getColor();
            g.setColor(fallbackColour == null ? Color.WHITE : fallbackColour);
            g.fillOval(drawX, drawY, diameter, diameter);
            g.setColor(original);
        }
    }

    // ======= Room creation / persistence =======

    /** Get existing room at pos or create a new one with 1–3 doors. Guarantees an entrance if required. */
    private Room makeOrGetRoom(Point pos, Dir mustHaveEntrance) {
        Room r = world.get(pos);
        if (r == null) {
            r = generateNewRoom(mustHaveEntrance);
            configureLocksForNewRoom(pos, r, mustHaveEntrance);
            world.put(new Point(pos), r); // store a copy of key to avoid mutation issues
            spawnEnemiesIfNeeded(pos, r);
            return r;
        }
        // Ensure the entrance exists if we’re entering from a new side later
        if (mustHaveEntrance != null && !r.doors.contains(mustHaveEntrance)) {
            r.doors.add(mustHaveEntrance);
            carveDoorOnGrid(r, mustHaveEntrance);
        }
        if (mustHaveEntrance != null) {
            r.lockedDoors.remove(mustHaveEntrance);
        }
        return r;
    }

    private void configureLocksForNewRoom(Point pos, Room r, Dir mustHaveEntrance) {
        EnumSet<Dir> locks = EnumSet.noneOf(Dir.class);
        if (pos.x == 0 && pos.y == 0) {
            r.lockedDoors = locks;
            return;
        }
        for (Dir d : r.doors) {
            if (d == mustHaveEntrance) continue;
            if (secureRandom.nextDouble() < 0.45) {
                locks.add(d);
            }
        }
        r.lockedDoors = locks;
    }

    private void spawnEnemiesIfNeeded(Point pos, Room r) {
        if (r == null || r.cleared) return;
        if (isBossRoom(pos)) return;
        if (!r.spawnsPrepared) {
            initializeEnemySpawns(r);
        }
        if (!r.enemies.isEmpty()) return;

        for (EnemySpawn spawn : r.enemySpawns) {
            if (spawn.defeated) continue;
            Enemy e = new Enemy();
            e.x = spawn.x;
            e.y = spawn.y;
            e.cd = rng.nextInt(30);
            e.spawn = spawn;
            r.enemies.add(e);
        }
    }

    private void initializeEnemySpawns(Room r) {
        if (r == null || r.spawnsPrepared) return;
        int count = 4 + rng.nextInt(4);
        int attempts = 0;
        while (r.enemySpawns.size() < count && attempts++ < count * 40) {
            int tx = 2 + rng.nextInt(COLS - 4);
            int ty = 2 + rng.nextInt(ROWS - 4);
            if (r.g[tx][ty] != T.FLOOR) continue;
            int px = tx * TILE + TILE / 2;
            int py = ty * TILE + TILE / 2;
            if (!isRectFree(r, px, py, (int) (TILE * 0.3))) continue;
            if (player != null) {
                int pcx = player.x + player.width / 2;
                int pcy = player.y + player.height / 2;
                if (Math.abs(px - pcx) + Math.abs(py - pcy) < TILE * 4) continue;
            }
            EnemySpawn spawn = new EnemySpawn();
            spawn.x = px;
            spawn.y = py;
            r.enemySpawns.add(spawn);
        }
        r.spawnsPrepared = true;
    }

    private boolean isBossRoom(Point pos) {
        BossEncounter encounter = bossEncounters.get(pos);
        return encounter != null && !encounter.defeated;
    }

    private boolean isRectFree(Room r, int cx, int cy, int half) {
        int minTX = Math.max(0, (cx - half) / TILE);
        int maxTX = Math.min(COLS - 1, (cx + half - 1) / TILE);
        int minTY = Math.max(0, (cy - half) / TILE);
        int maxTY = Math.min(ROWS - 1, (cy + half - 1) / TILE);
        for (int tx = minTX; tx <= maxTX; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                if (r.g[tx][ty] == T.WALL) return false;
            }
        }
        return true;
    }

    private void updateCombat() {
        if (room == null) return;
        updateRoomClearState(room);

        int pcx = player.x + player.width/2;
        int pcy = player.y + player.height/2;

        for (Enemy e : room.enemies) {
            if (!e.alive) continue;
            int dx = pcx - e.x;
            int dy = pcy - e.y;
            double len = Math.hypot(dx, dy);
            if (len > 1) {
                double speed = 0.9;
                int mx = (int)Math.round(dx/len * speed);
                int my = (int)Math.round(dy/len * speed);
                attemptEnemyMove(e, mx, 0);
                attemptEnemyMove(e, 0, my);
            }
            if (e.cd-- <= 0) {
                double spd = 3.75;
                Bullet b = new Bullet();
                b.x = e.x; b.y = e.y;
                double l = Math.max(1e-6, Math.hypot(dx, dy));
                b.vx = dx / l * spd;
                b.vy = dy / l * spd;
                b.r = ENEMY_PROJECTILE_RADIUS;
                bullets.add(b);
                e.cd = 60 + rng.nextInt(40);
            }
        }

        for (Bullet b : bullets) {
            if (!b.alive) continue;
            b.x += b.vx;
            b.y += b.vy;
            if (b.x < 0 || b.y < 0 || b.x >= COLS*TILE || b.y >= ROWS*TILE) { b.alive = false; explosions.add(makeExplosion(b.x, b.y)); continue; }
            int tx = (int)(b.x) / TILE;
            int ty = (int)(b.y) / TILE;
            if (tx >= 0 && tx < COLS && ty >= 0 && ty < ROWS) {
                if (room.g[tx][ty] == T.WALL) { b.alive = false; explosions.add(makeExplosion(b.x, b.y)); }
            }
            // Player hit detection (circle vs rect)
            if (player != null && intersectsCircleRect(b.x, b.y, b.r, player)) {
                if (iFrames == 0) {
                    playerHP = Math.max(0, playerHP - 1);
                    iFrames = 40;
                    if (playerHP <= 0) onPlayerDeath();
                }
                b.alive = false; explosions.add(makeExplosion(b.x, b.y));
            }
        }
        bullets.removeIf(bb -> !bb.alive);

        // Player bullets
        for (Bullet b : playerBullets) {
            if (!b.alive) continue;
            b.x += b.vx;
            b.y += b.vy;
            if (b.x < 0 || b.y < 0 || b.x >= COLS*TILE || b.y >= ROWS*TILE) { b.alive = false; explosions.add(makeExplosion(b.x, b.y)); continue; }
            int tx = (int)(b.x) / TILE;
            int ty = (int)(b.y) / TILE;
            if (tx >= 0 && tx < COLS && ty >= 0 && ty < ROWS) {
                if (room.g[tx][ty] == T.WALL) { b.alive = false; explosions.add(makeExplosion(b.x, b.y)); continue; }
            }
            // enemy collision (circle vs enemy AABB simplified)
            for (Enemy e : room.enemies) {
                if (!b.alive) break;
                if (!e.alive) continue;
                int ex0 = e.x - e.size/2, ey0 = e.y - e.size/2;
                int ex1 = ex0 + e.size, ey1 = ey0 + e.size;
                if (b.x >= ex0 && b.x <= ex1 && b.y >= ey0 && b.y <= ey1) {
                    b.alive = false; explosions.add(makeExplosion(b.x, b.y));
                    eliminateEnemy(room, e);
                }
            }
        }
        playerBullets.removeIf(bb -> !bb.alive);
        room.enemies.removeIf(e -> !e.alive);
        updateRoomClearState(room);

        // explosions advance
        for (Explosion ex : explosions) ex.age++;
        explosions.removeIf(ex -> ex.age >= ex.life);
    }

    private void eliminateEnemy(Room r, Enemy enemy) {
        if (enemy == null || !enemy.alive) return;
        enemy.alive = false;
        if (enemy.spawn != null) {
            enemy.spawn.defeated = true;
        }
        spawnKeyPickup(r, enemy.x, enemy.y);
    }

    private void spawnKeyPickup(Room r, int x, int y) {
        if (r == null) return;
        KeyPickup key = new KeyPickup();
        key.x = x;
        key.y = y;
        r.keyPickups.add(key);
        showMessage(texts.text("key_drop"));
    }

    private void updateRoomClearState(Room r) {
        if (r == null || !r.spawnsPrepared || r.cleared) return;
        boolean allDefeated = true;
        for (EnemySpawn spawn : r.enemySpawns) {
            if (!spawn.defeated) {
                allDefeated = false;
                break;
            }
        }
        if (allDefeated) {
            r.cleared = true;
            showMessage(texts.text("room_cleared"));
        }
    }

    private void checkKeyPickup() {
        if (room == null || room.keyPickups.isEmpty() || player == null) return;
        int pcx = player.x + player.width / 2;
        int pcy = player.y + player.height / 2;
        Iterator<KeyPickup> it = room.keyPickups.iterator();
        while (it.hasNext()) {
            KeyPickup key = it.next();
            double dx = key.x - pcx;
            double dy = key.y - pcy;
            double maxR = key.r + Math.min(player.width, player.height) / 2.0;
            if (dx * dx + dy * dy <= maxR * maxR) {
                it.remove();
                keysHeld++;
                showMessage(texts.text("key_obtained", keysHeld));
                updateRoomClearState(room);
            }
        }
    }

    private void checkForBossEncounter() {
        if (inBoss) return;
        BossEncounter encounter = bossEncounters.get(worldPos);
        if (encounter != null && !encounter.defeated) {
            showMessage(texts.text("boss_challenge", formatBossName(encounter.kind)));
            triggerBossEncounter(encounter);
        }
    }

    private void showMessage(String message) {
        if (message == null || message.isBlank()) {
            statusMessage = "";
            statusTicks = 0;
        } else {
            statusMessage = message;
            statusTicks = messageDurationTicks;
        }
    }

    private boolean registerVisit(Point pos) {
        Point key = new Point(pos);
        if (visited.add(key)) {
            roomsVisited++;
            return true;
        }
        return false;
    }

    private BossEncounter ensureBossFor(Point pos) {
        BossEncounter encounter = bossEncounters.get(pos);
        if (encounter != null) return encounter;
        if (bossPool.isEmpty()) {
            initializeBossPool();
        }
        if (bossPool.isEmpty()) return null;
        BossEncounter created = new BossEncounter();
        created.kind = bossPool.remove(0);
        bossEncounters.put(new Point(pos), created);
        return created;
    }

    private void prepareBossRoom(Room targetRoom) {
        if (targetRoom == null) return;
        targetRoom.enemies.clear();
        targetRoom.keyPickups.clear();
        targetRoom.cleared = true;
    }

    private void grantBossReward(BossEncounter encounter) {
        if (encounter == null || encounter.rewardClaimed) return;
        encounter.rewardClaimed = true;
        keysHeld++;
        showMessage(texts.text("victory_key", keysHeld));
    }

    private static String formatBossName(BossBattlePanel.BossKind kind) {
        String raw = kind.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean cap = true;
        for (char c : raw.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
                cap = (c == ' ');
            }
        }
        return sb.toString();
    }

    private void initializeBossPool() {
        bossPool.clear();
        Collections.addAll(bossPool, BossBattlePanel.BossKind.values());
        Collections.shuffle(bossPool, secureRandom);
    }

    private Explosion makeExplosion(double x, double y) {
        Explosion ex = new Explosion();
        ex.x = x; ex.y = y;
        return ex;
    }

    private void onPlayerDeath() {
        SwingUtilities.invokeLater(() -> {
            // Respawn at origin room with full HP and brief invulnerability
            playerHP = 5;
            iFrames = 60;
            up = down = left = right = false;
            inBoss = false;
            bullets.clear();
            playerBullets.clear();
            explosions.clear();
            world.clear();
            bossEncounters.clear();
            visited.clear();
            roomsVisited = 1;
            keysHeld = 0;
            statusMessage = "";
            statusTicks = 0;
            initializeBossPool();
            worldPos = new Point(0, 0);
            room = makeOrGetRoom(worldPos, null);
            spawnEnemiesIfNeeded(worldPos, room);
            visited.add(new Point(worldPos));
            placePlayerAtCenter();
            showMessage(texts.text("respawn"));
            repaint();
            requestFocusInWindow();
        });
    }

    private void triggerBossEncounter(BossEncounter encounter) {
        if (encounter == null) return;
        inBoss = true;
        timer.stop();
        Consumer<Outcome> finish = outcome -> SwingUtilities.invokeLater(() -> {
            if (outcome == Outcome.HERO_WIN) {
                encounter.defeated = true;
                grantBossReward(encounter);
            } else {
                showMessage(texts.text("boss_repelled"));
                onPlayerDeath();
            }
            inBoss = false;
            iFrames = 60; // grace on return
            timer.start();
            requestFocusInWindow();
        });

        if (bossBattleHost != null) {
            bossBattleHost.runBossBattle(encounter.kind, finish);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Boss Battle");
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setContentPane(BossBattlePanel.create(encounter.kind, outcome -> {
                finish.accept(outcome);
                f.dispose();
            }));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    private static boolean intersectsCircleRect(double cx, double cy, double r, Rectangle rect) {
        double closestX = Math.max(rect.x, Math.min(cx, rect.x + rect.width));
        double closestY = Math.max(rect.y, Math.min(cy, rect.y + rect.height));
        double dx = cx - closestX;
        double dy = cy - closestY;
        return dx*dx + dy*dy <= r*r;
    }

    private void shootPlayerBullet() {
        Bullet b = new Bullet();
        int pcx = player.x + player.width/2;
        int pcy = player.y + player.height/2;
        b.x = pcx; b.y = pcy;
        double dx = mouseX - pcx;
        double dy = mouseY - pcy;
        double l = Math.max(1e-6, Math.hypot(dx, dy));
        double spd = 7.0;
        b.vx = dx / l * spd;
        b.vy = dy / l * spd;
        b.r = PLAYER_PROJECTILE_RADIUS;
        playerBullets.add(b);
    }

    // ---- file-system sprite loading ----
    /** Create a fresh room with outer walls and 1–3 total doors (including the entrance, if any). */
    private Room generateNewRoom(Dir mustHaveEntrance) {
        Room r = new Room();

        // Floor fill and border walls
        for (int x = 0; x < COLS; x++)
            for (int y = 0; y < ROWS; y++)
                r.g[x][y] = T.FLOOR;

        for (int x = 0; x < COLS; x++) { r.g[x][0] = T.WALL; r.g[x][ROWS - 1] = T.WALL; }
        for (int y = 0; y < ROWS; y++) { r.g[0][y] = T.WALL; r.g[COLS - 1][y] = T.WALL; }

        // Choose 2–3 total doors to avoid dead-ends
        int totalDoors = 2 + rng.nextInt(2); // 2..3

        Set<Dir> chosen = EnumSet.noneOf(Dir.class);
        if (mustHaveEntrance != null) chosen.add(mustHaveEntrance);

        List<Dir> pool = new ArrayList<>(List.of(Dir.N, Dir.S, Dir.W, Dir.E));
        if (mustHaveEntrance != null) pool.remove(mustHaveEntrance);
        Collections.shuffle(pool, rng);
        for (Dir d : pool) {
            if (chosen.size() >= totalDoors) break;
            chosen.add(d);
        }
        // Materialize doors
        for (Dir d : chosen) carveDoorOnGrid(r, d);
        r.doors = chosen;

        // Sprinkle obstacles while keeping space near doors
        int blocks = 8 + rng.nextInt(7);
        for (int i = 0; i < blocks; i++) {
            int bx = 2 + rng.nextInt(COLS - 4);
            int by = 2 + rng.nextInt(ROWS - 4);
            if (nearAnyDoor(r, bx, by, 3)) continue;
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 0; dy < 2; dy++)
                    if (inBounds(bx + dx, by + dy))
                        r.g[bx + dx][by + dy] = T.WALL;
        }
        // Revalidate enemies: push out of walls if necessary
        List<Enemy> kept = new ArrayList<>();
        for (Enemy e : r.enemies) {
            if (isRectFree(r, e.x, e.y, e.size/2)) { kept.add(e); continue; }
            boolean placed = false;
            // Try spiral search around current tile
            int ex = Math.max(0, Math.min(COLS-1, e.x / TILE));
            int ey = Math.max(0, Math.min(ROWS-1, e.y / TILE));
            for (int radius = 1; radius < Math.max(COLS, ROWS) && !placed; radius++) {
                for (int dx = -radius; dx <= radius && !placed; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        int tx = ex + dx, ty = ey + dy;
                        if (!inBounds(tx, ty)) continue;
                        if (r.g[tx][ty] != T.FLOOR) continue;
                        int px = tx * TILE + TILE/2;
                        int py = ty * TILE + TILE/2;
                        if (!isRectFree(r, px, py, e.size/2)) continue;
                        e.x = px; e.y = py; kept.add(e); placed = true; break;
                    }
                }
            }
        }
        r.enemies = kept;
        return r;
    }

    private void carveDoorOnGrid(Room r, Dir d) {
        Point t = doorTile(d);
        r.g[t.x][t.y] = T.DOOR;
        // Ensure the tile just inside is floor
        Point inside = new Point(t.x, t.y);
        switch (d) {
            case N -> inside.y = t.y + 1;
            case S -> inside.y = t.y - 1;
            case W -> inside.x = t.x + 1;
            case E -> inside.x = t.x - 1;
        }
        if (inBounds(inside.x, inside.y)) r.g[inside.x][inside.y] = T.FLOOR;
    }

    private boolean nearAnyDoor(Room r, int tx, int ty, int dist) {
        for (Dir d : r.doors) {
            Point doorTile = doorTile(d);
            if (Math.abs(doorTile.x - tx) + Math.abs(doorTile.y - ty) <= dist)
                return true;
        }
        return false;
    }

    private static boolean inBounds(int x, int y) { return x >= 0 && x < COLS && y >= 0 && y < ROWS; }

    private static Point doorTile(Dir d) {
        int midX = COLS / 2, midY = ROWS / 2;
        return switch (d) {
            case N -> new Point(midX, 0);
            case S -> new Point(midX, ROWS - 1);
            case W -> new Point(0, midY);
            case E -> new Point(COLS - 1, midY);
        };
    }

    private static Dir dirForTile(int tx, int ty) {
        int midX = COLS / 2, midY = ROWS / 2;
        if (tx == midX && ty == 0) return Dir.N;
        if (tx == midX && ty == ROWS - 1) return Dir.S;
        if (ty == midY && tx == 0) return Dir.W;
        if (ty == midY && tx == COLS - 1) return Dir.E;
        return null;
    }

    // ======= Player control / updates =======

    private void placePlayerAtCenter() {
        int cx = COLS / 2 * TILE + TILE / 2;
        int cy = ROWS / 2 * TILE + TILE / 2;
        Point p = safePlayerSpawn(room, cx, cy);
        player = new Rectangle(p.x - PLAYER_SIZE / 2, p.y - PLAYER_SIZE / 2, PLAYER_SIZE, PLAYER_SIZE);
        repaint();
    }

    private void placePlayerJustInside(Dir enteredFrom) {
        // enteredFrom is the side of the *new room* we came through
        Point t = doorTile(enteredFrom);
        int px = t.x * TILE + TILE / 2;
        int py = t.y * TILE + TILE / 2;
        switch (enteredFrom) {
            case N -> py += TILE;
            case S -> py -= TILE;
            case W -> px += TILE;
            case E -> px -= TILE;
        }
        Point p = safePlayerSpawn(room, px, py);
        player = new Rectangle(p.x - PLAYER_SIZE / 2, p.y - PLAYER_SIZE / 2, PLAYER_SIZE, PLAYER_SIZE);
        repaint();
    }

    private Point safePlayerSpawn(Room r, int cx, int cy) {
        int half = PLAYER_SIZE / 2;
        if (isRectFree(r, cx, cy, half)) return new Point(cx, cy);
        int startTx = Math.max(0, Math.min(COLS - 1, cx / TILE));
        int startTy = Math.max(0, Math.min(ROWS - 1, cy / TILE));
        for (int radius = 0; radius < Math.max(COLS, ROWS); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int tx = startTx + dx;
                    int ty = startTy + dy;
                    if (!inBounds(tx, ty)) continue;
                    if (r.g[tx][ty] != T.FLOOR) continue;
                    int px = tx * TILE + TILE / 2;
                    int py = ty * TILE + TILE / 2;
                    if (isRectFree(r, px, py, half)) return new Point(px, py);
                }
            }
        }
        // Fallback to room center if all else fails
        return new Point(COLS / 2 * TILE + TILE / 2, ROWS / 2 * TILE + TILE / 2);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (paused || inBoss) {
            return;
        }
        animTick++;
        if (iFrames > 0) iFrames--;
        updatePlayer();
        updateCombat();
        checkKeyPickup();
        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0) statusMessage = "";
        }
        checkForBossEncounter();
        repaint();
    }

    private void updatePlayer() {
        // No diagonal movement: pick one axis
        int vx = 0, vy = 0;
        if (left || right) {
            vx = (left ? -PLAYER_SPEED : 0) + (right ? PLAYER_SPEED : 0);
            vy = 0;
        } else if (up || down) {
            vy = (up ? -PLAYER_SPEED : 0) + (down ? PLAYER_SPEED : 0);
            vx = 0;
        }

        moveAxis(vx, 0);
        moveAxis(0, vy);

        Dir through = touchingDoorOnEdge();
        if (through != null) switchRoom(through);
    }

    private void moveAxis(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        Rectangle next = new Rectangle(player);
        next.translate(dx, dy);

        next.x = Math.max(0, Math.min(next.x, COLS * TILE - next.width));
        next.y = Math.max(0, Math.min(next.y, ROWS * TILE - next.height));

        int minTX = Math.max(0, (next.x) / TILE);
        int maxTX = Math.min(COLS - 1, (next.x + next.width - 1) / TILE);
        int minTY = Math.max(0, (next.y) / TILE);
        int maxTY = Math.min(ROWS - 1, (next.y + next.height - 1) / TILE);

        boolean blocked = false;
        for (int tx = minTX; tx <= maxTX; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                if (room.g[tx][ty] == T.WALL) { blocked = true; break; }
            }
            if (blocked) break;
        }
        if (!blocked) player = next;
    }

    private void attemptEnemyMove(Enemy e, int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        int nx = e.x + dx;
        int ny = e.y + dy;
        int half = e.size / 2;
        // Clamp to room bounds
        nx = Math.max(half, Math.min(nx, COLS * TILE - half));
        ny = Math.max(half, Math.min(ny, ROWS * TILE - half));

        int minTX = Math.max(0, (nx - half) / TILE);
        int maxTX = Math.min(COLS - 1, (nx + half - 1) / TILE);
        int minTY = Math.max(0, (ny - half) / TILE);
        int maxTY = Math.min(ROWS - 1, (ny + half - 1) / TILE);

        boolean blocked = false;
        for (int tx = minTX; tx <= maxTX && !blocked; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                if (room.g[tx][ty] == T.WALL) { blocked = true; break; }
            }
        }
        if (!blocked) { e.x = nx; e.y = ny; }
    }

    private Dir touchingDoorOnEdge() {
        int cx = player.x + player.width / 2;
        int cy = player.y + player.height / 2;

        if (cy < TILE / 3) {
            Point t = doorTile(Dir.N);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cx - (t.x * TILE + TILE / 2)) < TILE / 2) return Dir.N;
        }
        if (cy > ROWS * TILE - TILE / 3) {
            Point t = doorTile(Dir.S);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cx - (t.x * TILE + TILE / 2)) < TILE / 2) return Dir.S;
        }
        if (cx < TILE / 3) {
            Point t = doorTile(Dir.W);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cy - (t.y * TILE + TILE / 2)) < TILE / 2) return Dir.W;
        }
        if (cx > COLS * TILE - TILE / 3) {
            Point t = doorTile(Dir.E);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cy - (t.y * TILE + TILE / 2)) < TILE / 2) return Dir.E;
        }
        return null;
    }

    private void switchRoom(Dir exitSide) {
        boolean consumedKey = false;
        if (room.lockedDoors.contains(exitSide)) {
            if (keysHeld <= 0) {
                showMessage(texts.text("door_locked"));
                return;
            }
            keysHeld--;
            consumedKey = true;
            room.lockedDoors.remove(exitSide);
            showMessage(texts.text("door_unlock", keysHeld));
        }

        // Compute target world coordinate
        Point nextPos = new Point(worldPos);
        switch (exitSide) {
            case N -> nextPos.y -= 1;
            case S -> nextPos.y += 1;
            case W -> nextPos.x -= 1;
            case E -> nextPos.x += 1;
        }

        // In the new room, we must have a door on the opposite side (our entrance)
        Dir entranceSide = opposite(exitSide);

        // Load or create the room and guarantee the entrance
        Room nextRoom = makeOrGetRoom(nextPos, entranceSide);
        if (consumedKey) {
            nextRoom.lockedDoors.remove(entranceSide);
        }
        worldPos = nextPos;
        room = nextRoom;

        // Track exploration
        boolean isNewVisit = registerVisit(worldPos);

        // Place player just inside the entrance we came through (from the new room’s perspective)
        placePlayerJustInside(entranceSide);
        bullets.clear();
        playerBullets.clear();
        explosions.clear();

        if (isNewVisit && roomsVisited == 2) {
            BossEncounter encounter = ensureBossFor(worldPos);
            if (encounter != null) {
                prepareBossRoom(nextRoom);
                showMessage(texts.text("boss_warning", formatBossName(encounter.kind)));
            }
        } else if (isNewVisit && consumedKey) {
            BossEncounter encounter = ensureBossFor(worldPos);
            if (encounter != null) {
                prepareBossRoom(nextRoom);
                showMessage(texts.text("boss_unlock", formatBossName(encounter.kind)));
            }
        }

        checkForBossEncounter();
    }

    private static Dir opposite(Dir d) {
        return switch (d) {
            case N -> Dir.S;
            case S -> Dir.N;
            case W -> Dir.E;
            case E -> Dir.W;
        };
    }

    private static Point step(Point origin, Dir dir) {
        if (origin == null || dir == null) {
            return null;
        }
        return switch (dir) {
            case N -> new Point(origin.x, origin.y - 1);
            case S -> new Point(origin.x, origin.y + 1);
            case W -> new Point(origin.x - 1, origin.y);
            case E -> new Point(origin.x + 1, origin.y);
        };
    }

    private static boolean shouldDrawConnector(Point origin, Point neighbour) {
        if (origin == null || neighbour == null) {
            return false;
        }
        if (origin.x < neighbour.x) {
            return true;
        }
        if (origin.x > neighbour.x) {
            return false;
        }
        return origin.y < neighbour.y;
    }

    private static String pointKey(Point p) {
        if (p == null) {
            return "";
        }
        return p.x + ":" + p.y;
    }

    // ======= Render =======

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D worldGraphics = (Graphics2D) g.create();
        Graphics2D overlay = (Graphics2D) g.create();
        try {
            worldGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            overlay.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            scaleX = getWidth() / (double) (COLS * TILE);
            scaleY = getHeight() / (double) (ROWS * TILE);
            if (scaleX <= 0 || Double.isNaN(scaleX)) scaleX = 1.0;
            if (scaleY <= 0 || Double.isNaN(scaleY)) scaleY = 1.0;
            worldGraphics.scale(scaleX, scaleY);

            drawWorld(worldGraphics);
            drawHud(overlay);
        } finally {
            worldGraphics.dispose();
            overlay.dispose();
        }
    }

    private void drawWorld(Graphics2D gg) {
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int px = x * TILE, py = y * TILE;
                T t = room.g[x][y];
                if (textures != null && textures.isReady()) {
                    int fCount = Math.max(1, textures.floorVariants());
                    int fIdx = Math.floorMod(x * 17 + y * 31, fCount);
                    int wIdx = 0;
                    switch (t) {
                        case FLOOR -> gg.drawImage(textures.floorVariant(fIdx), px, py, TILE, TILE, null);
                        case WALL  -> gg.drawImage(textures.wallVariant(wIdx),  px, py, TILE, TILE, null);
                        case DOOR  -> {
                            BufferedImage doorTile = textures.doorFloor();
                            if (doorTile != null) {
                                gg.drawImage(doorTile, px, py, TILE, TILE, null);
                            } else {
                                gg.drawImage(textures.floorVariant(fIdx), px, py, TILE, TILE, null);
                            }
                            gg.setColor(new Color(220, 172, 60));
                            if (x == 0 || x == COLS - 1)
                                gg.fillRect(px + (x == 0 ? 0 : TILE - 6), py + 6, 6, TILE - 12);
                            if (y == 0 || y == ROWS - 1)
                                gg.fillRect(px + 6, py + (y == 0 ? 0 : TILE - 6), TILE - 12, 6);
                            Dir doorDir = dirForTile(x, y);
                            if (doorDir != null && room.lockedDoors.contains(doorDir)) {
                                drawPadlock(gg, px, py);
                            }
                        }
                        default -> {}
                    }
                } else {
                    gg.setColor(new Color(18, 64, 78));
                    gg.fillRect(px, py, TILE, TILE);
                }
            }
        }

        for (KeyPickup key : room.keyPickups) {
            gg.setColor(new Color(255, 215, 82));
            gg.fillOval(key.x - key.r, key.y - key.r, key.r * 2, key.r * 2);
            gg.setColor(new Color(140, 90, 30));
            gg.drawOval(key.x - key.r, key.y - key.r, key.r * 2, key.r * 2);
        }

        for (Enemy e : room.enemies) {
            if (!e.alive) continue;
            if (enemyIdleFrames != null && enemyIdleFrames.length > 0) {
                int idx = (animTick / 10) % enemyIdleFrames.length;
                gg.drawImage(enemyIdleFrames[idx], e.x - e.size/2, e.y - e.size/2, e.size, e.size, null);
            } else {
                gg.setColor(new Color(200,60,60));
                gg.fillOval(e.x - e.size/2, e.y - e.size/2, e.size, e.size);
            }
        }

        if (playerIdleFrames != null && playerIdleFrames.length > 0){
            int idx = (animTick / 10) % playerIdleFrames.length;
            gg.drawImage(playerIdleFrames[idx], player.x, player.y, player.width, player.height, null);
        } else {
            gg.setColor(new Color(255,214,102));
            gg.fillOval(player.x,player.y,player.width,player.height);
        }

        gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (Bullet b : bullets) {
            drawProjectile(gg, b, enemyShotTexture, new Color(255, 200, 120, 230));
        }
        for (Bullet b : playerBullets) {
            drawProjectile(gg, b, playerShotTexture, new Color(160, 230, 255, 230));
        }

        for (Explosion ex : explosions) {
            float t = ex.age / (float)Math.max(1, ex.life);
            int r = (int)(ex.maxR * t);
            int alpha = (int)(180 * (1.0f - t));
            alpha = Math.max(0, Math.min(255, alpha));
            gg.setComposite(AlphaComposite.SrcOver.derive(alpha/255f));
            gg.setColor(new Color(255, 200, 80));
            gg.fillOval((int)ex.x - r, (int)ex.y - r, r*2, r*2);
            gg.setColor(new Color(255, 240, 160));
            gg.drawOval((int)ex.x - r, (int)ex.y - r, r*2, r*2);
            gg.setComposite(AlphaComposite.SrcOver);
        }
    }

    private void drawHud(Graphics2D overlay) {
        overlay.setColor(new Color(255, 255, 255, 210));
        String controlsLine = String.format("Move: %s/%s/%s/%s   Shoot: %s   Reroll: %s   Pause: %s",
                keyName(ControlAction.MOVE_UP),
                keyName(ControlAction.MOVE_DOWN),
                keyName(ControlAction.MOVE_LEFT),
                keyName(ControlAction.MOVE_RIGHT),
                keyName(ControlAction.SHOOT),
                keyName(ControlAction.REROLL),
                keyName(ControlAction.PAUSE));
        overlay.drawString("HP: " + playerHP + (iFrames>0?" (invul)":""), 10, 18);
        overlay.drawString(controlsLine, 10, 34);
        overlay.drawString(String.format("Room (%d,%d)  Keys: %d", worldPos.x, worldPos.y, keysHeld), 10, 50);
        overlay.drawString(texts.text("story"), 10, 66);
        if (isBossRoom(worldPos)) {
            overlay.drawString("Guardian Lair", getWidth() - 160, 18);
        }

        Rectangle minimapArea = drawMinimap(overlay);
        overlay.setColor(new Color(255, 255, 255, 210));

        if (!statusMessage.isBlank()) {
            int boxWidth = getWidth() - 20;
            int boxHeight = 26;
            int boxX = 10;
            int boxY = getHeight() - boxHeight - 10;
            if (minimapArea != null) {
                int candidateX = minimapArea.x + minimapArea.width + 10;
                int candidateWidth = getWidth() - candidateX - 10;
                if (candidateWidth >= 220) {
                    boxX = candidateX;
                    boxWidth = candidateWidth;
                }
            }
            overlay.setColor(new Color(0, 0, 0, 160));
            overlay.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 14, 14);
            overlay.setColor(new Color(255, 255, 255, 230));
            overlay.drawString(statusMessage, boxX + 10, boxY + 18);
        }
    }

    private Rectangle drawMinimap(Graphics2D overlay) {
        if (overlay == null || worldPos == null) {
            return null;
        }
        Set<Point> visitedRooms = visited == null ? Set.of() : visited;
        Set<Point> accessible = new HashSet<>();
        Set<Point> locked = new HashSet<>();
        Set<Point> known = new HashSet<>();
        if (world != null) {
            known.addAll(world.keySet());
        }
        known.add(new Point(worldPos));
        known.addAll(visitedRooms);

        for (Point roomPos : visitedRooms) {
            Room roomData = world.get(roomPos);
            if (roomData == null) {
                continue;
            }
            for (Dir door : roomData.doors) {
                Point neighbour = step(roomPos, door);
                if (visitedRooms.contains(neighbour)) {
                    known.add(neighbour);
                    continue;
                }
                if (roomData.lockedDoors.contains(door)) {
                    locked.add(neighbour);
                } else {
                    accessible.add(neighbour);
                }
                known.add(neighbour);
            }
        }

        if (known.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point p : known) {
            if (p == null) {
                continue;
            }
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }

        if (minX == Integer.MAX_VALUE) {
            return null;
        }

        int cellsWide = Math.max(1, maxX - minX + 1);
        int cellsTall = Math.max(1, maxY - minY + 1);
        int availableWidth = Math.max(MINIMAP_CELL_MIN, MINIMAP_MAX_WIDTH - MINIMAP_HORIZONTAL_PADDING * 2);
        int availableHeight = Math.max(MINIMAP_CELL_MIN, MINIMAP_MAX_HEIGHT - MINIMAP_HEADER - MINIMAP_FOOTER);
        int cellSize = Math.max(MINIMAP_CELL_MIN,
                Math.min(MINIMAP_CELL_MAX,
                        Math.min(availableWidth / Math.max(1, cellsWide),
                                availableHeight / Math.max(1, cellsTall))));
        if (cellSize <= 0) {
            cellSize = MINIMAP_CELL_MIN;
        }

        int mapWidth = MINIMAP_HORIZONTAL_PADDING * 2 + cellSize * cellsWide;
        int mapHeight = MINIMAP_HEADER + cellSize * cellsTall + MINIMAP_FOOTER;
        int mapX = MINIMAP_MARGIN;
        int mapY = Math.max(MINIMAP_MARGIN, getHeight() - mapHeight - MINIMAP_MARGIN);
        Rectangle bounds = new Rectangle(mapX, mapY, mapWidth, mapHeight);

        Color originalColour = overlay.getColor();
        Stroke originalStroke = overlay.getStroke();

        overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        overlay.setColor(new Color(0, 0, 0, 182));
        overlay.fillRoundRect(mapX, mapY, mapWidth, mapHeight, 18, 18);
        overlay.setColor(new Color(110, 188, 204, 220));
        overlay.setStroke(new BasicStroke(1.8f));
        overlay.drawRoundRect(mapX, mapY, mapWidth, mapHeight, 18, 18);

        overlay.setColor(new Color(218, 234, 240));
        overlay.drawString(texts.text("hud_map"), mapX + MINIMAP_HORIZONTAL_PADDING, mapY + 18);

        int gridOriginX = mapX + MINIMAP_HORIZONTAL_PADDING;
        int gridOriginY = mapY + MINIMAP_HEADER;
        Map<String, Rectangle> cellRects = new HashMap<>();
        Map<String, Point> cellCenters = new HashMap<>();
        for (Point p : known) {
            if (p == null) {
                continue;
            }
            int col = p.x - minX;
            int row = p.y - minY;
            int cellX = gridOriginX + col * cellSize;
            int cellY = gridOriginY + row * cellSize;
            Rectangle rect = new Rectangle(cellX, cellY, cellSize, cellSize);
            String key = pointKey(p);
            cellRects.put(key, rect);
            cellCenters.put(key, new Point(cellX + cellSize / 2, cellY + cellSize / 2));
        }

        overlay.setStroke(new BasicStroke(Math.max(1f, cellSize / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Point p : known) {
            if (p == null) {
                continue;
            }
            Room roomData = world.get(p);
            if (roomData == null) {
                continue;
            }
            Point centre = cellCenters.get(pointKey(p));
            if (centre == null) {
                continue;
            }
            for (Dir door : roomData.doors) {
                Point neighbour = step(p, door);
                if (!shouldDrawConnector(p, neighbour)) {
                    continue;
                }
                Point neighbourCentre = cellCenters.get(pointKey(neighbour));
                if (neighbourCentre == null) {
                    continue;
                }
                Color connectorColour = new Color(88, 140, 170, 208);
                if (!visitedRooms.contains(neighbour)) {
                    if (accessible.contains(neighbour)) {
                        connectorColour = new Color(138, 201, 38, 210);
                    } else if (locked.contains(neighbour)) {
                        connectorColour = new Color(220, 170, 90, 210);
                    }
                }
                overlay.setColor(connectorColour);
                overlay.drawLine(centre.x, centre.y, neighbourCentre.x, neighbourCentre.y);
            }
        }

        int roomSize = Math.max(6, cellSize - 6);
        int offset = (cellSize - roomSize) / 2;
        for (Point p : known) {
            if (p == null) {
                continue;
            }
            Rectangle rect = cellRects.get(pointKey(p));
            if (rect == null) {
                continue;
            }
            int drawX = rect.x + offset;
            int drawY = rect.y + offset;
            Color fill;
            if (p.equals(worldPos)) {
                fill = new Color(255, 240, 160, 240);
            } else if (visitedRooms.contains(p)) {
                fill = new Color(82, 144, 182, 228);
            } else if (accessible.contains(p)) {
                fill = new Color(138, 201, 38, 220);
            } else if (locked.contains(p)) {
                fill = new Color(220, 170, 90, 220);
            } else {
                fill = new Color(80, 96, 120, 160);
            }
            overlay.setColor(fill);
            overlay.fillRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
            BossEncounter encounter = bossEncounters.get(p);
            if (encounter != null && !encounter.defeated) {
                overlay.setColor(new Color(210, 120, 200, 232));
                overlay.setStroke(new BasicStroke(2f));
                overlay.drawRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
            } else if (p.equals(worldPos)) {
                overlay.setColor(new Color(255, 255, 255, 230));
                overlay.setStroke(new BasicStroke(1.8f));
                overlay.drawRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
            }
        }

        int footerY = mapY + mapHeight - 18;
        overlay.setStroke(originalStroke);
        overlay.setColor(new Color(210, 226, 232));
        overlay.drawString(texts.text("hud_rooms", visitedRooms.size()), mapX + MINIMAP_HORIZONTAL_PADDING, footerY);
        overlay.drawString(texts.text("hud_exits", accessible.size()), mapX + MINIMAP_HORIZONTAL_PADDING, footerY + 16);
        if (!locked.isEmpty()) {
            overlay.setColor(new Color(235, 210, 160));
            overlay.drawString(texts.text("hud_locked", locked.size()), mapX + MINIMAP_HORIZONTAL_PADDING, footerY + 32);
        }

        overlay.setColor(originalColour);
        overlay.setStroke(originalStroke);
        return bounds;
    }

    private String keyName(ControlAction action) {
        return KeyEvent.getKeyText(controls.keyFor(action));
    }

    private void drawPadlock(Graphics2D gg, int px, int py) {
        int lockWidth = 12;
        int lockHeight = 14;
        int cx = px + TILE / 2 - lockWidth / 2;
        int cy = py + TILE / 2 - lockHeight / 2;
        gg.setColor(new Color(40, 32, 22, 220));
        gg.fillRoundRect(cx, cy, lockWidth, lockHeight, 4, 4);
        gg.setColor(new Color(214, 186, 90));
        gg.drawRoundRect(cx, cy, lockWidth, lockHeight, 4, 4);
        gg.drawLine(cx + lockWidth / 2, cy + 3, cx + lockWidth / 2, cy + lockHeight - 3);
    }

    // ======= Input =======

    @Override
    public void keyPressed(KeyEvent e) {
        if (matches(e, ControlAction.MOVE_UP)) {
            up = true;
        }
        if (matches(e, ControlAction.MOVE_DOWN)) {
            down = true;
        }
        if (matches(e, ControlAction.MOVE_LEFT)) {
            left = true;
        }
        if (matches(e, ControlAction.MOVE_RIGHT)) {
            right = true;
        }
        if (matches(e, ControlAction.SHOOT)) {
            shootPlayerBullet();
        }
        if (matches(e, ControlAction.REROLL)) {
            room = rerollObstacles(room);
            repaint();
        }
        if (matches(e, ControlAction.PAUSE)) {
            showPauseMenu();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (matches(e, ControlAction.MOVE_UP)) up = false;
        if (matches(e, ControlAction.MOVE_DOWN)) down = false;
        if (matches(e, ControlAction.MOVE_LEFT)) left = false;
        if (matches(e, ControlAction.MOVE_RIGHT)) right = false;
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    private boolean matches(KeyEvent e, ControlAction action) {
        int code = e.getKeyCode();
        if (code == controls.keyFor(action)) {
            return true;
        }
        return switch (action) {
            case MOVE_UP -> code == KeyEvent.VK_UP;
            case MOVE_DOWN -> code == KeyEvent.VK_DOWN;
            case MOVE_LEFT -> code == KeyEvent.VK_LEFT;
            case MOVE_RIGHT -> code == KeyEvent.VK_RIGHT;
            default -> false;
        };
    }

    private void showPauseMenu() {
        if (paused) {
            return;
        }
        paused = true;
        timer.stop();
        String[] options = {
                texts.text("resume"),
                texts.text("save_and_exit"),
                texts.text("quit_without_saving")
        };
        int choice = JOptionPane.showOptionDialog(this, texts.text("pause_title"), texts.text("pause_title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 1) {
            try {
                saveHandler.accept(snapshot());
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, "Unable to save: " + ex.getMessage(),
                        texts.text("pause_title"), JOptionPane.ERROR_MESSAGE);
                paused = false;
                timer.start();
                requestFocusInWindow();
                return;
            }
            exitHandler.run();
        } else if (choice == 2) {
            exitHandler.run();
        } else {
            paused = false;
            timer.start();
            requestFocusInWindow();
        }
    }

    public DungeonRoomsSnapshot snapshot() {
        return new DungeonRoomsSnapshot(
                world,
                bossEncounters,
                visited,
                bossPool,
                worldPos,
                roomsVisited,
                room,
                player,
                up,
                down,
                left,
                right,
                bullets,
                playerBullets,
                explosions,
                playerHP,
                iFrames,
                keysHeld,
                statusMessage,
                statusTicks,
                inBoss,
                animTick,
                mouseX,
                mouseY,
                rng,
                secureRandom
        );
    }

    public void shutdown() {
        timer.stop();
    }

    /**
     * Callback interface that allows the dungeon panel to embed boss encounters inside the host UI instead of
     * launching an extra window. Implementations are expected to present {@link BossBattlePanel} content and invoke
     * the supplied callback when the fight resolves.
     */
    public interface BossBattleHost {
        void runBossBattle(BossBattlePanel.BossKind kind, Consumer<Outcome> outcomeHandler);
    }

    private Room rerollObstacles(Room r) {
        // Clear inside (except borders & doors), then re-add obstacles
        for (int x = 1; x < COLS - 1; x++)
            for (int y = 1; y < ROWS - 1; y++)
                if (r.g[x][y] != T.DOOR) r.g[x][y] = T.FLOOR;

        for (int x = 0; x < COLS; x++) { r.g[x][0] = T.WALL; r.g[x][ROWS - 1] = T.WALL; }
        for (int y = 0; y < ROWS; y++) { r.g[0][y] = T.WALL; r.g[COLS - 1][y] = T.WALL; }

        // Choose 1–3 total doors
        int totalDoors = 1 + rng.nextInt(3); // 1..3

        Set<Dir> chosen = EnumSet.noneOf(Dir.class);
        if (r.doors != null) chosen.addAll(r.doors);

        List<Dir> pool = new ArrayList<>(List.of(Dir.N, Dir.S, Dir.W, Dir.E));
        if (r.doors != null) pool.removeAll(r.doors);
        Collections.shuffle(pool, rng);
        for (Dir d : pool) {
            if (chosen.size() >= totalDoors) break;
            chosen.add(d);
        }
        // Materialize doors
        for (Dir d : chosen) carveDoorOnGrid(r, d);
        r.doors = chosen;

        // Sprinkle obstacles while keeping space near doors
        int blocks = 8 + rng.nextInt(7);
        for (int i = 0; i < blocks; i++) {
            int bx = 2 + rng.nextInt(COLS - 4);
            int by = 2 + rng.nextInt(ROWS - 4);
            if (nearAnyDoor(r, bx, by, 3)) continue;
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 0; dy < 2; dy++)
                    if (inBounds(bx + dx, by + dy))
                        r.g[bx + dx][by + dy] = T.WALL;
        }
        return r;
    }

    // ---- Main launcher ----
    public static void main(String[] args) {
        GameLauncher.main(args);
    }
}
