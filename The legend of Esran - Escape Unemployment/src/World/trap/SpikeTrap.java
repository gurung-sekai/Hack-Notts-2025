package World.trap;

public final class SpikeTrap extends BaseTrap {
    private final double cycleSeconds;
    private final double activeFraction;
    private double elapsed = 0.0;

    public SpikeTrap(double x, double y, Animation animation, double cycleSeconds, double activeFraction) {
        super(x, y, animation);
        this.cycleSeconds = Math.max(0.2, Double.isFinite(cycleSeconds) ? cycleSeconds : 2.0);
        this.activeFraction = Math.max(0.1, Math.min(0.9, activeFraction));
        setDamage(2);
        setContactCooldown(0.55);
    }

    @Override
    public void update(double dt) {
        super.update(dt);
        if (!Double.isFinite(dt) || dt <= 0) {
            return;
        }
        elapsed += dt;
        double cycle = cycleSeconds;
        if (cycle <= 0) {
            cycle = 1.0;
        }
        double phase = elapsed % cycle;
        double threshold = cycle * activeFraction;
        active = phase >= threshold;
    }
}
