package World;

import Battle.domain.Fighter;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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

    static class Room {
        T[][] g = new T[COLS][ROWS];
        Set<Dir> doors = EnumSet.noneOf(Dir.class);
        Room() {
            for (int x = 0; x < COLS; x++)
                for (int y = 0; y < ROWS; y++)
                    g[x][y] = T.VOID;
        }
    }

    private final Timer timer = new Timer(1000 / FPS, this);
    private final Random rng = new Random();

    // Persistent world: integer-grid of rooms using world coordinates
    private final Map<Point, Room> world = new HashMap<>();
    private Point worldPos = new Point(0, 0);   // current room coordinate

    private Room room;                 // current room
    private Rectangle player;          // player rectangle in pixels
    private boolean up, down, left, right;

    // Somewhere in ZeldaRooms when you detect a boss tile or key press:
    private void startBossBattle() {
        Fighter hero = new Fighter("King's Guardian", Battle.domain.Affinity.STONE, new Battle.domain.Stats(120,18,18,14));
        Fighter boss = new Fighter("Arch Druid", Battle.domain.Affinity.VERDANT, new Battle.domain.Stats(140,22,16,14));

        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        Battle.swing.BattlePanel bp = new Battle.swing.BattlePanel(hero, boss, winner -> {
            // After battle ends, restore the world scene
            frame.setContentPane(this);
            this.requestFocusInWindow();
            frame.revalidate();
            // Use 'winner' if you want to drop loot, open door, etc.
        });

        frame.setContentPane(bp);
        frame.revalidate();
        bp.requestFocusInWindow();
    }


    public ZeldaRooms() {
        setPreferredSize(new java.awt.Dimension(COLS * TILE, ROWS * TILE));
        setBackground(BG);
        setFocusable(true);
        addKeyListener(this);

        // Create the starting room (center of the world)
        room = makeOrGetRoom(worldPos, null);
        placePlayerAtCenter();
        timer.start();
    }

    // ======= Room creation / persistence =======

    /** Get existing room at pos or create a new one with 1–3 doors. Guarantees an entrance if required. */
    private Room makeOrGetRoom(Point pos, Dir mustHaveEntrance) {
        Room r = world.get(pos);
        if (r == null) {
            r = generateNewRoom(mustHaveEntrance);
            world.put(new Point(pos), r); // store a copy of key to avoid mutation issues
            return r;
        }
        // Ensure the entrance exists if we’re entering from a new side later
        if (mustHaveEntrance != null && !r.doors.contains(mustHaveEntrance)) {
            r.doors.add(mustHaveEntrance);
            carveDoorOnGrid(r, mustHaveEntrance);
        }
        return r;
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

        // Choose 1–3 total doors
        int totalDoors = 1 + rng.nextInt(3); // 1..3

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

    // ======= Player control / updates =======

    private void placePlayerAtCenter() {
        int cx = COLS / 2 * TILE + TILE / 2;
        int cy = ROWS / 2 * TILE + TILE / 2;
        player = new Rectangle(cx - PLAYER_SIZE / 2, cy - PLAYER_SIZE / 2, PLAYER_SIZE, PLAYER_SIZE);
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
        player = new Rectangle(px - PLAYER_SIZE / 2, py - PLAYER_SIZE / 2, PLAYER_SIZE, PLAYER_SIZE);
        repaint();
    }

    @Override public void actionPerformed(ActionEvent e) { updatePlayer(); repaint(); }

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
        room = makeOrGetRoom(nextPos, entranceSide);
        worldPos = nextPos;

        // Place player just inside the entrance we came through (from the new room’s perspective)
        placePlayerJustInside(entranceSide);
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
                switch (room.g[x][y]) {
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
                    }
                    default -> {}
                }
            }
        }

        // Player
        gg.setColor(new Color(255, 214, 102));
        gg.fillOval(player.x, player.y, player.width, player.height);
        gg.setColor(Color.BLACK);
        gg.drawOval(player.x, player.y, player.width, player.height);

        // HUD
        gg.setColor(new Color(255, 255, 255, 190));
        gg.drawString("Move: Arrows/WASD (no diagonal)   R: reroll obstacles   Esc: quit", 10, 18);
        gg.drawString("Room (" + worldPos.x + "," + worldPos.y + ")  Doors: " + room.doors, 10, 34);
    }

    // ======= Input =======

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = true;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = true;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = true;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = true;
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
    @Override public void keyTyped(KeyEvent e) {}

    private Room rerollObstacles(Room r) {
        // Clear inside (except borders & doors), then re-add obstacles
        for (int x = 1; x < COLS - 1; x++)
            for (int y = 1; y < ROWS - 1; y++)
                if (r.g[x][y] != T.DOOR) r.g[x][y] = T.FLOOR;

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

    // ======= Boot =======

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Zelda Rooms (persistent rooms)");
            ZeldaRooms p = new ZeldaRooms();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(p);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setResizable(false);
            f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}
