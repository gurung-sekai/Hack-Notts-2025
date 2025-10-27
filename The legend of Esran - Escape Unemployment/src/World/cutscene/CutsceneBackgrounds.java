package World.cutscene;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * Factory for animated backdrops used by cutscenes.
 */
public final class CutsceneBackgrounds {
    private CutsceneBackgrounds() {
    }

    public static AnimatedBackdrop emberSwirl() {
        return (g, w, h, tick) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float phase = (tick % 360) / 360f;
            Color base = blend(new Color(32, 12, 8), new Color(180, 70, 30), phase);
            g.setPaint(new GradientPaint(0, 0, base, w, h, base.brighter()));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.25f));
            for (int i = 0; i < 18; i++) {
                double angle = (tick * 0.02 + i * Math.PI * 2 / 18);
                int radius = (int) (Math.min(w, h) * 0.18);
                int cx = (int) (w / 2 + Math.cos(angle) * w * 0.25);
                int cy = (int) (h / 2 + Math.sin(angle * 1.5) * h * 0.25);
                g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius,
                        new float[]{0f, 0.8f, 1f},
                        new Color[]{new Color(255, 160, 60, 160), new Color(255, 210, 120, 80), new Color(0, 0, 0, 0)}));
                g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            }
            g.setComposite(AlphaComposite.SrcOver);
        };
    }

    public static AnimatedBackdrop prisonAura() {
        return (g, w, h, tick) -> {
            g.setColor(new Color(16, 22, 42));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.65f));
            for (int i = 0; i < 6; i++) {
                int offset = (int) ((tick + i * 40) % h);
                g.setColor(new Color(120, 120, 220, 90));
                g.fillRect(i * w / 6, (offset + h / 2) % h, w / 6 + 12, h / 3);
            }
            g.setComposite(AlphaComposite.SrcOver.derive(0.3f));
            g.setColor(new Color(255, 200, 230));
            g.fillOval(w / 4, h / 5, w / 2, h / 2);
            g.setComposite(AlphaComposite.SrcOver);
        };
    }

    public static AnimatedBackdrop royalCelebration() {
        return (g, w, h, tick) -> {
            g.setColor(new Color(16, 24, 44));
            g.fillRect(0, 0, w, h);
            Random rnd = new Random(42);
            for (int i = 0; i < 80; i++) {
                rnd.setSeed(i * 13 + tick);
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                int size = 2 + rnd.nextInt(4);
                g.setColor(new Color(220, 220, 255, 120 + rnd.nextInt(120)));
                g.fillOval(x, (y + (int) (Math.sin((tick + i) * 0.05) * 15) + h) % h, size, size);
            }
            g.setColor(new Color(255, 240, 170, 160));
            g.fillRoundRect(w / 5, h / 3, w * 3 / 5, h / 3, 30, 30);
        };
    }

    public static AnimatedBackdrop shopGlow() {
        return (g, w, h, tick) -> {
            float phase = (float) Math.sin(tick * 0.05);
            Color outer = blend(new Color(12, 26, 38), new Color(24, 48, 64), (phase + 1) / 2f);
            g.setColor(outer);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
            for (int i = 0; i < 5; i++) {
                int radius = w / 6 + i * 40;
                g.setColor(new Color(255, 220, 140, 120 - i * 18));
                g.fillOval(w / 2 - radius, h / 2 - radius, radius * 2, radius * 2);
            }
            g.setComposite(AlphaComposite.SrcOver);
        };
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() * (1 - t) + b.getRed() * t);
        int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bC = (int) (a.getBlue() * (1 - t) + b.getBlue() * t);
        return new Color(r, g, bC);
    }
}
