package World;

import World.gfx.DungeonTextures;
import Battle.scene.BossBattlePanel;
import Battle.scene.BossBattlePanel.Outcome;
import security.GameSecurity;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;

import java.awt.geom.GeneralPath;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Zelda-style, one-room view with persistent rooms.
 * - Arrow keys / WASD to move (no diagonal movement).
 * - Walking through an edge door loads the adjacent room.
 *   Existing rooms are remembered; backtracking restores the exact same room.
 * - Each new room starts with 1–3 doors (entrance counts as one).
 * - R = reroll current room (keeps doors the same but re-rolls obstacles)
 * - Esc = quit
 */
public class ZeldaRooms extends JPanel implements ActionListener, KeyListener {

    // ----- Tunables -----
    static final int TILE = 36;           // pixels per tile
    static final int COLS = 21;           // room width (odd looks nice)
    static final int ROWS = 13;           // room height (odd looks nice)
    static final int FPS  = 60;
    static final int PLAYER_SIZE = (int)(TILE * 0.6);
    static final int PLAYER_SPEED = 4;    // px per tick
    static final Color BG = new Color(6, 24, 32);

    enum T { VOID, FLOOR, WALL, DOOR }
    enum Dir { N, S, W, E }

    static class Enemy {
        int x, y;
        int size = (int)(TILE * 0.6);
        int cd = 0;
    }

    static class Bullet {
        double x, y;
        double vx, vy;
        int r = 4;
        boolean alive = true;
    }

    static class Explosion {
        double x, y;
        int age = 0;
        int life = 18;
        int maxR = 22;
    }

    static class KeyPickup {
        int x, y;
        int r = 12;
    }

    static class BossEncounter {
        BossBattlePanel.BossKind kind;
        boolean defeated = false;
        boolean rewardClaimed = false;
    }

    static class Room {
        T[][] g = new T[COLS][ROWS];
        Set<Dir> doors = EnumSet.noneOf(Dir.class);
        List<Enemy> enemies = new ArrayList<>();
        List<KeyPickup> keyPickups = new ArrayList<>();
        EnumSet<Dir> lockedDoors = EnumSet.noneOf(Dir.class);
        boolean cleared = false;
        boolean keyDropped = false;
        Room() {
            for (int x = 0; x < COLS; x++)
                for (int y = 0; y < ROWS; y++)
                    g[x][y] = T.VOID;
        }
    }

    private static final String STORY_TEXT = "Quest: Rescue Princess Elara from the Verdant Depths";
    private static final int MESSAGE_DURATION = FPS * 3;

    private final Timer timer = new Timer(1000 / FPS, this);
    private final Random rng = new Random();
    private final SecureRandom secureRandom = GameSecurity.secureRandom();

    // Persistent world: integer-grid of rooms using world coordinates
    private final Map<Point, Room> world = new HashMap<>();
    private final Map<Point, BossEncounter> bossEncounters = new HashMap<>();
    private final Set<Point> visited = new HashSet<>();
    private final List<BossBattlePanel.BossKind> bossPool = new ArrayList<>();
    private Point worldPos = new Point(0, 0);   // current room coordinate
    private int roomsVisited = 1;

    private Room room;                 // current room
    private Rectangle player;          // player rectangle in pixels
    private boolean up, down, left, right;

    // Optional textures loader (replaces the old tilesheet code)
    private DungeonTextures textures;

    // Sprites from resources for player/enemies
    private BufferedImage[] playerIdleFrames;   // Knight male idle
    private BufferedImage[] enemyIdleFrames;    // Imp idle
    private BufferedImage[] bossIdleFrames;     // Big zombie idle
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

