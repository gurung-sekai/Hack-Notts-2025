package World.trap;

import java.awt.Rectangle;

/**
 * Minimal player contract that traps can interact with for collision and damage.
 */
public interface Player {
    Rectangle getBounds();
    boolean isInvulnerable();
    void grantInvulnerability(double seconds);
    void takeDamage(int damage);

    default void update(double dt) {
        // Optional hook for implementations that need to decay timers.
    }
}
