package World.trap;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.io.Serializable;

public abstract class BaseTrap implements Trap, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    protected double x;
    protected double y;
    protected int width;
    protected int height;
    protected boolean active = true;
    protected int damage = 1;
    protected double contactCooldown = 0.5;
    protected double cooldownTimer = 0.0;

    private final Animation animation;
    private final Rectangle bounds = new Rectangle();

    protected BaseTrap(double x, double y, Animation animation) {
        this.x = x;
        this.y = y;
        this.animation = animation;
        this.width = animation == null ? 32 : Math.max(1, animation.getWidth());
        this.height = animation == null ? 32 : Math.max(1, animation.getHeight());
        updateBounds();
    }

    protected Animation animation() {
        return animation;
    }

    public void setDamage(int damage) {
        this.damage = Math.max(0, damage);
    }

    public void setContactCooldown(double seconds) {
        if (Double.isFinite(seconds) && seconds >= 0) {
            this.contactCooldown = seconds;
        }
    }

    public void setDimensions(int width, int height) {
        if (width > 0) {
            this.width = width;
        }
        if (height > 0) {
            this.height = height;
        }
        updateBounds();
    }

    protected void updateBounds() {
        bounds.setBounds((int) Math.round(x), (int) Math.round(y), width, height);
    }

    @Override
    public void update(double dt) {
        if (animation != null) {
            animation.update(dt);
        }
        if (cooldownTimer > 0) {
            cooldownTimer = Math.max(0.0, cooldownTimer - Math.max(0.0, dt));
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (g == null) {
            return;
        }
        if (animation != null) {
            BufferedImage frame = animation.getFrame();
            if (frame != null) {
                g.drawImage(frame, bounds.x, bounds.y, width, height, null);
                return;
            }
        }
        g.fillRect(bounds.x, bounds.y, width, height);
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(bounds);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void onPlayerCollision(Player player) {
        if (!active || player == null) {
            return;
        }
        if (cooldownTimer > 0) {
            return;
        }
        if (player.isInvulnerable()) {
            cooldownTimer = Math.max(cooldownTimer, contactCooldown * 0.5);
            return;
        }
        if (damage > 0) {
            player.takeDamage(damage);
        }
        player.grantInvulnerability(Math.max(0.1, contactCooldown));
        cooldownTimer = contactCooldown;
    }
}
