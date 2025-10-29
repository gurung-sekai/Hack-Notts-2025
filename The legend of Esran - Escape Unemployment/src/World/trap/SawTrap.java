package World.trap;

public final class SawTrap extends BaseTrap {
    public SawTrap(double x, double y, Animation animation) {
        super(x, y, animation);
        setDamage(1);
        setContactCooldown(0.35);
    }
}
