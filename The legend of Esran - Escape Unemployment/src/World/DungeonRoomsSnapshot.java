package World;

import Battle.scene.BossBattlePanel;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.Serial;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Serializable container used to resume in-progress dungeon runs.
 */
public final class DungeonRoomsSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    private final Map<Point, DungeonRooms.Room> world;
    private final Map<Point, DungeonRooms.BossEncounter> bossEncounters;
    private final Set<Point> visited;
    private final List<BossBattlePanel.BossKind> bossPool;
    private final Point worldPos;
    private final int roomsVisited;
    private final DungeonRooms.Room currentRoom;
    private final Rectangle playerRect;
    private final boolean moveUp;
    private final boolean moveDown;
    private final boolean moveLeft;
    private final boolean moveRight;
    private final List<DungeonRooms.Bullet> enemyBullets;
    private final List<DungeonRooms.Bullet> playerBullets;
    private final List<DungeonRooms.Explosion> explosions;
    private final int playerHP;
    private final double playerDamageBuffer;
    private final int iFrames;
    private final int keysHeld;
    private final int coins;
    private final String statusMessage;
    private final int statusTicks;
    private final boolean inBoss;
    private final int animTick;
    private final int mouseX;
    private final int mouseY;
    private final Point shopRoom;
    private final DungeonRooms.Dir shopDoorFacing;
    private final boolean shopInitialized;
    private final boolean introShown;
    private final boolean goldenKnightIntroShown;
    private final boolean queenRescued;
    private final boolean finaleShown;
    private final Random rng;
    private final SecureRandom secureRandom;
    private final int vitalityLevel;
    private final int dungeonLevel;
    private final int damageLevel;
    private final int enemiesDefeated;
    private final int bossesDefeated;
    private final DungeonRooms.Difficulty difficulty;
    private final Point checkpointRoom;
    private final BossBattlePanel.BossKind checkpointBoss;

    DungeonRoomsSnapshot(Map<Point, DungeonRooms.Room> world,
                         Map<Point, DungeonRooms.BossEncounter> bossEncounters,
                         Set<Point> visited,
                         List<BossBattlePanel.BossKind> bossPool,
                         Point worldPos,
                         int roomsVisited,
                         DungeonRooms.Room currentRoom,
                         Rectangle playerRect,
                         boolean moveUp,
                         boolean moveDown,
                         boolean moveLeft,
                         boolean moveRight,
                         List<DungeonRooms.Bullet> enemyBullets,
                         List<DungeonRooms.Bullet> playerBullets,
                         List<DungeonRooms.Explosion> explosions,
                         int playerHP,
                         double playerDamageBuffer,
                         int iFrames,
                         int keysHeld,
                         int coins,
                         String statusMessage,
                         int statusTicks,
                         boolean inBoss,
                         int animTick,
                         int mouseX,
                         int mouseY,
                         Point shopRoom,
                         DungeonRooms.Dir shopDoorFacing,
                         boolean shopInitialized,
                         boolean introShown,
                         boolean goldenKnightIntroShown,
                         boolean queenRescued,
                         boolean finaleShown,
                         Random rng,
                         SecureRandom secureRandom,
                         int vitalityLevel,
                         int dungeonLevel,
                         int damageLevel,
                         int enemiesDefeated,
                         int bossesDefeated,
                         DungeonRooms.Difficulty difficulty,
                         Point checkpointRoom,
                         BossBattlePanel.BossKind checkpointBoss) {
        this.world = copyRooms(world);
        this.bossEncounters = copyBossEncounters(bossEncounters);
        this.visited = copyVisited(visited);
        this.bossPool = bossPool == null ? null : new ArrayList<>(bossPool);
        this.worldPos = worldPos == null ? null : new Point(worldPos);
        this.roomsVisited = roomsVisited;
        this.currentRoom = copyRoom(currentRoom);
        this.playerRect = playerRect == null ? null : new Rectangle(playerRect);
        this.moveUp = moveUp;
        this.moveDown = moveDown;
        this.moveLeft = moveLeft;
        this.moveRight = moveRight;
        this.enemyBullets = copyBullets(enemyBullets);
        this.playerBullets = copyBullets(playerBullets);
        this.explosions = copyExplosions(explosions);
        this.playerHP = playerHP;
        this.playerDamageBuffer = playerDamageBuffer;
        this.iFrames = iFrames;
        this.keysHeld = keysHeld;
        this.coins = coins;
        this.statusMessage = statusMessage;
        this.statusTicks = statusTicks;
        this.inBoss = inBoss;
        this.animTick = animTick;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.shopRoom = shopRoom == null ? null : new Point(shopRoom);
        this.shopDoorFacing = shopDoorFacing;
        this.shopInitialized = shopInitialized;
        this.introShown = introShown;
        this.goldenKnightIntroShown = goldenKnightIntroShown;
        this.queenRescued = queenRescued;
        this.finaleShown = finaleShown;
        this.rng = rng == null ? null : copyRandom(rng);
        this.secureRandom = secureRandom == null ? null : copySecureRandom(secureRandom);
        this.vitalityLevel = vitalityLevel;
        this.dungeonLevel = dungeonLevel;
        this.damageLevel = damageLevel;
        this.enemiesDefeated = enemiesDefeated;
        this.bossesDefeated = bossesDefeated;
        this.difficulty = difficulty == null ? DungeonRooms.Difficulty.EASY : difficulty;
        this.checkpointRoom = checkpointRoom == null ? null : new Point(checkpointRoom);
        this.checkpointBoss = checkpointBoss;
    }

    public Map<Point, DungeonRooms.Room> world() {
        return copyRooms(world);
    }

    public Map<Point, DungeonRooms.BossEncounter> bossEncounters() {
        return copyBossEncounters(bossEncounters);
    }

    public Set<Point> visited() {
        return copyVisited(visited);
    }

    public List<BossBattlePanel.BossKind> bossPool() {
        return bossPool == null ? new ArrayList<>() : new ArrayList<>(bossPool);
    }

    public Point worldPos() {
        return worldPos == null ? new Point(0, 0) : new Point(worldPos);
    }

    public int roomsVisited() {
        return roomsVisited;
    }

    public DungeonRooms.Room currentRoom() {
        return copyRoom(currentRoom);
    }

    public Rectangle playerRect() {
        return playerRect == null ? null : new Rectangle(playerRect);
    }

    public boolean moveUp() {
        return moveUp;
    }

    public boolean moveDown() {
        return moveDown;
    }

    public boolean moveLeft() {
        return moveLeft;
    }

    public boolean moveRight() {
        return moveRight;
    }

    public List<DungeonRooms.Bullet> enemyBullets() {
        return copyBullets(enemyBullets);
    }

    public List<DungeonRooms.Bullet> playerBullets() {
        return copyBullets(playerBullets);
    }

    public List<DungeonRooms.Explosion> explosions() {
        return copyExplosions(explosions);
    }

    public int playerHP() {
        return playerHP;
    }

    public double playerDamageBuffer() {
        return playerDamageBuffer;
    }

    public int iFrames() {
        return iFrames;
    }

    public int keysHeld() {
        return keysHeld;
    }

    public int coins() {
        return coins;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public int statusTicks() {
        return statusTicks;
    }

    public boolean inBoss() {
        return inBoss;
    }

    public int animTick() {
        return animTick;
    }

    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
    }

    public Point shopRoom() {
        return shopRoom == null ? null : new Point(shopRoom);
    }

    public DungeonRooms.Dir shopDoorFacing() {
        return shopDoorFacing;
    }

    public boolean shopInitialized() {
        return shopInitialized;
    }

    public boolean introShown() {
        return introShown;
    }

    public boolean goldenKnightIntroShown() {
        return goldenKnightIntroShown;
    }

    public boolean queenRescued() {
        return queenRescued;
    }

    public boolean finaleShown() {
        return finaleShown;
    }

    public Random rng() {
        return rng == null ? new Random() : copyRandom(rng);
    }

    public SecureRandom secureRandom() {
        return secureRandom == null ? new SecureRandom() : copySecureRandom(secureRandom);
    }

    public int vitalityLevel() {
        return vitalityLevel;
    }

    public int dungeonLevel() {
        return dungeonLevel;
    }

    public int damageLevel() {
        return damageLevel;
    }

    public int enemiesDefeated() {
        return enemiesDefeated;
    }

    public int bossesDefeated() {
        return bossesDefeated;
    }

    public DungeonRooms.Difficulty difficulty() {
        return difficulty == null ? DungeonRooms.Difficulty.EASY : difficulty;
    }

    public Point checkpointRoom() {
        return checkpointRoom == null ? null : new Point(checkpointRoom);
    }

    public BossBattlePanel.BossKind checkpointBoss() {
        return checkpointBoss;
    }

    private static Map<Point, DungeonRooms.Room> copyRooms(Map<Point, DungeonRooms.Room> source) {
        Map<Point, DungeonRooms.Room> result = new HashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<Point, DungeonRooms.Room> entry : source.entrySet()) {
            result.put(new Point(entry.getKey()), copyRoom(entry.getValue()));
        }
        return result;
    }

    private static DungeonRooms.Room copyRoom(DungeonRooms.Room room) {
        if (room == null) {
            return null;
        }
        DungeonRooms.Room clone = new DungeonRooms.Room();
        for (int x = 0; x < DungeonRooms.COLS; x++) {
            System.arraycopy(room.g[x], 0, clone.g[x], 0, DungeonRooms.ROWS);
        }
        clone.doors.addAll(room.doors);
        clone.enemies = copyEnemies(room.enemies);
        clone.keyPickups = copyKeys(room.keyPickups);
        clone.coinPickups = copyCoins(room.coinPickups);
        clone.enemySpawns = copyEnemySpawns(room.enemySpawns);
        clone.lockedDoors = room.lockedDoors.clone();
        clone.cleared = room.cleared;
        clone.spawnsPrepared = room.spawnsPrepared;
        clone.trapSpawns = copyTraps(room.trapSpawns);
        clone.trapsPrepared = room.trapsPrepared;
        clone.trapSeed = room.trapSeed;
        clone.trapManager = null;
        clone.floorThemeSeed = room.floorThemeSeed;
        clone.wallThemeSeed = room.wallThemeSeed;
        clone.paletteIndex = room.paletteIndex;
        clone.accentSeed = room.accentSeed;
        clone.shopDoor = room.shopDoor;
        clone.shopVisited = room.shopVisited;
        clone.backgroundDirty = true;
        clone.cachedBackground = null;
        clone.cachedTextureEpoch = -1;
        return clone;
    }

    private static Map<Point, DungeonRooms.BossEncounter> copyBossEncounters(Map<Point, DungeonRooms.BossEncounter> source) {
        Map<Point, DungeonRooms.BossEncounter> result = new HashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<Point, DungeonRooms.BossEncounter> entry : source.entrySet()) {
            DungeonRooms.BossEncounter original = entry.getValue();
            DungeonRooms.BossEncounter copy = new DungeonRooms.BossEncounter();
            copy.kind = original.kind;
            copy.defeated = original.defeated;
            copy.rewardClaimed = original.rewardClaimed;
            copy.preludeShown = original.preludeShown;
            copy.requiredVitalityLevel = original.requiredVitalityLevel;
            result.put(new Point(entry.getKey()), copy);
        }
        return result;
    }

    private static Set<Point> copyVisited(Set<Point> visited) {
        Set<Point> result = new HashSet<>();
        if (visited == null) {
            return result;
        }
        for (Point p : visited) {
            result.add(new Point(p));
        }
        return result;
    }

    private static List<DungeonRooms.RoomEnemy> copyEnemies(List<DungeonRooms.RoomEnemy> enemies) {
        List<DungeonRooms.RoomEnemy> list = new ArrayList<>();
        if (enemies == null) {
            return list;
        }
        for (DungeonRooms.RoomEnemy enemy : enemies) {
            DungeonRooms.RoomEnemy e = new DungeonRooms.RoomEnemy();
            e.x = enemy.x;
            e.y = enemy.y;
            e.size = enemy.size;
            e.cd = enemy.cd;
            e.alive = enemy.alive;
            e.type = enemy.type;
            e.maxHealth = enemy.maxHealth;
            e.health = enemy.health;
            e.braceTicks = enemy.braceTicks;
            e.windup = enemy.windup;
            e.patternIndex = enemy.patternIndex;
            e.damageBuffer = enemy.damageBuffer;
            e.weapon = enemy.weapon;
            e.attackAnimTicks = enemy.attackAnimTicks;
            e.attackAnimDuration = enemy.attackAnimDuration;
            e.bowDrawTicks = enemy.bowDrawTicks;
            e.facingAngle = enemy.facingAngle;
            e.weaponAngle = enemy.weaponAngle;
            e.coinReward = enemy.coinReward;
            if (enemy.spawn != null) {
                DungeonRooms.EnemySpawn spawn = new DungeonRooms.EnemySpawn();
                spawn.x = enemy.spawn.x;
                spawn.y = enemy.spawn.y;
                spawn.defeated = enemy.spawn.defeated;
                spawn.type = enemy.spawn.type;
                e.spawn = spawn;
            }
            list.add(e);
        }
        return list;
    }

    private static List<DungeonRooms.KeyPickup> copyKeys(List<DungeonRooms.KeyPickup> keys) {
        List<DungeonRooms.KeyPickup> list = new ArrayList<>();
        if (keys == null) {
            return list;
        }
        for (DungeonRooms.KeyPickup key : keys) {
            DungeonRooms.KeyPickup k = new DungeonRooms.KeyPickup();
            k.x = key.x;
            k.y = key.y;
            k.r = key.r;
            list.add(k);
        }
        return list;
    }

    private static List<DungeonRooms.CoinPickup> copyCoins(List<DungeonRooms.CoinPickup> coins) {
        List<DungeonRooms.CoinPickup> list = new ArrayList<>();
        if (coins == null) {
            return list;
        }
        for (DungeonRooms.CoinPickup coin : coins) {
            DungeonRooms.CoinPickup c = new DungeonRooms.CoinPickup();
            c.x = coin.x;
            c.y = coin.y;
            c.r = coin.r;
            c.value = coin.value;
            c.animTick = coin.animTick;
            list.add(c);
        }
        return list;
    }

    private static List<DungeonRooms.RoomTrap> copyTraps(List<DungeonRooms.RoomTrap> traps) {
        List<DungeonRooms.RoomTrap> list = new ArrayList<>();
        if (traps == null) {
            return list;
        }
        for (DungeonRooms.RoomTrap trap : traps) {
            DungeonRooms.RoomTrap t = new DungeonRooms.RoomTrap();
            t.kind = trap.kind;
            t.x = trap.x;
            t.y = trap.y;
            t.width = trap.width;
            t.height = trap.height;
            t.animationFolder = trap.animationFolder;
            t.frameDuration = trap.frameDuration;
            t.cycleSeconds = trap.cycleSeconds;
            t.activeFraction = trap.activeFraction;
            t.burstEvery = trap.burstEvery;
            t.burstDuration = trap.burstDuration;
            t.damageOverride = trap.damageOverride;
            t.contactCooldownOverride = trap.contactCooldownOverride;
            list.add(t);
        }
        return list;
    }

    private static List<DungeonRooms.EnemySpawn> copyEnemySpawns(List<DungeonRooms.EnemySpawn> spawns) {
        List<DungeonRooms.EnemySpawn> list = new ArrayList<>();
        if (spawns == null) {
            return list;
        }
        for (DungeonRooms.EnemySpawn spawn : spawns) {
            DungeonRooms.EnemySpawn s = new DungeonRooms.EnemySpawn();
            s.x = spawn.x;
            s.y = spawn.y;
            s.defeated = spawn.defeated;
            s.type = spawn.type;
            list.add(s);
        }
        return list;
    }

    private static List<DungeonRooms.Bullet> copyBullets(List<DungeonRooms.Bullet> bullets) {
        List<DungeonRooms.Bullet> list = new ArrayList<>();
        if (bullets == null) {
            return list;
        }
        for (DungeonRooms.Bullet bullet : bullets) {
            DungeonRooms.Bullet b = new DungeonRooms.Bullet();
            b.x = bullet.x;
            b.y = bullet.y;
            b.vx = bullet.vx;
            b.vy = bullet.vy;
            b.r = bullet.r;
            b.alive = bullet.alive;
            b.damage = bullet.damage;
            b.life = bullet.life;
            b.maxLife = bullet.maxLife;
            b.friendly = bullet.friendly;
            b.useTexture = bullet.useTexture;
            b.tint = bullet.tint;
            b.explosive = bullet.explosive;
            b.explosionRadius = bullet.explosionRadius;
            b.explosionLife = bullet.explosionLife;
            b.kind = bullet.kind;
            list.add(b);
        }
        return list;
    }

    private static List<DungeonRooms.Explosion> copyExplosions(List<DungeonRooms.Explosion> explosions) {
        List<DungeonRooms.Explosion> list = new ArrayList<>();
        if (explosions == null) {
            return list;
        }
        for (DungeonRooms.Explosion explosion : explosions) {
            DungeonRooms.Explosion ex = new DungeonRooms.Explosion();
            ex.x = explosion.x;
            ex.y = explosion.y;
            ex.age = explosion.age;
            ex.life = explosion.life;
            ex.maxR = explosion.maxR;
            ex.inner = explosion.inner;
            ex.outer = explosion.outer;
            list.add(ex);
        }
        return list;
    }

    private static Random copyRandom(Random rng) {
        Random copy = new Random();
        copy.setSeed(getSeed(rng));
        return copy;
    }

    private static SecureRandom copySecureRandom(SecureRandom rng) {
        SecureRandom copy = new SecureRandom();
        copy.setSeed(getSeed(rng));
        return copy;
    }

    private static long getSeed(Random rng) {
        try {
            java.lang.reflect.Field seedField = Random.class.getDeclaredField("seed");
            seedField.setAccessible(true);
            Object seed = seedField.get(rng);
            if (seed instanceof java.util.concurrent.atomic.AtomicLong atomicLong) {
                return atomicLong.get();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return System.nanoTime();
    }
}
