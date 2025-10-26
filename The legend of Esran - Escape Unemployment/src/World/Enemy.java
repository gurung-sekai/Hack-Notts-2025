package World;

import Battle.domain.Stats;

import java.util.Objects;

public class Enemy {

    private double x, y;
    private double speed;
    private final Stats stats;

    public Enemy (double x, double y, double speed, Stats stats){
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.stats = Objects.requireNonNull(stats, "stats must not be null");
    }

    public Stats getStats() { return stats; }

    public double getX() { return x; }
    public double getY() { return y; }

    public double getSpeed() { return speed; }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }



}