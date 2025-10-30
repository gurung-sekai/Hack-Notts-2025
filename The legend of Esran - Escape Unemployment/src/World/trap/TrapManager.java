package World.trap;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
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
            if (playerBounds.intersects(trap.getBounds())) {
                trap.onPlayerCollision(player);
            }
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
}
