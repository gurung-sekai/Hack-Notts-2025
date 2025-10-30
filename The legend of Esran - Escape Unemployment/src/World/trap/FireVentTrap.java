package World.trap;

public final class FireVentTrap extends BaseTrap {
    private final double burstEvery;
    private final double burstDuration;
    private double elapsed = 0.0;

    public FireVentTrap(double x, double y, Animation animation, double burstEvery, double burstDuration) {
        super(x, y, animation);
        this.burstEvery = Math.max(0.5, Double.isFinite(burstEvery) ? burstEvery : 3.0);
        this.burstDuration = Math.max(0.1, Math.min(this.burstEvery, Double.isFinite(burstDuration) ? burstDuration : 1.0));
        setDamage(3);
        setContactCooldown(0.6);
        setIntegrity(3);
    }

    @Override
    public void update(double dt) {
        super.update(dt);
        if (!Double.isFinite(dt) || dt <= 0) {
            return;
        }
        elapsed += dt;
        double window = elapsed % burstEvery;
        boolean burning = window <= burstDuration;
        if (burning && !active && animation() != null) {
            animation().reset();
        }
        active = burning;
    }

    @Override
    protected String damageSource() {
        return "Fire vent";
    }
}
