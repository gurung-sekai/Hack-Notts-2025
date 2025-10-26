// ZeldaRooms.java
package World;

import Battle.scene.BossBattlePanel;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import javax.imageio.ImageIO;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ZeldaRooms extends JPanel implements ActionListener, KeyListener, MouseListener {

    // Tunables
    static final int TILE = 36, COLS = 21, ROWS = 13, FPS = 60;
    static final int PLAYER_SIZE = (int)(TILE * 0.6), PLAYER_SPEED = 4;
    static final Color BG = new Color(6, 24, 32);

    // Optional tilesheet (ignored if not found)
    static final String TILESET_PATH = "/dungeon_sheet.png";
    static final Rectangle FLOOR_SRC = new Rectangle(224, 64, 32, 32);
    static final Rectangle WALL_SRC  = new Rectangle(  0,  0, 32, 32);
    private boolean useSprites = false;
    private BufferedImage tilesheet, floorSprite, wallSprite;

    enum T { VOID, FLOOR, WALL, DOOR, STAIR, TRAP, POT, CHEST }
    enum Dir { N, S, W, E }
    enum Kind { SLIME, RUNNER, SNAIL, BOSS }
    enum ProjType { ENEMY_FIRE, PLAYER_ARROW }

    static class Coord {
        final int x, y, z;
        Coord(int x,int y,int z){ this.x=x; this.y=y; this.z=z; }
        @Override public boolean equals(Object o){ if(!(o instanceof Coord c))return false; return x==c.x&&y==c.y&&z==c.z; }
        @Override public int hashCode(){ return (x*73856093) ^ (y*19349663) ^ (z*83492791); }
    }

    static class Enemy {
        final int id;
        Kind kind;
        float x,y,vx,vy,speed;
        int shootCd;
        int windup = 0;
        boolean canShoot = true;
        boolean alive=true;

        // NEW: rush behavior for boss
        boolean rushing=false;

        Enemy(int id, Kind k,float s, Random rng){
            this.id=id; kind=k; speed=s;
            shootCd = 28 + rng.nextInt(20);
            if (k == Kind.BOSS) canShoot = false;
        }
        Rectangle hitbox(){
            int s = (int)(TILE*(kind==Kind.BOSS?1.1:0.6));
            return new Rectangle(Math.round(x)-s/2, Math.round(y)-s/2, s, s);
        }
        float radius(){
            Rectangle hb = hitbox();
            return Math.max(hb.width, hb.height) * 0.5f;
        }
    }

    static class Projectile {
        ProjType type; float x,y,vx,vy; int life=220;
        Projectile(ProjType t,float x,float y,float vx,float vy){ type=t; this.x=x; this.y=y; this.vx=vx; this.vy=vy; }
        Rectangle hitbox(){ int s=TILE/3; return new Rectangle(Math.round(x)-s/2, Math.round(y)-s/2, s, s); }
    }

    static class KeyItem   { int x,y; boolean taken=false; }
    static class BowItem   { int x,y; boolean taken=false; }
    static class ArrowItem { int x,y,count; boolean taken=false; }
    static class QuiverItem{ int x,y,addCap; boolean taken=false; }
    static class CompassItem{ int x,y; boolean taken=false; }
    static class HeartItem { int x,y; boolean taken=false; }
    static class CoinItem  { int x,y,amount; boolean taken=false; }
    static class ChestItem { int x,y; boolean opened=false; }

    static class Room {
        T[][] g = new T[COLS][ROWS];
        EnumSet<Dir> doors = EnumSet.noneOf(Dir.class);
        EnumSet<Dir> locked = EnumSet.noneOf(Dir.class);
        Dir mobDoor = null;
        boolean lockAll = false;
        List<Enemy> enemies = new ArrayList<>();
        List<KeyItem> keys = new ArrayList<>();
        List<BowItem> bows = new ArrayList<>();
        List<ArrowItem> arrows = new ArrayList<>();
        List<QuiverItem> quivers = new ArrayList<>();
        List<CompassItem> compasses = new ArrayList<>();
        List<HeartItem> hearts = new ArrayList<>();
        List<CoinItem> coins = new ArrayList<>();
        List<ChestItem> chests = new ArrayList<>();
        List<Projectile> projectiles = new ArrayList<>();
        Point stairTile = null; int stairDir = 0; // +1 down / -1 up
        Room(){ for(int x=0;x<COLS;x++) for(int y=0;y<ROWS;y++) g[x][y]=T.VOID; }
    }

    private final Timer timer = new Timer(1000/FPS, this);
    private final Random rng  = new Random(4242);

    private final Map<Coord, Room> world = new HashMap<>();
    private final Set<Coord>       discovered = new HashSet<>();
    private Coord worldPos = new Coord(0,0,0);
    private Room room;

    private Rectangle player;
    private boolean up,down,left,right;
    private int baseMaxHp=5;
    private int bonusMaxHp=0;
    private int hp=baseMaxHp;
    private int invuln=0, keysInv=0;

    private boolean hasBow=false; private int arrowCd=0;
    private int arrowsInv=0, arrowsCap=12;

    private int coinsScore=0;

    // Sweep (mouse)
    private boolean swingActive=false;
    private int swingTimer=0, swingCooldown=0;
    private static final int SWING_TOTAL=11, SWING_WINDUP=5, SWING_COOLDOWN=18;
    private static final double SWEEP_DEGREES=150;
    private static final double SWEEP_REACH=52, SWEEP_THICK=24;
    private double sweepStartAngle=0, sweepEndAngle=0, sweepCurrAngle=0;
    private boolean sweepCCW=true;
    private final Set<Integer> hitThisSwing = new HashSet<>();
    private int mouseX=COLS*TILE/2, mouseY=ROWS*TILE/2;

    // Boss tracking
    private int doorsPassed = 0;
    private boolean bossQueued = false;     // set when ≥7 and ≤9 doors
    private boolean bossSpawned = false;    // actually in the world
    private Coord bossRoom = null;          // where the boss lives (room coord)
    private boolean battleMode = false;     // freeze when touching boss (placeholder turn-based)

    // Compass
    private boolean compassOwned=false;     // from drops only (pots/enemies)

    // Game Over
    private boolean gameOver = false;

    private boolean showMap=false, lighting=false;
    private String toast=""; private boolean toastBold=false; private int toastTimer=0;

    private int nextEnemyId = 1;
    private int globalTick=0;

    public ZeldaRooms(){
        setPreferredSize(new java.awt.Dimension(COLS*TILE, ROWS*TILE));
        setBackground(BG); setFocusable(true); addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e){ mouseX=e.getX(); mouseY=e.getY(); }
            @Override public void mouseDragged(MouseEvent e){ mouseX=e.getX(); mouseY=e.getY(); }
        });

        loadTilesIfAny();
        room = makeOrGetRoom(worldPos, null, false);
        discovered.add(worldPos);
        placePlayerSafelyNearPixels(COLS*TILE/2 + TILE/2, ROWS*TILE/2 + TILE/2);
        ensureNeighborsGenerated();

        timer.start();
    }

    private void loadTilesIfAny(){
        try {
            var in = ZeldaRooms.class.getResourceAsStream(TILESET_PATH);
            if (in==null) { useSprites=false; return; }
            tilesheet = ImageIO.read(in);
            floorSprite = cut(tilesheet, FLOOR_SRC);
            wallSprite  = cut(tilesheet, WALL_SRC);
            useSprites=true;
        } catch(Exception e){ useSprites=false; }
    }
    private static BufferedImage cut(BufferedImage src, Rectangle r){
        int w=Math.min(r.width,src.getWidth()-r.x), h=Math.min(r.height,src.getHeight()-r.y);
        return src.getSubimage(r.x,r.y,Math.max(1,w),Math.max(1,h));
    }

    private Room makeOrGetRoom(Coord pos, Dir mustHaveEntrance, boolean heavyMobBias){
        Room r = world.get(pos);
        if (r==null){
            boolean spawnBossHere = bossQueued && !bossSpawned;
            r = generateNewRoom(mustHaveEntrance, heavyMobBias, spawnBossHere);
            world.put(new Coord(pos.x,pos.y,pos.z), r);
            if (spawnBossHere) {
                bossSpawned = true;
                bossQueued  = false;
                bossRoom = new Coord(pos.x,pos.y,pos.z);
            }
            return r;
        }
        if (mustHaveEntrance!=null && !r.doors.contains(mustHaveEntrance)){
            r.doors.add(mustHaveEntrance);
            carveDoorOnGrid(r, mustHaveEntrance);
        }
        return r;
    }

    private Room generateNewRoom(Dir mustHaveEntrance, boolean heavyMobBias, boolean withBoss){
        Room r = new Room();
        for(int x=0;x<COLS;x++) for(int y=0;y<ROWS;y++) r.g[x][y]=T.FLOOR;
        for(int x=0;x<COLS;x++){ r.g[x][0]=T.WALL; r.g[x][ROWS-1]=T.WALL; }
        for(int y=0;y<ROWS;y++){ r.g[0][y]=T.WALL; r.g[COLS-1][y]=T.WALL; }

        int totalDoors = (mustHaveEntrance!=null) ? (2 + rng.nextInt(2)) : (1 + rng.nextInt(3));
        EnumSet<Dir> chosen = EnumSet.noneOf(Dir.class);
        if (mustHaveEntrance!=null) chosen.add(mustHaveEntrance);
        List<Dir> pool = new ArrayList<>(List.of(Dir.N,Dir.S,Dir.W,Dir.E));
        if (mustHaveEntrance!=null) pool.remove(mustHaveEntrance);
        Collections.shuffle(pool, rng);
        for (Dir d: pool){ if (chosen.size()>=totalDoors) break; chosen.add(d); }
        for (Dir d: chosen) carveDoorOnGrid(r, d);
        r.doors = chosen;

        List<Dir> md = new ArrayList<>(r.doors);
        if (!md.isEmpty()) r.mobDoor = md.get(rng.nextInt(md.size()));

        List<Dir> lockable = new ArrayList<>(r.doors);
        if (mustHaveEntrance!=null) lockable.remove(mustHaveEntrance);
        if (!lockable.isEmpty() && rng.nextFloat()<0.25f) r.locked.add(lockable.get(rng.nextInt(lockable.size())));

        // Obstacles
        int blocks=6+rng.nextInt(6);
        for(int i=0;i<blocks;i++){
            int bx=2+rng.nextInt(COLS-4), by=2+rng.nextInt(ROWS-4);
            if (nearAnyDoor(r,bx,by,3)) continue;
            for(int dx=0;dx<2;dx++) for(int dy=0;dy<2;dy++) if(inBounds(bx+dx,by+dy)) r.g[bx+dx][by+dy]=T.WALL;
        }

        // Traps, Pots, Chest
        int traps = 2 + rng.nextInt(3);
        for (int i=0;i<traps;i++){ Point t = safeFloorTile(r,2); r.g[t.x][t.y]=T.TRAP; }
        int pots = 2 + rng.nextInt(4);
        for (int i=0;i<pots;i++){ Point t = safeFloorTile(r,2); r.g[t.x][t.y]=T.POT; }
        if (rng.nextFloat() < 0.06f){
            Point t = safeFloorTile(r,2);
            r.g[t.x][t.y]=T.CHEST; ChestItem c = new ChestItem(); c.x=t.x; c.y=t.y; r.chests.add(c);
        }

        if (rng.nextFloat()<0.10f){
            Point t=safeFloorTile(r,2); r.g[t.x][t.y]=T.STAIR; r.stairTile=new Point(t); r.stairDir=rng.nextBoolean()?+1:-1;
        }

        // Enemies (slower)
        int base=heavyMobBias?6:3, count=base+rng.nextInt(3);
        for(int i=0;i<count;i++){
            float roll=rng.nextFloat(); Kind k; float spd;
            if (roll<0.20f){ k=Kind.SNAIL;  spd=0.7f; }
            else if (roll<0.60f){ k=Kind.SLIME; spd=1.1f; }
            else { k=Kind.RUNNER; spd=1.8f; }
            r.enemies.add(spawnEnemyOnFloor(r,k,spd));
        }

        if (withBoss){
            Enemy boss = spawnEnemyOnFloor(r, Kind.BOSS, 3.0f);
            r.enemies.add(boss);
            toast = "A powerful presence approaches..."; toastBold=false; toastTimer=120;
        }

        // Pickups (NO direct compass spawn here)
        if (!r.locked.isEmpty()) r.keys.add(spawnKeyOnFloor(r)); // solvable lock
        else if (rng.nextFloat()<0.18f) r.keys.add(spawnKeyOnFloor(r));
        if (rng.nextFloat()<0.04f) r.bows.add(spawnBowOnFloor(r));
        if (rng.nextFloat()<0.30f) r.arrows.add(spawnArrowBundleOnFloor(r, 3 + rng.nextInt(4)));
        if (rng.nextFloat()<0.10f) r.quivers.add(spawnQuiverOnFloor(r, 6));
        if (rng.nextFloat()<0.20f) r.coins.add(spawnCoinOnFloor(r, 1 + rng.nextInt(4)));

        return r;
    }

    private Enemy spawnEnemyOnFloor(Room r, Kind kind, float speed){
        Point t=safeFloorTile(r,2);
        Enemy e=new Enemy(nextEnemyId++, kind,speed, rng);
        e.x=t.x*TILE+TILE/2f; e.y=t.y*TILE+TILE/2f;
        return e;
    }
    private KeyItem     spawnKeyOnFloor(Room r){ Point t=safeFloorTile(r,2); KeyItem k=new KeyItem(); k.x=t.x; k.y=t.y; return k; }
    private BowItem     spawnBowOnFloor(Room r){ Point t=safeFloorTile(r,2); BowItem b=new BowItem(); b.x=t.x; b.y=t.y; return b; }
    private ArrowItem   spawnArrowBundleOnFloor(Room r,int count){ Point t=safeFloorTile(r,2); ArrowItem a=new ArrowItem(); a.x=t.x; a.y=t.y; a.count=count; return a; }
    private QuiverItem  spawnQuiverOnFloor(Room r,int add){ Point t=safeFloorTile(r,2); QuiverItem q=new QuiverItem(); q.x=t.x; q.y=t.y; q.addCap=add; return q; }
    private CompassItem spawnCompassOnFloor(Room r){ Point t=safeFloorTile(r,2); CompassItem c=new CompassItem(); c.x=t.x; c.y=t.y; return c; }
    private HeartItem   spawnHeartOnFloor(Room r){ Point t=safeFloorTile(r,2); HeartItem h=new HeartItem(); h.x=t.x; h.y=t.y; return h; }
    private CoinItem    spawnCoinOnFloor(Room r,int amt){ Point t=safeFloorTile(r,2); CoinItem c=new CoinItem(); c.x=t.x; c.y=t.y; c.amount=amt; return c; }

    private Point safeFloorTile(Room r,int margin){
        for(int tries=0;tries<200;tries++){
            int x=margin+rng.nextInt(COLS-2*margin), y=margin+rng.nextInt(ROWS-2*margin);
            if (r.g[x][y]!=T.FLOOR) continue;
            if (nearAnyDoor(r,x,y,2)) continue;
            if (r.stairTile!=null && r.stairTile.x==x && r.stairTile.y==y) continue;
            return new Point(x,y);
        }
        return new Point(COLS/2,ROWS/2);
    }

    private static boolean inBounds(int x,int y){ return x>=0&&x<COLS&&y>=0&&y<ROWS; }
    private static Point doorTile(Dir d){
        int mx=COLS/2,my=ROWS/2;
        return switch(d){ case N->new Point(mx,0); case S->new Point(mx,ROWS-1); case W->new Point(0,my); case E->new Point(COLS-1,my); };
    }
    private void carveDoorOnGrid(Room r, Dir d){
        Point t=doorTile(d); r.g[t.x][t.y]=T.DOOR;
        Point in=new Point(t); switch(d){ case N->in.y++; case S->in.y--; case W->in.x++; case E->in.x--; }
        if (inBounds(in.x,in.y)) r.g[in.x][in.y]=T.FLOOR;
    }
    private boolean nearAnyDoor(Room r,int tx,int ty,int d){
        for(Dir dir:r.doors){ Point t=doorTile(dir); if (Math.abs(t.x-tx)+Math.abs(t.y-ty)<=d) return true; }
        return false;
    }
    private static Dir opposite(Dir d){ return switch(d){ case N->Dir.S; case S->Dir.N; case W->Dir.E; case E->Dir.W; }; }

    private boolean isBlocked(Rectangle rect){
        int minTX=Math.max(0,rect.x/TILE), maxTX=Math.min(COLS-1,(rect.x+rect.width-1)/TILE);
        int minTY=Math.max(0,rect.y/TILE), maxTY=Math.min(ROWS-1,(rect.y+rect.height-1)/TILE);
        for (int tx=minTX; tx<=maxTX; tx++){
            for (int ty=minTY; ty<=maxTY; ty++){
                if (room.g[tx][ty]==T.WALL) return true;
            }
        }
        return false;
    }
    private Point findNearestFreeCenterPixels(int cx, int cy){
        cx = clamp(cx, PLAYER_SIZE/2, COLS*TILE - PLAYER_SIZE/2);
        cy = clamp(cy, PLAYER_SIZE/2, ROWS*TILE - PLAYER_SIZE/2);
        int startTX = clamp(cx / TILE, 0, COLS-1);
        int startTY = clamp(cy / TILE, 0, ROWS-1);
        int maxR = Math.max(COLS, ROWS);
        for (int r=0; r<=maxR; r++){
            for (int dx=-r; dx<=r; dx++){
                for (int dy=-r; dy<=r; dy++){
                    if (Math.max(Math.abs(dx), Math.abs(dy))!=r) continue;
                    int tx = startTX + dx, ty = startTY + dy;
                    if (!inBounds(tx, ty)) continue;
                    if (room.g[tx][ty] != T.FLOOR) continue;
                    int pcx = tx*TILE + TILE/2;
                    int pcy = ty*TILE + TILE/2;
                    Rectangle candidate = new Rectangle(pcx - PLAYER_SIZE/2, pcy - PLAYER_SIZE/2, PLAYER_SIZE, PLAYER_SIZE);
                    if (!isBlocked(candidate)) return new Point(pcx, pcy);
                }
            }
        }
        return new Point(COLS*TILE/2 + TILE/2, ROWS*TILE/2 + TILE/2);
    }
    private void placePlayerSafelyNearPixels(int cx, int cy){
        Point p = findNearestFreeCenterPixels(cx, cy);
        player = new Rectangle(p.x - PLAYER_SIZE/2, p.y - PLAYER_SIZE/2, PLAYER_SIZE, PLAYER_SIZE);
        resolveTileCollisions();
    }
    private void resolveTileCollisions() {
        Rectangle p = player;
        int minTX = Math.max(0, p.x / TILE);
        int maxTX = Math.min(COLS - 1, (p.x + p.width - 1) / TILE);
        int minTY = Math.max(0, p.y / TILE);
        int maxTY = Math.min(ROWS - 1, (p.y + p.height - 1) / TILE);
        for (int tx = minTX; tx <= maxTX; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                if (room.g[tx][ty] != T.WALL) continue;
                int wx = tx * TILE, wy = ty * TILE;
                Rectangle w = new Rectangle(wx, wy, TILE, TILE);
                if (!p.intersects(w)) continue;
                int overlapLeft   = p.x + p.width - w.x;
                int overlapRight  = w.x + w.width - p.x;
                int overlapTop    = p.y + p.height - w.y;
                int overlapBottom = w.y + w.height - p.y;
                int minOverlap = Integer.MAX_VALUE;
                int dir = -1;
                if (overlapLeft   > 0 && overlapLeft   < minOverlap) { minOverlap = overlapLeft;   dir = 0; }
                if (overlapRight  > 0 && overlapRight  < minOverlap) { minOverlap = overlapRight;  dir = 1; }
                if (overlapTop    > 0 && overlapTop    < minOverlap) { minOverlap = overlapTop;    dir = 2; }
                if (overlapBottom > 0 && overlapBottom < minOverlap) { minOverlap = overlapBottom; dir = 3; }
                if (dir == 0)      p.x -= minOverlap;
                else if (dir == 1) p.x += minOverlap;
                else if (dir == 2) p.y -= minOverlap;
                else if (dir == 3) p.y += minOverlap;
            }
        }
        p.x = clamp(p.x, 0, COLS * TILE - p.width);
        p.y = clamp(p.y, 0, ROWS * TILE - p.height);
    }

    private void ensureNeighborsGenerated(){
        for (Dir d : room.doors){
            Coord neigh = switch (d){
                case N -> new Coord(worldPos.x,     worldPos.y-1, worldPos.z);
                case S -> new Coord(worldPos.x,     worldPos.y+1, worldPos.z);
                case W -> new Coord(worldPos.x-1,   worldPos.y,   worldPos.z);
                case E -> new Coord(worldPos.x+1,   worldPos.y,   worldPos.z);
            };
            if (!world.containsKey(neigh)){
                makeOrGetRoom(neigh, opposite(d), false);
            }
        }
    }

    @Override public void actionPerformed(ActionEvent e){ if(!showMap) updateGame(); repaint(); }

    private int getMaxHp(){ return baseMaxHp + bonusMaxHp; }

    private void updateGame(){
        if (gameOver) return;
        if (battleMode) return; // freeze during boss encounter placeholder

        globalTick++;

        int dx = (left?-PLAYER_SPEED:0) + (right?PLAYER_SPEED:0);
        int dy = (up?-PLAYER_SPEED:0) + (down?PLAYER_SPEED:0);
        if (dx!=0 && dy!=0){ dx = Math.round(dx*0.70710678f); dy = Math.round(dy*0.70710678f); }

        if (arrowCd>0) arrowCd--;

        moveStepped(dx, dy);
        resolveTileCollisions();

        // Traps
        handleTrapsDamage();

        if (invuln>0) invuln--;

        float pcx=player.x+player.width/2f, pcy=player.y+player.height/2f;

        // Enemies
        for (Enemy en: room.enemies){
            if (!en.alive) continue;

            float ddx=pcx-en.x, ddy=pcy-en.y, len=(float)Math.hypot(ddx,ddy);
            if (len>0.001f){
                float nx=ddx/len, ny=ddy/len;
                float baseChase = (en.kind==Kind.SNAIL?0.45f:0.70f);
                float windupSlow = (en.windup>0 ? 0.6f : 1.0f);
                float chase = baseChase * windupSlow;

                // Boss rush behavior: if same room and close enough -> rush
                if (en.kind == Kind.BOSS){
                    // Trigger rush if near (~6 tiles)
                    if (!en.rushing && len <= TILE*6) en.rushing = true;
                    if (en.rushing) chase = 2.2f; // sprint!
                }

                en.vx=en.speed*nx*chase; en.vy=en.speed*ny*chase;
            }

            if (en.canShoot){
                if (en.shootCd>0) en.shootCd--;
                else if (en.windup==0) { en.windup = 18; }
                else {
                    en.windup--;
                    if (en.windup==0){
                        float sp=1.8f;
                        float vx=(len==0?0:sp*ddx/Math.max(1e-4f,len));
                        float vy=(len==0?0:sp*ddy/Math.max(1e-4f,len));
                        room.projectiles.add(new Projectile(ProjType.ENEMY_FIRE,en.x,en.y,vx,vy));
                        en.shootCd = 32 + rng.nextInt(18);
                    }
                }
            }

            moveEnemyStepped(en, Math.round(en.vx), Math.round(en.vy));

            // Touch boss -> freeze (turn-based placeholder)
            if (en.kind==Kind.BOSS && en.hitbox().intersects(player)) {
                battleMode = true;
                toast("BOSS ENCOUNTER! (turn-based TBD)", true);
            }

            if (invuln==0 && en.kind!=Kind.BOSS && en.hitbox().intersects(player)) damagePlayer(1);
        }

        // sword sweep
        updateSwordSweep();

        // projectiles
        List<Projectile> remove=new ArrayList<>();
        for (Projectile p: room.projectiles){
            p.x+=p.vx; p.y+=p.vy; p.life--;
            Rectangle hb=p.hitbox();
            if (p.life<=0 || hitWall(hb)) { remove.add(p); continue; }
            if (p.type==ProjType.ENEMY_FIRE){
                if (invuln==0 && hb.intersects(player)){ damagePlayer(1); remove.add(p); }
            } else { // player arrow
                boolean consumed=false;
                for (Enemy en: room.enemies){
                    if (!en.alive) continue;
                    if (hb.intersects(en.hitbox())){
                        if (en.kind==Kind.BOSS){
                            // Hitting boss does not kill; it aggro/rushes
                            en.rushing = true;
                        } else {
                            handleEnemyDeath(en);
                        }
                        consumed=true; break;
                    }
                }
                if (!consumed){
                    if (hitPotRect(hb)) consumed=true;
                }
                if (consumed) remove.add(p);
            }
        }
        room.projectiles.removeAll(remove);

        // pickups
        handlePickups();

        // stairs
        if (room.stairTile!=null){
            Rectangle sr=new Rectangle(room.stairTile.x*TILE, room.stairTile.y*TILE, TILE, TILE);
            if (sr.intersects(player)){
                Coord next=new Coord(worldPos.x,worldPos.y,worldPos.z+room.stairDir);
                room=makeOrGetRoom(next,null,false); worldPos=next; discovered.add(worldPos);
                placePlayerSafelyNearPixels(COLS*TILE/2 + TILE/2, ROWS*TILE/2 + TILE/2);
                ensureNeighborsGenerated();
                toast("Floor "+worldPos.z, false); return;
            }
        }

        // doors
        Dir through=touchingDoorOnEdge();
        if (through!=null){
            if (room.lockAll){ bounceFromDoor(through); toast("Defeat all enemies!", false); return; }
            if (room.locked.contains(through)){
                if (keysInv>0){ keysInv--; room.locked.remove(through); toast("Unlocked with a key ("+keysInv+" left)", false); }
                else { bounceFromDoor(through); toast("It's locked.", false); return; }
            }
            boolean heavy=(room.mobDoor==through);
            switchRoom(through, heavy);
            placePlayerJustInside(opposite(through));
            ensureNeighborsGenerated();

            doorsPassed++;

            // Guarantee window: 7..9 -> queue boss
            if (doorsPassed>=7 && doorsPassed<=9 && !bossSpawned){
                bossQueued = true;
                toast("You sense a powerful foe...", false);
            }
            // If still dodging: at 10+ force spawn now
            if (doorsPassed>=10 && !bossSpawned){
                forceSpawnBossInCurrentRoom();
                toast("The boss finds you!", true);
            }
        }

        // death -> game over
        if (hp<=0){
            swingActive=false;
            room.projectiles.clear();
            gameOver = true;
            toast("You died.", false);
        }

        if (toastTimer>0) toastTimer--;
    }

    private void forceSpawnBossInCurrentRoom(){
        Enemy boss = spawnEnemyOnFloor(room, Kind.BOSS, 3.0f);
        room.enemies.add(boss);
        bossSpawned = true; bossQueued = false; bossRoom = new Coord(worldPos.x,worldPos.y,worldPos.z);
    }

    private void handleTrapsDamage(){
        boolean active = (globalTick % 60) < 30;
        if (!active) return;
        Rectangle p = player;
        int minTX=Math.max(0,p.x/TILE), maxTX=Math.min(COLS-1,(p.x+p.width-1)/TILE);
        int minTY=Math.max(0,p.y/TILE), maxTY=Math.min(ROWS-1,(p.y+p.height-1)/TILE);
        for(int tx=minTX;tx<=maxTX;tx++){
            for(int ty=minTY;ty<=maxTY;ty++){
                if (room.g[tx][ty]==T.TRAP){
                    Rectangle tr = new Rectangle(tx*TILE, ty*TILE, TILE, TILE);
                    if (tr.intersects(p) && invuln==0){
                        damagePlayer(1);
                        return;
                    }
                }
            }
        }
    }

    private void bounceFromDoor(Dir d){
        switch(d){
            case N -> player.y+=8;
            case S -> player.y-=8;
            case W -> player.x+=8;
            case E -> player.x-=8;
        }
        resolveTileCollisions();
    }

    private void handleEnemyDeath(Enemy en){
        if (en.kind==Kind.BOSS) return; // never kill boss via this path
        en.alive=false;

        // Key: slightly hard but not impossible
        float keyChance = 0.14f - 0.03f * Math.min(3, keysInv);
        if (rng.nextFloat() < keyChance) room.keys.add(spawnKeyOnFloor(room));

        // 50% Heart chance
        if (rng.nextFloat() < 0.50f) room.hearts.add(spawnHeartOnFloor(room));

        // Coins drop (score)
        if (rng.nextFloat() < 0.85f) room.coins.add(spawnCoinOnFloor(room, 1 + rng.nextInt(5)));

        // Bow / arrows
        if (!hasBow && rng.nextFloat()<0.15f) room.bows.add(spawnBowOnFloor(room));
        if (rng.nextFloat()<0.40f) room.arrows.add(spawnArrowBundleOnFloor(room, 3 + rng.nextInt(4)));

        // Compass (pink) — 40% chance from enemies
        if (!compassOwned && !bossSpawned && rng.nextFloat() < 0.40f){
            room.compasses.add(spawnCompassOnFloor(room));
        }
    }

    private void handlePickups(){
        Rectangle pr = player;

        for (KeyItem k: room.keys){
            if (k.taken) continue;
            Rectangle kr=new Rectangle(k.x*TILE+TILE/4, k.y*TILE+TILE/4, TILE/2, TILE/2);
            if (kr.intersects(pr)){ k.taken=true; keysInv++; toast("KEY OBTAINED! ("+keysInv+")", true); }
        }
        for (BowItem b: room.bows){
            if (b.taken) continue;
            Rectangle br=new Rectangle(b.x*TILE+TILE/4, b.y*TILE+TILE/4, TILE/2, TILE/2);
            if (br.intersects(pr)){ b.taken=true; hasBow=true; toast("Bow acquired — press F to shoot!", false); }
        }
        for (ArrowItem a: room.arrows){
            if (a.taken) continue;
            Rectangle ar=new Rectangle(a.x*TILE+TILE/4, a.y*TILE+TILE/4, TILE/2, TILE/2);
            if (ar.intersects(pr)){
                a.taken=true;
                arrowsInv = Math.min(arrowsCap, arrowsInv + a.count);
                toast("Picked "+a.count+" arrows (Total: "+arrowsInv+"/"+arrowsCap+")", false);
            }
        }
        for (QuiverItem q: room.quivers){
            if (q.taken) continue;
            Rectangle qr=new Rectangle(q.x*TILE+TILE/4, q.y*TILE+TILE/4, TILE/2, TILE/2);
            if (qr.intersects(pr)){
                q.taken=true;
                arrowsCap += q.addCap;
                toast("Quiver +"+q.addCap+" (Cap: "+arrowsCap+")", true);
            }
        }
        for (CompassItem c: room.compasses){
            if (c.taken) continue;
            Rectangle cr=new Rectangle(c.x*TILE+TILE/4, c.y*TILE+TILE/4, TILE/2, TILE/2);
            if (cr.intersects(pr)){
                c.taken=true; compassOwned=true; toast("Compass found! Boss revealed.", true);
                if (!bossSpawned){
                    spawnBossInNearestNewRoom();
                }
            }
        }
        for (HeartItem h: room.hearts){
            if (h.taken) continue;
            Rectangle hr=new Rectangle(h.x*TILE+TILE/4, h.y*TILE+TILE/4, TILE/2, TILE/2);
            if (hr.intersects(pr)){
                h.taken=true; if (hp<getMaxHp()){ hp++; toast("Healed ("+hp+"/"+getMaxHp()+")", false); }
            }
        }
        for (CoinItem c: room.coins){
            if (c.taken) continue;
            Rectangle cr=new Rectangle(c.x*TILE+TILE/4, c.y*TILE+TILE/4, TILE/2, TILE/2);
            if (cr.intersects(pr)){ c.taken=true; coinsScore += c.amount; }
        }
        for (ChestItem ch: room.chests){
            if (ch.opened) continue;
            Rectangle rr = new Rectangle(ch.x*TILE, ch.y*TILE, TILE, TILE);
            if (rr.intersects(pr)){
                ch.opened = true;
                bonusMaxHp++;
                hp = Math.min(getMaxHp(), hp+1);
                toast("Max health increased!", true);
            }
        }
    }

    private void spawnBossInNearestNewRoom(){
        if (room.doors.isEmpty()){
            forceSpawnBossInCurrentRoom();
            return;
        }
        Dir d = room.doors.iterator().next();
        Coord next = switch(d){
            case N -> new Coord(worldPos.x, worldPos.y-1, worldPos.z);
            case S -> new Coord(worldPos.x, worldPos.y+1, worldPos.z);
            case W -> new Coord(worldPos.x-1, worldPos.y, worldPos.z);
            case E -> new Coord(worldPos.x+1, worldPos.y, worldPos.z);
        };
        Room r = world.get(next);
        if (r == null){
            r = makeOrGetRoom(next, opposite(d), false);
        }
        Enemy boss = spawnEnemyOnFloor(r, Kind.BOSS, 3.0f);
        r.enemies.add(boss);
        bossSpawned = true; bossQueued=false; bossRoom = next;
    }

    private void damagePlayer(int amt){ hp=Math.max(0,hp-amt); invuln=45; toast("Ouch! ("+hp+"/"+getMaxHp()+")", false); }
    private void toast(String s, boolean bold){ toast=s; toastBold=bold; toastTimer=90; }

    private boolean hitWall(Rectangle r){
        int minTX=Math.max(0,r.x/TILE), maxTX=Math.min(COLS-1,(r.x+r.width-1)/TILE);
        int minTY=Math.max(0,r.y/TILE), maxTY=Math.min(ROWS-1,(r.y+r.height-1)/TILE);
        for(int tx=minTX;tx<=maxTX;tx++) for(int ty=minTY;ty<=maxTY;ty++) if (room.g[tx][ty]==T.WALL) return true;
        return false;
    }

    // Stepped movement
    private void moveStepped(int dx, int dy){
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        double sx = dx/(double)steps;
        double sy = dy/(double)steps;
        double px = player.x, py = player.y;
        for(int i=0;i<steps;i++){
            Rectangle tryX = new Rectangle((int)Math.round(px+sx), (int)Math.round(py), player.width, player.height);
            if (!hitWall(tryX)) px += sx;
            Rectangle tryY = new Rectangle((int)Math.round(px), (int)Math.round(py+sy), player.width, player.height);
            if (!hitWall(tryY)) py += sy;
        }
        player.x = clamp((int)Math.round(px), 0, COLS*TILE - player.width);
        player.y = clamp((int)Math.round(py), 0, ROWS*TILE - player.height);
    }

    private void moveEnemyStepped(Enemy en, int dx, int dy){
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        for (int i=0;i<steps;i++){
            Rectangle nx = new Rectangle(en.hitbox()); nx.translate(sx, 0);
            if (!hitWall(nx)) en.x += sx;
            Rectangle ny = new Rectangle(en.hitbox()); ny.translate(0, sy);
            if (!hitWall(ny)) en.y += sy;
        }
    }

    private static int clamp(int v, int lo, int hi){ return Math.max(lo, Math.min(hi, v)); }

    private Dir touchingDoorOnEdge(){
        int cx=player.x+player.width/2, cy=player.y+player.height/2;
        if (cy < TILE/3){
            Point t=doorTile(Dir.N);
            if (room.g[t.x][t.y]==T.DOOR && Math.abs(cx-(t.x*TILE+TILE/2))<TILE/2) return Dir.N;
        }
        if (cy > ROWS*TILE - TILE/3){
            Point t=doorTile(Dir.S);
            if (room.g[t.x][t.y]==T.DOOR && Math.abs(cx-(t.x*TILE+TILE/2))<TILE/2) return Dir.S;
        }
        if (cx < TILE/3){
            Point t=doorTile(Dir.W);
            if (room.g[t.x][t.y]==T.DOOR && Math.abs(cy-(t.y*TILE+TILE/2))<TILE/2) return Dir.W;
        }
        if (cx > COLS*TILE - TILE/3){
            Point t=doorTile(Dir.E);
            if (room.g[t.x][t.y]==T.DOOR && Math.abs(cy-(t.y*TILE+TILE/2))<TILE/2) return Dir.E;
        }
        return null;
    }

    private void placePlayerJustInside(Dir enteredFrom){
        Point t=doorTile(enteredFrom);
        int px=t.x*TILE+TILE/2, py=t.y*TILE+TILE/2;
        switch(enteredFrom){ case N->py+=TILE; case S->py-=TILE; case W->px+=TILE; case E->px-=TILE; }
        placePlayerSafelyNearPixels(px, py);
    }

    private void switchRoom(Dir exitSide, boolean heavyForNext){
        Coord next = switch(exitSide){
            case N -> new Coord(worldPos.x, worldPos.y-1, worldPos.z);
            case S -> new Coord(worldPos.x, worldPos.y+1, worldPos.z);
            case W -> new Coord(worldPos.x-1, worldPos.y, worldPos.z);
            case E -> new Coord(worldPos.x+1, worldPos.y, worldPos.z);
        };
        Dir entrance = opposite(exitSide);
        room = makeOrGetRoom(next, entrance, heavyForNext);
        worldPos = next; discovered.add(worldPos);
    }

    private boolean hitPotRect(Rectangle r){
        int minTX=Math.max(0,r.x/TILE), maxTX=Math.min(COLS-1,(r.x+r.width-1)/TILE);
        int minTY=Math.max(0,r.y/TILE), maxTY=Math.min(ROWS-1,(r.y+r.height-1)/TILE);
        boolean broke=false;
        for (int tx=minTX;tx<=maxTX;tx++){
            for (int ty=minTY;ty<=maxTY;ty++){
                if (room.g[tx][ty]==T.POT){
                    room.g[tx][ty]=T.FLOOR; broke=true;
                    // 50% heart chance from pots
                    if (rng.nextFloat()<0.50f) room.hearts.add(spawnHeartOnFloor(room));
                    // Slight key chance
                    if (rng.nextFloat()<0.08f) room.keys.add(spawnKeyOnFloor(room));
                    // Coins
                    if (rng.nextFloat()<0.85f) room.coins.add(spawnCoinOnFloor(room, 1 + rng.nextInt(3)));
                    // Arrows
                    if (rng.nextFloat()<0.25f) room.arrows.add(spawnArrowBundleOnFloor(room, 1 + rng.nextInt(3)));
                    // Compass drop possible from pots — 40%
                    if (!compassOwned && !bossSpawned && rng.nextFloat()<0.40f) room.compasses.add(spawnCompassOnFloor(room));
                }
            }
        }
        return broke;
    }

    // Sword sweep helpers
    private void startSwing(){
        if (gameOver || battleMode) return;
        if (swingActive || swingCooldown>0) return;
        swingActive = true;
        swingTimer  = SWING_WINDUP + SWING_TOTAL;
        swingCooldown = SWING_COOLDOWN;
        hitThisSwing.clear();

        int cx = player.x + player.width/2;
        int cy = player.y + player.height/2;
        double facing = Math.atan2(mouseY - cy, mouseX - cx);

        double sweepRad = Math.toRadians(SWEEP_DEGREES);
        sweepStartAngle = normalize(facing - sweepRad*0.5);
        sweepEndAngle   = normalize(facing + sweepRad*0.5);
        sweepCCW = shortestCCW(sweepStartAngle, sweepEndAngle);
        sweepCurrAngle = sweepStartAngle;
    }

    private void updateSwordSweep(){
        if (swingCooldown>0) swingCooldown--;
        if (!swingActive) return;

        swingTimer--;
        if (swingTimer <= 0){ swingActive=false; return; }

        int elapsedAfterWindup = Math.max(0, (SWING_WINDUP + SWING_TOTAL) - swingTimer - SWING_WINDUP);
        double t = (SWING_TOTAL == 0) ? 1.0 : clamp01(elapsedAfterWindup / (double)SWING_TOTAL);
        double eased = easeInOutCubic(t);
        sweepCurrAngle = lerpAngle(sweepStartAngle, sweepEndAngle, eased, sweepCCW);

        if (elapsedAfterWindup > 0){
            int cx = player.x + player.width/2;
            int cy = player.y + player.height/2;

            for (Enemy en : room.enemies){
                if (!en.alive) continue;
                if (en.kind==Kind.BOSS) continue; // can't kill boss with sword
                if (hitThisSwing.contains(en.id)) continue;

                double dx = en.x - cx, dy = en.y - cy;
                double dist = Math.hypot(dx, dy);
                if (dist > SWEEP_REACH + en.radius()) continue;

                double ang = Math.atan2(dy, dx);
                if (angleIsBetween(ang, sweepStartAngle, sweepCurrAngle, sweepCCW)){
                    handleEnemyDeath(en);
                    hitThisSwing.add(en.id);
                }
            }

            Rectangle sweepAabb = sweepAABB(cx, cy);
            if (sweepAabb != null) hitPotRect(sweepAabb);
        }
    }

    private Rectangle sweepAABB(int cx, int cy){
        double rOuter = SWEEP_REACH;
        int minX = (int)Math.floor(cx - rOuter);
        int maxX = (int)Math.ceil (cx + rOuter);
        int minY = (int)Math.floor(cy - rOuter);
        int maxY = (int)Math.ceil (cy + rOuter);
        return new Rectangle(minX, minY, maxX-minX, maxY-minY);
    }

    private static double clamp01(double v){ return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static double normalize(double a){
        a = a % (Math.PI * 2);
        if (a <= -Math.PI) a += Math.PI * 2;
        if (a > Math.PI) a -= Math.PI * 2;
        return a;
    }
    private static boolean shortestCCW(double a, double b){
        double diff = normalize(b - a);
        return diff >= 0;
    }
    private static double lerpAngle(double a, double b, double t, boolean ccw){
        a = normalize(a); b = normalize(b);
        double diff = normalize(b - a);
        if (!ccw && diff > 0) diff -= Math.PI * 2;
        if ( ccw && diff < 0) diff += Math.PI * 2;
        return normalize(a + diff * t);
    }
    private static boolean angleIsBetween(double x, double a, double b, boolean ccw){
        x = normalize(x); a = normalize(a); b = normalize(b);
        if (ccw) {
            double ab = normalize(b - a);
            double ax = normalize(x - a);
            return ax >= -1e-6 && ax <= ab + 1e-6;
        } else {
            double ba = normalize(a - b);
            double bx = normalize(x - b);
            return bx >= -1e-6 && bx <= ba + 1e-6;
        }
    }
    private static double easeInOutCubic(double s){
        return (s < 0.5) ? 4*s*s*s : 1 - Math.pow(-2*s + 2, 3)/2;
    }

    @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D gg=(Graphics2D)g; gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (showMap){ drawFullMap(gg); return; }

        // Tiles
        for(int x=0;x<COLS;x++) for(int y=0;y<ROWS;y++){
            int px=x*TILE, py=y*TILE; T t=room.g[x][y];
            switch(t){
                case FLOOR -> { gg.setColor(new Color(18,64,78)); gg.fillRect(px,py,TILE,TILE); gg.setColor(new Color(10,45,55)); gg.drawRect(px,py,TILE,TILE); }
                case WALL  -> { gg.setColor(new Color(36,132,156)); gg.fillRect(px,py,TILE,TILE); }
                case DOOR  -> { gg.setColor(new Color(18,64,78)); gg.fillRect(px,py,TILE,TILE); gg.setColor(new Color(220,172,60));
                    if (x==0||x==COLS-1) gg.fillRect(px+(x==0?0:TILE-6), py+6, 6, TILE-12);
                    if (y==0||y==ROWS-1) gg.fillRect(px+6, py+(y==0?0:TILE-6), TILE-12, 6); }
                case STAIR -> { gg.setColor(new Color(25,80,25)); gg.fillRect(px,py,TILE,TILE); gg.setColor(new Color(50,180,50)); gg.drawRect(px,py,TILE,TILE); }
                case TRAP -> { boolean active = (globalTick % 60) < 30;
                    gg.setColor(active?new Color(200,50,50):new Color(120,30,30));
                    gg.fillRect(px,py,TILE,TILE); }
                case POT -> { gg.setColor(new Color(170,130,90));
                    gg.fillRect(px+6,py+6,TILE-12,TILE-12); gg.setColor(Color.BLACK); gg.drawRect(px+6,py+6,TILE-12,TILE-12); }
                case CHEST -> { gg.setColor(new Color(220,170,60));
                    gg.fillRect(px+4,py+4,TILE-8,TILE-8); gg.setColor(Color.BLACK); gg.drawRect(px+4,py+4,TILE-8,TILE-8); }
                default -> {}
            }
        }

        // Mob door hint
        if (room.mobDoor!=null){
            gg.setColor(new Color(255,60,60,160));
            Point t=doorTile(room.mobDoor); int px=t.x*TILE, py=t.y*TILE;
            if (t.x==0||t.x==COLS-1) gg.fillRect(px+(t.x==0?4:TILE-10), py+TILE/3, 6, TILE/3);
            if (t.y==0||t.y==ROWS-1) gg.fillRect(px+TILE/3, py+(t.y==0?4:TILE-10), TILE/3, 6);
        }

        // Locks
        gg.setColor(new Color(255,80,80));
        for (Dir d: room.locked){
            Point t=doorTile(d); int px=t.x*TILE, py=t.y*TILE;
            if (t.x==0||t.x==COLS-1) gg.fillRect(px+(t.x==0?2:TILE-8), py+TILE/3, 6, TILE/3);
            if (t.y==0||t.y==ROWS-1) gg.fillRect(px+TILE/3, py+(t.y==0?2:TILE-8), TILE/3, 6);
        }

        // Pickups visuals (compass is PINK)
        for (KeyItem k: room.keys){
            if (k.taken) continue; int px=k.x*TILE, py=k.y*TILE;
            gg.setColor(new Color(250,210,80)); int s=TILE/2;
            gg.fillOval(px+TILE/2-s/2, py+TILE/2-s/2, s, s); gg.setColor(Color.BLACK); gg.drawOval(px+TILE/2-s/2, py+TILE/2-s/2, s, s);
        }
        for (BowItem b: room.bows){
            if (b.taken) continue; int px=b.x*TILE, py=b.y*TILE;
            gg.setColor(new Color(200,200,255)); gg.fillRect(px+TILE/3, py+TILE/3, TILE/3, TILE/3); gg.setColor(Color.BLACK); gg.drawRect(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
        }
        for (ArrowItem a: room.arrows){
            if (a.taken) continue; int px=a.x*TILE, py=a.y*TILE;
            gg.setColor(new Color(230,230,255)); gg.fillOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
            gg.setColor(Color.BLACK); gg.drawOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
        }
        for (QuiverItem q: room.quivers){
            if (q.taken) continue; int px=q.x*TILE, py=q.y*TILE;
            gg.setColor(new Color(140,120,80)); gg.fillRect(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
            gg.setColor(Color.BLACK); gg.drawRect(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
        }
        for (CompassItem c: room.compasses){
            if (c.taken) continue; int px=c.x*TILE, py=c.y*TILE;
            gg.setColor(new Color(255,120,200)); // PINK
            gg.fillOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
            gg.setColor(Color.BLACK); gg.drawOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
        }
        for (HeartItem h: room.hearts){
            if (h.taken) continue; int px=h.x*TILE, py=h.y*TILE;
            gg.setColor(new Color(220,60,80)); gg.fillOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
            gg.setColor(Color.BLACK); gg.drawOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
        }
        for (CoinItem c: room.coins){
            if (c.taken) continue; int px=c.x*TILE, py=c.y*TILE;
            gg.setColor(new Color(255,225,90)); gg.fillOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
            gg.setColor(Color.BLACK); gg.drawOval(px+TILE/3, py+TILE/3, TILE/3, TILE/3);
        }

        // Projectiles
        for (Projectile p: room.projectiles){
            gg.setColor(p.type==ProjType.ENEMY_FIRE ? new Color(255,110,40) : new Color(230,230,255));
            Rectangle hb=p.hitbox(); gg.fillOval(hb.x,hb.y,hb.width,hb.height);
        }

        // Enemies
        for (Enemy en: room.enemies){
            if (!en.alive) continue;
            Rectangle hb=en.hitbox();
            if (en.kind==Kind.BOSS){
                gg.setColor(new Color(230,40,40));
                gg.fillOval(hb.x, hb.y, hb.width, hb.height);
                gg.setColor(Color.BLACK); gg.drawOval(hb.x, hb.y, hb.width, hb.height);
            } else {
                switch(en.kind){ case SLIME -> gg.setColor(new Color(255,140,0));
                    case RUNNER -> gg.setColor(new Color(255,70,70));
                    case SNAIL -> gg.setColor(new Color(255,200,120));
                    default -> gg.setColor(Color.PINK);}
                gg.fillOval(hb.x,hb.y,hb.width,hb.height);
                gg.setColor(Color.BLACK); gg.drawOval(hb.x,hb.y,hb.width,hb.height);
                if (en.windup>0){
                    gg.setColor(new Color(255,220,120,140));
                    gg.fillOval(hb.x-4, hb.y-4, hb.width+8, hb.height+8);
                }
            }
        }

        // Player
        if (invuln>0 && (invuln/5)%2==0) gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.4f));
        gg.setColor(new Color(255,214,102)); gg.fillOval(player.x,player.y,player.width,player.height);
        gg.setColor(Color.BLACK); gg.drawOval(player.x,player.y,player.width,player.height);
        gg.setComposite(AlphaComposite.SrcOver);

        // Compass pointer (points to boss if known)
        if (compassOwned){
            Point target = compassTarget();
            if (target!=null){
                int cx = player.x + player.width/2;
                int cy = player.y + player.height/2;
                double ang = Math.atan2(target.y - cy, target.x - cx);
                int len = 22;
                int x1 = cx + (int)(Math.cos(ang)* (PLAYER_SIZE/2));
                int y1 = cy + (int)(Math.sin(ang)* (PLAYER_SIZE/2));
                int x2 = cx + (int)(Math.cos(ang)* (PLAYER_SIZE/2 + len));
                int y2 = cy + (int)(Math.sin(ang)* (PLAYER_SIZE/2 + len));
                gg.setColor(new Color(255,120,200)); // pink pointer to match compass
                gg.setStroke(new BasicStroke(2f));
                gg.drawLine(x1,y1,x2,y2);
            }
        }

        // Sword sweep visualization
        if (swingActive && swingTimer < (SWING_WINDUP + SWING_TOTAL) && swingTimer <= SWING_TOTAL){
            int cx = player.x + player.width/2;
            int cy = player.y + player.height/2;

            double rOuter = SWEEP_REACH;
            double rInner = Math.max(0, SWEEP_REACH - SWEEP_THICK);

            GeneralPath path = new GeneralPath();
            int steps = 18;

            for (int i=0;i<=steps;i++){
                double t = i/(double)steps;
                double a = lerpAngle(sweepStartAngle, sweepCurrAngle, t, sweepCCW);
                double ox = cx + Math.cos(a) * rOuter;
                double oy = cy + Math.sin(a) * rOuter;
                if (i==0) path.moveTo(ox, oy); else path.lineTo(ox, oy);
            }
            for (int i=steps;i>=0;i--){
                double t = i/(double)steps;
                double a = lerpAngle(sweepStartAngle, sweepCurrAngle, t, sweepCCW);
                double ix = cx + Math.cos(a) * rInner;
                double iy = cy + Math.sin(a) * rInner;
                path.lineTo(ix, iy);
            }
            path.closePath();

            Composite old = gg.getComposite();
            gg.setComposite(AlphaComposite.SrcOver.derive(0.22f));
            gg.setColor(new Color(255,255,255));
            gg.fill(path);
            gg.setComposite(old);
        }

        // BOSS encounter freeze overlay
        if (battleMode){
            SwingUtilities.invokeLater(() -> {
                JFrame f = new JFrame("Boss Battle");
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                BossBattlePanel panel = BossBattlePanel.create(
                        BossBattlePanel.BossKind.OGRE_WARLORD,
                        () -> {
                            System.out.println("Battle ended (win or game over) — return to world here.");
                            f.dispose();
                        });

                f.setContentPane(panel);
                f.pack();
                f.setLocationRelativeTo(null);
                f.setVisible(true);
                panel.requestFocusInWindow();
                battleMode = false;
            });
        }

        if (lighting) drawLighting(gg);
        drawHUD(gg);
        drawMiniMap(gg,12,getHeight()-12);
    }

    // Compass logic: prioritize boss
    private Point compassTarget(){
        if (bossSpawned && bossRoom!=null && bossRoom.z == worldPos.z){
            if (bossRoom.x == worldPos.x && bossRoom.y == worldPos.y){
                for (Enemy e : room.enemies){
                    if (e.alive && e.kind==Kind.BOSS){
                        return new Point((int)e.x, (int)e.y);
                    }
                }
            } else {
                int dx = bossRoom.x - worldPos.x;
                int dy = bossRoom.y - worldPos.y;
                Dir best;
                if (Math.abs(dx) >= Math.abs(dy)){
                    best = (dx>0) ? Dir.E : Dir.W;
                } else {
                    best = (dy>0) ? Dir.S : Dir.N;
                }
                Dir use = room.doors.contains(best) ? best : (room.doors.isEmpty()?Dir.N:room.doors.iterator().next());
                Point t = doorTile(use);
                return new Point(t.x*TILE + TILE/2, t.y*TILE + TILE/2);
            }
        }
        return null;
    }

    private void drawLighting(Graphics2D gg){
        BufferedImage mask=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg=mask.createGraphics(); mg.setComposite(AlphaComposite.Src);
        mg.setColor(new Color(0,0,0,200)); mg.fillRect(0,0,getWidth(),getHeight());
        int cx=player.x+player.width/2, cy=player.y+player.height/2; float r=TILE*3.5f;
        RadialGradientPaint rgp=new RadialGradientPaint(new Point(cx,cy), r, new float[]{0f,1f}, new Color[]{new Color(0,0,0,0), new Color(0,0,0,200)});
        mg.setPaint(rgp); mg.setComposite(AlphaComposite.DstOut); mg.fillOval((int)(cx-r),(int)(cy-r),(int)(2*r),(int)(2*r)); mg.dispose();
        gg.drawImage(mask,0,0,null);
    }

    private void drawHUD(Graphics2D gg){
        int x=10,y=8,s=14;
        for (int i=0;i<getMaxHp();i++){
            gg.setColor(new Color(40,40,40,160)); gg.drawRect(x-1+i*(s+6),y-1,s+2,s+2);
            gg.setColor(i<hp?new Color(220,60,80):new Color(60,60,60)); gg.fillOval(x+i*(s+6),y,s,s);
        }
        gg.setColor(new Color(255,215,100)); gg.fillRect(10,28,10,6); gg.fillRect(17,24,6,14);
        gg.setColor(Color.WHITE); gg.drawString("x "+keysInv+"   Arrows: "+arrowsInv+"/"+arrowsCap+"   Coins: "+coinsScore, 28, 38);

        gg.setColor(new Color(255,255,255,190));
        gg.drawString("WASD move • LMB sweep • F shoot • M map • L light • Esc quit", 10, getHeight()-10);

        if (toastTimer>0){
            gg.setColor(new Color(0,0,0,170));
            int w=gg.getFontMetrics().stringWidth(toast)+24; gg.fillRoundRect(getWidth()/2-w/2,8,w,26,8,8);
            gg.setColor(Color.WHITE); Font base=gg.getFont(); if (toastBold) gg.setFont(base.deriveFont(Font.BOLD, base.getSize()+0f));
            gg.drawString(toast,getWidth()/2-w/2+12,26); if (toastBold) gg.setFont(base);
        }
    }

    private void drawMiniMap(Graphics2D gg,int left,int bottom){
        if (discovered.isEmpty()) return;
        int cell=8,pad=2,minX=1<<30,minY=1<<30,maxX=-(1<<30),maxY=-(1<<30);
        for (Coord p: discovered){ if (p.z!=worldPos.z) continue; minX=Math.min(minX,p.x); minY=Math.min(minY,p.y); maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); }
        if (minX>maxX) return; int cols=(maxX-minX+1), rows=(maxY-minY+1);
        int w=cols*cell+2*pad, h=rows*cell+2*pad, x0=left, y0=bottom-h;
        gg.setColor(new Color(0,0,0,160)); gg.fillRoundRect(x0-4,y0-4,w+8,h+8,8,8);
        for (Coord p: discovered){
            if (p.z!=worldPos.z) continue; int cx=x0+pad+(p.x-minX)*cell, cy=y0+pad+(p.y-minY)*cell;
            gg.setColor(new Color(70,120,255)); gg.fillRect(cx,cy,cell,cell);
            Room r=world.get(p); if (r!=null){ gg.setColor(Color.BLACK); int gap=Math.max(2,cell/5);
                if (r.doors.contains(Dir.N)) gg.fillRect(cx+cell/3, cy-1, gap,2);
                if (r.doors.contains(Dir.S)) gg.fillRect(cx+cell/3, cy+cell-1, gap,2);
                if (r.doors.contains(Dir.W)) gg.fillRect(cx-1, cy+cell/3, 2,gap);
                if (r.doors.contains(Dir.E)) gg.fillRect(cx+cell-1, cy+cell/3, 2,gap);
            }
        }
        int rx=x0+pad+(worldPos.x-minX)*cell, ry=y0+pad+(worldPos.y-minY)*cell;
        gg.setColor(new Color(255,200,0)); gg.drawRect(rx-1,ry-1,cell+2,cell+2);
    }

    private void drawFullMap(Graphics2D gg){
        gg.setColor(new Color(4,8,16)); gg.fillRect(0,0,getWidth(),getHeight());
        if (discovered.isEmpty()) return; int pad=40;
        int minX=1<<30,minY=1<<30,maxX=-(1<<30),maxY=-(1<<30);
        for (Coord p: discovered){ if (p.z!=worldPos.z) continue; minX=Math.min(minX,p.x); minY=Math.min(minY,p.y); maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); }
        if (minX>maxX){ gg.setColor(Color.WHITE); gg.drawString("No rooms on floor "+worldPos.z, 20, 30); return; }
        int cols=(maxX-minX+1), rows=(maxY-minY+1);
        int cell=Math.max(12, Math.min((getWidth()-2*pad)/Math.max(1,cols), (getHeight()-2*pad)/Math.max(1,rows)));
        int x0=(getWidth()-cols*cell)/2, y0=(getHeight()-rows*cell)/2;
        for (Coord p: discovered){
            if (p.z!=worldPos.z) continue; int cx=x0+(p.x-minX)*cell, cy=y0+(p.y-minY)*cell;
            gg.setColor(new Color(34,70,180)); gg.fillRect(cx,cy,cell,cell);
            gg.setColor(new Color(18,36,90)); gg.drawRect(cx,cy,cell,cell);
            Room r=world.get(p); if (r!=null){ gg.setColor(Color.BLACK); int gap=Math.max(2,cell/5);
                if (r.doors.contains(Dir.N)) gg.fillRect(cx+cell/2-gap/2, cy-2, gap,4);
                if (r.doors.contains(Dir.S)) gg.fillRect(cx+cell/2-gap/2, cy+cell-2, gap,4);
                if (r.doors.contains(Dir.W)) gg.fillRect(cx-2, cy+cell/2-gap/2, 4,gap);
                if (r.doors.contains(Dir.E)) gg.fillRect(cx+cell-2, cy+cell/2-gap/2, 4,gap);
            }
        }
        int rx=x0+(worldPos.x-minX)*cell, ry=y0+(worldPos.y-minY)*cell;
        gg.setColor(new Color(255,210,0)); gg.setStroke(new BasicStroke(3f)); gg.drawRect(rx-2,ry-2,cell+4,cell+4);
        gg.setColor(new Color(255,255,255,200)); gg.drawString("Automap (Floor "+worldPos.z+") — press M", 16, 24);
    }

    // ---- input ----
    @Override public void keyPressed(KeyEvent e){
        if (gameOver){
            if (e.getKeyCode()==KeyEvent.VK_SPACE){
                resetGame();
            }
            return;
        }
        if (battleMode){
            if (e.getKeyCode()==KeyEvent.VK_ESCAPE) System.exit(0);
            return;
        }
        switch(e.getKeyCode()){
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up=true;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down=true;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left=true;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right=true;
            case KeyEvent.VK_F -> {
                if (hasBow && arrowCd==0 && arrowsInv>0){
                    float cx=player.x+player.width/2f, cy=player.y+player.height/2f;
                    double ang = Math.atan2(mouseY - cy, mouseX - cx);
                    float sp = 5.0f;
                    float vx = (float)(Math.cos(ang) * sp);
                    float vy = (float)(Math.sin(ang) * sp);
                    room.projectiles.add(new Projectile(ProjType.PLAYER_ARROW, cx, cy, vx, vy));
                    arrowCd=16;
                    arrowsInv--;
                }
            }
            case KeyEvent.VK_L -> { lighting=!lighting; repaint(); }
            case KeyEvent.VK_M -> { showMap=!showMap; repaint(); }
            case KeyEvent.VK_ESCAPE -> System.exit(0);
        }
    }
    @Override public void keyReleased(KeyEvent e){
        if (gameOver || battleMode) return;
        switch(e.getKeyCode()){
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up=false;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down=false;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left=false;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right=false;
        }
    }
    @Override public void keyTyped(KeyEvent e){}

    @Override public void mousePressed(MouseEvent e){
        if (gameOver){
            if (e.getButton()==MouseEvent.BUTTON1) resetGame();
            return;
        }
        if (battleMode) return;
        if (e.getButton()==MouseEvent.BUTTON1){
            mouseX = e.getX(); mouseY = e.getY();
            startSwing();
        }
    }
    @Override public void mouseReleased(MouseEvent e){}
    @Override public void mouseClicked(MouseEvent e){}
    @Override public void mouseEntered(MouseEvent e){}
    @Override public void mouseExited(MouseEvent e){}

    private void resetGame(){
        bonusMaxHp=0; baseMaxHp=5; hp=getMaxHp();
        keysInv=0; arrowsInv=0; hasBow=false; arrowsCap=12;
        coinsScore=0;
        invuln = 0;
        swingActive=false; swingCooldown=0; hitThisSwing.clear();

        world.clear(); discovered.clear();
        worldPos=new Coord(0,0,0);
        room=makeOrGetRoom(worldPos,null,false);
        discovered.add(worldPos);

        placePlayerSafelyNearPixels(COLS*TILE/2 + TILE/2, ROWS*TILE/2 + TILE/2);
        ensureNeighborsGenerated();

        toast("Ready.", false);

        doorsPassed=0;
        bossQueued=false; bossSpawned=false; bossRoom=null; battleMode=false;
        gameOver=false;
        compassOwned=false;
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> {
            JFrame f=new JFrame("Zelda Rooms — boss rush + compass 40%");
            ZeldaRooms p=new ZeldaRooms();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(p); f.pack(); f.setLocationRelativeTo(null); f.setResizable(false); f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}
