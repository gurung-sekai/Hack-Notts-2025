package World.trap;

import java.awt.AlphaComposite;
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
    protected int integrity = 3;
    protected boolean destroyed = false;

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

    public void setIntegrity(int hits) {
        if (hits > 0) {
            this.integrity = hits;
        }
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
        float alpha = active ? 1f : 0.35f;
        java.awt.Composite oldComposite = null;
        if (alpha < 1f) {
            oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        }
        if (animation != null) {
            BufferedImage frame = animation.getFrame();
            if (frame != null) {
                g.drawImage(frame, bounds.x, bounds.y, width, height, null);
                if (oldComposite != null) {
                    g.setComposite(oldComposite);
                }
                return;
            }
        }
        g.fillRect(bounds.x, bounds.y, width, height);
        if (oldComposite != null) {
            g.setComposite(oldComposite);
        }
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
            player.takeDamage(damage, damageSource());
        }
        player.grantInvulnerability(Math.max(0.1, contactCooldown));
        cooldownTimer = contactCooldown;
    }

    @Override
    public boolean onProjectileHit(double px, double py, double projectileDamage) {
        if (!active) {
            return false;
        }
        integrity -= Math.max(1, (int) Math.round(Math.max(0.0, projectileDamage)));
        if (integrity <= 0) {
            deactivate();
        } else {
            cooldownTimer = Math.min(cooldownTimer, contactCooldown * 0.5);
        }
        return true;
    }

    @Override
    public boolean shouldRemove() {
        return destroyed;
    }

    protected String damageSource() {
        return getClass().getSimpleName();
    }

    protected void deactivate() {
        active = false;
        destroyed = true;
        cooldownTimer = 0.0;
    }
}
