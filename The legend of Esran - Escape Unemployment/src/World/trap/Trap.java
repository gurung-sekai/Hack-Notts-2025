package World.trap;

import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface Trap {
    void update(double dt);
    void render(Graphics2D g);
    Rectangle getBounds();
    boolean isActive();
    void onPlayerCollision(Player player);
}