    public ZeldaRooms() {
        GameSecurity.verifyIntegrity();
        initializeBossPool();

        setPreferredSize(new java.awt.Dimension(COLS * TILE, ROWS * TILE));
        setBackground(BG);
        setFocusable(true);
        addKeyListener(this);

        // Create the starting room (center of the world)
        room = makeOrGetRoom(worldPos, null);
        spawnEnemiesIfNeeded(worldPos, room);
        placePlayerAtCenter();
        timer.start();

        // New: try to load textures (falls back to flat colors if not found)
        textures = DungeonTextures.autoDetect();

        // Load character sprite sequences from src/resources (filesystem)
        playerIdleFrames = loadSequenceFS("src/resources/sprites/Knight/Idle/knight_m_idle_anim_f", 0, 3);
        enemyIdleFrames  = loadSequenceFS("src/resources/sprites/Imp/imp_idle_anim_f", 0, 3);
        bossIdleFrames   = loadSequenceFS("src/resources/sprites/Bigzombie/big_zombie_idle_anim_f", 0, 3);

        visited.add(new Point(worldPos));
        showMessage("The king begs: save Princess Elara hidden deep within!");

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e){ mouseX=e.getX(); mouseY=e.getY(); }
            @Override public void mouseDragged(MouseEvent e){ mouseX=e.getX(); mouseY=e.getY(); }
        });
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
        if (r == null || !r.enemies.isEmpty() || r.cleared) return;
        if (isBossRoom(pos)) return;
        int count = 4 + rng.nextInt(4);
        for (int i = 0; i < count; i++) {
            int tries = 0;
            while (tries++ < 50) {
                int tx = 2 + rng.nextInt(COLS - 4);
                int ty = 2 + rng.nextInt(ROWS - 4);
                if (r.g[tx][ty] != T.FLOOR) continue;
                int px = tx * TILE + TILE/2;
                int py = ty * TILE + TILE/2;
                if (!isRectFree(r, px, py, (int)(TILE*0.3))) continue;
                if (player != null && (Math.abs(px - (player.x+player.width/2)) + Math.abs(py - (player.y+player.height/2)) < TILE*4)) continue;
                Enemy e = new Enemy();
                e.x = px; e.y = py; e.cd = rng.nextInt(30);
                r.enemies.add(e);
                break;
            }
        }
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
        dropKeyIfEligible(room);

        int pcx = player.x + player.width/2;
        int pcy = player.y + player.height/2;

        for (Enemy e : room.enemies) {
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
                int ex0 = e.x - e.size/2, ey0 = e.y - e.size/2;
                int ex1 = ex0 + e.size, ey1 = ey0 + e.size;
                if (b.x >= ex0 && b.x <= ex1 && b.y >= ey0 && b.y <= ey1) {
                    b.alive = false; explosions.add(makeExplosion(b.x, b.y));
                    // remove enemy on hit
                    e.size = 0; // mark for removal
                }
            }
        }
        playerBullets.removeIf(bb -> !bb.alive);
        room.enemies.removeIf(e -> e.size <= 0);
        dropKeyIfEligible(room);

        // explosions advance
        for (Explosion ex : explosions) ex.age++;
        explosions.removeIf(ex -> ex.age >= ex.life);
    }

    private void dropKeyIfEligible(Room r) {
        if (r == null) return;
        if (!r.enemies.isEmpty()) return;
        if (r.keyDropped) return;
        if (isBossRoom(worldPos)) return;
        r.cleared = true;
        dropKey(r);
    }

    private void dropKey(Room r) {
        if (r == null || r.keyDropped) return;
        KeyPickup key = new KeyPickup();
        Point spot = safePlayerSpawn(r, COLS / 2 * TILE + TILE / 2, ROWS / 2 * TILE + TILE / 2);
        key.x = spot.x;
        key.y = spot.y;
        r.keyPickups.add(key);
        r.keyDropped = true;
        showMessage("A shimmering key drops to the ground!");
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
                room.cleared = true;
                showMessage("You obtained a dungeon key! Keys: " + keysHeld);
            }
        }
    }

    private void checkForBossEncounter() {
        if (inBoss) return;
        BossEncounter encounter = bossEncounters.get(worldPos);
        if (encounter != null && !encounter.defeated) {
            showMessage("Boss challenge: " + formatBossName(encounter.kind));
            triggerBossEncounter(encounter);
        }
    }

    private void showMessage(String message) {
        if (message == null || message.isBlank()) {
            statusMessage = "";
            statusTicks = 0;
        } else {
            statusMessage = message;
            statusTicks = MESSAGE_DURATION;
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
        targetRoom.keyDropped = true;
    }

    private void grantBossReward(BossEncounter encounter) {
        if (encounter == null || encounter.rewardClaimed) return;
        encounter.rewardClaimed = true;
        keysHeld++;
        showMessage("Victory! You claimed a royal key. Keys: " + keysHeld);
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
            showMessage("You awaken at the gate. Princess Elara still needs you!");
            repaint();
            requestFocusInWindow();
        });
    }

    private void triggerBossEncounter(BossEncounter encounter) {
        if (encounter == null) return;
        inBoss = true;
        timer.stop();
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Boss Battle");
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setContentPane(BossBattlePanel.create(encounter.kind, outcome -> {
                if (outcome == Outcome.HERO_WIN) {
                    encounter.defeated = true;
                    grantBossReward(encounter);
                } else {
                    showMessage("The boss repelled you! Regroup at the entrance.");
                    onPlayerDeath();
                }
                f.dispose();
                inBoss = false;
                iFrames = 60; // grace on return
                timer.start();
                requestFocusInWindow();
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
        b.r = 4;
        playerBullets.add(b);
    }

    // ---- file-system sprite loading ----
    private static BufferedImage[] loadSequenceFS(String prefix, int from, int toInclusive) {
        java.util.List<BufferedImage> list = new java.util.ArrayList<>();
        for (int i = from; i <= toInclusive; i++) {
            File f = new File(prefix + i + ".png");
            if (f.exists()) {
                try {
                    list.add(ImageIO.read(f));
                } catch (Exception ignored) {}
            }
        }
        return list.isEmpty() ? null : list.toArray(new BufferedImage[0]);
    }

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
                showMessage("The door is locked. You need a key.");
                return;
            }
            keysHeld--;
            consumedKey = true;
            room.lockedDoors.remove(exitSide);
            showMessage("You unlock the door. Keys left: " + keysHeld);
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
                showMessage("A boss stands in your way: " + formatBossName(encounter.kind));
            }
        } else if (isNewVisit && consumedKey) {
            BossEncounter encounter = ensureBossFor(worldPos);
            if (encounter != null) {
                prepareBossRoom(nextRoom);
                showMessage("The unlocked path reveals a boss: " + formatBossName(encounter.kind));
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

    // ======= Render =======

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D gg = (Graphics2D) g;
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Tiles
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int px = x * TILE, py = y * TILE;
                T t = room.g[x][y];
                if (textures != null && textures.isReady()) {
                    int fCount = Math.max(1, textures.floorVariants());
                    // Simple deterministic hash per tile for variety
                    int fIdx = Math.floorMod(x * 17 + y * 31, fCount);
                    int wIdx = 0; // force wall_1 only
                    switch (t) {
                        case FLOOR -> gg.drawImage(textures.floorVariant(fIdx), px, py, TILE, TILE, null);
                        case WALL  -> gg.drawImage(textures.wallVariant(wIdx),  px, py, TILE, TILE, null);
                        case DOOR  -> {
                            gg.drawImage(textures.floorVariant(fIdx), px, py, TILE, TILE, null);
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
                    switch (t) {
                        case FLOOR -> {
                            gg.setColor(new Color(18, 64, 78));
                            gg.fillRect(px, py, TILE, TILE);
                            gg.setColor(new Color(10, 45, 55));
                            gg.drawRect(px, py, TILE, TILE);
                        }
                        case WALL -> {
                            gg.setColor(new Color(36, 132, 156));
                            gg.fillRect(px, py, TILE, TILE);
                        }
                        case DOOR -> {
                            gg.setColor(new Color(18, 64, 78));
                            gg.fillRect(px, py, TILE, TILE);
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
                }
            }
        }

        // Keys
        for (KeyPickup key : room.keyPickups) {
            gg.setColor(new Color(255, 215, 82));
            gg.fillOval(key.x - key.r, key.y - key.r, key.r * 2, key.r * 2);
            gg.setColor(new Color(140, 90, 30));
            gg.drawOval(key.x - key.r, key.y - key.r, key.r * 2, key.r * 2);
            gg.drawLine(key.x + key.r / 2, key.y, key.x + key.r + 2, key.y);
            gg.drawLine(key.x + key.r - 2, key.y - 2, key.x + key.r + 4, key.y - 2);
            gg.drawLine(key.x + key.r - 2, key.y + 2, key.x + key.r + 4, key.y + 2);
        }

        // Enemies
        for (Enemy e : room.enemies) {
            if (enemyIdleFrames != null && enemyIdleFrames.length > 0) {
                int idx = (animTick / 10) % enemyIdleFrames.length;
                gg.drawImage(enemyIdleFrames[idx], e.x - e.size/2, e.y - e.size/2, e.size, e.size, null);
            } else {
                gg.setColor(new Color(200,60,60));
                gg.fillOval(e.x - e.size/2, e.y - e.size/2, e.size, e.size);
                gg.setColor(Color.BLACK);
                gg.drawOval(e.x - e.size/2, e.y - e.size/2, e.size, e.size);
            }
        }

        // Player
        if (playerIdleFrames != null && playerIdleFrames.length > 0){
            int idx = (animTick / 10) % playerIdleFrames.length;
            gg.drawImage(playerIdleFrames[idx], player.x, player.y, player.width, player.height, null);
        } else {
            gg.setColor(new Color(255,214,102)); gg.fillOval(player.x,player.y,player.width,player.height);
            gg.setColor(Color.BLACK); gg.drawOval(player.x,player.y,player.width,player.height);
        }

        // Bullets (enemy and player)
        gg.setColor(new Color(255,240,120));
        for (Bullet b : bullets) if (b.alive) gg.fillOval((int)(b.x - b.r), (int)(b.y - b.r), b.r*2, b.r*2);
        gg.setColor(new Color(120,210,255));
        for (Bullet b : playerBullets) if (b.alive) gg.fillOval((int)(b.x - b.r), (int)(b.y - b.r), b.r*2, b.r*2);

        // Explosions
        for (Explosion ex : explosions) {
            float t = ex.age / (float)Math.max(1, ex.life);
            int r = (int)(ex.maxR * t);
            int alpha = (int)(180 * (1.0f - t));
            alpha = Math.max(0, Math.min(255, alpha));
            ((Graphics2D)g).setComposite(AlphaComposite.SrcOver.derive(alpha/255f));
            gg.setColor(new Color(255, 200, 80));
            gg.fillOval((int)ex.x - r, (int)ex.y - r, r*2, r*2);
            gg.setColor(new Color(255, 240, 160));
            gg.drawOval((int)ex.x - r, (int)ex.y - r, r*2, r*2);
            ((Graphics2D)g).setComposite(AlphaComposite.SrcOver);
        }

        // HUD
        gg.setColor(new Color(255, 255, 255, 190));
        gg.drawString("HP: " + playerHP + (iFrames>0?" (invul)":"") + "   Move: Arrows/WASD (no diagonal)   Space: shoot   R: reroll obstacles   Esc: quit", 10, 18);
        gg.drawString("Room (" + worldPos.x + "," + worldPos.y + ")  Doors: " + room.doors
                + "  Enemies: " + room.enemies.size()
                + "  Bullets: " + (bullets.size() + playerBullets.size()), 10, 34);
        gg.drawString("Keys: " + keysHeld, getWidth() - 120, 34);
        gg.drawString(STORY_TEXT, 10, 52);
        gg.drawString("Mode: Endless | Unlock doors to face random bosses", 10, 68);
        if (isBossRoom(worldPos)) gg.drawString("Boss Chamber", getWidth()-140, 18);
        if (!statusMessage.isBlank()) {
            int boxWidth = getWidth() - 20;
            int boxHeight = 26;
            int boxX = 10;
            int boxY = getHeight() - boxHeight - 10;
            gg.setColor(new Color(0, 0, 0, 160));
            gg.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);
            gg.setColor(new Color(255, 255, 255, 220));
            gg.drawString(statusMessage, boxX + 10, boxY + boxHeight - 8);
        }
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
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = true;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = true;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = true;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = true;
            case KeyEvent.VK_SPACE -> shootPlayerBullet();
            case KeyEvent.VK_R -> {            // reroll current room obstacles, keep doors
                room = rerollObstacles(room);
                repaint();
            }
            case KeyEvent.VK_ESCAPE -> System.exit(0);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = false;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = false;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = false;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { }

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
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JFrame f = new javax.swing.JFrame("Zelda Rooms");
            ZeldaRooms p = new ZeldaRooms();
            f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            f.setContentPane(p);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setResizable(false);
            f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}
