package World;

import Battle.domain.Stats;
import Battle.scene.BossBattlePanel;
import Battle.scene.BossBattlePanel.Outcome;
import World.DialogueText;
import World.cutscene.CutsceneDialog;
import World.cutscene.CutsceneLibrary;
import World.cutscene.CutsceneScript;
import World.cutscene.ShopDialog;
import World.trap.Animation;
import World.trap.BaseTrap;
import World.trap.FireVentTrap;
import World.trap.Player;
import World.trap.SawTrap;
import World.trap.SpikeTrap;
import World.trap.SpriteLoader;
import World.trap.Trap;
import World.trap.TrapManager;
import World.gfx.CharacterSkinLibrary;
import World.gfx.DungeonTextures;
import gfx.HiDpiScaler;
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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
import java.util.function.Supplier;

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
    static final int PLAYER_SPEED = 3;    // px per tick (tempered for slower pacing)
    static final Color BG = new Color(6, 24, 32);
    static final int PLAYER_PROJECTILE_RADIUS = 6;
    static final int ENEMY_PROJECTILE_RADIUS = 5;

    private static final int MINIMAP_MARGIN = 16;
    private static final int MINIMAP_CELL_MIN = 10;
    private static final int MINIMAP_CELL_MAX = 26;
    private static final int MINIMAP_MAX_WIDTH = 220;
    private static final int MINIMAP_MAX_HEIGHT = 220;
    private static final int MINIMAP_HEADER = 26;
    private static final int MINIMAP_FOOTER = 92;
    private static final int MINIMAP_HORIZONTAL_PADDING = 12;

    private static final int DASH_DURATION_TICKS = 12;
    private static final int DASH_COOLDOWN_TICKS = 90;
    private static final double DASH_SPEED = PLAYER_SPEED * 3.6;
    private static final double DASH_INVUL_SECONDS = 0.25;
    private static final int PARRY_WINDOW_TICKS = 18;
    private static final int PARRY_COOLDOWN_TICKS = 120;
    private static final int PARRY_FLASH_TICKS = 20;
    private static final int SPECIAL_COOLDOWN_TICKS = 360;
    private static final int COMBO_DECAY_TICKS = 180;
    private static final int MAX_COMBO_LEVEL = 6;
    private static final double COMBO_DAMAGE_STEP = 0.18;
    private static final int PLAYER_SHOT_BASE_COOLDOWN = 14;
    private static final int PLAYER_SHOT_MIN_COOLDOWN = 4;

    enum T { VOID, FLOOR, WALL, DOOR }
    enum Dir { N, S, W, E }

    enum EnemyType { ZOMBIE, IMP, KNIGHT, OGRE, PUMPKIN, SKELETON, WIZARD, NECROMANCER, BARD }

    enum WeaponType { CLAWS, SWORD, HAMMER, BOW, STAFF }

    enum ProjectileKind { ORB, ARROW }

    enum TrapKind { SAW, SPIKE, FIRE_VENT }

    enum AreaAbility { FIRE_RING, LIGHTNING_PULSE }

    public enum Difficulty { EASY, HARD }

    private enum MinimapRoomKind { CURRENT, BOSS_ACTIVE, BOSS_DEFEATED, SHOP, VISITED, ACCESSIBLE, LOCKED, UNKNOWN }

    private static final List<BossBattlePanel.BossKind> STORY_BOSS_SEQUENCE = List.of(
            BossBattlePanel.BossKind.GOLLUM,
            BossBattlePanel.BossKind.GRIM,
            BossBattlePanel.BossKind.FIRE_FLINGER,
            BossBattlePanel.BossKind.GOLD_MECH,
            BossBattlePanel.BossKind.PURPLE_EMPRESS,
            BossBattlePanel.BossKind.THE_WELCH,
            BossBattlePanel.BossKind.TOXIC_TREE,
            BossBattlePanel.BossKind.GOLDEN_KNIGHT
    );

    static class RoomEnemy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int x, y;
        int size = (int)(TILE * 0.6);
        int cd = 0;
        boolean alive = true;
        EnemyType type = EnemyType.ZOMBIE;
        int maxHealth = 3;
        int health = 3;
        int braceTicks = 0;
        int windup = 0;
        int patternIndex = 0;
        double damageBuffer = 0.0;
        EnemySpawn spawn;
        WeaponType weapon = WeaponType.CLAWS;
        int attackAnimTicks = 0;
        int attackAnimDuration = 0;
        int bowDrawTicks = 0;
        double facingAngle = 0.0;
        double weaponAngle = 0.0;
        int coinReward = 0;
        double damageMultiplier = 1.0;
        double speedMultiplier = 1.0;
        int buffTicks = 0;
        int supportTicks = 0;
    }

    static class Bullet implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        double x, y;
        double vx, vy;
        int r = ENEMY_PROJECTILE_RADIUS;
        boolean alive = true;
        double damage = 1.0;
        int life = 0;
        int maxLife = 420;
        boolean friendly = false;
        boolean useTexture = true;
        java.awt.Color tint;
        boolean explosive = false;
        int explosionRadius = 0;
        int explosionLife = 0;
        ProjectileKind kind = ProjectileKind.ORB;
    }

    static class Explosion implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        double x, y;
        int age = 0;
        int life = 18;
        int maxR = 22;
        Color inner = new Color(255, 200, 80);
        Color outer = new Color(255, 240, 160);
    }

    static class KeyPickup implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int x, y;
        int r = 12;
    }

    static class CoinPickup implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int x, y;
        int r = 10;
        int value = 1;
        int animTick = 0;
    }

    static class EnemySpawn implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int x, y;
        boolean defeated = false;
        EnemyType type = EnemyType.ZOMBIE;
    }

    static class BossEncounter implements Serializable {
        @Serial
        private static final long serialVersionUID = 3L;
        BossBattlePanel.BossKind kind;
        boolean defeated = false;
        boolean rewardClaimed = false;
        boolean preludeShown = false;
        int requiredVitalityLevel = 0;
    }

    static class Room implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        T[][] g = new T[COLS][ROWS];
        Set<Dir> doors = EnumSet.noneOf(Dir.class);
        List<RoomEnemy> enemies = new ArrayList<>();
        List<KeyPickup> keyPickups = new ArrayList<>();
        List<CoinPickup> coinPickups = new ArrayList<>();
        List<EnemySpawn> enemySpawns = new ArrayList<>();
        EnumSet<Dir> lockedDoors = EnumSet.noneOf(Dir.class);
        boolean cleared = false;
        boolean spawnsPrepared = false;
        List<RoomTrap> trapSpawns = new ArrayList<>();
        boolean trapsPrepared = false;
        int trapSeed = 0;
        transient TrapManager trapManager;
        int floorThemeSeed = 0;
        int wallThemeSeed = 0;
        int paletteIndex = -1;
        int accentSeed = 0;
        Dir shopDoor = null;
        boolean shopVisited = false;
        transient boolean backgroundDirty = true;
        transient int cachedTextureEpoch = -1;
        transient BufferedImage cachedBackground;
        Room() {
            for (int x = 0; x < COLS; x++)
                for (int y = 0; y < ROWS; y++)
                    g[x][y] = T.VOID;
        }
    }

    static class RoomTrap implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        TrapKind kind = TrapKind.SAW;
        int x;
        int y;
        int width = TILE;
        int height = TILE;
        String animationFolder;
        double frameDuration = 0.08;
        double cycleSeconds = 2.0;
        double activeFraction = 0.5;
        double burstEvery = 3.0;
        double burstDuration = 1.0;
        int damageOverride = 0;
        double contactCooldownOverride = -1.0;
    }

    private final class TrapAwarePlayer implements Player {
        private final Rectangle bounds = new Rectangle();
        private double extraInvulnerability = 0.0;

        void syncFromDungeon() {
            if (player != null) {
                bounds.setBounds(player);
            } else {
                bounds.setBounds(0, 0, 0, 0);
            }
        }

        void reset() {
            extraInvulnerability = 0.0;
            syncFromDungeon();
        }

        @Override
        public Rectangle getBounds() {
            return new Rectangle(bounds);
        }

        @Override
        public boolean isInvulnerable() {
            return extraInvulnerability > 0.0 || iFrames > 0;
        }

        @Override
        public void grantInvulnerability(double seconds) {
            if (!Double.isFinite(seconds) || seconds <= 0) {
                return;
            }
            extraInvulnerability = Math.max(extraInvulnerability, seconds);
            int frames = (int) Math.ceil(seconds / tickSeconds());
            iFrames = Math.max(iFrames, frames);
        }

        @Override
        public void takeDamage(int damage, String source) {
            if (damage <= 0) {
                return;
            }
            applyTrapDamage(damage, source);
        }

        @Override
        public void update(double dt) {
            if (extraInvulnerability > 0.0 && Double.isFinite(dt) && dt > 0.0) {
                extraInvulnerability = Math.max(0.0, extraInvulnerability - dt);
            }
        }
    }

    static class RoomPalette {
        final Color floorTint;
        final float floorAlpha;
        final Color floorAccent;
        final Color wallTint;
        final float wallAlpha;
        final Color wallHighlight;
        final Color wallShadow;

        RoomPalette(Color floorTint,
                    float floorAlpha,
                    Color floorAccent,
                    Color wallTint,
                    float wallAlpha,
                    Color wallHighlight,
                    Color wallShadow) {
            this.floorTint = floorTint;
            this.floorAlpha = floorAlpha;
            this.floorAccent = floorAccent;
            this.wallTint = wallTint;
            this.wallAlpha = wallAlpha;
            this.wallHighlight = wallHighlight;
            this.wallShadow = wallShadow;
        }
    }

    private static final RoomPalette[] ROOM_PALETTES = {
            new RoomPalette(
                    new Color(36, 90, 120), 0.26f,
                    new Color(90, 140, 170, 140),
                    new Color(20, 36, 54), 0.34f,
                    new Color(220, 240, 255, 130),
                    new Color(12, 20, 28, 150)
            ),
            new RoomPalette(
                    new Color(88, 48, 120), 0.24f,
                    new Color(150, 100, 180, 130),
                    new Color(40, 22, 58), 0.36f,
                    new Color(240, 220, 255, 120),
                    new Color(18, 8, 26, 170)
            ),
            new RoomPalette(
                    new Color(70, 96, 42), 0.22f,
                    new Color(130, 168, 94, 130),
                    new Color(32, 44, 24), 0.32f,
                    new Color(210, 232, 190, 120),
                    new Color(18, 24, 16, 150)
            ),
            new RoomPalette(
                    new Color(120, 76, 32), 0.24f,
                    new Color(176, 120, 60, 140),
                    new Color(54, 32, 16), 0.34f,
                    new Color(240, 214, 170, 120),
                    new Color(28, 16, 10, 160)
            ),
            new RoomPalette(
                    new Color(48, 72, 110), 0.28f,
                    new Color(120, 150, 190, 130),
                    new Color(26, 34, 54), 0.30f,
                    new Color(225, 230, 250, 130),
                    new Color(14, 18, 28, 160)
            )
    };

    private static final int MESSAGE_SECONDS = 3;
    private static final int BASE_PLAYER_HP = 6;
    private static final int VITALITY_STEP = 2;
    private static final int DUNGEON_HEART_STEP = 1;
    private static final double BASE_PLAYER_DAMAGE = 1.0;
    private static final double DAMAGE_STEP = 0.35;
    private static final int[] DAMAGE_THRESHOLDS = {10, 24, 45, 72, 110};
    private static final int MAX_VITALITY_UPGRADES = 6;
    private static final int MAX_DUNGEON_HEART_UPGRADES = 6;
    private static final int MIN_EXPLORED_ROOMS_BEFORE_BOSS = 6;
    private static final int SAFE_RING_RADIUS = 3;
    private static final int SAFE_ROOMS_BEFORE_TRAPS = 3;
    private static final int MIN_VITALITY_FOR_BOSS_SPAWN = 2;
    private static final int MAX_PENDING_BOSS_DOORS = 1;
    private static final int HEAL_FLASH_TICKS = FPS * 2;
    private static final String PLAYER_IDLE_PREFIX = "resources/sprites/Knight/Idle/knight_m_idle_anim_f";
    private static final String ENEMY_IDLE_PREFIX = "resources/sprites/Imp/imp_idle_anim_f";
    private static final String BOSS_IDLE_PREFIX = "resources/sprites/Bigzombie/big_zombie_idle_anim_f";
    private static final String SAW_TRAP_FOLDER = "resources/traps/Saw Trap/idle";
    private static final String SPIKE_TRAP_FOLDER = "resources/traps/Spike Trap/cycle";
    private static final String FIRE_TRAP_FOLDER = "resources/traps/Fire Trap/attack";

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
    private Map<EnemyType, BufferedImage[]> enemyIdleAnimations = new EnumMap<>(EnemyType.class);
    private transient Map<WeaponType, BufferedImage> weaponTextures = new EnumMap<>(WeaponType.class);
    private transient Map<WeaponCacheKey, BufferedImage> scaledWeaponCache = new HashMap<>();
    private transient Map<Integer, BufferedImage> scaledArrowCache = new HashMap<>();
    private transient BufferedImage arrowTexture;
    private BufferedImage[] defaultEnemyFrames;
    private BufferedImage[] bossIdleFrames;
    private BufferedImage playerShotTexture;
    private BufferedImage enemyShotTexture;
    private int animTick = 0;
    private int mouseX = COLS * TILE / 2, mouseY = ROWS * TILE / 2;
    private final List<Bullet> bullets = new ArrayList<>();       // enemy bullets
    private final List<Bullet> playerBullets = new ArrayList<>(); // player bullets
    private final List<Explosion> explosions = new ArrayList<>();
    private int playerHP = BASE_PLAYER_HP;
    private int iFrames = 0;
    private int healTicks = 0;
    private double playerDamageBuffer = 0.0;
    private int keysHeld = 0;
    private int coins = 0;
    private String statusMessage = "";
    private int statusTicks = 0;
    private volatile boolean inBoss = false;
    private boolean paused;
    private boolean overlayActive;
    private Dimension renderSize;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private final ShopManager shopManager = new ShopManager();
    private Dir lastShopDoorway = null;
    private boolean awaitingShopExitClear = false;
    private final TrapAwarePlayer trapPlayer = new TrapAwarePlayer();
    private boolean suppressNextMovementPress = false;
    private long suppressMovementDeadlineNanos = 0L;
    private boolean introShown = false;
    private boolean goldenKnightIntroShown = false;
    private boolean queenRescued = false;
    private boolean finaleShown = false;
    private int textureEpoch = 0;
    private int vitalityUpgrades = 0;
    private int dungeonHeartUpgrades = 0;
    private int damageLevel = 0;
    private int enemiesDefeated = 0;
    private int bossesDefeated = 0;
    private Difficulty difficulty = Difficulty.EASY;
    private Point checkpointRoom = new Point(0, 0);
    private BossBattlePanel.BossKind checkpointBoss = null;
    private boolean gameOverShown = false;

    private int dashTicks = 0;
    private int dashCooldownTicks = 0;
    private double dashDirX = 0.0;
    private double dashDirY = 0.0;
    private int parryWindowTicks = 0;
    private int parryCooldownTicks = 0;
    private int parryFlashTicks = 0;
    private int specialCooldownTicks = 0;
    private AreaAbility nextAreaAbility = AreaAbility.FIRE_RING;
    private int comboCount = 0;
    private int comboTimerTicks = 0;
    private int comboLevel = 0;
    private int playerShotCooldownTicks = 0;
    private String lastDamageCause = "Unknown mishap";

    public DungeonRooms(GameSettings settings,
                        ControlsProfile controls,
                        LanguageBundle texts,
                        Consumer<DungeonRoomsSnapshot> saveHandler,
                        Runnable exitHandler,
                        DungeonRoomsSnapshot snapshot,
                        BossBattleHost bossBattleHost,
                        Difficulty difficulty) {
        GameSecurity.verifyIntegrity();
        this.settings = settings == null ? new GameSettings() : new GameSettings(settings);
        this.controls = controls == null ? new ControlsProfile() : new ControlsProfile(controls);
        this.texts = texts == null ? new LanguageBundle(this.settings.language()) : texts;
        this.saveHandler = saveHandler == null ? snapshotIgnored -> { } : saveHandler;
        this.exitHandler = exitHandler == null ? () -> { } : exitHandler;
        this.bossBattleHost = bossBattleHost;
        this.difficulty = difficulty == null ? Difficulty.EASY : difficulty;
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
        vitalityUpgrades = 0;
        dungeonHeartUpgrades = 0;
        damageLevel = 0;
        enemiesDefeated = 0;
        bossesDefeated = 0;
        checkpointRoom = new Point(worldPos);
        checkpointBoss = null;
        gameOverShown = false;
        dashTicks = 0;
        dashCooldownTicks = 0;
        dashDirX = 0.0;
        dashDirY = 0.0;
        parryWindowTicks = 0;
        parryCooldownTicks = 0;
        parryFlashTicks = 0;
        specialCooldownTicks = 0;
        nextAreaAbility = AreaAbility.FIRE_RING;
        comboCount = 0;
        comboTimerTicks = 0;
        comboLevel = 0;
        playerShotCooldownTicks = 0;
        lastDamageCause = "Unknown mishap";
        playerHP = playerMaxHp();
        iFrames = 0;
        playerDamageBuffer = 0.0;
        keysHeld = 0;
        coins = 0;
        statusMessage = "";
        statusTicks = 0;
        inBoss = false;
        paused = false;
        shopManager.reset();
        goldenKnightIntroShown = false;
        queenRescued = false;
        finaleShown = false;
        introShown = false;
        refreshArtAssets();
        initializeBossPool();
        room = makeOrGetRoom(worldPos, null);
        ensureTrapLayoutForRoom(worldPos, room);
        spawnEnemiesIfNeeded(worldPos, room);
        placePlayerAtCenter();
        trapPlayer.reset();
        visited.add(new Point(worldPos));
        shopManager.ensureDoorway(room, worldPos, this::hasRoomAt, this::carveDoorOnGrid, this::markRoomDirty);
        showMessage(texts.text("intro"));
        persistProgressAsync("initial run");
        SwingUtilities.invokeLater(this::playPrologueIfNeeded);
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
        ensureRoomTheme(room);
        normalizeEnemyState(room);
        ensureTrapLayoutForRoom(worldPos, room);
        trapPlayer.reset();
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
        playerDamageBuffer = snapshot.playerDamageBuffer();
        keysHeld = snapshot.keysHeld();
        coins = Math.max(0, snapshot.coins());
        vitalityUpgrades = Math.min(MAX_VITALITY_UPGRADES, Math.max(0, snapshot.vitalityLevel()));
        dungeonHeartUpgrades = Math.min(MAX_DUNGEON_HEART_UPGRADES, Math.max(0, snapshot.dungeonLevel()));
        damageLevel = Math.max(0, snapshot.damageLevel());
        enemiesDefeated = Math.max(0, snapshot.enemiesDefeated());
        bossesDefeated = Math.max(0, snapshot.bossesDefeated());
        difficulty = snapshot.difficulty();
        dashTicks = Math.max(0, snapshot.dashTicks());
        dashCooldownTicks = Math.max(0, snapshot.dashCooldownTicks());
        dashDirX = snapshot.dashDirX();
        dashDirY = snapshot.dashDirY();
        parryWindowTicks = Math.max(0, snapshot.parryWindowTicks());
        parryCooldownTicks = Math.max(0, snapshot.parryCooldownTicks());
        parryFlashTicks = Math.max(0, snapshot.parryFlashTicks());
        specialCooldownTicks = Math.max(0, snapshot.specialCooldownTicks());
        nextAreaAbility = snapshot.nextAreaAbility();
        comboCount = Math.max(0, snapshot.comboCount());
        comboTimerTicks = Math.max(0, snapshot.comboTimerTicks());
        comboLevel = Math.max(0, Math.min(MAX_COMBO_LEVEL, snapshot.comboLevel()));
        playerShotCooldownTicks = Math.max(0, snapshot.playerShotCooldownTicks());
        lastDamageCause = snapshot.lastDamageCause() == null || snapshot.lastDamageCause().isBlank()
                ? "Unknown mishap"
                : snapshot.lastDamageCause();
        Point restoredCheckpoint = snapshot.checkpointRoom();
        checkpointRoom = restoredCheckpoint == null ? new Point(worldPos) : restoredCheckpoint;
        checkpointBoss = snapshot.checkpointBoss();
        refreshPlayerHpAfterUpgrade();
        statusMessage = snapshot.statusMessage() == null ? "" : snapshot.statusMessage();
        statusTicks = snapshot.statusTicks();
        inBoss = snapshot.inBoss();
        animTick = snapshot.animTick();
        mouseX = snapshot.mouseX();
        mouseY = snapshot.mouseY();
        shopManager.restore(snapshot.shopRoom(), snapshot.shopDoorFacing(), snapshot.shopInitialized());
        goldenKnightIntroShown = snapshot.goldenKnightIntroShown();
        queenRescued = snapshot.queenRescued();
        finaleShown = snapshot.finaleShown();
        introShown = snapshot.introShown();
        rng = snapshot.rng();
        secureRandom = snapshot.secureRandom();
        paused = false;
        if (shopManager.initialized()) {
            Point shopLocation = shopManager.location();
            if (shopLocation != null) {
                Room shop = world.get(shopLocation);
                if (shop != null) {
                    shopManager.ensureDoorway(shop, shopLocation, this::hasRoomAt, this::carveDoorOnGrid, this::markRoomDirty);
                }
            }
        } else {
            shopManager.ensureDoorway(room, worldPos, this::hasRoomAt, this::carveDoorOnGrid, this::markRoomDirty);
        }
        if (!introShown) {
            SwingUtilities.invokeLater(this::playPrologueIfNeeded);
        }
        persistProgressAsync("restored run");
    }

    private void persistProgressAsync(String reason) {
        SwingUtilities.invokeLater(() -> persistProgress(reason));
    }

    private void persistProgress(String reason) {
        try {
            saveHandler.accept(snapshot());
        } catch (RuntimeException ex) {
            System.err.println("[DungeonRooms] Failed to persist " + reason + ": " + ex.getMessage());
        }
    }

    private boolean cutscenesEnabled() {
        return settings == null || settings.cutscenesEnabled();
    }

    private void refreshArtAssets() {
        textureEpoch++;
        markAllRoomsDirty();
        textures = DungeonTextures.load(TILE);
        playerIdleFrames = CharacterSkinLibrary.loadIdleAnimation("hero",
                () -> {
                    BufferedImage[] frames = loadSpriteSequence(PLAYER_IDLE_PREFIX, 0, 3);
                    return frames != null ? frames : fallbackIdleFrames(new Color(255, 214, 102), new Color(40, 30, 10));
                },
                "/resources/sprites/Knight/Idle",
                "/resources/sprites/Knight",
                "/resources/sprites/Hero");
        playerIdleFrames = ensureScaledFrames(playerIdleFrames, PLAYER_SIZE, PLAYER_SIZE);

        defaultEnemyFrames = CharacterSkinLibrary.loadIdleAnimation("enemy_imp_default",
                () -> {
                    BufferedImage[] frames = loadSpriteSequence(ENEMY_IDLE_PREFIX, 0, 3);
                    return frames != null ? frames : fallbackIdleFrames(new Color(198, 72, 72), new Color(38, 20, 20));
                },
                "/resources/sprites/Imp/Idle",
                "/resources/sprites/Imp");
        defaultEnemyFrames = ensureScaledFrames(defaultEnemyFrames, defaultEnemySize(EnemyType.ZOMBIE));

        enemyIdleAnimations.clear();
        for (EnemyType type : EnemyType.values()) {
            BufferedImage[] frames = loadEnemyAnimation(type);
            enemyIdleAnimations.put(type, ensureScaledFrames(frames, defaultEnemySize(type)));
        }

        bossIdleFrames = CharacterSkinLibrary.loadIdleAnimation("boss_guardian",
                () -> loadBossIdleFallback(),
                "/resources/bosses/Bigzombie",
                "/resources/bosses/Guardians",
                "/resources/bosses",
                "/resources/sprites/Bigzombie/Idle",
                "/resources/sprites/Bigzombie");
        bossIdleFrames = ensureScaledFrames(bossIdleFrames, (int) (TILE * 1.4));
        weaponTextures = new EnumMap<>(WeaponType.class);
        loadWeaponTextures();
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
            if (b.maxLife <= 0) {
                b.maxLife = 420;
            }
            if (b.damage <= 0) {
                b.damage = 1.0;
            }
            if (b.tint == null && !b.friendly) {
                b.useTexture = true;
            }
            if (b.kind == null) {
                b.kind = ProjectileKind.ORB;
            }
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

    private BufferedImage[] ensureScaledFrames(BufferedImage[] frames, int size) {
        return ensureScaledFrames(frames, size, size);
    }

    private BufferedImage[] ensureScaledFrames(BufferedImage[] frames, int width, int height) {
        if (frames == null) {
            return null;
        }
        if (frames.length == 0) {
            return new BufferedImage[0];
        }
        int targetWidth = Math.max(1, width);
        int targetHeight = Math.max(1, height);
        BufferedImage[] scaled = new BufferedImage[frames.length];
        for (int i = 0; i < frames.length; i++) {
            BufferedImage frame = frames[i];
            if (frame == null) {
                scaled[i] = null;
                continue;
            }
            scaled[i] = HiDpiScaler.scale(frame, targetWidth, targetHeight);
        }
        return scaled;
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

    private void loadWeaponTextures() {
        if (weaponTextures == null) {
            weaponTextures = new EnumMap<>(WeaponType.class);
        }
        if (scaledWeaponCache == null) {
            scaledWeaponCache = new HashMap<>();
        } else {
            scaledWeaponCache.clear();
        }
        if (scaledArrowCache == null) {
            scaledArrowCache = new HashMap<>();
        } else {
            scaledArrowCache.clear();
        }
        weaponTextures.clear();
        putWeaponTexture(WeaponType.SWORD, "resources/Miscellanious/weapon_regular_sword.png", true, true, false);
        putWeaponTexture(WeaponType.HAMMER, "resources/Miscellanious/weapon_hammer.png", true, true, false);
        putWeaponTexture(WeaponType.BOW, "resources/Miscellanious/weapon_bow.png", true, false, false);
        putWeaponTexture(WeaponType.STAFF, "resources/Miscellanious/weapon_green_magic_staff.png", true, false, false);
        putWeaponTexture(WeaponType.CLAWS, "resources/Miscellanious/weapon_knife.png", true, false, false);
        arrowTexture = orientWeapon(loadSpriteImage("resources/Miscellanious/weapon_arrow.png"), true, false, false);
    }

    private void putWeaponTexture(WeaponType type, String resource, boolean rotateHorizontal,
                                  boolean flipHorizontal, boolean flipVertical) {
        if (type == null || resource == null) {
            return;
        }
        BufferedImage img = loadSpriteImage(resource);
        if (img == null) {
            return;
        }
        BufferedImage prepared = orientWeapon(img, rotateHorizontal, flipHorizontal, flipVertical);
        weaponTextures.put(type, prepared);
    }

    private BufferedImage loadSpriteImage(String resource) {
        if (resource == null || resource.isBlank()) {
            return null;
        }
        try (InputStream in = ResourceLoader.open(resource)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            System.err.println("Failed to load sprite image: " + resource + " -> " + ex.getMessage());
            return null;
        }
    }

    private BufferedImage orientWeapon(BufferedImage img, boolean rotate, boolean flipHorizontal, boolean flipVertical) {
        if (img == null) {
            return null;
        }
        BufferedImage result = img;
        boolean shouldRotate = rotate && img.getHeight() > img.getWidth();
        if (shouldRotate) {
            int w = result.getWidth();
            int h = result.getHeight();
            if (w > 0 && h > 0) {
                BufferedImage rotated = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = rotated.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.translate(h / 2.0, w / 2.0);
                    g.rotate(-Math.PI / 2.0);
                    g.translate(-w / 2.0, -h / 2.0);
                    g.drawImage(result, 0, 0, null);
                } finally {
                    g.dispose();
                }
                result = rotated;
            }
        }
        if (flipHorizontal && result != null) {
            int w = result.getWidth();
            int h = result.getHeight();
            BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = flipped.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(result, w, 0, -w, h, null);
            } finally {
                g.dispose();
            }
            result = flipped;
        }
        if (flipVertical && result != null) {
            int w = result.getWidth();
            int h = result.getHeight();
            BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = flipped.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(result, 0, h, w, -h, null);
            } finally {
                g.dispose();
            }
            result = flipped;
        }
        return result;
    }

    private BufferedImage[] loadEnemyAnimation(EnemyType type) {
        EnemyType resolvedType = type == null ? EnemyType.ZOMBIE : type;
        Color[] palette = enemyFallbackPalette(resolvedType);
        Supplier<BufferedImage[]> fallback = () -> {
            BufferedImage[] frames = loadSpriteSequence(enemySpritePrefix(resolvedType), 0, 3);
            return frames != null && frames.length > 0 ? frames : fallbackIdleFrames(palette[0], palette[1]);
        };
        BufferedImage[] frames = CharacterSkinLibrary.loadIdleAnimation(enemySkinCacheKey(resolvedType),
                fallback,
                enemySkinDirectories(resolvedType));
        if (frames == null || frames.length == 0) {
            frames = fallback.get();
        }
        return frames;
    }

    private Color[] enemyFallbackPalette(EnemyType type) {
        return switch (type) {
            case ZOMBIE -> new Color[]{new Color(126, 186, 132), new Color(24, 60, 32)};
            case IMP -> new Color[]{new Color(198, 72, 72), new Color(38, 20, 20)};
            case KNIGHT -> new Color[]{new Color(180, 180, 200), new Color(68, 70, 88)};
            case OGRE -> new Color[]{new Color(150, 104, 44), new Color(66, 34, 10)};
            case PUMPKIN -> new Color[]{new Color(224, 132, 40), new Color(90, 42, 8)};
            case SKELETON -> new Color[]{new Color(230, 230, 230), new Color(76, 86, 106)};
            case WIZARD -> new Color[]{new Color(120, 90, 200), new Color(40, 28, 70)};
            case NECROMANCER -> new Color[]{new Color(96, 82, 180), new Color(24, 18, 60)};
            case BARD -> new Color[]{new Color(90, 186, 198), new Color(22, 74, 86)};
        };
    }

    private String enemySpritePrefix(EnemyType type) {
        return switch (type) {
            case ZOMBIE -> "resources/sprites/Bigzombie/big_zombie_idle_anim_f";
            case IMP -> ENEMY_IDLE_PREFIX;
            case KNIGHT -> "resources/sprites/Knight/Idle/knight_m_idle_anim_f";
            case OGRE -> "resources/sprites/Ogre/ogre_idle_anim_f";
            case PUMPKIN -> "resources/sprites/Pumpkin/pumpkin_dude_idle_anim_f";
            case SKELETON -> "resources/sprites/Skeleton/skelet_idle_anim_f";
            case WIZARD -> "resources/sprites/Wizard/wizzard_m_idle_anim_f";
            case NECROMANCER -> "resources/sprites/Wizard/wizzard_m_idle_anim_f";
            case BARD -> ENEMY_IDLE_PREFIX;
        };
    }

    private String enemySkinCacheKey(EnemyType type) {
        return "enemy_" + (type == null ? "unknown" : type.name().toLowerCase(Locale.ENGLISH));
    }

    private String[] enemySkinDirectories(EnemyType type) {
        if (type == null) {
            return new String[0];
        }
        return switch (type) {
            case ZOMBIE -> new String[]{
                    "/resources/sprites/Bigzombie/Idle",
                    "/resources/sprites/Bigzombie",
                    "/resources/sprites/Zombie",
                    "/resources/bosses/Gollum"
            };
            case IMP -> new String[]{
                    "/resources/sprites/Imp/Idle",
                    "/resources/sprites/Imp"
            };
            case KNIGHT -> new String[]{
                    "/resources/sprites/Knight/Idle",
                    "/resources/sprites/Knight"
            };
            case OGRE -> new String[]{
                    "/resources/sprites/Ogre/Idle",
                    "/resources/sprites/Ogre"
            };
            case PUMPKIN -> new String[]{
                    "/resources/sprites/Pumpkin/Idle",
                    "/resources/sprites/Pumpkin"
            };
            case SKELETON -> new String[]{
                    "/resources/sprites/Skeleton/Idle",
                    "/resources/sprites/Skeleton"
            };
            case WIZARD -> new String[]{
                    "/resources/sprites/Wizard/Idle",
                    "/resources/sprites/Wizard"
            };
            case NECROMANCER -> new String[]{
                    "/resources/sprites/Wizard/Idle",
                    "/resources/sprites/Wizard"
            };
            case BARD -> new String[]{
                    "/resources/sprites/Imp/Idle",
                    "/resources/sprites/Imp"
            };
        };
    }

    private BufferedImage[] loadBossIdleFallback() {
        BufferedImage[] frames = loadSpriteSequence(BOSS_IDLE_PREFIX, 0, 3);
        if (frames != null && frames.length > 0) {
            return frames;
        }
        return fallbackIdleFrames(new Color(120, 210, 150), new Color(32, 60, 40));
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
        if (bullet.kind == ProjectileKind.ARROW) {
            drawArrowProjectile(g, bullet);
            return;
        }
        int diameter = Math.max(4, bullet.r * 2);
        int drawX = (int) Math.round(bullet.x - diameter / 2.0);
        int drawY = (int) Math.round(bullet.y - diameter / 2.0);
        boolean renderTexture = texture != null && bullet.useTexture;
        if (renderTexture) {
            g.drawImage(texture, drawX, drawY, diameter, diameter, null);
        }
        if (!renderTexture) {
            Color original = g.getColor();
            Color colour = bullet.tint != null ? bullet.tint : (fallbackColour == null ? Color.WHITE : fallbackColour);
            g.setColor(colour);
            g.fillOval(drawX, drawY, diameter, diameter);
            g.setColor(original);
        }
    }

    private void drawArrowProjectile(Graphics2D g, Bullet bullet) {
        AffineTransform oldTransform = g.getTransform();
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        g.translate(bullet.x, bullet.y);
        double angle = Math.atan2(bullet.vy, bullet.vx);
        g.rotate(angle);
        BufferedImage sprite = scaledArrowSprite(Math.max(18, bullet.r * 4));
        if (sprite != null) {
            g.drawImage(sprite, -sprite.getWidth() / 2, -sprite.getHeight() / 2, null);
            if (bullet.tint != null) {
                java.awt.Composite oldComposite = g.getComposite();
                g.setComposite(AlphaComposite.SrcAtop.derive(0.45f));
                g.setColor(bullet.tint);
                g.fillRect(-sprite.getWidth() / 2, -sprite.getHeight() / 2, sprite.getWidth(), sprite.getHeight());
                g.setComposite(oldComposite);
            }
        } else {
            int length = Math.max(18, bullet.r * 4);
            int shaftWidth = Math.max(2, bullet.r / 2);
            Color shaft = bullet.tint != null ? new Color(Math.max(0, bullet.tint.getRed() - 30),
                    Math.max(0, bullet.tint.getGreen() - 30),
                    Math.max(0, bullet.tint.getBlue() - 30),
                    bullet.tint.getAlpha()) : new Color(200, 200, 200);
            Color head = bullet.tint != null ? bullet.tint : new Color(255, 255, 255);
            g.setStroke(new BasicStroke(shaftWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(shaft);
            g.drawLine(-length / 2, 0, length / 2 - 4, 0);
            Path2D.Double arrowHead = new Path2D.Double();
            arrowHead.moveTo(length / 2, 0);
            arrowHead.lineTo(length / 2 - 6, -4 - shaftWidth / 2.0);
            arrowHead.lineTo(length / 2 - 6, 4 + shaftWidth / 2.0);
            arrowHead.closePath();
            g.setColor(head);
            g.fill(arrowHead);
        }
        g.setColor(oldColor);
        g.setStroke(oldStroke);
        g.setTransform(oldTransform);
    }

    // ======= Room creation / persistence =======

    /** Get existing room at pos or create a new one with 1–3 doors. Guarantees an entrance if required. */
    private Room makeOrGetRoom(Point pos, Dir mustHaveEntrance) {
        Room r = world.get(pos);
        if (r == null) {
            r = generateNewRoom(mustHaveEntrance);
            configureLocksForNewRoom(pos, r, mustHaveEntrance);
            world.put(new Point(pos), r); // store a copy of key to avoid mutation issues
            ensureRoomTheme(r);
            normalizeEnemyState(r);
            ensureTrapLayoutForRoom(pos, r);
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
        ensureRoomTheme(r);
        normalizeEnemyState(r);
        ensureTrapLayoutForRoom(pos, r);
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
            initializeEnemySpawns(pos, r);
        }
        if (!r.enemies.isEmpty()) return;

        for (EnemySpawn spawn : r.enemySpawns) {
            if (spawn.defeated) continue;
            RoomEnemy e = instantiateEnemyFromSpawn(spawn);
            r.enemies.add(e);
        }
    }

    private static final EnumSet<EnemyType> MELEE_ENEMIES = EnumSet.of(
            EnemyType.ZOMBIE,
            EnemyType.KNIGHT,
            EnemyType.OGRE
    );
    private static final EnumSet<EnemyType> RANGED_ENEMIES = EnumSet.of(
            EnemyType.IMP,
            EnemyType.PUMPKIN,
            EnemyType.SKELETON,
            EnemyType.WIZARD,
            EnemyType.NECROMANCER,
            EnemyType.BARD
    );
    private static final EnumSet<EnemyType> ARCHER_ENEMIES = EnumSet.of(
            EnemyType.PUMPKIN,
            EnemyType.SKELETON
    );

    private void initializeEnemySpawns(Point pos, Room r) {
        if (r == null || r.spawnsPrepared) return;
        boolean firstVisit = pos != null && (visited == null || !visited.contains(pos));
        int explorationCount = roomsVisited + (firstVisit ? 1 : 0);
        Random rngLocal = rng == null ? new Random() : rng;
        int base = 2 + Math.min(explorationCount / 5, 2); // 2..4 based on progress
        int variance = Math.min(explorationCount / 6, 2);
        int count = base + rngLocal.nextInt(variance + 1);
        count = Math.max(2, Math.min(5, count));
        int attempts = 0;
        while (r.enemySpawns.size() < count && attempts++ < count * 40) {
            int tx = 2 + rngLocal.nextInt(COLS - 4);
            int ty = 2 + rngLocal.nextInt(ROWS - 4);
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
            spawn.type = chooseEnemyTypeForRoom(r);
            r.enemySpawns.add(spawn);
        }
        ensureEnemySynergies(r, explorationCount, rngLocal);
        r.spawnsPrepared = true;
    }

    private void ensureEnemySynergies(Room room, int explorationCount, Random random) {
        if (room == null || room.enemySpawns == null || room.enemySpawns.isEmpty()) {
            return;
        }
        Random rngLocal = random == null ? new Random() : random;
        boolean hasArcher = false;
        boolean hasKnight = false;
        boolean hasNecromancer = false;
        boolean hasBard = false;
        int frontlineCount = 0;
        for (EnemySpawn spawn : room.enemySpawns) {
            if (spawn == null) {
                continue;
            }
            if (spawn.type == null) {
                spawn.type = EnemyType.ZOMBIE;
            }
            EnemyType type = spawn.type;
            if (ARCHER_ENEMIES.contains(type)) {
                hasArcher = true;
            }
            if (type == EnemyType.KNIGHT) {
                hasKnight = true;
            }
            if (type == EnemyType.NECROMANCER) {
                hasNecromancer = true;
            }
            if (type == EnemyType.BARD) {
                hasBard = true;
            }
            if (type != EnemyType.NECROMANCER && type != EnemyType.BARD) {
                frontlineCount++;
            }
        }

        if (hasArcher && !hasKnight && explorationCount >= 4 &&
                countSpawnedEnemies(room, EnemyType.KNIGHT) < perRoomLimit(EnemyType.KNIGHT)) {
            EnemySpawn knight = createSupportSpawn(room, EnemyType.KNIGHT, rngLocal);
            if (knight != null) {
                room.enemySpawns.add(knight);
                hasKnight = true;
                frontlineCount++;
            }
        }

        if (hasNecromancer && frontlineCount == 0) {
            EnemyType filler = explorationCount >= 4 ? EnemyType.ZOMBIE : EnemyType.IMP;
            if (countSpawnedEnemies(room, filler) < perRoomLimit(filler)) {
                EnemySpawn revivedTarget = createSupportSpawn(room, filler, rngLocal);
                if (revivedTarget != null) {
                    room.enemySpawns.add(revivedTarget);
                    frontlineCount++;
                }
            }
        }

        if (hasBard && frontlineCount <= 1) {
            EnemyType partner = explorationCount >= 5 ? EnemyType.SKELETON : EnemyType.IMP;
            if (countSpawnedEnemies(room, partner) < perRoomLimit(partner)) {
                EnemySpawn ally = createSupportSpawn(room, partner, rngLocal);
                if (ally != null) {
                    room.enemySpawns.add(ally);
                    frontlineCount++;
                }
            }
        }
    }

    private int countSpawnedEnemies(Room room, EnemyType type) {
        if (room == null || room.enemySpawns == null || type == null) {
            return 0;
        }
        int count = 0;
        for (EnemySpawn spawn : room.enemySpawns) {
            if (spawn == null) {
                continue;
            }
            EnemyType current = spawn.type == null ? EnemyType.ZOMBIE : spawn.type;
            if (current == type) {
                count++;
            }
        }
        return count;
    }

    private EnemySpawn createSupportSpawn(Room room, EnemyType type, Random random) {
        if (room == null || type == null) {
            return null;
        }
        Random rngLocal = random == null ? new Random() : random;
        int attempts = 0;
        while (attempts++ < 80) {
            int tx = 2 + rngLocal.nextInt(COLS - 4);
            int ty = 2 + rngLocal.nextInt(ROWS - 4);
            if (room.g[tx][ty] != T.FLOOR) {
                continue;
            }
            int px = tx * TILE + TILE / 2;
            int py = ty * TILE + TILE / 2;
            if (!isRectFree(room, px, py, (int) (TILE * 0.3))) {
                continue;
            }
            if (nearAnyDoor(room, tx, ty, 2)) {
                continue;
            }
            if (enemySpawnConflict(room.enemySpawns, px, py, TILE)) {
                continue;
            }
            EnemySpawn spawn = new EnemySpawn();
            spawn.x = px;
            spawn.y = py;
            spawn.type = type;
            return spawn;
        }
        return null;
    }

    private boolean enemySpawnConflict(List<EnemySpawn> spawns, int px, int py, int minDistance) {
        if (spawns == null || spawns.isEmpty()) {
            return false;
        }
        int minSq = minDistance * minDistance;
        for (EnemySpawn existing : spawns) {
            if (existing == null) {
                continue;
            }
            int dx = existing.x - px;
            int dy = existing.y - py;
            if (dx * dx + dy * dy < minSq) {
                return true;
            }
        }
        return false;
    }

    private void ensureRoomTheme(Room room) {
        if (room == null) {
            return;
        }
        if (room.floorThemeSeed == 0) {
            room.floorThemeSeed = secureRandom.nextInt(10_000);
        }
        if (room.wallThemeSeed == 0) {
            room.wallThemeSeed = secureRandom.nextInt(10_000);
        }
        boolean paletteUnassigned = room.paletteIndex < 0 || room.paletteIndex >= ROOM_PALETTES.length;
        if (!paletteUnassigned && room.accentSeed == 0) {
            paletteUnassigned = true;
        }
        if (room.accentSeed == 0) {
            room.accentSeed = secureRandom.nextInt(10_000);
        }
        if (paletteUnassigned) {
            room.paletteIndex = Math.floorMod(room.floorThemeSeed + room.wallThemeSeed, ROOM_PALETTES.length);
        }
    }

    private void normalizeEnemyState(Room room) {
        if (room == null) {
            return;
        }
        if (room.enemySpawns != null) {
            for (EnemySpawn spawn : room.enemySpawns) {
                if (spawn != null && spawn.type == null) {
                    spawn.type = EnemyType.ZOMBIE;
                }
            }
        }
        if (room.enemies != null) {
            for (RoomEnemy enemy : room.enemies) {
                if (enemy == null) {
                    continue;
                }
                if (enemy.type == null) {
                    enemy.type = EnemyType.ZOMBIE;
                }
                if (enemy.maxHealth <= 0) {
                    enemy.type = enemy.type == null ? EnemyType.ZOMBIE : enemy.type;
                    applyEnemyDefaults(enemy);
                } else {
                    enemy.health = Math.max(1, enemy.health <= 0 ? enemy.maxHealth : Math.min(enemy.maxHealth, enemy.health));
                    enemy.damageBuffer = Math.max(0.0, enemy.damageBuffer);
                }
                if (enemy.weapon == null) {
                    enemy.weapon = weaponFor(enemy.type);
                }
                if (!Double.isFinite(enemy.weaponAngle)) {
                    enemy.weaponAngle = 0.0;
                }
                if (!Double.isFinite(enemy.facingAngle)) {
                    enemy.facingAngle = 0.0;
                }
                enemy.attackAnimDuration = Math.max(0, enemy.attackAnimDuration);
                enemy.attackAnimTicks = Math.max(0, Math.min(enemy.attackAnimTicks, enemy.attackAnimDuration));
                enemy.bowDrawTicks = Math.max(0, enemy.bowDrawTicks);
            }
        }
    }

    private TrapManager trapManagerForRoom(Room target) {
        if (target == null) {
            return null;
        }
        if (target.trapSpawns == null) {
            target.trapSpawns = new ArrayList<>();
        }
        if (target.trapManager == null) {
            target.trapManager = buildTrapManager(target.trapSpawns);
        }
        return target.trapManager;
    }

    private void ensureTrapLayoutForRoom(Point pos, Room room) {
        if (room == null) {
            return;
        }
        if (room.trapSpawns == null) {
            room.trapSpawns = new ArrayList<>();
        }
        if (room.trapSeed == 0) {
            room.trapSeed = secureRandom.nextInt(100_000);
        }
        Point anchor = pos == null ? worldPos : pos;
        if (!room.trapsPrepared) {
            Random trapRandom = new Random(room.trapSeed ^ (anchor == null ? 0 : anchor.hashCode()));
            populateTrapSpawns(room, anchor, trapRandom);
            room.trapsPrepared = true;
        }
        removeDoorConflicts(room);
        if (room.trapManager == null) {
            room.trapManager = buildTrapManager(room.trapSpawns);
        }
    }

    private TrapManager buildTrapManager(List<RoomTrap> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        TrapManager manager = new TrapManager();
        boolean any = false;
        for (RoomTrap def : definitions) {
            Trap trap = instantiateTrap(def);
            if (trap != null) {
                manager.add(trap);
                any = true;
            }
        }
        return any ? manager : null;
    }

    private Trap instantiateTrap(RoomTrap def) {
        if (def == null || def.kind == null) {
            return null;
        }
        String folder = def.animationFolder == null || def.animationFolder.isBlank()
                ? defaultTrapFolder(def.kind)
                : def.animationFolder;
        double frameDuration = def.frameDuration > 0 ? def.frameDuration : defaultFrameDuration(def.kind);
        Animation animation = new Animation(SpriteLoader.loadDefault(folder), frameDuration);
        BaseTrap trap;
        switch (def.kind) {
            case SAW -> trap = new SawTrap(def.x, def.y, animation);
            case SPIKE -> trap = new SpikeTrap(def.x, def.y, animation, def.cycleSeconds, def.activeFraction);
            case FIRE_VENT -> trap = new FireVentTrap(def.x, def.y, animation, def.burstEvery, def.burstDuration);
            default -> trap = null;
        }
        if (trap == null) {
            return null;
        }
        if (def.width > 0 || def.height > 0) {
            trap.setDimensions(def.width, def.height);
        }
        if (def.damageOverride > 0) {
            trap.setDamage(def.damageOverride);
        }
        if (def.contactCooldownOverride >= 0.0) {
            trap.setContactCooldown(def.contactCooldownOverride);
        }
        return trap;
    }

    private String defaultTrapFolder(TrapKind kind) {
        return switch (kind) {
            case SAW -> SAW_TRAP_FOLDER;
            case SPIKE -> SPIKE_TRAP_FOLDER;
            case FIRE_VENT -> FIRE_TRAP_FOLDER;
        };
    }

    private double defaultFrameDuration(TrapKind kind) {
        return switch (kind) {
            case SAW -> 0.06;
            case SPIKE -> 0.08;
            case FIRE_VENT -> 0.07;
        };
    }

    private void configureTrapDefaults(RoomTrap trap, Random random) {
        if (trap == null) {
            return;
        }
        Random rngLocal = random == null ? new Random() : random;
        switch (trap.kind) {
            case SAW -> {
                trap.animationFolder = SAW_TRAP_FOLDER;
                trap.frameDuration = 0.06;
                trap.damageOverride = Math.max(trap.damageOverride, 1);
                trap.contactCooldownOverride = 0.35;
            }
            case SPIKE -> {
                trap.animationFolder = SPIKE_TRAP_FOLDER;
                trap.frameDuration = 0.08;
                trap.cycleSeconds = Math.max(1.2, 1.8 + rngLocal.nextDouble() * 1.6);
                trap.activeFraction = 0.45 + rngLocal.nextDouble() * 0.3;
                trap.damageOverride = Math.max(trap.damageOverride, 2);
                trap.contactCooldownOverride = 0.55;
            }
            case FIRE_VENT -> {
                trap.animationFolder = FIRE_TRAP_FOLDER;
                trap.frameDuration = 0.07;
                trap.burstEvery = Math.max(2.0, 3.0 + rngLocal.nextDouble() * 2.5);
                trap.burstDuration = Math.max(0.6, 0.9 + rngLocal.nextDouble() * 0.8);
                trap.damageOverride = Math.max(trap.damageOverride, 3);
                trap.contactCooldownOverride = 0.7;
            }
        }
    }

    private TrapKind chooseTrapKind(Random random, int depth, int progress) {
        Random rngLocal = random == null ? new Random() : random;
        if (progress <= 1) {
            return TrapKind.SAW;
        }
        if (progress == 2) {
            return rngLocal.nextBoolean() ? TrapKind.SAW : TrapKind.SPIKE;
        }
        if (progress == 3) {
            TrapKind[] pool = depth < 4
                    ? new TrapKind[]{TrapKind.SAW, TrapKind.SPIKE, TrapKind.SPIKE}
                    : new TrapKind[]{TrapKind.SAW, TrapKind.SPIKE, TrapKind.FIRE_VENT};
            return pool[rngLocal.nextInt(pool.length)];
        }
        TrapKind[] pool = depth < 4
                ? new TrapKind[]{TrapKind.SAW, TrapKind.SPIKE, TrapKind.SPIKE}
                : new TrapKind[]{TrapKind.SAW, TrapKind.SPIKE, TrapKind.FIRE_VENT};
        return pool[rngLocal.nextInt(pool.length)];
    }

    private void populateTrapSpawns(Room room, Point pos, Random random) {
        if (room == null) {
            return;
        }
        if (room.trapSpawns == null) {
            room.trapSpawns = new ArrayList<>();
        }
        if (isBossRoom(pos)) {
            room.trapSpawns.clear();
            return;
        }
        boolean firstVisit = pos != null && (visited == null || !visited.contains(pos));
        int explorationCount = roomsVisited + (firstVisit ? 1 : 0);
        if (explorationCount <= SAFE_ROOMS_BEFORE_TRAPS) {
            return;
        }
        Random rngLocal = random == null ? new Random() : random;
        int depth = pos == null ? 0 : Math.abs(pos.x) + Math.abs(pos.y);
        int progress = Math.max(0, explorationCount - SAFE_ROOMS_BEFORE_TRAPS);
        if (depth < 2 && progress < 2) {
            return;
        }
        int base = Math.max(1, Math.min(2 + progress / 2, depth < 6 ? 2 : 3));
        int variance = Math.max(1, Math.min(3, progress / 2 + Math.max(1, depth / 4)));
        int target = Math.min(4, base + rngLocal.nextInt(Math.max(1, variance)));
        if (progress <= 1) {
            target = Math.min(target, 1);
        } else if (progress == 2) {
            target = Math.min(target, 2);
        }
        int attempts = 0;
        while (room.trapSpawns.size() < target && attempts++ < target * 40) {
            int tx = 1 + rngLocal.nextInt(COLS - 2);
            int ty = 1 + rngLocal.nextInt(ROWS - 2);
            if (room.g[tx][ty] != T.FLOOR) continue;
            if (nearAnyDoor(room, tx, ty, 2)) continue;
            int px = tx * TILE + TILE / 2;
            int py = ty * TILE + TILE / 2;
            int half = TILE / 2;
            if (!isRectFree(room, px, py, half)) continue;
            Rectangle candidate = new Rectangle(px - half, py - half, TILE, TILE);
            if (trapConflicts(room, candidate)) continue;
            if (pickupConflict(room, candidate)) continue;

            RoomTrap trap = new RoomTrap();
            trap.kind = chooseTrapKind(rngLocal, depth, progress);
            trap.x = candidate.x;
            trap.y = candidate.y;
            trap.width = candidate.width;
            trap.height = candidate.height;
            configureTrapDefaults(trap, rngLocal);
            room.trapSpawns.add(trap);
        }
    }

    private boolean trapConflicts(Room room, Rectangle candidate) {
        if (room == null || room.trapSpawns == null) {
            return false;
        }
        for (RoomTrap existing : room.trapSpawns) {
            Rectangle current = new Rectangle(existing.x, existing.y, existing.width, existing.height);
            if (candidate.intersects(current)) {
                return true;
            }
        }
        return false;
    }

    private boolean pickupConflict(Room room, Rectangle candidate) {
        if (room == null || candidate == null) {
            return false;
        }
        if (room.keyPickups != null) {
            for (KeyPickup key : room.keyPickups) {
                Rectangle keyRect = new Rectangle(key.x - key.r, key.y - key.r, key.r * 2, key.r * 2);
                if (candidate.intersects(keyRect)) {
                    return true;
                }
            }
        }
        if (room.coinPickups != null) {
            for (CoinPickup coin : room.coinPickups) {
                Rectangle coinRect = new Rectangle(coin.x - coin.r, coin.y - coin.r, coin.r * 2, coin.r * 2);
                if (candidate.intersects(coinRect)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void removeDoorConflicts(Room room) {
        if (room == null || room.trapSpawns == null || room.trapSpawns.isEmpty()) {
            return;
        }
        boolean removed = room.trapSpawns.removeIf(trap -> {
            int cx = trap.x + trap.width / 2;
            int cy = trap.y + trap.height / 2;
            int tx = Math.max(0, Math.min(COLS - 1, cx / TILE));
            int ty = Math.max(0, Math.min(ROWS - 1, cy / TILE));
            return nearAnyDoor(room, tx, ty, 1);
        });
        if (removed) {
            room.trapManager = null;
        }
    }

    private void markAllRoomsDirty() {
        if (world != null) {
            for (Room r : world.values()) {
                markRoomDirty(r);
            }
        }
        markRoomDirty(room);
    }

    private void markRoomDirty(Room r) {
        if (r == null) {
            return;
        }
        r.backgroundDirty = true;
        r.cachedBackground = null;
        r.cachedTextureEpoch = -1;
    }

    private boolean hasRoomAt(Point p) {
        return p != null && world != null && world.containsKey(p);
    }

    private void openShop(Dir doorSide) {
        Room current = room;
        if (current != null) {
            current.shopVisited = true;
        }
        lastShopDoorway = doorSide;
        awaitingShopExitClear = true;
        nudgePlayerAwayFromShopDoor(doorSide);
        pauseForOverlay(() -> {
            ShopDialog.Result result = ShopDialog.showShop(
                    SwingUtilities.getWindowAncestor(DungeonRooms.this),
                    coins,
                    playerHP,
                    BASE_PLAYER_HP,
                    vitalityUpgrades,
                    dungeonHeartUpgrades,
                    MAX_VITALITY_UPGRADES,
                    MAX_DUNGEON_HEART_UPGRADES);
            coins = Math.max(0, result.remainingCoins());
            vitalityUpgrades = Math.min(MAX_VITALITY_UPGRADES, Math.max(0, result.vitalityLevel()));
            dungeonHeartUpgrades = Math.min(MAX_DUNGEON_HEART_UPGRADES, Math.max(0, result.dungeonLevel()));
            refreshPlayerHpAfterUpgrade();
            healPlayerTo(result.resultingHp());
            String remark = result.closingRemark();
            if (remark != null && !remark.isBlank()) {
                showMessage(remark);
            } else {
                showMessage("The shopkeeper bids you safe travels.");
            }
        });
    }

    private void nudgePlayerAwayFromShopDoor(Dir doorSide) {
        if (doorSide == null || room == null || player == null) {
            return;
        }
        Point tile = doorTile(doorSide);
        int dx = 0;
        int dy = 0;
        switch (doorSide) {
            case N -> dy = 1;
            case S -> dy = -1;
            case W -> dx = 1;
            case E -> dx = -1;
        }
        int tx = tile.x;
        int ty = tile.y;
        Point target = null;
        for (int step = 0; step < Math.max(COLS, ROWS); step++) {
            tx += dx;
            ty += dy;
            if (!inBounds(tx, ty)) {
                break;
            }
            if (room.g[tx][ty] == T.FLOOR) {
                target = new Point(tx * TILE + TILE / 2, ty * TILE + TILE / 2);
                break;
            }
        }
        if (target == null) {
            int fallbackX = tile.x * TILE + TILE / 2 + dx * TILE * 3;
            int fallbackY = tile.y * TILE + TILE / 2 + dy * TILE * 3;
            target = new Point(fallbackX, fallbackY);
        }
        Point safe = safePlayerSpawn(room, target.x, target.y);
        player.setLocation(safe.x - PLAYER_SIZE / 2, safe.y - PLAYER_SIZE / 2);
        trapPlayer.syncFromDungeon();
    }

    private void pauseForOverlay(Runnable runnable) {
        boolean previousPaused = paused;
        boolean previousOverlay = overlayActive;
        overlayActive = true;
        paused = true;
        clearMovementInput();
        timer.stop();
        try {
            runnable.run();
        } finally {
            overlayActive = previousOverlay;
            paused = previousPaused;
            if (!timer.isRunning()) {
                timer.start();
            }
            clearMovementInput(true);
            requestFocusInWindow();
        }
    }

    private void clearMovementInput() {
        clearMovementInput(false);
    }

    private void clearMovementInput(boolean suppressNext) {
        up = down = left = right = false;
        if (suppressNext) {
            suppressNextMovementPress = true;
            suppressMovementDeadlineNanos = System.nanoTime() + 300_000_000L; // ~0.3s grace
        }
    }

    private void healPlayerTo(int targetHp) {
        int clamped = Math.max(0, Math.min(playerMaxHp(), targetHp));
        if (clamped > playerHP) {
            playerHP = clamped;
            healTicks = HEAL_FLASH_TICKS;
        } else {
            playerHP = clamped;
        }
    }

    private int playerMaxHp() {
        return BASE_PLAYER_HP + vitalityUpgrades * VITALITY_STEP + dungeonHeartUpgrades * DUNGEON_HEART_STEP;
    }

    private void refreshPlayerHpAfterUpgrade() {
        playerHP = Math.min(playerHP, playerMaxHp());
    }

    private double playerDamage() {
        double base = BASE_PLAYER_DAMAGE + damageLevel * DAMAGE_STEP;
        double comboBonus = 1.0 + comboLevel * COMBO_DAMAGE_STEP;
        return base * comboBonus;
    }

    private void registerEnemyDefeat() {
        enemiesDefeated++;
        awardCombo(2);
        while (damageLevel < DAMAGE_THRESHOLDS.length && enemiesDefeated >= DAMAGE_THRESHOLDS[damageLevel]) {
            damageLevel++;
            showMessage("Your strikes grow stronger! (Power " + (damageLevel + 1) + ")");
        }
    }

    private void playPrologueIfNeeded() {
        if (introShown) {
            return;
        }
        introShown = true;
        if (!cutscenesEnabled()) {
            return;
        }
        pauseForOverlay(() -> CutsceneDialog.play(SwingUtilities.getWindowAncestor(DungeonRooms.this),
                CutsceneLibrary.prologue()));
    }

    private void playGoldenKnightIntro() {
        if (goldenKnightIntroShown) {
            return;
        }
        goldenKnightIntroShown = true;
        if (!cutscenesEnabled()) {
            return;
        }
        pauseForOverlay(() -> CutsceneDialog.play(SwingUtilities.getWindowAncestor(DungeonRooms.this),
                CutsceneLibrary.goldenKnightMonologue()));
    }

    private void playBossPrelude(BossEncounter encounter) {
        if (encounter == null || encounter.kind == null) {
            return;
        }
        if (encounter.kind == BossBattlePanel.BossKind.GOLDEN_KNIGHT) {
            encounter.preludeShown = true;
            if (!goldenKnightIntroShown) {
                playGoldenKnightIntro();
            }
            return;
        }
        if (encounter.preludeShown) {
            return;
        }
        CutsceneScript script = CutsceneLibrary.bossPrelude(encounter.kind, storyChapterFor(encounter.kind));
        encounter.preludeShown = true;
        if (!cutscenesEnabled()) {
            return;
        }
        if (script == null || script.slides().isEmpty()) {
            return;
        }
        CutsceneScript finalScript = script;
        pauseForOverlay(() -> CutsceneDialog.play(SwingUtilities.getWindowAncestor(DungeonRooms.this), finalScript));
    }

    private void playBossEpilogue(BossBattlePanel.BossKind kind) {
        if (kind == null || kind == BossBattlePanel.BossKind.GOLDEN_KNIGHT) {
            return;
        }
        if (!cutscenesEnabled()) {
            return;
        }
        CutsceneScript script = CutsceneLibrary.bossEpilogue(kind, storyChapterFor(kind));
        if (script == null || script.slides().isEmpty()) {
            script = CutsceneLibrary.defaultBossVictory(kind);
        }
        if (script == null || script.slides().isEmpty()) {
            return;
        }
        CutsceneScript finalScript = script;
        pauseForOverlay(() -> CutsceneDialog.play(SwingUtilities.getWindowAncestor(DungeonRooms.this), finalScript));
    }

    private void handleGameWon() {
        if (finaleShown) {
            return;
        }
        finaleShown = true;
        if (!cutscenesEnabled()) {
            JOptionPane.showMessageDialog(DungeonRooms.this,
                    "You saved the queen! Peace returns to the realm.",
                    "Victory",
                    JOptionPane.INFORMATION_MESSAGE);
            exitHandler.run();
            return;
        }
        pauseForOverlay(() -> {
            CutsceneDialog.play(SwingUtilities.getWindowAncestor(DungeonRooms.this),
                    CutsceneLibrary.queenRescued());
            JOptionPane.showMessageDialog(DungeonRooms.this,
                    "You saved the queen! Peace returns to the realm.",
                    "Victory",
                    JOptionPane.INFORMATION_MESSAGE);
            exitHandler.run();
        });
    }

    private RoomEnemy instantiateEnemyFromSpawn(EnemySpawn spawn) {
        RoomEnemy e = new RoomEnemy();
        e.x = spawn.x;
        e.y = spawn.y;
        e.cd = rng.nextInt(45);
        e.spawn = spawn;
        e.type = spawn.type == null ? EnemyType.ZOMBIE : spawn.type;
        e.weaponAngle = 0.0;
        e.facingAngle = 0.0;
        e.attackAnimTicks = 0;
        e.attackAnimDuration = 0;
        e.bowDrawTicks = 0;
        applyEnemyDefaults(e);
        return e;
    }

    private int defaultEnemySize(EnemyType type) {
        return switch (type) {
            case ZOMBIE -> (int) (TILE * 0.68);
            case IMP -> (int) (TILE * 0.6);
            case KNIGHT -> (int) (TILE * 0.7);
            case OGRE -> (int) (TILE * 0.82);
            case PUMPKIN -> (int) (TILE * 0.6);
            case SKELETON -> (int) (TILE * 0.62);
            case WIZARD -> (int) (TILE * 0.7);
            case NECROMANCER -> (int) (TILE * 0.68);
            case BARD -> (int) (TILE * 0.62);
        };
    }

    private void applyEnemyDefaults(RoomEnemy enemy) {
        enemy.size = defaultEnemySize(enemy.type);
        switch (enemy.type) {
            case ZOMBIE -> {
                enemy.maxHealth = 3;
                enemy.coinReward = 4 + rng.nextInt(3);
            }
            case IMP -> {
                enemy.maxHealth = 2;
                enemy.coinReward = 3 + rng.nextInt(2);
            }
            case KNIGHT -> {
                enemy.maxHealth = 6;
                enemy.braceTicks = 0;
                enemy.coinReward = 6 + rng.nextInt(4);
            }
            case OGRE -> {
                enemy.maxHealth = 7;
                enemy.coinReward = 8 + rng.nextInt(5);
            }
            case PUMPKIN -> {
                enemy.maxHealth = 3;
                enemy.coinReward = 5 + rng.nextInt(3);
            }
            case SKELETON -> {
                enemy.maxHealth = 2;
                enemy.coinReward = 4 + rng.nextInt(3);
            }
            case WIZARD -> {
                enemy.maxHealth = 4;
                enemy.coinReward = 7 + rng.nextInt(4);
            }
            case NECROMANCER -> {
                enemy.maxHealth = 5;
                enemy.coinReward = 8 + rng.nextInt(5);
            }
            case BARD -> {
                enemy.maxHealth = 3;
                enemy.coinReward = 6 + rng.nextInt(4);
            }
        }
        enemy.health = enemy.maxHealth;
        enemy.damageBuffer = 0.0;
        enemy.patternIndex = rng.nextInt(3);
        enemy.weapon = weaponFor(enemy.type);
        enemy.attackAnimTicks = 0;
        enemy.attackAnimDuration = 0;
        enemy.bowDrawTicks = 0;
        enemy.weaponAngle = 0.0;
        enemy.facingAngle = 0.0;
        enemy.damageMultiplier = 1.0;
        enemy.speedMultiplier = 1.0;
        enemy.buffTicks = 0;
        enemy.supportTicks = 0;
    }

    private EnemyType chooseEnemyTypeForRoom(Room room) {
        if (room == null) {
            return EnemyType.ZOMBIE;
        }

        EnumMap<EnemyType, Integer> counts = new EnumMap<>(EnemyType.class);
        for (EnemySpawn spawn : room.enemySpawns) {
            EnemyType type = spawn == null || spawn.type == null ? EnemyType.ZOMBIE : spawn.type;
            counts.merge(type, 1, Integer::sum);
        }

        List<EnemyType> pool = buildEnemyPool(counts);
        if (pool.isEmpty()) {
            return EnemyType.ZOMBIE;
        }

        boolean hasMelee = counts.keySet().stream().anyMatch(MELEE_ENEMIES::contains);
        boolean hasRanged = counts.keySet().stream().anyMatch(RANGED_ENEMIES::contains);
        if (!hasMelee || !hasRanged) {
            List<EnemyType> forced = new ArrayList<>();
            for (EnemyType type : pool) {
                if (!hasMelee && MELEE_ENEMIES.contains(type)) {
                    forced.add(type);
                } else if (!hasRanged && RANGED_ENEMIES.contains(type)) {
                    forced.add(type);
                }
            }
            if (!forced.isEmpty()) {
                pool = forced;
            }
        }

        EnemyType last = room.enemySpawns.isEmpty() ? null : room.enemySpawns.get(room.enemySpawns.size() - 1).type;
        if (last != null) {
            List<EnemyType> nonRepeat = new ArrayList<>();
            for (EnemyType type : pool) {
                if (type != last) {
                    nonRepeat.add(type);
                }
            }
            if (!nonRepeat.isEmpty()) {
                pool = nonRepeat;
            }
        }

        return pool.get(rng.nextInt(pool.size()));
    }

    private List<EnemyType> buildEnemyPool(Map<EnemyType, Integer> counts) {
        List<EnemyType> pool = new ArrayList<>();
        for (EnemyType type : unlockedEnemyTypes()) {
            int limit = perRoomLimit(type);
            int current = counts.getOrDefault(type, 0);
            if (current >= limit) {
                continue;
            }
            int weight = enemySpawnWeight(type);
            for (int i = 0; i < weight; i++) {
                pool.add(type);
            }
        }
        return pool;
    }

    private List<EnemyType> unlockedEnemyTypes() {
        List<EnemyType> types = new ArrayList<>();
        types.add(EnemyType.ZOMBIE);
        if (roomsVisited >= 1) {
            types.add(EnemyType.IMP);
        }
        if (roomsVisited >= 2) {
            types.add(EnemyType.SKELETON);
        }
        if (roomsVisited >= 3) {
            types.add(EnemyType.PUMPKIN);
        }
        if (roomsVisited >= 4) {
            types.add(EnemyType.KNIGHT);
        }
        if (roomsVisited >= 5) {
            types.add(EnemyType.WIZARD);
            types.add(EnemyType.BARD);
        }
        if (roomsVisited >= 6) {
            types.add(EnemyType.OGRE);
        }
        if (roomsVisited >= 7) {
            types.add(EnemyType.NECROMANCER);
        }
        return types;
    }

    private int perRoomLimit(EnemyType type) {
        return switch (type) {
            case ZOMBIE -> 2 + roomsVisited / 10;
            case IMP, SKELETON, PUMPKIN -> 2;
            case WIZARD -> roomsVisited >= 8 ? 2 : 1;
            case KNIGHT -> roomsVisited >= 7 ? 2 : 1;
            case OGRE -> 1 + roomsVisited / 12;
            case NECROMANCER -> roomsVisited >= 9 ? 2 : 1;
            case BARD -> 2;
        };
    }

    private int enemySpawnWeight(EnemyType type) {
        return switch (type) {
            case ZOMBIE -> 3;
            case IMP -> 2;
            case SKELETON -> roomsVisited >= 3 ? 2 : 1;
            case PUMPKIN -> roomsVisited >= 4 ? 2 : 1;
            case KNIGHT -> roomsVisited >= 6 ? 2 : 1;
            case WIZARD -> roomsVisited >= 7 ? 2 : 1;
            case OGRE -> roomsVisited >= 8 ? 2 : 1;
            case NECROMANCER -> roomsVisited >= 9 ? 2 : 1;
            case BARD -> roomsVisited >= 5 ? 2 : 1;
        };
    }

    private WeaponType weaponFor(EnemyType type) {
        return switch (type) {
            case ZOMBIE, KNIGHT -> WeaponType.SWORD;
            case OGRE -> WeaponType.HAMMER;
            case PUMPKIN, SKELETON -> WeaponType.BOW;
            case WIZARD -> WeaponType.STAFF;
            case NECROMANCER -> WeaponType.STAFF;
            case BARD -> WeaponType.STAFF;
            case IMP -> WeaponType.CLAWS;
        };
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

        for (RoomEnemy e : room.enemies) {
            if (!e.alive) {
                continue;
            }
            updateEnemyBehavior(e, pcx, pcy);
        }

        updateEnemyProjectiles(pcx, pcy);
        updatePlayerProjectiles();
        room.enemies.removeIf(e -> !e.alive);
        updateRoomClearState(room);

        for (Explosion ex : explosions) {
            ex.age++;
        }
        explosions.removeIf(ex -> ex.age >= ex.life);
    }

    private void updateEnemyBehavior(RoomEnemy enemy, int pcx, int pcy) {
        if (enemy == null || !enemy.alive) {
            return;
        }
        if (enemy.cd > 0) {
            enemy.cd--;
        }
        if (enemy.braceTicks > 0) {
            enemy.braceTicks--;
        }

        if (enemy.buffTicks > 0) {
            enemy.buffTicks--;
            if (enemy.buffTicks <= 0) {
                enemy.damageMultiplier = 1.0;
                enemy.speedMultiplier = 1.0;
            }
        } else {
            enemy.damageMultiplier = Math.max(1.0, enemy.damageMultiplier);
            enemy.speedMultiplier = Math.max(1.0, enemy.speedMultiplier);
        }

        boolean preppingHeavy = enemy.type == EnemyType.OGRE && enemy.windup > 0;
        if (!preppingHeavy) {
            if (enemy.attackAnimTicks > 0) {
                enemy.attackAnimTicks--;
            } else {
                enemy.attackAnimTicks = 0;
            }
        }
        if (enemy.bowDrawTicks > 0) {
            enemy.bowDrawTicks--;
        }

        double angleToPlayer = Math.atan2(pcy - enemy.y, pcx - enemy.x);
        if (!Double.isFinite(angleToPlayer)) {
            angleToPlayer = 0.0;
        }
        enemy.facingAngle = angleToPlayer;
        if (enemy.attackAnimTicks <= 0 && !preppingHeavy) {
            enemy.weaponAngle = angleToPlayer;
        }

        double distance = Math.hypot(pcx - enemy.x, pcy - enemy.y);
        switch (enemy.type) {
            case ZOMBIE -> {
                moveEnemyToward(enemy, pcx, pcy, 0.75);
                attemptMeleeStrike(enemy, enemy.size * 0.65, 1.0, 55);
            }
            case IMP -> {
                double minRange = TILE * 3.0;
                double maxRange = TILE * 6.5;
                if (distance < minRange) {
                    moveEnemyAway(enemy, pcx, pcy, 0.9);
                } else if (distance > maxRange) {
                    moveEnemyToward(enemy, pcx, pcy, 0.72);
                } else {
                    strafeEnemy(enemy, pcx, pcy, 0.6, (enemy.patternIndex & 1) == 0);
                }
                if (enemy.cd <= 0 && hasLineOfSight(enemy.x, enemy.y, pcx, pcy)) {
                    spawnEnemyProjectile(enemy, pcx, pcy, 4.4, 1.0, 5, false,
                            new Color(210, 186, 120), false, 0, 0);
                    enemy.cd = 60 + rng.nextInt(20);
                }
            }
            case KNIGHT -> {
                boolean los = hasLineOfSight(enemy.x, enemy.y, pcx, pcy);
                if (los) {
                    enemy.braceTicks = Math.min(90, enemy.braceTicks + 6);
                }
                moveEnemyToward(enemy, pcx, pcy, 0.52);
                attemptMeleeStrike(enemy, enemy.size * 0.7, 1.2, 70);
            }
            case OGRE -> {
                if (enemy.windup > 0) {
                    enemy.weaponAngle = angleToPlayer;
                    enemy.attackAnimDuration = Math.max(enemy.attackAnimDuration, enemy.windup);
                    enemy.attackAnimTicks = Math.max(enemy.attackAnimTicks, enemy.windup);
                    enemy.windup--;
                    if (enemy.windup == 0) {
                        if (player != null && intersectsCircleRect(enemy.x, enemy.y, TILE * 1.2, player)) {
                            applyPlayerDamage(2.0 * enemy.damageMultiplier, "Ogre shockwave");
                        }
                        explosions.add(makeExplosion(enemy.x, enemy.y, 24, 36,
                                new Color(255, 156, 110), new Color(255, 216, 170)));
                    }
                    return;
                }
                moveEnemyToward(enemy, pcx, pcy, 0.6);
                if (distance < TILE * 1.3 && enemy.cd <= 0) {
                    enemy.windup = 18;
                    enemy.cd = 80;
                    enemy.weaponAngle = angleToPlayer;
                    enemy.attackAnimDuration = enemy.windup;
                    enemy.attackAnimTicks = enemy.windup;
                }
            }
            case PUMPKIN -> {
                double minRange = TILE * 4.0;
                double maxRange = TILE * 7.0;
                if (distance < minRange) {
                    moveEnemyAway(enemy, pcx, pcy, 0.82);
                } else if (distance > maxRange) {
                    moveEnemyToward(enemy, pcx, pcy, 0.7);
                } else {
                    strafeEnemy(enemy, pcx, pcy, 0.58, (enemy.patternIndex & 1) == 1);
                }
                if (enemy.cd <= 0 && hasLineOfSight(enemy.x, enemy.y, pcx, pcy)) {
                    startBowDraw(enemy, angleToPlayer);
                    spawnEnemyProjectile(enemy, pcx, pcy, 2.8, 1.1, 7, false,
                            new Color(255, 138, 56), true, 40, 32);
                    enemy.cd = 95 + rng.nextInt(30);
                }
            }
            case SKELETON -> {
                double minRange = TILE * 3.2;
                double maxRange = TILE * 6.0;
                if (distance < minRange) {
                    moveEnemyAway(enemy, pcx, pcy, 1.05);
                } else if (distance > maxRange) {
                    moveEnemyToward(enemy, pcx, pcy, 0.82);
                } else {
                    strafeEnemy(enemy, pcx, pcy, 0.72, (enemy.patternIndex & 1) == 0);
                }
                if (enemy.cd <= 0 && hasLineOfSight(enemy.x, enemy.y, pcx, pcy)) {
                    startBowDraw(enemy, angleToPlayer);
                    spawnEnemyProjectile(enemy, pcx, pcy, 5.2, 1.2, 6, false,
                            new Color(220, 220, 220), false, 0, 0);
                    enemy.cd = 55 + rng.nextInt(30);
                }
            }
            case WIZARD -> {
                double minRange = TILE * 4.5;
                double maxRange = TILE * 7.8;
                if (distance < minRange) {
                    moveEnemyAway(enemy, pcx, pcy, 0.8);
                } else if (distance > maxRange) {
                    moveEnemyToward(enemy, pcx, pcy, 0.68);
                } else {
                    strafeEnemy(enemy, pcx, pcy, 0.64, (enemy.patternIndex & 1) == 0);
                }
                if (enemy.cd <= 0 && hasLineOfSight(enemy.x, enemy.y, pcx, pcy)) {
                    enemy.weaponAngle = angleToPlayer;
                    castWizardPattern(enemy, pcx, pcy);
                    enemy.cd = 80 + rng.nextInt(40);
                }
            }
            case NECROMANCER -> {
                double minRange = TILE * 4.8;
                double maxRange = TILE * 7.6;
                if (distance < minRange) {
                    moveEnemyAway(enemy, pcx, pcy, 0.74);
                } else if (distance > maxRange) {
                    moveEnemyToward(enemy, pcx, pcy, 0.62);
                } else {
                    strafeEnemy(enemy, pcx, pcy, 0.6, (enemy.patternIndex & 1) == 0);
                }
                if (enemy.supportTicks > 0) {
                    enemy.supportTicks--;
                }
                if (enemy.cd <= 0) {
                    if (enemy.supportTicks <= 0 && attemptResurrection(enemy)) {
                        enemy.cd = 140;
                        enemy.supportTicks = 200;
                        enemy.weaponAngle = angleToPlayer;
                    } else if (hasLineOfSight(enemy.x, enemy.y, pcx, pcy)) {
                        enemy.weaponAngle = angleToPlayer;
                        spawnEnemyProjectileAngle(enemy, angleToPlayer, 3.4, 1.3 * enemy.damageMultiplier, 6, false,
                                new Color(180, 140, 240), true, 28, 24);
                        enemy.cd = 70 + rng.nextInt(30);
                    }
                }
            }
            case BARD -> {
                double orbit = TILE * (3.2 + (enemy.patternIndex & 1));
                if (distance < orbit) {
                    moveEnemyAway(enemy, pcx, pcy, 0.66);
                } else if (distance > orbit + TILE) {
                    moveEnemyToward(enemy, pcx, pcy, 0.6);
                } else {
                    strafeEnemy(enemy, pcx, pcy, 0.58, (enemy.patternIndex & 1) == 1);
                }
                if (enemy.supportTicks > 0) {
                    enemy.supportTicks--;
                }
                if (enemy.supportTicks <= 0) {
                    empowerAllies(enemy);
                    enemy.supportTicks = 180;
                }
                enemy.weaponAngle = angleToPlayer;
                if (enemy.cd <= 0 && hasLineOfSight(enemy.x, enemy.y, pcx, pcy)) {
                    spawnEnemyProjectile(enemy, pcx, pcy, 3.2, 0.9, 5, false,
                            new Color(200, 240, 160), false, 0, 0);
                    enemy.cd = 90 + rng.nextInt(40);
                }
            }
        }
    }

    private void updateEnemyProjectiles(int pcx, int pcy) {
        for (Bullet b : bullets) {
            if (!b.alive) {
                continue;
            }
            b.x += b.vx;
            b.y += b.vy;
            b.life++;
            if (b.life > b.maxLife) {
                b.alive = false;
            }
            if (!b.alive) {
                continue;
            }
            if (b.x < 0 || b.y < 0 || b.x >= COLS * TILE || b.y >= ROWS * TILE) {
                b.alive = false;
                resolveBulletImpact(b);
                continue;
            }
            int tx = (int) (b.x) / TILE;
            int ty = (int) (b.y) / TILE;
            if (tx >= 0 && tx < COLS && ty >= 0 && ty < ROWS) {
                if (room.g[tx][ty] == T.WALL) {
                    b.alive = false;
                    resolveBulletImpact(b);
                    continue;
                }
            }
            if (b.friendly) {
                boolean consumed = false;
                TrapManager traps = trapManagerForRoom(room);
                if (traps != null && traps.damageTrap(b.x, b.y, Math.max(1.0, b.damage))) {
                    consumed = true;
                    awardCombo(1);
                }
                if (!consumed && room != null && room.enemies != null) {
                    for (RoomEnemy enemy : room.enemies) {
                        if (enemy == null || !enemy.alive) {
                            continue;
                        }
                        int ex0 = enemy.x - enemy.size / 2;
                        int ey0 = enemy.y - enemy.size / 2;
                        int ex1 = ex0 + enemy.size;
                        int ey1 = ey0 + enemy.size;
                        if (b.x >= ex0 && b.x <= ex1 && b.y >= ey0 && b.y <= ey1) {
                            applyDamageToEnemy(enemy, Math.max(1.0, b.damage));
                            consumed = true;
                            awardCombo(2);
                            break;
                        }
                    }
                }
                if (consumed) {
                    b.alive = false;
                    resolveBulletImpact(b);
                }
                continue;
            }

            if (player != null && intersectsCircleRect(b.x, b.y, Math.max(2, b.r), player)) {
                if (parryWindowTicks > 0) {
                    reflectProjectile(b, pcx, pcy);
                    parryWindowTicks = 0;
                    parryFlashTicks = PARRY_FLASH_TICKS;
                    awardCombo(2);
                } else {
                    applyPlayerDamage(b.damage, projectileCause(b));
                    b.alive = false;
                    resolveBulletImpact(b);
                }
            }
        }
        bullets.removeIf(bb -> !bb.alive);
    }

    private void updatePlayerProjectiles() {
        for (Bullet b : playerBullets) {
            if (!b.alive) {
                continue;
            }
            b.x += b.vx;
            b.y += b.vy;
            b.life++;
            if (b.life > b.maxLife) {
                b.alive = false;
            }
            if (!b.alive) {
                continue;
            }
            if (b.x < 0 || b.y < 0 || b.x >= COLS * TILE || b.y >= ROWS * TILE) {
                b.alive = false;
                resolvePlayerProjectileImpact(b);
                continue;
            }
            int tx = (int) (b.x) / TILE;
            int ty = (int) (b.y) / TILE;
            if (tx >= 0 && tx < COLS && ty >= 0 && ty < ROWS) {
                if (room.g[tx][ty] == T.WALL) {
                    b.alive = false;
                    resolvePlayerProjectileImpact(b);
                    continue;
                }
            }
            TrapManager traps = trapManagerForRoom(room);
            if (traps != null && traps.damageTrap(b.x, b.y, b.damage)) {
                b.alive = false;
                resolvePlayerProjectileImpact(b);
                awardCombo(1);
                continue;
            }
            for (RoomEnemy enemy : room.enemies) {
                if (!enemy.alive || !b.alive) {
                    continue;
                }
                int ex0 = enemy.x - enemy.size / 2;
                int ey0 = enemy.y - enemy.size / 2;
                int ex1 = ex0 + enemy.size;
                int ey1 = ey0 + enemy.size;
                if (b.x >= ex0 && b.x <= ex1 && b.y >= ey0 && b.y <= ey1) {
                    b.alive = false;
                    resolvePlayerProjectileImpact(b);
                    awardCombo(1);
                    applyDamageToEnemy(enemy, b.damage);
                }
            }
        }
        playerBullets.removeIf(bb -> !bb.alive);
    }

    private void resolveBulletImpact(Bullet b) {
        if (b.explosive) {
            int radius = b.explosionRadius > 0 ? b.explosionRadius : 30;
            int life = b.explosionLife > 0 ? b.explosionLife : 24;
            Color inner = b.tint != null ? b.tint : new Color(255, 180, 110);
            Color outer = inner.brighter();
            explosions.add(makeExplosion(b.x, b.y, life, radius, inner, outer));
        } else {
            explosions.add(makeExplosion(b.x, b.y));
        }
    }

    private void resolvePlayerProjectileImpact(Bullet b) {
        explosions.add(makeExplosion(b.x, b.y, 16, 18,
                new Color(200, 240, 255), new Color(150, 210, 255)));
    }

    private void attemptMeleeStrike(RoomEnemy enemy, double range, double damage, int cooldown) {
        if (enemy.cd > 0) {
            return;
        }
        if (player != null && intersectsCircleRect(enemy.x, enemy.y, range, player)) {
            String cause = switch (enemy.type) {
                case KNIGHT -> "Knight slash";
                case OGRE -> "Ogre smash";
                case ZOMBIE -> "Zombie swipe";
                case IMP -> "Imp claws";
                case NECROMANCER -> "Necromancer touch";
                case BARD -> "Enchanted baton";
                default -> "Enemy strike";
            };
            applyPlayerDamage(damage * enemy.damageMultiplier, cause);
            enemy.cd = cooldown;
            triggerMeleeSwing(enemy);
        }
        double angle = enemy.facingAngle;
        if (player != null) {
            double centerX = player.x + player.width / 2.0;
            double centerY = player.y + player.height / 2.0;
            double computed = Math.atan2(centerY - enemy.y, centerX - enemy.x);
            if (Double.isFinite(computed)) {
                angle = computed;
            }
        }
        enemy.weaponAngle = angle;
        enemy.attackAnimDuration = 20;
        enemy.attackAnimTicks = 20;
    }

    private void triggerMeleeSwing(RoomEnemy enemy) {
        if (enemy == null) {
            return;
        }
        if (enemy.weapon != WeaponType.SWORD && enemy.weapon != WeaponType.HAMMER) {
            return;
        }
        int duration = enemy.weapon == WeaponType.HAMMER ? 26 : 16;
        enemy.attackAnimDuration = duration;
        enemy.attackAnimTicks = duration;
        enemy.weaponAngle = enemy.facingAngle;
    }

    private void startBowDraw(RoomEnemy enemy, double angle) {
        if (enemy == null) {
            return;
        }
        enemy.weapon = WeaponType.BOW;
        enemy.weaponAngle = angle;
        enemy.bowDrawTicks = Math.max(enemy.bowDrawTicks, 12);
        enemy.attackAnimDuration = Math.max(enemy.attackAnimDuration, 12);
        enemy.attackAnimTicks = Math.max(enemy.attackAnimTicks, 6);
    }

    private void triggerStaffCast(RoomEnemy enemy, double angle) {
        if (enemy == null) {
            return;
        }
        enemy.weapon = WeaponType.STAFF;
        enemy.weaponAngle = angle;
        enemy.attackAnimDuration = 20;
        enemy.attackAnimTicks = 20;
    }

    private void applyPlayerDamage(double damage, String cause) {
        if (player == null || damage <= 0) {
            return;
        }
        if (iFrames > 0) {
            return;
        }
        playerDamageBuffer += damage;
        int whole = (int) Math.floor(playerDamageBuffer);
        if (whole <= 0) {
            return;
        }
        playerDamageBuffer -= whole;
        playerHP = Math.max(0, playerHP - whole);
        iFrames = 40;
        lastDamageCause = (cause == null || cause.isBlank()) ? "Unknown danger" : cause;
        resetCombo();
        if (playerHP <= 0) {
            onPlayerDeath();
        }
    }

    private void applyTrapDamage(int damage, String source) {
        if (damage <= 0) {
            return;
        }
        applyPlayerDamage(damage, source == null || source.isBlank() ? "Trap" : source);
    }

    private void applyDamageToEnemy(RoomEnemy enemy, double damage) {
        if (enemy == null || !enemy.alive || damage <= 0) {
            return;
        }
        double modifier = 1.0;
        if (enemy.type == EnemyType.KNIGHT && enemy.braceTicks > 0 && player != null &&
                hasLineOfSight(enemy.x, enemy.y, player.x + player.width / 2, player.y + player.height / 2)) {
            modifier *= 0.35;
        }
        enemy.damageBuffer += damage * modifier;
        while (enemy.damageBuffer >= 1.0) {
            enemy.health--;
            enemy.damageBuffer -= 1.0;
        }
        if (enemy.health <= 0) {
            eliminateEnemy(room, enemy);
        }
    }

    private void moveEnemyToward(RoomEnemy enemy, int targetX, int targetY, double speed) {
        double dx = targetX - enemy.x;
        double dy = targetY - enemy.y;
        double adjustedSpeed = speed * (enemy == null ? 1.0 : enemy.speedMultiplier);
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return;
        }
        double normX = dx / len;
        double normY = dy / len;
        int mx = (int) Math.round(normX * adjustedSpeed);
        int my = (int) Math.round(normY * adjustedSpeed);
        if (mx == 0 && Math.abs(adjustedSpeed) >= 0.45) {
            mx = adjustedSpeed >= 0 ? (normX >= 0 ? 1 : -1) : (normX >= 0 ? -1 : 1);
        }
        if (my == 0 && Math.abs(adjustedSpeed) >= 0.45) {
            my = adjustedSpeed >= 0 ? (normY >= 0 ? 1 : -1) : (normY >= 0 ? -1 : 1);
        }
        if (mx != 0) {
            attemptEnemyMove(enemy, mx, 0);
        }
        if (my != 0) {
            attemptEnemyMove(enemy, 0, my);
        }
    }

    private void moveEnemyAway(RoomEnemy enemy, int targetX, int targetY, double speed) {
        moveEnemyToward(enemy, targetX, targetY, -speed);
    }

    private void strafeEnemy(RoomEnemy enemy, int targetX, int targetY, double speed, boolean clockwise) {
        double dx = targetX - enemy.x;
        double dy = targetY - enemy.y;
        double adjustedSpeed = speed * (enemy == null ? 1.0 : enemy.speedMultiplier);
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return;
        }
        double sx = clockwise ? dy / len : -dy / len;
        double sy = clockwise ? -dx / len : dx / len;
        int mx = (int) Math.round(sx * adjustedSpeed);
        int my = (int) Math.round(sy * adjustedSpeed);
        if (mx == 0 && Math.abs(adjustedSpeed) >= 0.45) {
            mx = sx >= 0 ? 1 : -1;
        }
        if (my == 0 && Math.abs(adjustedSpeed) >= 0.45) {
            my = sy >= 0 ? 1 : -1;
        }
        if (mx != 0) {
            attemptEnemyMove(enemy, mx, 0);
        }
        if (my != 0) {
            attemptEnemyMove(enemy, 0, my);
        }
    }

    private boolean hasLineOfSight(int sx, int sy, int tx, int ty) {
        if (room == null) {
            return false;
        }
        double dx = tx - sx;
        double dy = ty - sy;
        double distance = Math.hypot(dx, dy);
        int steps = Math.max(1, (int) Math.ceil(distance / (TILE / 2.0)));
        double stepX = dx / steps;
        double stepY = dy / steps;
        double cx = sx;
        double cy = sy;
        for (int i = 0; i <= steps; i++) {
            int ix = Math.max(0, Math.min(COLS - 1, (int) (cx / TILE)));
            int iy = Math.max(0, Math.min(ROWS - 1, (int) (cy / TILE)));
            if (room.g[ix][iy] == T.WALL) {
                return false;
            }
            cx += stepX;
            cy += stepY;
        }
        return true;
    }

    private void castWizardPattern(RoomEnemy enemy, int pcx, int pcy) {
        double baseAngle = Math.atan2(pcy - enemy.y, pcx - enemy.x);
        triggerStaffCast(enemy, baseAngle);
        int pattern = enemy.patternIndex % 3;
        switch (pattern) {
            case 0 -> {
                spawnEnemyProjectileAngle(enemy, baseAngle, 4.6, 1.0, 5, true, null, false, 0, 0);
                spawnEnemyProjectileAngle(enemy, baseAngle + 0.25, 4.4, 1.0, 5, true, null, false, 0, 0);
                spawnEnemyProjectileAngle(enemy, baseAngle - 0.25, 4.4, 1.0, 5, true, null, false, 0, 0);
            }
            case 1 -> spawnEnemyProjectileAngle(enemy, baseAngle, 3.1, 1.4, 7, false,
                    new Color(130, 192, 255), true, 36, 34);
            case 2 -> {
                spawnEnemyProjectileAngle(enemy, baseAngle, 5.4, 0.8, 4, false,
                        new Color(230, 140, 255), false, 0, 0);
                spawnEnemyProjectileAngle(enemy, baseAngle + 0.12, 5.0, 0.8, 4, false,
                        new Color(255, 180, 120), false, 0, 0);
            }
        }
        enemy.patternIndex = (enemy.patternIndex + 1) % 6;
    }

    private void spawnEnemyProjectile(RoomEnemy shooter, double targetX, double targetY, double speed,
                                      double damage, int radius, boolean useTexture, Color tint,
                                      boolean explosive, int explosionRadius, int explosionLife) {
        if (shooter == null) {
            return;
        }
        double angle = Math.atan2(targetY - shooter.y, targetX - shooter.x);
        spawnEnemyProjectileAngle(shooter, angle, speed, damage * shooter.damageMultiplier, radius,
                useTexture, tint, explosive, explosionRadius, explosionLife);
    }

    private void spawnEnemyProjectileAngle(RoomEnemy shooter, double angle, double speed, double damage,
                                           int radius, boolean useTexture, Color tint,
                                           boolean explosive, int explosionRadius, int explosionLife) {
        Bullet b = new Bullet();
        b.x = shooter.x;
        b.y = shooter.y;
        b.vx = Math.cos(angle) * speed;
        b.vy = Math.sin(angle) * speed;
        b.r = radius <= 0 ? ENEMY_PROJECTILE_RADIUS : radius;
        b.damage = Math.max(0.25, damage);
        b.maxLife = 520;
        b.useTexture = useTexture;
        b.tint = tint;
        b.explosive = explosive;
        b.explosionRadius = explosionRadius;
        b.explosionLife = explosionLife;
        if (shooter.weapon == WeaponType.BOW) {
            b.kind = ProjectileKind.ARROW;
            b.useTexture = false;
            if (b.tint == null) {
                b.tint = shooter.type == EnemyType.PUMPKIN
                        ? new Color(255, 190, 120)
                        : new Color(220, 220, 220);
            }
        } else {
            b.kind = ProjectileKind.ORB;
        }
        bullets.add(b);
    }

    private void eliminateEnemy(Room r, RoomEnemy enemy) {
        if (enemy == null || !enemy.alive) return;
        enemy.alive = false;
        if (enemy.spawn != null) {
            enemy.spawn.defeated = true;
        }
        registerEnemyDefeat();
        spawnKeyPickup(r, enemy.x, enemy.y);
        if (enemy.coinReward > 0) {
            spawnCoinPickup(r, enemy.x, enemy.y, enemy.coinReward);
        }
    }

    private void spawnKeyPickup(Room r, int x, int y) {
        if (r == null) return;
        KeyPickup key = new KeyPickup();
        key.x = x;
        key.y = y;
        r.keyPickups.add(key);
        showMessage(texts.text("key_drop"));
    }

    private void spawnCoinPickup(Room r, int x, int y, int value) {
        if (r == null) {
            return;
        }
        CoinPickup coin = new CoinPickup();
        coin.x = x;
        coin.y = y;
        coin.value = Math.max(1, value);
        coin.r = Math.max(8, (int) (TILE * 0.25));
        r.coinPickups.add(coin);
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

    private void checkCoinPickup() {
        if (room == null || room.coinPickups.isEmpty() || player == null) {
            return;
        }
        int pcx = player.x + player.width / 2;
        int pcy = player.y + player.height / 2;
        Iterator<CoinPickup> it = room.coinPickups.iterator();
        while (it.hasNext()) {
            CoinPickup coin = it.next();
            double dx = coin.x - pcx;
            double dy = coin.y - pcy;
            double maxR = coin.r + Math.min(player.width, player.height) / 2.0;
            if (dx * dx + dy * dy <= maxR * maxR) {
                it.remove();
                int gained = Math.max(1, coin.value);
                coins += gained;
                showMessage(String.format("+%d coins (total %d)", gained, coins));
            }
        }
    }

    private void animateCoinPickups() {
        if (room == null || room.coinPickups.isEmpty()) {
            return;
        }
        for (CoinPickup coin : room.coinPickups) {
            if (coin == null) {
                continue;
            }
            coin.animTick = (coin.animTick + 1) % 120;
        }
    }

    private void checkForBossEncounter() {
        if (inBoss) return;
        BossEncounter encounter = bossEncounters.get(worldPos);
        if (encounter != null && !encounter.defeated) {
            if (!canEnterBossEncounter(encounter)) {
                showMessage("A crushing aura repels you. Vitality sigils required: " + encounter.requiredVitalityLevel);
                return;
            }
            showMessage(texts.text("boss_challenge", formatBossName(encounter.kind)));
            triggerBossEncounter(encounter);
        }
    }

    private boolean canEnterBossEncounter(BossEncounter encounter) {
        if (encounter == null) {
            return true;
        }
        return vitalityUpgrades >= encounter.requiredVitalityLevel;
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
        created.requiredVitalityLevel = Math.min(MAX_VITALITY_UPGRADES, Math.max(2, 2 + bossesDefeated));
        bossEncounters.put(new Point(pos), created);
        return created;
    }

    private BossEncounter previewBossFor(Point pos, boolean consumedKey) {
        if (pos == null) {
            return null;
        }
        BossEncounter existing = bossEncounters.get(pos);
        if (existing != null) {
            return existing;
        }
        boolean isNewVisit = !visited.contains(new Point(pos));
        if (!isNewVisit) {
            return null;
        }
        if (!shouldSeedBossAt(pos, consumedKey)) {
            return null;
        }
        return ensureBossFor(pos);
    }

    private boolean shouldSeedBossAt(Point pos, boolean consumedKey) {
        if (pos == null) {
            return false;
        }
        if (bossEncounters != null && activeBossDoors() >= MAX_PENDING_BOSS_DOORS) {
            return false;
        }
        int requiredVitality = Math.min(MAX_VITALITY_UPGRADES, Math.max(MIN_VITALITY_FOR_BOSS_SPAWN, 2 + bossesDefeated));
        if (vitalityUpgrades < requiredVitality) {
            return false;
        }
        if (!consumedKey && roomsVisited < MIN_EXPLORED_ROOMS_BEFORE_BOSS) {
            return false;
        }
        int distance = Math.abs(pos.x) + Math.abs(pos.y);
        if (!consumedKey && distance < SAFE_RING_RADIUS) {
            return false;
        }
        return true;
    }

    private int activeBossDoors() {
        if (bossEncounters == null || bossEncounters.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (BossEncounter encounter : bossEncounters.values()) {
            if (encounter != null && !encounter.defeated) {
                count++;
            }
        }
        return count;
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
        healPlayerTo(playerMaxHp());
        int rewardCoins = 25;
        coins += rewardCoins;
        String victory = texts.text("victory_heal", keysHeld, playerMaxHp());
        showMessage(victory + "  +" + rewardCoins + " coins");
        bossesDefeated++;
        checkpointRoom = new Point(worldPos);
        checkpointBoss = encounter.kind;
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
        for (BossBattlePanel.BossKind kind : STORY_BOSS_SEQUENCE) {
            if (kind == BossBattlePanel.BossKind.GOLDEN_KNIGHT || BossBattlePanel.hasDedicatedAssets(kind)) {
                bossPool.add(kind);
            }
        }
        if (bossPool.isEmpty()) {
            Collections.addAll(bossPool, BossBattlePanel.BossKind.values());
        }
    }

    private List<BossBattlePanel.BossKind> activeStoryOrder() {
        List<BossBattlePanel.BossKind> order = new ArrayList<>();
        for (BossBattlePanel.BossKind candidate : STORY_BOSS_SEQUENCE) {
            if (candidate == BossBattlePanel.BossKind.GOLDEN_KNIGHT || BossBattlePanel.hasDedicatedAssets(candidate)) {
                order.add(candidate);
            }
        }
        if (bossEncounters != null) {
            for (BossEncounter encounter : bossEncounters.values()) {
                if (encounter != null && encounter.kind != null && !order.contains(encounter.kind)) {
                    order.add(encounter.kind);
                }
            }
        }
        if (order.isEmpty()) {
            Collections.addAll(order, BossBattlePanel.BossKind.values());
        }
        return order;
    }

    private int storyChapterFor(BossBattlePanel.BossKind kind) {
        if (kind == null) {
            return 0;
        }
        List<BossBattlePanel.BossKind> order = activeStoryOrder();
        int idx = order.indexOf(kind);
        return idx >= 0 ? idx : Math.max(0, kind.ordinal());
    }

    private Explosion makeExplosion(double x, double y) {
        return makeExplosion(x, y, 18, 22,
                new Color(255, 200, 80),
                new Color(255, 240, 160));
    }

    private Explosion makeExplosion(double x, double y, int life, int radius, Color inner, Color outer) {
        Explosion ex = new Explosion();
        ex.x = x;
        ex.y = y;
        ex.life = life;
        ex.maxR = radius;
        if (inner != null) {
            ex.inner = inner;
        }
        if (outer != null) {
            ex.outer = outer;
        }
        return ex;
    }

    private void onPlayerDeath() {
        SwingUtilities.invokeLater(() -> {
            if (difficulty == Difficulty.HARD) {
                if (!gameOverShown) {
                    gameOverShown = true;
                    String causeLine = (lastDamageCause == null || lastDamageCause.isBlank())
                            ? ""
                            : "\nCause: " + lastDamageCause;
                    JOptionPane.showMessageDialog(DungeonRooms.this,
                            "Your adventure ends here." + causeLine,
                            "Game Over",
                            JOptionPane.INFORMATION_MESSAGE);
                }
                exitHandler.run();
                return;
            }
            respawnAtCheckpoint();
        });
    }

    private void respawnAtCheckpoint() {
        bullets.clear();
        playerBullets.clear();
        explosions.clear();
        clearMovementInput(true);
        playerDamageBuffer = 0.0;
        inBoss = false;
        awaitingShopExitClear = false;
        lastShopDoorway = null;

        Point target = checkpointRoom == null ? new Point(0, 0) : new Point(checkpointRoom);
        worldPos = target;
        room = makeOrGetRoom(worldPos, null);
        ensureTrapLayoutForRoom(worldPos, room);
        spawnEnemiesIfNeeded(worldPos, room);
        visited.add(new Point(worldPos));
        placePlayerAtCenter();
        trapPlayer.reset();
        shopManager.ensureDoorway(room, worldPos, this::hasRoomAt, this::carveDoorOnGrid, this::markRoomDirty);
        refreshPlayerHpAfterUpgrade();
        healPlayerTo(playerMaxHp());
        iFrames = 120;
        showMessage(texts.text("respawn"));
        repaint();
        requestFocusInWindow();
        persistProgressAsync("checkpoint respawn");
    }

    private void triggerBossEncounter(BossEncounter encounter) {
        if (encounter == null) return;
        playBossPrelude(encounter);
        inBoss = true;
        timer.stop();
        BossBattlePanel.BattleTuning tuning = createBattleTuning(encounter);
        Consumer<Outcome> finish = outcome -> SwingUtilities.invokeLater(() -> {
            if (outcome == Outcome.HERO_WIN) {
                encounter.defeated = true;
                grantBossReward(encounter);
                if (encounter.kind == BossBattlePanel.BossKind.GOLDEN_KNIGHT) {
                    queenRescued = true;
                    handleGameWon();
                } else {
                    playBossEpilogue(encounter.kind);
                }
            } else {
                showMessage(texts.text("boss_repelled"));
                onPlayerDeath();
            }
            inBoss = false;
            iFrames = 60; // grace on return
            timer.start();
            clearMovementInput(true);
            requestFocusInWindow();
        });

        if (bossBattleHost != null) {
            bossBattleHost.runBossBattle(encounter.kind, tuning, finish);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Boss Battle");
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setContentPane(BossBattlePanel.create(encounter.kind, tuning, outcome -> {
                finish.accept(outcome);
                f.dispose();
            }));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    private BossBattlePanel.BattleTuning createBattleTuning(BossEncounter encounter) {
        int maxHp = playerMaxHp();
        int heroHp = 180 + maxHp * 18;
        int heroPower = 26 + damageLevel * 4;
        int heroGuard = 18 + Math.max(0, vitalityUpgrades) * 2 + Math.max(0, dungeonHeartUpgrades);
        int heroSpeed = 18 + Math.max(0, damageLevel);
        Stats stats = new Stats(heroHp, heroPower, heroGuard, heroSpeed);
        double heroOffense = 1.08 + damageLevel * 0.05;
        double heroDefense = 1.18 + (vitalityUpgrades + dungeonHeartUpgrades) * 0.04;
        int heroMomentum = Math.min(4, 2 + damageLevel / 2);

        double bossHealthMultiplier = 1.0 + Math.max(0, bossesDefeated) * 0.15;
        if (encounter != null) {
            bossHealthMultiplier += Math.max(0, encounter.requiredVitalityLevel - 2) * 0.08;
        }
        double bossOffenseMultiplier = 1.0 + Math.max(0, bossesDefeated) * 0.08;
        double bossDefenseMultiplier = 1.0 + Math.max(0, bossesDefeated) * 0.06;
        int bossHealthBonus = maxHp * 6;

        return new BossBattlePanel.BattleTuning(stats,
                heroOffense,
                heroDefense,
                heroMomentum,
                bossHealthMultiplier,
                bossOffenseMultiplier,
                bossDefenseMultiplier,
                bossHealthBonus);
    }

    private static boolean intersectsCircleRect(double cx, double cy, double r, Rectangle rect) {
        double closestX = Math.max(rect.x, Math.min(cx, rect.x + rect.width));
        double closestY = Math.max(rect.y, Math.min(cy, rect.y + rect.height));
        double dx = cx - closestX;
        double dy = cy - closestY;
        return dx*dx + dy*dy <= r*r;
    }

    private void shootPlayerBullet() {
        if (playerShotCooldownTicks > 0 || player == null) {
            return;
        }
        Bullet b = new Bullet();
        int pcx = player.x + player.width/2;
        int pcy = player.y + player.height/2;
        b.x = pcx; b.y = pcy;
        double dx = mouseX - pcx;
        double dy = mouseY - pcy;
        double l = Math.max(1e-6, Math.hypot(dx, dy));
        double spd = 7.0 + comboLevel * 0.4;
        b.vx = dx / l * spd;
        b.vy = dy / l * spd;
        b.r = PLAYER_PROJECTILE_RADIUS;
        b.friendly = true;
        b.damage = playerDamage();
        b.maxLife = 360;
        b.useTexture = true;
        playerBullets.add(b);
        playerShotCooldownTicks = Math.max(PLAYER_SHOT_MIN_COOLDOWN,
                PLAYER_SHOT_BASE_COOLDOWN - comboLevel * 2);
    }

    // ---- file-system sprite loading ----
    /** Create a fresh room with outer walls and 1–3 total doors (including the entrance, if any). */
    private Room generateNewRoom(Dir mustHaveEntrance) {
        Room r = new Room();
        r.floorThemeSeed = secureRandom.nextInt(10_000);
        r.wallThemeSeed = secureRandom.nextInt(10_000);
        r.accentSeed = secureRandom.nextInt(10_000);
        if (ROOM_PALETTES.length > 0) {
            r.paletteIndex = secureRandom.nextInt(ROOM_PALETTES.length);
        }

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
        markRoomDirty(r);
        // Revalidate enemies: push out of walls if necessary
        List<RoomEnemy> kept = new ArrayList<>();
        for (RoomEnemy e : r.enemies) {
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
        markRoomDirty(r);
        removeDoorConflicts(r);
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
        trapPlayer.syncFromDungeon();
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
        trapPlayer.syncFromDungeon();
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

    private double tickSeconds() {
        int refresh = settings == null ? FPS : Math.max(30, settings.refreshRate());
        return 1.0 / refresh;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (paused || inBoss) {
            return;
        }
        animTick++;
        if (iFrames > 0) iFrames--;
        if (healTicks > 0) healTicks--;
        tickAbilityTimers();
        updatePlayer();
        updateTraps();
        updateCombat();
        checkKeyPickup();
        checkCoinPickup();
        animateCoinPickups();
        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0) statusMessage = "";
        }
        checkForBossEncounter();
        repaint();
    }

    private void updatePlayer() {
        if (dashTicks > 0) {
            int dx = (int) Math.round(dashDirX * DASH_SPEED);
            int dy = (int) Math.round(dashDirY * DASH_SPEED);
            moveAxis(dx, 0);
            moveAxis(0, dy);
            trapPlayer.grantInvulnerability(DASH_INVUL_SECONDS);
        } else {
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
        }

        Dir through = touchingDoorOnEdge();
        if (through != null) switchRoom(through);
    }

    private void tickAbilityTimers() {
        if (dashTicks > 0) {
            dashTicks--;
            if (dashTicks == 0) {
                dashDirX = 0.0;
                dashDirY = 0.0;
            }
        }
        if (dashCooldownTicks > 0) {
            dashCooldownTicks--;
        }
        if (parryWindowTicks > 0) {
            parryWindowTicks--;
        }
        if (parryCooldownTicks > 0) {
            parryCooldownTicks--;
        }
        if (parryFlashTicks > 0) {
            parryFlashTicks--;
        }
        if (specialCooldownTicks > 0) {
            specialCooldownTicks--;
        }
        if (playerShotCooldownTicks > 0) {
            playerShotCooldownTicks--;
        }
        if (comboCount > 0) {
            if (comboTimerTicks > 0) {
                comboTimerTicks--;
            } else {
                comboCount = Math.max(0, comboCount - 1);
                comboTimerTicks = COMBO_DECAY_TICKS / 2;
                updateComboLevel();
            }
        } else {
            comboTimerTicks = 0;
            updateComboLevel();
        }
    }

    private void updateComboLevel() {
        int newLevel = Math.min(MAX_COMBO_LEVEL, Math.max(0, comboCount) / 4);
        if (newLevel != comboLevel) {
            int previous = comboLevel;
            comboLevel = newLevel;
            if (comboLevel > previous && comboLevel > 0) {
                showMessage("Combo level " + comboLevel + "!");
            }
        }
    }

    private void awardCombo(int amount) {
        if (amount <= 0) {
            return;
        }
        comboCount = Math.max(0, comboCount + amount);
        comboTimerTicks = COMBO_DECAY_TICKS;
        updateComboLevel();
    }

    private void resetCombo() {
        if (comboCount == 0) {
            return;
        }
        comboCount = 0;
        comboTimerTicks = 0;
        updateComboLevel();
    }

    private void startDash() {
        if (dashCooldownTicks > 0 || dashTicks > 0 || player == null) {
            return;
        }
        double dirX = (right ? 1.0 : 0.0) - (left ? 1.0 : 0.0);
        double dirY = (down ? 1.0 : 0.0) - (up ? 1.0 : 0.0);
        if (Math.abs(dirX) < 1e-4 && Math.abs(dirY) < 1e-4) {
            int pcx = player.x + player.width / 2;
            int pcy = player.y + player.height / 2;
            dirX = mouseX - pcx;
            dirY = mouseY - pcy;
        }
        if (Math.abs(dirX) < 1e-4 && Math.abs(dirY) < 1e-4) {
            dirY = -1.0;
        }
        double len = Math.hypot(dirX, dirY);
        if (len < 1e-4) {
            dirX = 1.0;
            dirY = 0.0;
            len = 1.0;
        }
        dashDirX = dirX / len;
        dashDirY = dirY / len;
        dashTicks = DASH_DURATION_TICKS;
        dashCooldownTicks = DASH_COOLDOWN_TICKS;
        trapPlayer.grantInvulnerability(DASH_INVUL_SECONDS);
        iFrames = Math.max(iFrames, (int) Math.ceil(DASH_INVUL_SECONDS / tickSeconds()));
    }

    private void startParry() {
        if (parryCooldownTicks > 0 || parryWindowTicks > 0) {
            return;
        }
        parryWindowTicks = PARRY_WINDOW_TICKS;
        parryCooldownTicks = PARRY_COOLDOWN_TICKS;
        parryFlashTicks = PARRY_FLASH_TICKS;
        trapPlayer.grantInvulnerability(tickSeconds() * PARRY_WINDOW_TICKS / 2.0);
    }

    private void activateAreaAbility() {
        if (specialCooldownTicks > 0 || room == null || player == null) {
            return;
        }
        int baseCooldown = Math.max(90, SPECIAL_COOLDOWN_TICKS - comboLevel * 40);
        specialCooldownTicks = baseCooldown;
        int pcx = player.x + player.width / 2;
        int pcy = player.y + player.height / 2;
        switch (nextAreaAbility) {
            case FIRE_RING -> {
                double radius = TILE * (2.6 + comboLevel * 0.45);
                applyFireRing(pcx, pcy, radius);
                nextAreaAbility = AreaAbility.LIGHTNING_PULSE;
            }
            case LIGHTNING_PULSE -> {
                applyLightningPulse(pcx, pcy);
                nextAreaAbility = AreaAbility.FIRE_RING;
            }
        }
    }

    private void applyFireRing(int pcx, int pcy, double radius) {
        explosions.add(makeExplosion(pcx, pcy, 26, (int) Math.round(radius),
                new Color(255, 150, 90), new Color(255, 220, 150)));
        double radiusSq = radius * radius;
        if (room != null && room.enemies != null) {
            for (RoomEnemy enemy : room.enemies) {
                if (enemy == null || !enemy.alive) {
                    continue;
                }
                double dx = enemy.x - pcx;
                double dy = enemy.y - pcy;
                if (dx * dx + dy * dy <= radiusSq) {
                    applyDamageToEnemy(enemy, playerDamage() * (1.2 + comboLevel * 0.2));
                    enemy.cd += 18;
                    awardCombo(1);
                }
            }
        }
        TrapManager traps = trapManagerForRoom(room);
        if (traps != null) {
            int samples = 12;
            for (int i = 0; i < samples; i++) {
                double angle = (Math.PI * 2.0 * i) / samples;
                double sx = pcx + Math.cos(angle) * radius;
                double sy = pcy + Math.sin(angle) * radius;
                if (traps.damageTrap(sx, sy, playerDamage())) {
                    awardCombo(1);
                }
            }
        }
    }

    private void applyLightningPulse(int pcx, int pcy) {
        if (room == null || room.enemies == null) {
            return;
        }
        int limit = Math.min(room.enemies.size(), 2 + comboLevel);
        Set<RoomEnemy> targeted = new HashSet<>();
        for (int i = 0; i < limit; i++) {
            RoomEnemy best = null;
            double bestDist = Double.MAX_VALUE;
            for (RoomEnemy candidate : room.enemies) {
                if (candidate == null || !candidate.alive || targeted.contains(candidate)) {
                    continue;
                }
                double dist = distanceSq(candidate.x, candidate.y, pcx, pcy);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            targeted.add(best);
            applyDamageToEnemy(best, playerDamage() * (1.4 + comboLevel * 0.25));
            best.cd += 40;
            best.braceTicks = Math.max(best.braceTicks, 24);
            explosions.add(makeExplosion(best.x, best.y, 20, 28,
                    new Color(150, 210, 255), new Color(90, 150, 230)));
            awardCombo(2);
        }
    }

    private double distanceSq(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }

    private void reflectProjectile(Bullet b, int pcx, int pcy) {
        if (b == null) {
            return;
        }
        double dirX = mouseX - pcx;
        double dirY = mouseY - pcy;
        if (Math.abs(dirX) < 1e-4 && Math.abs(dirY) < 1e-4) {
            dirX = dashDirX;
            dirY = dashDirY;
        }
        if (Math.abs(dirX) < 1e-4 && Math.abs(dirY) < 1e-4) {
            dirX = -b.vx;
            dirY = -b.vy;
        }
        double len = Math.hypot(dirX, dirY);
        if (len < 1e-4) {
            dirX = 1.0;
            dirY = 0.0;
            len = 1.0;
        }
        double speed = Math.max(4.5, Math.hypot(b.vx, b.vy)) + comboLevel * 0.4;
        b.vx = dirX / len * speed;
        b.vy = dirY / len * speed;
        b.friendly = true;
        b.damage = Math.max(b.damage, playerDamage() * 1.1);
        b.life = 0;
        b.tint = new Color(190, 230, 255);
        b.kind = ProjectileKind.ORB;
    }

    private String projectileCause(Bullet b) {
        if (b == null) {
            return "Projectile";
        }
        if (b.explosive) {
            return "Explosive orb";
        }
        return switch (b.kind) {
            case ARROW -> "Arrow";
            case ORB -> "Arcane bolt";
        };
    }

    private boolean attemptResurrection(RoomEnemy necromancer) {
        if (room == null || room.enemySpawns == null || necromancer == null) {
            return false;
        }
        List<EnemySpawn> corpses = new ArrayList<>();
        for (EnemySpawn spawn : room.enemySpawns) {
            if (spawn == null || !spawn.defeated) {
                continue;
            }
            if (spawn.type == EnemyType.NECROMANCER) {
                continue;
            }
            corpses.add(spawn);
        }
        if (corpses.isEmpty()) {
            return false;
        }
        EnemySpawn target = corpses.get(rng.nextInt(corpses.size()));
        target.defeated = false;
        RoomEnemy revived = instantiateEnemyFromSpawn(target);
        revived.x = target.x;
        revived.y = target.y;
        revived.health = Math.max(1, revived.maxHealth - 1);
        revived.damageMultiplier = 1.0;
        revived.speedMultiplier = 1.0;
        revived.buffTicks = 0;
        revived.supportTicks = 0;
        room.enemies.add(revived);
        explosions.add(makeExplosion(target.x, target.y, 22, 28,
                new Color(180, 150, 255), new Color(130, 100, 220)));
        showMessage("A necromancer resurrects a foe!");
        return true;
    }

    private void empowerAllies(RoomEnemy bard) {
        if (room == null || room.enemies == null || bard == null) {
            return;
        }
        for (RoomEnemy ally : room.enemies) {
            if (ally == null || ally == bard || !ally.alive) {
                continue;
            }
            ally.buffTicks = Math.max(ally.buffTicks, 180);
            ally.damageMultiplier = Math.min(ally.damageMultiplier + 0.25, 2.0);
            ally.speedMultiplier = Math.min(ally.speedMultiplier + 0.2, 1.8);
        }
        explosions.add(makeExplosion(bard.x, bard.y, 18, 24,
                new Color(210, 255, 180), new Color(160, 220, 150)));
        showMessage("A bard inspires the enemies!");
    }

    private void updateTraps() {
        TrapManager manager = trapManagerForRoom(room);
        if (manager == null) {
            return;
        }
        trapPlayer.syncFromDungeon();
        manager.update(tickSeconds(), trapPlayer);
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

    private void attemptEnemyMove(RoomEnemy e, int dx, int dy) {
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
        if (player == null || room == null) {
            return null;
        }
        int cx = player.x + player.width / 2;
        int cy = player.y + player.height / 2;

        Dir candidate = null;
        if (cy < TILE / 3) {
            Point t = doorTile(Dir.N);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cx - (t.x * TILE + TILE / 2)) < TILE / 2) {
                candidate = Dir.N;
            }
        } else if (cy > ROWS * TILE - TILE / 3) {
            Point t = doorTile(Dir.S);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cx - (t.x * TILE + TILE / 2)) < TILE / 2) {
                candidate = Dir.S;
            }
        } else if (cx < TILE / 3) {
            Point t = doorTile(Dir.W);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cy - (t.y * TILE + TILE / 2)) < TILE / 2) {
                candidate = Dir.W;
            }
        } else if (cx > COLS * TILE - TILE / 3) {
            Point t = doorTile(Dir.E);
            if (room.g[t.x][t.y] == T.DOOR && Math.abs(cy - (t.y * TILE + TILE / 2)) < TILE / 2) {
                candidate = Dir.E;
            }
        }

        if (candidate == null) {
            if (awaitingShopExitClear && lastShopDoorway != null && playerClearedShopDoorway(lastShopDoorway)) {
                awaitingShopExitClear = false;
            }
            return null;
        }

        if (room.shopDoor == candidate) {
            if (awaitingShopExitClear) {
                if (playerClearedShopDoorway(candidate)) {
                    awaitingShopExitClear = false;
                } else {
                    return null;
                }
            }
        } else {
            awaitingShopExitClear = false;
        }
        return candidate;
    }

    private boolean playerClearedShopDoorway(Dir doorSide) {
        if (doorSide == null || room == null || player == null) {
            return true;
        }
        Point door = doorTile(doorSide);
        if (door == null) {
            return true;
        }
        int doorCenterX = door.x * TILE + TILE / 2;
        int doorCenterY = door.y * TILE + TILE / 2;
        int cx = player.x + player.width / 2;
        int cy = player.y + player.height / 2;
        int clearance = TILE;
        return switch (doorSide) {
            case N -> cy >= doorCenterY + clearance;
            case S -> cy <= doorCenterY - clearance;
            case W -> cx >= doorCenterX + clearance;
            case E -> cx <= doorCenterX - clearance;
        };
    }

    private void switchRoom(Dir exitSide) {
        if (room != null && room.shopDoor == exitSide) {
            openShop(exitSide);
            return;
        }
        awaitingShopExitClear = false;
        lastShopDoorway = null;
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

        BossEncounter pendingEncounter = previewBossFor(nextPos, consumedKey);
        if (pendingEncounter != null && !pendingEncounter.defeated && !canEnterBossEncounter(pendingEncounter)) {
            showMessage("The guardian beyond demands " + pendingEncounter.requiredVitalityLevel + " vitality sigils.");
            suppressNextMovementPress = true;
            return;
        }

        // Load or create the room and guarantee the entrance
        Room nextRoom = makeOrGetRoom(nextPos, entranceSide);
        if (consumedKey) {
            nextRoom.lockedDoors.remove(entranceSide);
        }
        worldPos = nextPos;
        room = nextRoom;
        ensureTrapLayoutForRoom(worldPos, room);

        // Track exploration
        boolean isNewVisit = registerVisit(worldPos);

        // Place player just inside the entrance we came through (from the new room’s perspective)
        placePlayerJustInside(entranceSide);
        bullets.clear();
        playerBullets.clear();
        explosions.clear();

        if (isNewVisit) {
            BossEncounter encounter = bossEncounters.get(worldPos);
            if (encounter == null && shouldSeedBossAt(worldPos, consumedKey)) {
                encounter = ensureBossFor(worldPos);
            }
            if (encounter != null && !encounter.defeated) {
                prepareBossRoom(nextRoom);
                String messageKey = consumedKey ? "boss_unlock" : "boss_warning";
                showMessage(texts.text(messageKey, formatBossName(encounter.kind)));
            }
        }

        spawnEnemiesIfNeeded(worldPos, room);

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
            worldGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
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

    private BufferedImage renderRoomBackground(Room target, RoomPalette palette) {
        if (target == null) {
            return null;
        }
        if (!target.backgroundDirty && target.cachedBackground != null && target.cachedTextureEpoch == textureEpoch) {
            return target.cachedBackground;
        }
        BufferedImage img = new BufferedImage(COLS * TILE, ROWS * TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (textures != null && textures.isReady()) {
                paintRoomTiles(g, target, palette);
            } else {
                g.setColor(new Color(18, 64, 78));
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
            }
        } finally {
            g.dispose();
        }
        target.cachedBackground = img;
        target.cachedTextureEpoch = textureEpoch;
        target.backgroundDirty = false;
        return img;
    }

    private void paintRoomTiles(Graphics2D gg, Room target, RoomPalette palette) {
        if (target == null) {
            return;
        }
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int px = x * TILE, py = y * TILE;
                T t = target.g[x][y];
                if (textures != null && textures.isReady()) {
                    int fCount = Math.max(1, textures.floorVariants());
                    int wCount = Math.max(1, textures.wallVariants());
                    int fIdx = tileVariant(target.floorThemeSeed, x, y, fCount, 0);
                    int wIdx = tileVariant(target.wallThemeSeed, x, y, wCount, 1);
                    int fOrientation = tileOrientation(target.floorThemeSeed, x, y, 2);
                    int wOrientation = tileOrientation(target.wallThemeSeed, x, y, 3);
                    switch (t) {
                        case FLOOR -> drawFloorTile(gg, textures.floorVariant(fIdx), px, py, palette, target, x, y, fOrientation);
                        case WALL  -> drawWallTile(gg, textures.wallVariant(wIdx),  px, py, palette, target, x, y, wOrientation);
                        case DOOR  -> {
                            BufferedImage doorTile = textures.doorFloor();
                            if (doorTile != null) {
                                drawFloorTile(gg, doorTile, px, py, palette, target, x, y, 0);
                            } else {
                                drawFloorTile(gg, textures.floorVariant(fIdx), px, py, palette, target, x, y, fOrientation);
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
    }

    private void drawWorld(Graphics2D gg) {
        ensureRoomTheme(room);
        RoomPalette palette = paletteFor(room);
        BufferedImage cached = renderRoomBackground(room, palette);
        if (cached != null) {
            gg.drawImage(cached, 0, 0, null);
        } else {
            paintRoomTiles(gg, room, palette);
        }
        if (textures != null && textures.hasDoorAnimation()) {
            drawDoorways(gg);
        }
        if (room != null && room.shopDoor != null && room.lockedDoors.contains(room.shopDoor)) {
            // shop doors never lock but keep defensive guard
            room.lockedDoors.remove(room.shopDoor);
        }
        if (room != null) {
            for (Dir d : room.lockedDoors) {
                Point tile = doorTile(d);
                int px = tile.x * TILE;
                int py = tile.y * TILE;
                drawPadlock(gg, px, py);
            }
        }

        for (KeyPickup key : room.keyPickups) {
            gg.setColor(new Color(255, 215, 82));
            gg.fillOval(key.x - key.r, key.y - key.r, key.r * 2, key.r * 2);
            gg.setColor(new Color(140, 90, 30));
            gg.drawOval(key.x - key.r, key.y - key.r, key.r * 2, key.r * 2);
        }

        for (CoinPickup coin : room.coinPickups) {
            drawCoinPickup(gg, coin);
        }

        TrapManager traps = trapManagerForRoom(room);
        if (traps != null) {
            traps.render(gg);
        }

        for (RoomEnemy e : room.enemies) {
            if (!e.alive) continue;
            BufferedImage[] frames = enemyIdleAnimations.get(e.type);
            if (frames == null || frames.length == 0) {
                frames = defaultEnemyFrames;
            }
            if (frames != null && frames.length > 0) {
                int idx = (animTick / 10) % frames.length;
                BufferedImage frame = frames[idx];
                if (frame != null) {
                    int drawW = frame.getWidth();
                    int drawH = frame.getHeight();
                    gg.drawImage(frame, e.x - drawW / 2, e.y - drawH / 2, null);
                    drawEnemyWeapon(gg, e);
                    continue;
                }
            }
            {
                Color[] paletteFallback = enemyFallbackPalette(e.type);
                gg.setColor(paletteFallback[0]);
                gg.fillOval(e.x - e.size/2, e.y - e.size/2, e.size, e.size);
                gg.setColor(paletteFallback[1]);
                gg.setStroke(new BasicStroke(2f));
                gg.drawOval(e.x - e.size/2, e.y - e.size/2, e.size, e.size);
                drawEnemyWeapon(gg, e);
            }
            drawEnemyWeapon(gg, e);
        }

        if (playerIdleFrames != null && playerIdleFrames.length > 0){
            int idx = (animTick / 10) % playerIdleFrames.length;
            BufferedImage frame = playerIdleFrames[idx];
            if (frame != null) {
                gg.drawImage(frame, player.x, player.y, null);
            } else {
                gg.setColor(new Color(255,214,102));
                gg.fillOval(player.x,player.y,player.width,player.height);
            }
        } else {
            gg.setColor(new Color(255,214,102));
            gg.fillOval(player.x,player.y,player.width,player.height);
        }

        if (healTicks > 0) {
            float phase = healTicks / (float) HEAL_FLASH_TICKS;
            int radius = (int) (Math.max(player.width, player.height) * (1.2f + (1.0f - phase) * 1.6f));
            int centerX = player.x + player.width / 2;
            int centerY = player.y + player.height / 2;
            java.awt.Paint oldPaint = gg.getPaint();
            java.awt.Composite oldComposite = gg.getComposite();
            RadialGradientPaint aura = new RadialGradientPaint(
                    new Point2D.Float(centerX, centerY),
                    Math.max(1, radius),
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{
                            new Color(120, 255, 160, 200),
                            new Color(60, 200, 120, 90),
                            new Color(30, 120, 80, 0)
                    }
            );
            gg.setComposite(AlphaComposite.SrcOver.derive(0.8f));
            gg.setPaint(aura);
            gg.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            gg.setComposite(oldComposite);
            gg.setPaint(oldPaint);
            gg.setColor(new Color(120, 255, 160, 180));
            gg.setStroke(new BasicStroke(2f));
            gg.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
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
            gg.setColor(ex.inner == null ? new Color(255, 200, 80) : ex.inner);
            gg.fillOval((int)ex.x - r, (int)ex.y - r, r*2, r*2);
            gg.setColor(ex.outer == null ? new Color(255, 240, 160) : ex.outer);
            gg.drawOval((int)ex.x - r, (int)ex.y - r, r*2, r*2);
            gg.setComposite(AlphaComposite.SrcOver);
        }
    }

    private RoomPalette paletteFor(Room room) {
        if (room == null || ROOM_PALETTES.length == 0) {
            return null;
        }
        int index = Math.floorMod(room.paletteIndex, ROOM_PALETTES.length);
        return ROOM_PALETTES[index];
    }

    private void drawFloorTile(Graphics2D gg, BufferedImage texture, int px, int py, RoomPalette palette,
                               Room room, int tx, int ty, int orientation) {
        drawTexture(gg, texture, px, py, orientation, new Color(24, 60, 78));
        if (palette == null) {
            return;
        }
        java.awt.Composite original = gg.getComposite();
        gg.setComposite(AlphaComposite.SrcOver.derive(palette.floorAlpha));
        gg.setColor(palette.floorTint);
        gg.fillRect(px, py, TILE, TILE);
        gg.setComposite(original);

        if (textures != null && textures.hasFloorOverlays()) {
            int overlaySalt = accentHash(room, tx, ty, 9);
            if (Math.floorMod(overlaySalt, 6) == 0) {
                BufferedImage overlay = textures.floorOverlay(Math.floorMod(overlaySalt, Math.max(1, textures.floorOverlayCount())));
                if (overlay != null) {
                    java.awt.Composite old = gg.getComposite();
                    gg.setComposite(AlphaComposite.SrcOver.derive(0.35f));
                    gg.drawImage(overlay, px, py, TILE, TILE, null);
                    gg.setComposite(old);
                }
            }
        }

        if (palette.floorAccent != null) {
            gg.setComposite(AlphaComposite.SrcOver.derive(0.18f));
            gg.setColor(palette.floorAccent);
            if (Math.floorMod(accentHash(room, tx, ty, 1), 5) == 0) {
                gg.fillRect(px, py + TILE - 3, TILE, 3);
            }
            if (Math.floorMod(accentHash(room, tx, ty, 2), 7) == 0) {
                gg.fillRect(px + TILE - 3, py, 3, TILE);
            }
            gg.setComposite(original);
        }
        gg.setColor(new Color(0, 0, 0, 35));
        gg.drawRect(px, py, TILE, TILE);
    }

    private void drawWallTile(Graphics2D gg, BufferedImage texture, int px, int py, RoomPalette palette,
                               Room room, int tx, int ty, int orientation) {
        drawTexture(gg, texture, px, py, orientation, new Color(38, 82, 96));
        if (palette == null) {
            return;
        }
        java.awt.Composite original = gg.getComposite();
        gg.setComposite(AlphaComposite.SrcOver.derive(palette.wallAlpha));
        gg.setColor(palette.wallTint);
        gg.fillRect(px, py, TILE, TILE);
        gg.setComposite(original);

        gg.setComposite(AlphaComposite.SrcOver.derive(0.55f));
        gg.setColor(palette.wallHighlight);
        gg.fillRect(px, py, TILE, 3);
        gg.setComposite(AlphaComposite.SrcOver.derive(0.65f));
        gg.setColor(palette.wallShadow);
        gg.fillRect(px, py + TILE - 5, TILE, 5);
        gg.setComposite(original);

        if (Math.floorMod(accentHash(room, tx, ty, 5), 6) == 0) {
            gg.setColor(new Color(palette.wallHighlight.getRed(), palette.wallHighlight.getGreen(), palette.wallHighlight.getBlue(), 80));
            gg.fillRect(px, py, 4, TILE);
        }

        int crackSeed = accentHash(room, tx, ty, 7);
        if (Math.floorMod(crackSeed, 9) == 0) {
            java.awt.Composite oldComposite = gg.getComposite();
            Stroke previousStroke = gg.getStroke();
            gg.setComposite(AlphaComposite.SrcOver.derive(0.4f));
            gg.setColor(new Color(palette.wallShadow.getRed(), palette.wallShadow.getGreen(), palette.wallShadow.getBlue(), 180));
            gg.setStroke(new BasicStroke(Math.max(1.4f, TILE / 24f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = px + TILE / 2 + Math.floorMod(crackSeed, 5) - 2;
            gg.drawLine(cx, py + 4, cx - 3, py + TILE / 2);
            gg.drawLine(cx - 3, py + TILE / 2, cx + 1, py + TILE - 6);
            gg.setStroke(previousStroke);
            gg.setComposite(oldComposite);
        }

        if (Math.floorMod(crackSeed, 11) == 3) {
            java.awt.Composite oldComposite = gg.getComposite();
            gg.setComposite(AlphaComposite.SrcOver.derive(0.28f));
            gg.setColor(new Color(60, 110, 70, 160));
            int offsetX = px + Math.min(TILE - 10, Math.max(2, Math.floorMod(crackSeed, TILE)));
            gg.fillOval(offsetX - 4, py + TILE - 10, 8, 6);
            gg.setComposite(oldComposite);
        }

        if (textures != null && textures.hasWallOverlays()) {
            int overlaySalt = accentHash(room, tx, ty, 13);
            if (Math.floorMod(overlaySalt, 7) == 0) {
                BufferedImage overlay = textures.wallOverlay(Math.floorMod(overlaySalt, Math.max(1, textures.wallOverlayCount())));
                if (overlay != null) {
                    java.awt.Composite oldComposite = gg.getComposite();
                    gg.setComposite(AlphaComposite.SrcOver.derive(0.42f));
                    gg.drawImage(overlay, px, py, TILE, TILE, null);
                    gg.setComposite(oldComposite);
                }
            }
        }
    }

    private void drawTexture(Graphics2D gg, BufferedImage texture, int px, int py, int orientation, Color fallback) {
        if (texture == null) {
            gg.setColor(fallback);
            gg.fillRect(px, py, TILE, TILE);
            return;
        }
        int orient = Math.floorMod(orientation, 4);
        if (orient == 0) {
            gg.drawImage(texture, px, py, TILE, TILE, null);
            return;
        }
        AffineTransform original = gg.getTransform();
        gg.translate(px + TILE / 2.0, py + TILE / 2.0);
        switch (orient) {
            case 1 -> gg.rotate(Math.PI / 2.0);
            case 2 -> gg.rotate(Math.PI);
            case 3 -> gg.rotate(-Math.PI / 2.0);
            default -> { }
        }
        gg.drawImage(texture, -TILE / 2, -TILE / 2, TILE, TILE, null);
        gg.setTransform(original);
    }

    private int tileVariant(int seed, int x, int y, int count, int salt) {
        if (count <= 0) {
            return 0;
        }
        int hash = mix32(seed ^ (x * 0x632bea5d) ^ (y * 0x85157af5) ^ (salt * 0x27d4eb2d));
        return Math.floorMod(hash, count);
    }

    private int tileOrientation(int seed, int x, int y, int salt) {
        int hash = mix32(seed ^ (x * 0x45d9f3b) ^ (y * 0x119de1f3) ^ (salt * 0x6eed0e9d));
        return Math.floorMod(hash, 4);
    }

    private static int mix32(int value) {
        int z = value;
        z ^= (z >>> 16);
        z *= 0x7feb352d;
        z ^= (z >>> 15);
        z *= 0x846ca68b;
        z ^= (z >>> 16);
        return z;
    }

    private int accentHash(Room room, int tx, int ty, int salt) {
        if (room == null) {
            return 0;
        }
        int seed = room.accentSeed == 0 ? 1 : room.accentSeed;
        return seed + tx * 53 + ty * 97 + salt * 131;
    }

    private void drawEnemyWeapon(Graphics2D gg, RoomEnemy enemy) {
        if (enemy == null || !enemy.alive) {
            return;
        }
        switch (enemy.weapon) {
            case CLAWS -> drawClaws(gg, enemy);
            case SWORD -> drawSword(gg, enemy);
            case HAMMER -> drawHammer(gg, enemy);
            case BOW -> drawBow(gg, enemy);
            case STAFF -> drawStaff(gg, enemy);
            default -> {
            }
        }
    }

    private void drawClaws(Graphics2D gg, RoomEnemy enemy) {
        double angle = computeSwingAngle(enemy, Math.toRadians(80));
        int offset = Math.max(6, enemy.size / 2 - 6);
        int target = Math.max((int) (enemy.size * 0.9), TILE / 2 + enemy.size / 3);
        if (!drawWeaponSprite(gg, enemy, WeaponType.CLAWS, angle, offset, target, null)) {
            drawClawsPrimitive(gg, enemy, angle, offset);
        }
    }

    private double computeSwingAngle(RoomEnemy enemy, double sweepRadians) {
        double base = enemy.weaponAngle;
        if (!Double.isFinite(base)) {
            base = 0.0;
        }
        if (enemy.attackAnimDuration <= 0) {
            return base - sweepRadians * 0.35;
        }
        double progress = 1.0 - (enemy.attackAnimTicks / (double) Math.max(1, enemy.attackAnimDuration));
        progress = Math.max(0.0, Math.min(1.0, progress));
        return base - sweepRadians / 2.0 + progress * sweepRadians;
    }

    private void drawSword(Graphics2D gg, RoomEnemy enemy) {
        double angle = computeSwingAngle(enemy, Math.toRadians(110));
        int offset = Math.max(10, enemy.size / 2);
        int target = Math.max(enemy.size + TILE / 2, (int) (enemy.size * 1.45));
        if (!drawWeaponSprite(gg, enemy, WeaponType.SWORD, angle, offset, target, null)) {
            drawSwordPrimitive(gg, enemy, angle, offset);
        }
    }

    private void drawHammer(Graphics2D gg, RoomEnemy enemy) {
        double angle = computeSwingAngle(enemy, Math.toRadians(140));
        int offset = Math.max(8, enemy.size / 2);
        int target = Math.max(enemy.size + TILE / 2, (int) (enemy.size * 1.6));
        if (!drawWeaponSprite(gg, enemy, WeaponType.HAMMER, angle, offset, target, null)) {
            drawHammerPrimitive(gg, enemy, angle, offset);
        }
    }

    private void drawBow(Graphics2D gg, RoomEnemy enemy) {
        double angle = enemy.weaponAngle;
        int offset = Math.max(6, enemy.size / 2 - 4);
        int target = Math.max(enemy.size + TILE / 3, (int) (enemy.size * 1.35));
        if (!drawWeaponSprite(gg, enemy, WeaponType.BOW, angle, offset, target, this::drawBowOverlay)) {
            drawBowPrimitive(gg, enemy);
        }
    }

    private void drawStaff(Graphics2D gg, RoomEnemy enemy) {
        double angle = enemy.weaponAngle;
        int offset = Math.max(6, enemy.size / 2 - 4);
        int target = Math.max(enemy.size + TILE / 2, (int) (enemy.size * 1.5));
        if (!drawWeaponSprite(gg, enemy, WeaponType.STAFF, angle, offset, target, this::drawStaffOrb)) {
            drawStaffPrimitive(gg, enemy, angle, offset);
        }
    }

    private void drawClawsPrimitive(Graphics2D gg, RoomEnemy enemy, double angle, int offset) {
        AffineTransform original = gg.getTransform();
        gg.translate(enemy.x, enemy.y);
        gg.rotate(angle + weaponOrientationOffset(WeaponType.CLAWS));
        int clawLength = Math.max(14, TILE / 2);
        int clawWidth = Math.max(3, TILE / 10);
        gg.setColor(new Color(210, 210, 225));
        gg.fillRoundRect(offset, -clawWidth / 2, clawLength, clawWidth, clawWidth, clawWidth);
        gg.setColor(new Color(150, 150, 170));
        gg.drawRoundRect(offset, -clawWidth / 2, clawLength, clawWidth, clawWidth, clawWidth);
        gg.setTransform(original);
    }

    private void drawSwordPrimitive(Graphics2D gg, RoomEnemy enemy, double angle, int offset) {
        int bladeLength = (int) (TILE * 1.1);
        int bladeWidth = Math.max(4, TILE / 7);
        int guardWidth = Math.max(bladeWidth * 3, 14);
        int gripLength = Math.max(8, TILE / 3);

        AffineTransform original = gg.getTransform();
        gg.translate(enemy.x, enemy.y);
        gg.rotate(angle + weaponOrientationOffset(WeaponType.SWORD));
        gg.setColor(new Color(200, 210, 230));
        gg.fillRoundRect(offset, -bladeWidth / 2, bladeLength, bladeWidth, bladeWidth, bladeWidth);
        gg.setColor(new Color(160, 170, 190));
        gg.drawRoundRect(offset, -bladeWidth / 2, bladeLength, bladeWidth, bladeWidth, bladeWidth);
        gg.setColor(new Color(170, 132, 60));
        gg.fillRoundRect(offset - gripLength, -bladeWidth / 2, gripLength, bladeWidth, bladeWidth, bladeWidth);
        gg.fillRoundRect(offset - bladeWidth / 2, -guardWidth / 2, guardWidth, guardWidth, bladeWidth, bladeWidth);
        gg.setTransform(original);
    }

    private void drawHammerPrimitive(Graphics2D gg, RoomEnemy enemy, double angle, int offset) {
        int handleLength = (int) (TILE * 1.0);
        int handleWidth = Math.max(4, TILE / 8);
        int headWidth = Math.max(TILE / 2, 18);
        int headHeight = Math.max(TILE / 3, 14);

        AffineTransform original = gg.getTransform();
        gg.translate(enemy.x, enemy.y);
        gg.rotate(angle + weaponOrientationOffset(WeaponType.HAMMER));
        gg.setColor(new Color(94, 62, 32));
        gg.fillRoundRect(offset - 4, -handleWidth / 2, handleLength, handleWidth, handleWidth, handleWidth);
        gg.setColor(new Color(60, 40, 24));
        gg.drawRoundRect(offset - 4, -handleWidth / 2, handleLength, handleWidth, handleWidth, handleWidth);
        gg.setColor(new Color(190, 190, 204));
        gg.fillRoundRect(offset + handleLength - headWidth, -headHeight / 2, headWidth, headHeight, headHeight / 2, headHeight / 2);
        gg.setColor(new Color(150, 150, 170));
        gg.drawRoundRect(offset + handleLength - headWidth, -headHeight / 2, headWidth, headHeight, headHeight / 2, headHeight / 2);
        gg.setTransform(original);
    }

    private void drawBowPrimitive(Graphics2D gg, RoomEnemy enemy) {
        double angle = enemy.weaponAngle;
        int offset = Math.max(6, enemy.size / 2 - 4);
        int bowHeight = Math.max(TILE, enemy.size + TILE / 3);
        int bowLength = Math.max(TILE / 2, enemy.size);
        float drawProgress = Math.min(1f, enemy.bowDrawTicks / 12f);
        int pull = (int) (bowLength * 0.6f * drawProgress);

        AffineTransform original = gg.getTransform();
        Stroke oldStroke = gg.getStroke();
        gg.translate(enemy.x, enemy.y);
        gg.rotate(angle);
        gg.setStroke(new BasicStroke(Math.max(2f, TILE / 18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gg.setColor(new Color(150, 110, 60));
        gg.drawLine(offset, -bowHeight / 2, offset, bowHeight / 2);
        gg.setColor(new Color(210, 180, 120));
        int stringX = offset - pull;
        gg.drawLine(offset, -bowHeight / 2, stringX, 0);
        gg.drawLine(offset, bowHeight / 2, stringX, 0);

        if (enemy.bowDrawTicks > 0) {
            Color shaft = enemy.type == EnemyType.PUMPKIN ? new Color(210, 150, 70) : new Color(230, 230, 230);
            Color head = enemy.type == EnemyType.PUMPKIN ? new Color(255, 210, 130) : new Color(255, 255, 255);
            drawArrowShape(gg, stringX, 0, bowLength, shaft, head);
        }

        gg.setStroke(oldStroke);
        gg.setTransform(original);
    }

    private void drawBowOverlay(Graphics2D gg, BufferedImage sprite, RoomEnemy enemy) {
        if (gg == null || sprite == null || enemy == null) {
            return;
        }
        float drawProgress = Math.min(1f, enemy.bowDrawTicks / 12f);
        int halfHeight = sprite.getHeight() / 2;
        int pull = (int) (sprite.getWidth() * 0.45f * drawProgress);
        Stroke oldStroke = gg.getStroke();
        gg.setStroke(new BasicStroke(Math.max(2f, TILE / 18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gg.setColor(new Color(210, 180, 120));
        gg.drawLine(-pull, -halfHeight, -pull, halfHeight);
        gg.setStroke(oldStroke);

        if (enemy.bowDrawTicks > 0) {
            BufferedImage arrow = scaledArrowSprite(Math.max(sprite.getWidth(), sprite.getHeight()));
            if (arrow != null) {
                int drawX = -pull - arrow.getWidth();
                gg.drawImage(arrow, drawX, -arrow.getHeight() / 2, null);
            } else {
                Color shaft = enemy.type == EnemyType.PUMPKIN ? new Color(210, 150, 70) : new Color(230, 230, 230);
                Color head = enemy.type == EnemyType.PUMPKIN ? new Color(255, 210, 130) : new Color(255, 255, 255);
                drawArrowShape(gg, -pull, 0, sprite.getWidth(), shaft, head);
            }
        }
    }

    private void drawStaffPrimitive(Graphics2D gg, RoomEnemy enemy, double angle, int offset) {
        int staffLength = (int) (TILE * 1.05);
        int staffWidth = Math.max(5, TILE / 10);
        int orbRadius = Math.max(6, TILE / 4);
        double pulse = enemy.attackAnimDuration > 0 ? 1.0 - (enemy.attackAnimTicks / (double) Math.max(1, enemy.attackAnimDuration)) : 0.25;
        pulse = Math.max(0.2, Math.min(1.0, pulse));

        AffineTransform original = gg.getTransform();
        gg.translate(enemy.x, enemy.y);
        gg.rotate(angle + weaponOrientationOffset(WeaponType.BOW));
        gg.setColor(new Color(80, 60, 120));
        gg.fillRoundRect(offset - 4, -staffWidth / 2, staffLength, staffWidth, staffWidth, staffWidth);
        int orbX = offset + staffLength - orbRadius * 2;
        int orbY = -orbRadius;
        java.awt.Composite oldComposite = gg.getComposite();
        gg.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        gg.setColor(new Color(170, 150, 255));
        gg.fillOval(orbX, orbY, orbRadius * 2, orbRadius * 2);
        gg.setComposite(AlphaComposite.SrcOver.derive((float) (0.45 + pulse * 0.35)));
        gg.setColor(new Color(255, 240, 255));
        gg.fillOval(orbX + 2, orbY + 2, orbRadius * 2 - 4, orbRadius * 2 - 4);
        gg.setComposite(oldComposite);
        gg.setColor(new Color(120, 90, 200));
        gg.drawOval(orbX, orbY, orbRadius * 2, orbRadius * 2);
        gg.setTransform(original);
    }

    private void drawStaffOrb(Graphics2D gg, BufferedImage sprite, RoomEnemy enemy) {
        if (gg == null || sprite == null || enemy == null) {
            return;
        }
        int orbRadius = Math.max(6, sprite.getHeight() / 4);
        int orbX = sprite.getWidth() - orbRadius * 2;
        int orbY = -orbRadius;
        double pulse = enemy.attackAnimDuration > 0 ? 1.0 - (enemy.attackAnimTicks / (double) Math.max(1, enemy.attackAnimDuration)) : 0.25;
        pulse = Math.max(0.2, Math.min(1.0, pulse));
        java.awt.Composite oldComposite = gg.getComposite();
        gg.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        gg.setColor(new Color(170, 150, 255));
        gg.fillOval(orbX, orbY, orbRadius * 2, orbRadius * 2);
        gg.setComposite(AlphaComposite.SrcOver.derive((float) (0.45 + pulse * 0.35)));
        gg.setColor(new Color(255, 240, 255));
        gg.fillOval(orbX + 2, orbY + 2, orbRadius * 2 - 4, orbRadius * 2 - 4);
        gg.setComposite(oldComposite);
    }

    private boolean drawWeaponSprite(Graphics2D gg, RoomEnemy enemy, WeaponType type,
                                     double angle, int offset, int targetLongEdge,
                                     WeaponOverlay overlay) {
        BufferedImage sprite = scaledWeaponSprite(type, targetLongEdge);
        if (sprite == null) {
            return false;
        }
        AffineTransform original = gg.getTransform();
        gg.translate(enemy.x, enemy.y);
        gg.rotate(angle);
        gg.translate(offset, 0);
        double orientation = weaponOrientationOffset(type);
        if (Math.abs(orientation) > 1e-6) {
            gg.rotate(orientation);
        }
        gg.drawImage(sprite, 0, -sprite.getHeight() / 2, null);
        if (overlay != null) {
            overlay.draw(gg, sprite, enemy);
        }
        gg.setTransform(original);
        return true;
    }

    private double weaponOrientationOffset(WeaponType type) {
        if (type == null) {
            return 0.0;
        }
        double fallback = switch (type) {
            case CLAWS -> 0.0;
            case SWORD, HAMMER, BOW, STAFF -> -Math.PI / 2.0;
        };
        if (weaponTextures != null) {
            BufferedImage base = weaponTextures.get(type);
            if (base != null && base.getWidth() >= base.getHeight()) {
                return 0.0;
            }
        }
        return fallback;
    }

    private BufferedImage scaledWeaponSprite(WeaponType type, int targetLongEdge) {
        if (weaponTextures == null || weaponTextures.isEmpty() || type == null) {
            return null;
        }
        BufferedImage base = weaponTextures.get(type);
        if (base == null) {
            return null;
        }
        int baseWidth = base.getWidth();
        int baseHeight = base.getHeight();
        if (baseWidth <= 0 || baseHeight <= 0) {
            return base;
        }
        int targetWidth = Math.max(1, targetLongEdge);
        WeaponCacheKey cacheKey = new WeaponCacheKey(type, targetWidth);
        if (scaledWeaponCache == null) {
            scaledWeaponCache = new HashMap<>();
        } else {
            BufferedImage cached = scaledWeaponCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        double scale = targetWidth / (double) baseWidth;
        int targetHeight = Math.max(1, (int) Math.round(baseHeight * scale));
        BufferedImage scaled = HiDpiScaler.scale(base, targetWidth, targetHeight);
        scaledWeaponCache.put(cacheKey, scaled);
        return scaled;
    }

    private BufferedImage scaledArrowSprite(int targetLongEdge) {
        if (arrowTexture == null) {
            return null;
        }
        int baseWidth = arrowTexture.getWidth();
        int baseHeight = arrowTexture.getHeight();
        if (baseWidth <= 0 || baseHeight <= 0) {
            return arrowTexture;
        }
        int targetWidth = Math.max(1, targetLongEdge);
        if (scaledArrowCache == null) {
            scaledArrowCache = new HashMap<>();
        } else {
            BufferedImage cached = scaledArrowCache.get(targetWidth);
            if (cached != null) {
                return cached;
            }
        }
        double scale = targetWidth / (double) baseWidth;
        int targetHeight = Math.max(1, (int) Math.round(baseHeight * scale));
        BufferedImage scaled = HiDpiScaler.scale(arrowTexture, targetWidth, targetHeight);
        scaledArrowCache.put(targetWidth, scaled);
        return scaled;
    }

    @FunctionalInterface
    private interface WeaponOverlay {
        void draw(Graphics2D g, BufferedImage sprite, RoomEnemy enemy);
    }

    private record WeaponCacheKey(WeaponType type, int targetWidth) { }

    private void drawArrowShape(Graphics2D gg, int startX, int startY, int length, Color shaft, Color head) {
        int arrowLength = Math.max(18, length);
        int endX = startX + arrowLength;
        Stroke old = gg.getStroke();
        gg.setStroke(new BasicStroke(Math.max(2f, TILE / 22f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gg.setColor(shaft);
        gg.drawLine(startX, startY, endX, startY);
        Path2D.Float arrowHead = new Path2D.Float();
        arrowHead.moveTo(endX, startY);
        arrowHead.lineTo(endX - 6, startY - 4);
        arrowHead.lineTo(endX - 6, startY + 4);
        arrowHead.closePath();
        gg.setColor(head);
        gg.fill(arrowHead);
        gg.setStroke(old);
    }

    private void drawHud(Graphics2D overlay) {
        if (overlay == null) {
            return;
        }
        overlay.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font hudFont = DialogueText.font(18f);
        overlay.setFont(hudFont);
        FontMetrics fm = overlay.getFontMetrics(hudFont);
        int padding = 14;

        List<String> infoLines = new ArrayList<>();
        String invul = iFrames > 0 ? " (INVUL)" : "";
        infoLines.add(String.format("HP: %d / %d%s", playerHP, playerMaxHp(), invul).toUpperCase(Locale.ENGLISH));
        infoLines.add(String.format("VIT: %d   DUN: %d   DMG: %d",
                vitalityUpgrades,
                dungeonHeartUpgrades,
                damageLevel + 1).toUpperCase(Locale.ENGLISH));
        infoLines.add(String.format("MODE: %s   BOSSES: %d", difficulty.name(), bossesDefeated).toUpperCase(Locale.ENGLISH));
        infoLines.add(String.format("KEYS: %d   COINS: %d", keysHeld, coins).toUpperCase(Locale.ENGLISH));
        if (worldPos != null) {
            infoLines.add(String.format("ROOM: (%d, %d)", worldPos.x, worldPos.y).toUpperCase(Locale.ENGLISH));
        }
        String moveLine = String.format("MOVE: %s/%s/%s/%s",
                keyName(ControlAction.MOVE_UP),
                keyName(ControlAction.MOVE_DOWN),
                keyName(ControlAction.MOVE_LEFT),
                keyName(ControlAction.MOVE_RIGHT));
        infoLines.add(moveLine.toUpperCase(Locale.ENGLISH));
        String actionLine = String.format("SHOOT: %s   REROLL: %s   PAUSE: %s",
                keyName(ControlAction.SHOOT),
                keyName(ControlAction.REROLL),
                keyName(ControlAction.PAUSE));
        infoLines.add(actionLine.toUpperCase(Locale.ENGLISH));

        String abilityLine = String.format("DASH: %s   PARRY: %s",
                abilityChargeText(dashTicks, dashCooldownTicks, DASH_COOLDOWN_TICKS),
                abilityChargeText(parryWindowTicks, parryCooldownTicks, PARRY_COOLDOWN_TICKS));
        infoLines.add(abilityLine.toUpperCase(Locale.ENGLISH));

        String burstName = nextAreaAbility == AreaAbility.FIRE_RING ? "FIRE" : "LIGHTNING";
        String specialLine = String.format("BURST: %s   NEXT: %s",
                abilityChargeText(0, specialCooldownTicks, SPECIAL_COOLDOWN_TICKS),
                burstName);
        infoLines.add(specialLine.toUpperCase(Locale.ENGLISH));

        int comboBonus = (int) Math.round(comboLevel * COMBO_DAMAGE_STEP * 100);
        String comboLine = String.format("COMBO: %d   POWER: +%d%%", Math.max(0, comboLevel), comboBonus);
        infoLines.add(comboLine.toUpperCase(Locale.ENGLISH));

        if (lastDamageCause != null && !lastDamageCause.isBlank()) {
            infoLines.add(("LAST HIT: " + lastDamageCause).toUpperCase(Locale.ENGLISH));
        }
        String story = texts.text("story");
        if (story != null && !story.isBlank()) {
            infoLines.add(story.toUpperCase(Locale.ENGLISH));
        }

        int lineHeight = fm.getHeight();
        int infoWidth = 0;
        for (String line : infoLines) {
            infoWidth = Math.max(infoWidth, fm.stringWidth(line));
        }
        Rectangle infoBox = new Rectangle(10, 10, infoWidth + padding * 2,
                lineHeight * infoLines.size() + padding * 2);
        DialogueText.paintFrame(overlay, infoBox, 22);

        int textY = infoBox.y + padding + fm.getAscent();
        for (String line : infoLines) {
            DialogueText.drawString(overlay, line, infoBox.x + padding, textY);
            textY += lineHeight;
        }

        if (isBossRoom(worldPos)) {
            String label = "GUARDIAN LAIR";
            int labelWidth = fm.stringWidth(label);
            DialogueText.drawString(overlay, label,
                    Math.max(infoBox.x + infoBox.width + 20, getWidth() - labelWidth - padding),
                    infoBox.y + fm.getAscent() + padding);
        }

        Rectangle minimapArea = drawMinimap(overlay);

        String bossHint = nextBossHint();
        if (bossHint != null && !bossHint.isBlank()) {
            String hintText = bossHint.toUpperCase(Locale.ENGLISH);
            int maxWidth = Math.min(getWidth() - 20, 360);
            int hintContentWidth = maxWidth - padding * 2;
            int lines = Math.max(1, estimateParagraphLines(fm, hintText, hintContentWidth));
            int hintHeight = padding * 2 + lines * lineHeight;
            int hintX = getWidth() - maxWidth - 10;
            int hintY;
            if (minimapArea != null) {
                hintY = minimapArea.y + minimapArea.height + 12;
                if (hintY + hintHeight > getHeight() - 20) {
                    hintY = Math.max(infoBox.y + infoBox.height + 12, 10);
                }
            } else {
                hintY = infoBox.y + infoBox.height + 12;
            }
            Rectangle hintBox = new Rectangle(hintX, hintY, maxWidth, hintHeight);
            DialogueText.paintFrame(overlay, hintBox, 18);
            int hintBaseline = hintBox.y + padding + fm.getAscent();
            DialogueText.drawParagraph(overlay, hintText, hintBox.x + padding, hintBaseline, hintContentWidth);
        }

        if (!statusMessage.isBlank()) {
            int baseWidth = getWidth() - 20;
            int boxX = 10;
            if (minimapArea != null) {
                int candidateX = minimapArea.x + minimapArea.width + 10;
                int candidateWidth = getWidth() - candidateX - 10;
                if (candidateWidth >= 240) {
                    boxX = candidateX;
                    baseWidth = candidateWidth;
                }
            }
            int contentWidth = Math.max(120, baseWidth - padding * 2);
            int messageLines = estimateParagraphLines(fm, statusMessage, contentWidth);
            int boxHeight = padding * 2 + Math.max(1, messageLines) * lineHeight;
            int boxY = getHeight() - boxHeight - 10;
            Rectangle statusBox = new Rectangle(boxX, boxY, baseWidth, boxHeight);
            DialogueText.paintFrame(overlay, statusBox, 18);
            int messageY = statusBox.y + padding + fm.getAscent();
            DialogueText.drawParagraph(overlay, statusMessage.toUpperCase(Locale.ENGLISH),
                    statusBox.x + padding, messageY, statusBox.width - padding * 2);
        }
    }

    private String abilityChargeText(int activeTicks, int cooldownTicks, int maxCooldownTicks) {
        if (activeTicks > 0) {
            return "ACTIVE";
        }
        if (cooldownTicks <= 0) {
            return "READY";
        }
        double seconds = cooldownTicks * tickSeconds();
        if (seconds >= 10.0) {
            return String.format(Locale.ENGLISH, "%.0fs", seconds);
        }
        return String.format(Locale.ENGLISH, "%.1fs", seconds);
    }

    private String nextBossHint() {
        if (worldPos == null) {
            return null;
        }
        BossEncounter nearestEncounter = null;
        Point nearestPoint = null;
        int nearestDistance = Integer.MAX_VALUE;
        if (bossEncounters != null) {
            for (Map.Entry<Point, BossEncounter> entry : bossEncounters.entrySet()) {
                Point pos = entry.getKey();
                BossEncounter encounter = entry.getValue();
                if (pos == null || encounter == null || encounter.defeated) {
                    continue;
                }
                int distance = Math.abs(pos.x - worldPos.x) + Math.abs(pos.y - worldPos.y);
                if (distance == 0) {
                    continue;
                }
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestEncounter = encounter;
                    nearestPoint = new Point(pos);
                }
            }
        }
        if (nearestEncounter != null && nearestPoint != null) {
            String direction = describeDirection(nearestPoint.x - worldPos.x, nearestPoint.y - worldPos.y);
            String requirement = nearestEncounter.requiredVitalityLevel <= vitalityUpgrades
                    ? "DOOR OPEN"
                    : "NEEDS " + nearestEncounter.requiredVitalityLevel + " VIT";
            String name = formatBossName(nearestEncounter.kind);
            return String.format(Locale.ENGLISH, "%s %s (%s)",
                    name,
                    direction.isBlank() ? "NEARBY" : direction,
                    requirement);
        }

        Point rumourPoint = null;
        nearestDistance = Integer.MAX_VALUE;
        if (visited != null && world != null) {
            for (Point explored : visited) {
                Room exploredRoom = world.get(explored);
                if (exploredRoom == null || exploredRoom.doors == null) {
                    continue;
                }
                for (Dir door : exploredRoom.doors) {
                    Point candidate = step(explored, door);
                    if (candidate == null) {
                        continue;
                    }
                    if (bossEncounters != null && bossEncounters.containsKey(candidate)) {
                        continue;
                    }
                    if (!shouldSeedBossAt(candidate, false)) {
                        continue;
                    }
                    int distance = Math.abs(candidate.x - worldPos.x) + Math.abs(candidate.y - worldPos.y);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        rumourPoint = new Point(candidate);
                    }
                }
            }
        }
        if (rumourPoint != null) {
            String direction = describeDirection(rumourPoint.x - worldPos.x, rumourPoint.y - worldPos.y);
            int requiredVitality = Math.min(MAX_VITALITY_UPGRADES,
                    Math.max(MIN_VITALITY_FOR_BOSS_SPAWN, 2 + bossesDefeated));
            return String.format(Locale.ENGLISH, "RUMOUR: GUARDIAN %s (NEEDS %d VIT)",
                    direction.isBlank() ? "AHEAD" : direction,
                    requiredVitality);
        }
        return null;
    }

    private String describeDirection(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return "NEARBY";
        }
        String vertical = "";
        String horizontal = "";
        if (dy < 0) {
            vertical = "NORTH";
        } else if (dy > 0) {
            vertical = "SOUTH";
        }
        if (dx < 0) {
            horizontal = "WEST";
        } else if (dx > 0) {
            horizontal = "EAST";
        }
        if (!vertical.isEmpty() && !horizontal.isEmpty()) {
            return vertical + "-" + horizontal;
        }
        return vertical.isEmpty() ? horizontal : vertical;
    }

    private int estimateParagraphLines(FontMetrics fm, String text, int width) {
        if (fm == null || text == null || text.isBlank() || width <= 0) {
            return 0;
        }
        String remaining = text.trim();
        boolean first = true;
        int lines = 0;
        while (!remaining.isEmpty()) {
            int available = width - fm.stringWidth(first ? "* " : "  ");
            if (available <= 0) {
                lines++;
                break;
            }
            int len = remaining.length();
            while (len > 0 && fm.stringWidth(remaining.substring(0, len)) > available) {
                len--;
            }
            if (len <= 0) {
                lines++;
                break;
            }
            int lastSpace = remaining.substring(0, len).lastIndexOf(' ');
            if (lastSpace > 0 && len < remaining.length()) {
                len = lastSpace + 1;
            }
            remaining = remaining.substring(Math.min(len, remaining.length())).trim();
            lines++;
            first = false;
        }
        return lines;
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
        if (bossEncounters != null) {
            known.addAll(bossEncounters.keySet());
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
                if (neighbour == null) {
                    continue;
                }
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
        DialogueText.drawString(overlay,
                texts.text("hud_map").toUpperCase(Locale.ENGLISH),
                mapX + MINIMAP_HORIZONTAL_PADDING, mapY + 18);

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
            Room roomData = world.get(p);
            BossEncounter encounter = bossEncounters.get(p);
            MinimapRoomKind kind = classifyMinimapRoom(p, roomData, encounter, visitedRooms, accessible, locked);
            overlay.setColor(minimapFillColour(kind));
            overlay.fillRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
            decorateMinimapCell(overlay, drawX, drawY, roomSize, kind, roomData, p);
        }

        drawShopIndicator(overlay, cellCenters, cellSize);
        drawMinimapLegend(overlay, mapX, mapY, mapHeight, cellSize);

        int footerY = mapY + mapHeight - 18;
        int statsX = mapX + mapWidth - MINIMAP_HORIZONTAL_PADDING - 150;
        statsX = Math.max(mapX + MINIMAP_HORIZONTAL_PADDING + 120, statsX);
        overlay.setStroke(originalStroke);
        overlay.setColor(new Color(210, 226, 232));
        DialogueText.drawString(overlay,
                texts.text("hud_rooms", visitedRooms.size()).toUpperCase(Locale.ENGLISH),
                statsX, footerY);
        DialogueText.drawString(overlay,
                texts.text("hud_exits", accessible.size()).toUpperCase(Locale.ENGLISH),
                statsX, footerY + 16);
        if (!locked.isEmpty()) {
            overlay.setColor(new Color(235, 210, 160));
            DialogueText.drawString(overlay,
                    texts.text("hud_locked", locked.size()).toUpperCase(Locale.ENGLISH),
                    statsX, footerY + 32);
        }

        overlay.setColor(originalColour);
        overlay.setStroke(originalStroke);
        return bounds;
    }

    private MinimapRoomKind classifyMinimapRoom(Point position,
                                                Room roomData,
                                                BossEncounter encounter,
                                                Set<Point> visitedRooms,
                                                Set<Point> accessible,
                                                Set<Point> locked) {
        if (position == null) {
            return MinimapRoomKind.UNKNOWN;
        }
        if (position.equals(worldPos)) {
            return MinimapRoomKind.CURRENT;
        }
        if (encounter != null) {
            return encounter.defeated ? MinimapRoomKind.BOSS_DEFEATED : MinimapRoomKind.BOSS_ACTIVE;
        }
        if (roomData != null && roomData.shopDoor != null) {
            return MinimapRoomKind.SHOP;
        }
        if (visitedRooms.contains(position)) {
            return MinimapRoomKind.VISITED;
        }
        if (accessible.contains(position)) {
            return MinimapRoomKind.ACCESSIBLE;
        }
        if (locked.contains(position)) {
            return MinimapRoomKind.LOCKED;
        }
        return MinimapRoomKind.UNKNOWN;
    }

    private Color minimapFillColour(MinimapRoomKind kind) {
        return switch (kind) {
            case CURRENT -> new Color(255, 240, 160, 240);
            case BOSS_ACTIVE -> new Color(210, 120, 200, 220);
            case BOSS_DEFEATED -> new Color(170, 150, 220, 220);
            case SHOP -> new Color(255, 214, 120, 230);
            case VISITED -> new Color(82, 144, 182, 228);
            case ACCESSIBLE -> new Color(138, 201, 38, 220);
            case LOCKED -> new Color(220, 170, 90, 220);
            case UNKNOWN -> new Color(80, 96, 120, 160);
        };
    }

    private void decorateMinimapCell(Graphics2D overlay,
                                     int drawX,
                                     int drawY,
                                     int roomSize,
                                     MinimapRoomKind kind,
                                     Room roomData,
                                     Point position) {
        Stroke oldStroke = overlay.getStroke();
        Color oldColour = overlay.getColor();
        switch (kind) {
            case CURRENT -> {
                overlay.setColor(new Color(255, 255, 255, 230));
                overlay.setStroke(new BasicStroke(1.8f));
                overlay.drawRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
            }
            case BOSS_ACTIVE -> {
                overlay.setColor(new Color(255, 170, 240, 240));
                overlay.setStroke(new BasicStroke(2.2f));
                overlay.drawRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
                drawBossGlyph(overlay, drawX, drawY, roomSize, roomSize, false);
            }
            case BOSS_DEFEATED -> {
                overlay.setColor(new Color(200, 190, 255, 220));
                overlay.setStroke(new BasicStroke(1.8f));
                overlay.drawRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
                drawBossGlyph(overlay, drawX, drawY, roomSize, roomSize, true);
            }
            case SHOP -> {
                overlay.setColor(new Color(120, 72, 18, 200));
                overlay.setStroke(new BasicStroke(1.6f));
                overlay.drawRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
                drawShopGlyph(overlay, drawX, drawY, roomSize, roomSize);
            }
            default -> {
                if (visited != null && position != null && visited.contains(position)) {
                    overlay.setColor(new Color(255, 255, 255, 60));
                    overlay.setStroke(new BasicStroke(1f));
                    overlay.drawRoundRect(drawX, drawY, roomSize, roomSize, 8, 8);
                }
            }
        }
        overlay.setStroke(oldStroke);
        overlay.setColor(oldColour);
    }

    private void drawShopIndicator(Graphics2D overlay, Map<String, Point> cellCenters, int cellSize) {
        if (overlay == null || cellCenters == null || worldPos == null) {
            return;
        }
        Point currentCentre = cellCenters.get(pointKey(worldPos));
        if (currentCentre == null) {
            return;
        }
        Point arrowTarget = null;
        Room currentRoom = world.get(worldPos);
        if (currentRoom != null && currentRoom.shopDoor != null) {
            Point vector = directionVector(currentRoom.shopDoor);
            if (vector != null) {
                arrowTarget = new Point(
                        currentCentre.x + vector.x * cellSize / 2,
                        currentCentre.y + vector.y * cellSize / 2);
            }
        }
        if (arrowTarget == null) {
            List<Point> path = pathToNearestShop(worldPos);
            if (!path.isEmpty()) {
                if (path.size() >= 2) {
                    arrowTarget = cellCenters.get(pointKey(path.get(1)));
                } else {
                    Room shopRoom = world.get(path.get(0));
                    if (shopRoom != null && shopRoom.shopDoor != null) {
                        Point vector = directionVector(shopRoom.shopDoor);
                        if (vector != null) {
                            arrowTarget = new Point(
                                    currentCentre.x + vector.x * cellSize / 2,
                                    currentCentre.y + vector.y * cellSize / 2);
                        }
                    }
                }
            }
        }
        if (arrowTarget == null) {
            return;
        }
        drawMinimapArrow(overlay, currentCentre, arrowTarget);
    }

    private void drawMinimapLegend(Graphics2D overlay, int mapX, int mapY, int mapHeight, int cellSize) {
        if (overlay == null) {
            return;
        }
        int legendX = mapX + MINIMAP_HORIZONTAL_PADDING;
        int legendY = mapY + mapHeight - MINIMAP_FOOTER + 8;
        int boxSize = Math.max(12, Math.min(cellSize, 18));
        int spacing = Math.max(boxSize + 6, overlay.getFontMetrics().getHeight() + 6);
        drawLegendEntry(overlay, legendX, legendY, boxSize, MinimapRoomKind.VISITED, "REGULAR");
        legendY += spacing;
        drawLegendEntry(overlay, legendX, legendY, boxSize, MinimapRoomKind.BOSS_ACTIVE, "BOSS");
        legendY += spacing;
        drawLegendEntry(overlay, legendX, legendY, boxSize, MinimapRoomKind.SHOP, "SHOP");
        legendY += spacing;
        drawLegendEntry(overlay, legendX, legendY, boxSize, MinimapRoomKind.UNKNOWN, "UNEXPLORED");
    }

    private void drawLegendEntry(Graphics2D overlay, int x, int y, int size, MinimapRoomKind kind, String label) {
        Color oldColour = overlay.getColor();
        Stroke oldStroke = overlay.getStroke();
        overlay.setColor(minimapFillColour(kind));
        overlay.fillRoundRect(x, y, size, size, 6, 6);
        switch (kind) {
            case BOSS_ACTIVE -> drawBossGlyph(overlay, x, y, size, size, false);
            case SHOP -> drawShopGlyph(overlay, x, y, size, size);
            default -> { }
        }
        overlay.setColor(new Color(0, 0, 0, 160));
        overlay.setStroke(new BasicStroke(1f));
        overlay.drawRoundRect(x, y, size, size, 6, 6);
        overlay.setColor(new Color(214, 234, 242));
        DialogueText.drawString(overlay, label.toUpperCase(Locale.ENGLISH), x + size + 8, y + Math.max(size - 4, 12));
        overlay.setColor(oldColour);
        overlay.setStroke(oldStroke);
    }

    private void drawBossGlyph(Graphics2D overlay, int drawX, int drawY, int width, int height, boolean defeated) {
        Graphics2D g2 = (Graphics2D) overlay.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int glyphWidth = Math.max(8, width - 6);
            int glyphHeight = Math.max(6, height / 2);
            int baseX = drawX + (width - glyphWidth) / 2;
            int baseY = drawY + (height - glyphHeight) / 2;
            Color fill = defeated ? new Color(188, 178, 236, 215) : new Color(255, 170, 240, 230);
            Color stroke = defeated ? new Color(120, 110, 180, 220) : new Color(180, 60, 160, 240);
            Path2D.Double crown = new Path2D.Double();
            crown.moveTo(baseX, baseY + glyphHeight);
            crown.lineTo(baseX + glyphWidth * 0.2, baseY + glyphHeight * 0.4);
            crown.lineTo(baseX + glyphWidth * 0.4, baseY + glyphHeight);
            crown.lineTo(baseX + glyphWidth * 0.6, baseY + glyphHeight * 0.4);
            crown.lineTo(baseX + glyphWidth * 0.8, baseY + glyphHeight);
            crown.lineTo(baseX + glyphWidth, baseY + glyphHeight * 0.4);
            crown.closePath();
            g2.setColor(fill);
            g2.fill(crown);
            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(crown);
            g2.setColor(new Color(255, 230, 255, 210));
            int gemSize = Math.max(4, glyphWidth / 6);
            g2.fillOval(baseX + glyphWidth / 2 - gemSize / 2, baseY + glyphHeight / 3, gemSize, gemSize);
        } finally {
            g2.dispose();
        }
    }

    private void drawShopGlyph(Graphics2D overlay, int drawX, int drawY, int width, int height) {
        Graphics2D g2 = (Graphics2D) overlay.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int iconSize = Math.max(6, Math.min(width, height) / 2);
            int iconX = drawX + width - iconSize - 4;
            int iconY = drawY + 4;
            g2.setColor(new Color(255, 214, 120, 240));
            g2.fillOval(iconX, iconY, iconSize, iconSize);
            g2.setColor(new Color(64, 40, 12, 220));
            Font oldFont = g2.getFont();
            g2.setFont(oldFont.deriveFont(Font.BOLD, Math.max(10f, iconSize - 2f)));
            g2.drawString("S", iconX + iconSize / 4f, iconY + iconSize * 0.8f);
            g2.setFont(oldFont);
        } finally {
            g2.dispose();
        }
    }

    private void drawMinimapArrow(Graphics2D overlay, Point from, Point to) {
        if (overlay == null || from == null || to == null) {
            return;
        }
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double len = Math.hypot(dx, dy);
        if (len < 1e-3) {
            return;
        }
        double startOffset = Math.min(6.0, Math.max(2.0, len * 0.15));
        double endOffset = Math.min(12.0, Math.max(4.0, len * 0.3));
        double startX = from.x + dx / len * startOffset;
        double startY = from.y + dy / len * startOffset;
        double endX = to.x - dx / len * endOffset;
        double endY = to.y - dy / len * endOffset;
        Stroke oldStroke = overlay.getStroke();
        Color oldColour = overlay.getColor();
        overlay.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color arrowColour = new Color(255, 214, 120, 235);
        overlay.setColor(arrowColour);
        overlay.drawLine((int) Math.round(startX), (int) Math.round(startY),
                (int) Math.round(endX), (int) Math.round(endY));
        Path2D.Double head = new Path2D.Double();
        double angle = Math.atan2(dy, dx);
        double headSize = Math.max(8.0, len * 0.18);
        head.moveTo(endX, endY);
        head.lineTo(endX - Math.cos(angle - Math.PI / 6) * headSize,
                endY - Math.sin(angle - Math.PI / 6) * headSize);
        head.lineTo(endX - Math.cos(angle + Math.PI / 6) * headSize,
                endY - Math.sin(angle + Math.PI / 6) * headSize);
        head.closePath();
        overlay.fill(head);
        overlay.setStroke(oldStroke);
        overlay.setColor(oldColour);
    }

    private List<Point> pathToNearestShop(Point start) {
        if (start == null || world == null || world.isEmpty()) {
            return List.of();
        }
        Set<Point> shopRooms = new HashSet<>();
        for (Map.Entry<Point, Room> entry : world.entrySet()) {
            if (entry.getValue() != null && entry.getValue().shopDoor != null) {
                shopRooms.add(entry.getKey());
            }
        }
        if (shopRooms.isEmpty()) {
            return List.of();
        }
        ArrayDeque<Point> queue = new ArrayDeque<>();
        Map<Point, Point> parent = new HashMap<>();
        Set<Point> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        Point found = null;
        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            if (shopRooms.contains(current)) {
                found = current;
                break;
            }
            Room room = world.get(current);
            if (room == null) {
                continue;
            }
            for (Dir door : room.doors) {
                Point neighbour = step(current, door);
                if (neighbour == null || !seen.add(neighbour)) {
                    continue;
                }
                if (!world.containsKey(neighbour)) {
                    continue;
                }
                queue.add(neighbour);
                parent.put(neighbour, current);
            }
        }
        if (found == null) {
            return List.of();
        }
        List<Point> path = new ArrayList<>();
        Point cursor = found;
        while (cursor != null) {
            path.add(0, cursor);
            cursor = parent.get(cursor);
        }
        return path;
    }

    private Point directionVector(Dir dir) {
        if (dir == null) {
            return null;
        }
        return switch (dir) {
            case N -> new Point(0, -1);
            case S -> new Point(0, 1);
            case W -> new Point(-1, 0);
            case E -> new Point(1, 0);
        };
    }

    private String keyName(ControlAction action) {
        return KeyEvent.getKeyText(controls.keyFor(action));
    }

    private void drawDoorways(Graphics2D gg) {
        if (gg == null || room == null || room.doors == null || room.doors.isEmpty()) {
            return;
        }
        if (textures == null || !textures.hasDoorAnimation()) {
            return;
        }
        int frameCount = Math.max(1, textures.doorFrameCount());
        BufferedImage frame = textures.doorFrame((animTick / 12) % frameCount);
        if (frame == null) {
            return;
        }
        double align = (TILE / 2.0) - (frame.getHeight() / 2.0);
        for (Dir dir : room.doors) {
            if (dir == null) {
                continue;
            }
            Point tile = doorTile(dir);
            if (tile == null) {
                continue;
            }
            boolean isShopDoor = dir == room.shopDoor;
            BossEncounter bossDoor = bossEncounterBehind(dir);
            boolean isBossDoor = bossDoor != null;
            int px = tile.x * TILE;
            int py = tile.y * TILE;
            AffineTransform original = gg.getTransform();
            gg.translate(px + TILE / 2.0, py + TILE / 2.0);
            switch (dir) {
                case N -> {
                    gg.rotate(Math.PI);
                    gg.translate(0, align);
                }
                case S -> gg.translate(0, align);
                case W -> {
                    gg.rotate(Math.PI / 2.0);
                    gg.translate(0, align);
                }
                case E -> {
                    gg.rotate(-Math.PI / 2.0);
                    gg.translate(0, align);
                }
            }
            gg.drawImage(frame, -frame.getWidth() / 2, -frame.getHeight() / 2, null);
            gg.setTransform(original);
            if (isShopDoor) {
                drawShopDoorHighlight(gg, dir, px, py, frame);
            } else if (isBossDoor) {
                drawBossDoorHighlight(gg, dir, px, py, frame, bossDoor);
            }
        }
    }

    private void drawShopDoorHighlight(Graphics2D gg, Dir dir, int px, int py, BufferedImage frame) {
        if (gg == null || dir == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) gg.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int doorWidth = frame != null ? frame.getWidth() : TILE;
            int doorHeight = frame != null ? frame.getHeight() : TILE;
            int centerX = px + TILE / 2;
            int centerY = py + TILE / 2;
            double rotation = switch (dir) {
                case N -> Math.PI;
                case S -> 0;
                case W -> Math.PI / 2.0;
                case E -> -Math.PI / 2.0;
            };
            g2.translate(centerX, centerY);
            g2.rotate(rotation);

            double glowWidth = doorWidth + 28;
            double glowHeight = doorHeight + 44;
            RoundRectangle2D halo = new RoundRectangle2D.Double(
                    -glowWidth / 2.0,
                    -glowHeight / 2.0,
                    glowWidth,
                    glowHeight,
                    18,
                    18);

            float pulse = (float) (0.6 + 0.4 * Math.sin(animTick / 10.0));
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(0f, (float) (-doorHeight / 3.0)),
                    (float) (glowWidth / 1.4),
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{
                            new Color(255, 214, 120, (int) Math.round(140 + pulse * 70)),
                            new Color(255, 214, 120, 40),
                            new Color(255, 214, 120, 0)
                    }
            ));
            g2.fill(halo);
            g2.setStroke(new BasicStroke(2.2f));
            g2.setColor(new Color(120, 72, 18, 210));
            g2.draw(halo);

            Font previous = g2.getFont();
            g2.setFont(DialogueText.font(Math.max(16f, Math.min(24f, doorWidth / 1.6f))));
            FontMetrics metrics = g2.getFontMetrics();
            String label = "SHOP";
            int textWidth = metrics.stringWidth(label);
            int baseline = (int) Math.round(-doorHeight / 2.0 - 6);
            g2.setColor(new Color(40, 22, 6, 245));
            g2.drawString(label, -textWidth / 2f, baseline);
            g2.setFont(previous);

            Ellipse2D floorGlow = new Ellipse2D.Double(
                    -doorWidth * 0.9,
                    doorHeight / 2.0 - 8,
                    doorWidth * 1.8,
                    24);
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(0f, (float) (doorHeight / 2.0)),
                    (float) (doorWidth),
                    new float[]{0f, 1f},
                    new Color[]{
                            new Color(255, 214, 120, 150),
                            new Color(255, 214, 120, 0)
                    }
            ));
            g2.fill(floorGlow);
        } finally {
            g2.dispose();
        }
    }

    private BossEncounter bossEncounterBehind(Dir dir) {
        if (dir == null || worldPos == null || bossEncounters == null) {
            return null;
        }
        Point target = step(worldPos, dir);
        if (target == null) {
            return null;
        }
        BossEncounter encounter = bossEncounters.get(target);
        if (encounter != null && !encounter.defeated) {
            return encounter;
        }
        return null;
    }

    private void drawBossDoorHighlight(Graphics2D gg, Dir dir, int px, int py, BufferedImage frame, BossEncounter encounter) {
        if (gg == null || dir == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) gg.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int doorWidth = frame != null ? frame.getWidth() : TILE;
            int doorHeight = frame != null ? frame.getHeight() : TILE;
            int centerX = px + TILE / 2;
            int centerY = py + TILE / 2;
            double rotation = switch (dir) {
                case N -> Math.PI;
                case S -> 0;
                case W -> Math.PI / 2.0;
                case E -> -Math.PI / 2.0;
            };
            g2.translate(centerX, centerY);
            g2.rotate(rotation);

            double glowWidth = doorWidth + 36;
            double glowHeight = doorHeight + 52;
            RoundRectangle2D halo = new RoundRectangle2D.Double(
                    -glowWidth / 2.0,
                    -glowHeight / 2.0,
                    glowWidth,
                    glowHeight,
                    20,
                    20);

            float pulse = (float) (0.5 + 0.5 * Math.sin(animTick / 8.0));
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(0f, (float) (-doorHeight / 3.0)),
                    (float) (glowWidth / 1.3),
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{
                            new Color(255, 96, 120, (int) Math.round(160 + pulse * 80)),
                            new Color(200, 40, 80, 50),
                            new Color(200, 40, 80, 0)
                    }
            ));
            g2.fill(halo);
            g2.setStroke(new BasicStroke(2.4f));
            g2.setColor(new Color(120, 20, 40, 220));
            g2.draw(halo);

            Font previous = g2.getFont();
            g2.setFont(DialogueText.font(Math.max(18f, Math.min(26f, doorWidth / 1.6f))));
            FontMetrics metrics = g2.getFontMetrics();
            String label = "BOSS";
            int textWidth = metrics.stringWidth(label);
            int baseline = (int) Math.round(-doorHeight / 2.0 - 8);
            g2.setColor(new Color(20, 4, 4, 245));
            g2.drawString(label, -textWidth / 2f, baseline);

            if (encounter != null && encounter.requiredVitalityLevel > vitalityUpgrades) {
                String req = "VIT " + encounter.requiredVitalityLevel;
                g2.setFont(DialogueText.font(Math.max(14f, Math.min(20f, doorWidth / 2.2f))));
                FontMetrics reqMetrics = g2.getFontMetrics();
                int reqWidth = reqMetrics.stringWidth(req);
                g2.setColor(new Color(255, 220, 220, 220));
                g2.drawString(req, -reqWidth / 2f, baseline - reqMetrics.getHeight());
            }
            g2.setFont(previous);

            Ellipse2D floorGlow = new Ellipse2D.Double(
                    -doorWidth * 1.0,
                    doorHeight / 2.0 - 6,
                    doorWidth * 2.0,
                    26);
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(0f, (float) (doorHeight / 2.0)),
                    (float) (doorWidth * 1.1),
                    new float[]{0f, 1f},
                    new Color[]{
                            new Color(255, 110, 140, 140),
                            new Color(255, 110, 140, 0)
                    }
            ));
            g2.fill(floorGlow);
        } finally {
            g2.dispose();
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

    private void drawCoinPickup(Graphics2D gg, CoinPickup coin) {
        if (coin == null) {
            return;
        }
        int radius = Math.max(8, coin.r);
        double phase = (coin.animTick % 120) / 120.0;
        int bob = (int) Math.round(Math.sin(phase * Math.PI * 2) * 4);
        int cx = coin.x;
        int cy = coin.y - bob;
        java.awt.Paint oldPaint = gg.getPaint();
        gg.setPaint(new RadialGradientPaint(
                new Point2D.Float(cx, cy),
                radius,
                new float[]{0f, 0.6f, 1f},
                new Color[]{
                        new Color(255, 252, 182, 255),
                        new Color(235, 210, 90, 255),
                        new Color(180, 120, 20, 220)
                }
        ));
        gg.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        gg.setPaint(oldPaint);
        gg.setColor(new Color(255, 255, 210, 200));
        gg.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    // ======= Input =======

    @Override
    public void keyPressed(KeyEvent e) {
        if (overlayActive) {
            return;
        }
        boolean blockMovement = false;
        if (suppressNextMovementPress) {
            long now = System.nanoTime();
            if (now > suppressMovementDeadlineNanos) {
                suppressNextMovementPress = false;
            } else if (isMovementKey(e)) {
                blockMovement = true;
                suppressNextMovementPress = false;
            }
        }
        if (!blockMovement && matches(e, ControlAction.MOVE_UP)) {
            up = true;
        }
        if (!blockMovement && matches(e, ControlAction.MOVE_DOWN)) {
            down = true;
        }
        if (!blockMovement && matches(e, ControlAction.MOVE_LEFT)) {
            left = true;
        }
        if (!blockMovement && matches(e, ControlAction.MOVE_RIGHT)) {
            right = true;
        }
        if (matches(e, ControlAction.SHOOT)) {
            shootPlayerBullet();
        }
        if (matches(e, ControlAction.DASH)) {
            startDash();
        }
        if (matches(e, ControlAction.PARRY)) {
            startParry();
        }
        if (matches(e, ControlAction.SPECIAL)) {
            activateAreaAbility();
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
        if (overlayActive) {
            return;
        }
        if (matches(e, ControlAction.MOVE_UP)) up = false;
        if (matches(e, ControlAction.MOVE_DOWN)) down = false;
        if (matches(e, ControlAction.MOVE_LEFT)) left = false;
        if (matches(e, ControlAction.MOVE_RIGHT)) right = false;
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    private boolean isMovementKey(KeyEvent e) {
        return matches(e, ControlAction.MOVE_UP)
                || matches(e, ControlAction.MOVE_DOWN)
                || matches(e, ControlAction.MOVE_LEFT)
                || matches(e, ControlAction.MOVE_RIGHT);
    }

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
                playerDamageBuffer,
                iFrames,
                keysHeld,
                coins,
                statusMessage,
                statusTicks,
                inBoss,
                animTick,
                mouseX,
                mouseY,
                shopManager.location(),
                shopManager.doorFacing(),
                shopManager.initialized(),
                introShown,
                goldenKnightIntroShown,
                queenRescued,
                finaleShown,
                rng,
                secureRandom,
                vitalityUpgrades,
                dungeonHeartUpgrades,
                damageLevel,
                enemiesDefeated,
                bossesDefeated,
                difficulty,
                checkpointRoom,
                checkpointBoss,
                dashTicks,
                dashCooldownTicks,
                dashDirX,
                dashDirY,
                parryWindowTicks,
                parryCooldownTicks,
                parryFlashTicks,
                specialCooldownTicks,
                nextAreaAbility,
                comboCount,
                comboTimerTicks,
                comboLevel,
                playerShotCooldownTicks,
                lastDamageCause
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
        default void runBossBattle(BossBattlePanel.BossKind kind,
                                   BossBattlePanel.BattleTuning tuning,
                                   Consumer<Outcome> outcomeHandler) {
            runBossBattle(kind, outcomeHandler);
        }

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
        markRoomDirty(r);
        return r;
    }

    // ---- Main launcher ----
    public static void main(String[] args) {
        GameLauncher.main(args);
    }
}
