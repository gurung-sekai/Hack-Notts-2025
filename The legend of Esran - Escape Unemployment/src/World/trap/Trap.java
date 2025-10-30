package World.trap;

import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface Trap {
    void update(double dt);
    void render(Graphics2D g);
    Rectangle getBounds();
    boolean isActive();
    void onPlayerCollision(Player player);

    default boolean onProjectileHit(double px, double py, double damage) {
        return false;
    }

    default boolean shouldRemove() {
        return false;
    }
}
