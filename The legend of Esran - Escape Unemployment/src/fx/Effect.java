package fx;

import java.awt.*;
import java.awt.image.BufferedImage;

/** A single effect instance (world or screen space). */
public class Effect {
    public enum Space { WORLD, SCREEN }
    public enum Blend { NORMAL, ADDITIVE }

    private final FrameAnim anim;
    private final Space space;
    private final Blend blend;

    private double x, y, vx, vy, scale = 1.0, life = -1;

    public Effect(FrameAnim a, Space s, Blend b, double x, double y) {
        this.anim = a; this.space = s; this.blend = b; this.x = x; this.y = y;
    }

    public Effect vel(double vx, double vy) { this.vx = vx; this.vy = vy; return this; }
    public Effect scale(double s) { this.scale = s; return this; }
    public Effect life(double seconds) { this.life = seconds; return this; }

    public void update(double dt) {
        x += vx * dt; y += vy * dt; anim.update(dt);
        if (life >= 0) life -= dt;
    }

    public boolean dead() { return (life >= 0) ? life <= 0 : anim.finished(); }

    public void render(Graphics2D g, int camX, int camY) {
        BufferedImage f = anim.frame(); if (f == null) return;
        int dx = (int)Math.round((space == Space.WORLD ? x - camX : x));
        int dy = (int)Math.round((space == Space.WORLD ? y - camY : y));
        int w = (int)Math.round(f.getWidth() * scale);
        int h = (int)Math.round(f.getHeight() * scale);

        Composite prev = g.getComposite();
        if (blend == Blend.ADDITIVE) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
        g.drawImage(f, dx, dy, w, h, null);
        g.setComposite(prev);
    }
}
