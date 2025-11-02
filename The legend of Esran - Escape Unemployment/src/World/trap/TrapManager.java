package World.trap;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TrapManager implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Trap> traps = new ArrayList<>();

    public void add(Trap trap) {
        if (trap != null) {
            traps.add(trap);
        }
    }

    public void clear() {
        traps.clear();
    }

    public List<Trap> traps() {
        return Collections.unmodifiableList(traps);
    }

    public void update(double dt, Player player) {
        if (player != null) {
            player.update(dt);
        }
        for (Trap trap : traps) {
            trap.update(dt);
        }
        if (player == null) {
            return;
        }
        Rectangle playerBounds = player.getBounds();
        if (playerBounds == null) {
            return;
        }
        traps.removeIf(trap -> trap != null && trap.shouldRemove());
        for (Trap trap : traps) {
            if (trap == null || !trap.isActive()) {
                continue;
            }
            Rectangle trapBounds = trap.getBounds();
            Rectangle visualBounds = null;
            boolean intersects = trapBounds != null && playerBounds.intersects(trapBounds);
            BaseTrap base = trap instanceof BaseTrap ? (BaseTrap) trap : null;
            if (!intersects && base != null) {
                visualBounds = base.getVisualBounds();
                if (visualBounds != null && visualBounds.width > 0 && visualBounds.height > 0) {
                    intersects = playerBounds.intersects(visualBounds);
                }
            }
            if (!intersects) {
                continue;
            }
            if (base != null && base.isPixelAccurate()) {
                if (visualBounds == null) {
                    visualBounds = base.getVisualBounds();
                }
                // Only damage if player touches non-transparent pixels
                // Do not derive hitbox from sprite scale or user settings
                if (!pixelPerfectOverlap(base, playerBounds, visualBounds)) {
                    continue;
                }
            }
            trap.onPlayerCollision(player);
        }
    }

    public void render(Graphics2D g) {
        if (g == null) {
            return;
        }
        for (Trap trap : traps) {
            trap.render(g);
        }
    }

    public boolean damageTrap(double px, double py, double damage) {
        boolean hit = false;
        for (int i = traps.size() - 1; i >= 0; i--) {
            Trap trap = traps.get(i);
            if (trap == null) {
                continue;
            }
            Rectangle bounds = trap.getBounds();
            if (bounds != null && bounds.contains(px, py)) {
                if (trap.onProjectileHit(px, py, damage)) {
                    hit = true;
                }
                if (trap.shouldRemove()) {
                    traps.remove(i);
                }
            }
        }
        return hit;
    }

    private boolean pixelPerfectOverlap(BaseTrap trap, Rectangle playerBounds, Rectangle visualBounds) {
        if (trap == null || playerBounds == null) {
            return false;
        }
        Rectangle visual = visualBounds != null ? visualBounds : trap.getVisualBounds();
        if (visual.width <= 0 || visual.height <= 0) {
            return true;
        }
        int startX = Math.max(playerBounds.x, visual.x);
        int endX = Math.min(playerBounds.x + playerBounds.width, visual.x + visual.width);
        int startY = Math.max(playerBounds.y, visual.y);
        int endY = Math.min(playerBounds.y + playerBounds.height, visual.y + visual.height);
        if (startX >= endX || startY >= endY) {
            return false;
        }
        boolean[][] mask = trap.getAlphaMask();
        if (mask == null || mask.length == 0) {
            return true;
        }
        int maskHeight = mask.length;
        int maskWidth = 0;
        for (boolean[] row : mask) {
            if (row != null) {
                maskWidth = Math.max(maskWidth, row.length);
            }
        }
        if (maskWidth <= 0 || maskHeight <= 0) {
            return true;
        }
        int[] xs = samplePoints(startX, endX);
        int[] ys = samplePoints(startY, endY);
        for (int sx : xs) {
            int localX = sx - visual.x;
            if (localX < 0 || localX >= maskWidth) {
                continue;
            }
            for (int sy : ys) {
                int localY = sy - visual.y;
                if (localY < 0 || localY >= maskHeight) {
                    continue;
                }
                boolean[] row = mask[localY];
                if (row == null || localX >= row.length) {
                    continue;
                }
                if (row[localX]) {
                    return true;
                }
            }
        }
        return false;
    }

    private int[] samplePoints(int startInclusive, int endExclusive) {
        int last = endExclusive - 1;
        if (last <= startInclusive) {
            return new int[]{startInclusive};
        }
        int span = Math.max(1, last - startInclusive);
        int[] candidates = new int[]{
                startInclusive,
                startInclusive + span / 4,
                startInclusive + span / 2,
                startInclusive + (span * 3) / 4,
                last
        };
        int[] samples = new int[candidates.length];
        int count = 0;
        for (int value : candidates) {
            int clamped = Math.max(startInclusive, Math.min(last, value));
            if (count == 0 || samples[count - 1] != clamped) {
                samples[count++] = clamped;
            }
        }
        return Arrays.copyOf(samples, count);
    }
}
