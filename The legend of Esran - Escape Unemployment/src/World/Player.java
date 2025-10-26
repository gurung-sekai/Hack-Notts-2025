package World;

import Battle.domain.Stats;

import java.util.Objects;

public class Player {

    private double x, y;
    private double speed;
    private final Stats stats;

    public Player(double x, double y, double speed, Stats stats){
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.stats = Objects.requireNonNull(stats, "stats must not be null");
    }

    public Player(double x, double y){
        this(x, y, 1.0, new Stats());
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public Stats getStats() { return stats; }

    public double getSpeed() { return speed; }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

}