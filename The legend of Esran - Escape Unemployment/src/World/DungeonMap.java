package World;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

public class DungeonMap extends JPanel {
    // ---------- Tunables ----------
    static final int TILE = 24;                 // tile size in pixels
    static final int MAP_W = 64;                // tiles wide
    static final int MAP_H = 40;                // tiles high
    static final int ROOM_MIN_W = 6, ROOM_MAX_W = 12;
    static final int ROOM_MIN_H = 5, ROOM_MAX_H = 10;
    static final int ROOM_COUNT = 18;
    static final long SEED = System.nanoTime(); // change for reproducible maps

    enum T { VOID, FLOOR, WALL, DOOR }
    static class Room {
        int x,y,w,h, cx, cy;
        Room(int x,int y,int w,int h){
            this.x=x; this.y=y; this.w=w; this.h=h;
            cx = x + w/2; cy = y + h/2;
        }
        boolean overlaps(Room o){
            return x-1 <= o.x+o.w && x+w+1 >= o.x && y-1 <= o.y+o.h && y+h+1 >= o.y;
        }
    }

    private final Random rng = new Random(SEED);
    private T[][] grid;
    private List<Room> rooms;

    public DungeonMap() {
        setPreferredSize(new Dimension(MAP_W * TILE, MAP_H * TILE));
        setBackground(new Color(6, 24, 32)); // deep teal
        regenerate();

        // hotkeys
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_R) { regenerate(); repaint(); }
                if (e.getKeyCode() == KeyEvent.VK_E) { exportPng(); }
            }
        });
    }

    private void regenerate() {
        grid = new T[MAP_W][MAP_H];
        rooms = new ArrayList<>();
        for (int x=0;x<MAP_W;x++) for (int y=0;y<MAP_H;y++) grid[x][y] = T.VOID;

        // 1) place non-overlapping rooms
        int tries = 0;
        while (rooms.size() < ROOM_COUNT && tries < ROOM_COUNT * 40) {
            tries++;
            int w = rnd(ROOM_MIN_W, ROOM_MAX_W);
            int h = rnd(ROOM_MIN_H, ROOM_MAX_H);
            int x = rnd(1, MAP_W - w - 2);
            int y = rnd(1, MAP_H - h - 2);
            Room candidate = new Room(x,y,w,h);
            boolean ok = true;
            for (Room r : rooms) { if (candidate.overlaps(r)) { ok=false; break; } }
            if (!ok) continue;
            rooms.add(candidate);
            fillRect(x, y, w, h, T.FLOOR);
        }

        if (rooms.isEmpty()) return;

        // 2) connect rooms with simple MST-ish chain (sorted by x)
        rooms.sort((a,b)->Integer.compare(a.cx, b.cx));
        for (int i=0;i<rooms.size()-1;i++) connect(rooms.get(i), rooms.get(i+1));

        // 3) build walls around floors
        surroundFloorsWithWalls();

        // 4) mark doors where corridors enter rooms
        markDoors();
    }

    private void connect(Room a, Room b) {
        // “L-shaped” corridors: horizontal then vertical (or vice versa)
        int x1 = a.cx, y1 = a.cy, x2 = b.cx, y2 = b.cy;
        if (rng.nextBoolean()) {
            carveLine(x1, y1, x2, y1);
            carveLine(x2, y1, x2, y2);
        } else {
            carveLine(x1, y1, x1, y2);
            carveLine(x1, y2, x2, y2);
        }
    }

    private void carveLine(int x1,int y1,int x2,int y2) {
        int dx = Integer.compare(x2, x1);
        int dy = Integer.compare(y2, y1);
        int x=x1, y=y1;
        gridClampSet(x,y,T.FLOOR);
        while (x!=x2 || y!=y2) {
            if (x!=x2) x+=dx; else y+=dy;
            gridClampSet(x,y,T.FLOOR);
            // optional “fatter” corridor look
            for (int ox=-1; ox<=1; ox++)
                for (int oy=-1; oy<=1; oy++)
                    gridClampSet(x+ox,y+oy,T.FLOOR);
        }
    }

    private void surroundFloorsWithWalls() {
        for (int x=1;x<MAP_W-1;x++) {
            for (int y=1;y<MAP_H-1;y++) {
                if (grid[x][y] == T.VOID && hasNeighbor(x,y,T.FLOOR)) {
                    grid[x][y] = T.WALL;
                }
            }
        }
    }

    private void markDoors() {
        for (Room r : rooms) {
            // scan room perimeter; where a wall borders floor outside, place a door
            for (int i=r.x; i<r.x+r.w; i++) {
                maybeDoor(i, r.y-1, 0, -1);
                maybeDoor(i, r.y+r.h, 0, 1);
            }
            for (int j=r.y; j<r.y+r.h; j++) {
                maybeDoor(r.x-1, j, -1, 0);
                maybeDoor(r.x+r.w, j, 1, 0);
            }
        }
    }

    private void maybeDoor(int wx,int wy,int dx,int dy) {
        if (!inBounds(wx,wy)) return;
        if (grid[wx][wy] == T.WALL) {
            int ax = wx+dx, ay = wy+dy;     // outside cell
            int bx = wx-dx, by = wy-dy;     // inside cell
            if (inBounds(ax,ay) && inBounds(bx,by)
                    && grid[ax][ay] == T.FLOOR && grid[bx][by] == T.FLOOR) {
                grid[wx][wy] = T.DOOR;
            }
        }
    }

    private boolean hasNeighbor(int x,int y,T t) {
        for (int ox=-1; ox<=1; ox++)
            for (int oy=-1; oy<=1; oy++)
                if (!(ox==0 && oy==0) && inBounds(x+ox,y+oy) && grid[x+ox][y+oy]==t)
                    return true;
        return false;
    }

    private void fillRect(int x,int y,int w,int h,T t){
        for (int i=x;i<x+w;i++) for (int j=y;j<y+h;j++) grid[i][j]=t;
    }

    private void gridClampSet(int x,int y,T t) {
        if (inBounds(x,y)) grid[x][y]=t;
    }

    private boolean inBounds(int x,int y){
        return x>=0 && x<MAP_W && y>=0 && y<MAP_H;
    }

    private int rnd(int lo, int hi) { // inclusive bounds
        return lo + rng.nextInt(hi - lo + 1);
    }

    // ---------- Rendering ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D gg = (Graphics2D) g;
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int x=0;x<MAP_W;x++) {
            for (int y=0;y<MAP_H;y++) {
                int px = x*TILE, py = y*TILE;
                switch (grid[x][y]) {
                    case FLOOR -> {
                        gg.setColor(new Color(18, 64, 78));   // floor
                        gg.fillRect(px, py, TILE, TILE);
                        gg.setColor(new Color(14, 50, 61));
                        gg.drawRect(px, py, TILE, TILE);
                    }
                    case WALL -> {
                        gg.setColor(new Color(36, 132, 156)); // wall
                        gg.fillRect(px, py, TILE, TILE);
                    }
                    case DOOR -> {
                        gg.setColor(new Color(220, 172, 60)); // door
                        gg.fillRect(px, py, TILE, TILE);
                        gg.setColor(new Color(90, 70, 20));
                        gg.drawRect(px+4, py+4, TILE-8, TILE-8);
                    }
                    default -> {} // VOID: leave background
                }
            }
        }

        // Title overlay
        gg.setColor(new Color(255,255,255,180));
        gg.drawString("R = regenerate   E = export PNG", 12, 18);
    }

    private void exportPng() {
        BufferedImage img = new BufferedImage(MAP_W*TILE, MAP_H*TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        this.paintAll(g2);
        g2.dispose();
        try {
            File out = new File("dungeon-" + System.currentTimeMillis() + ".png");
            ImageIO.write(img, "png", out);
            System.out.println("Saved: " + out.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---------- Bootstrap ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Dungeon Map (Java/Swing)");
            DungeonMap view = new DungeonMap();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(view);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
