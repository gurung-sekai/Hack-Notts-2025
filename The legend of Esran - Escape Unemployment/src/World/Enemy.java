package World;

import Battle.domain.Stats;

public class Enemy {

    private double x, y;
    private double speed;
    private Stats stats;

    public Enemy (double x, double y, double speed, Stats stats){
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.stats = stats;
    }

    public Stats getStats() { return stats; }

    public double getX() { return x; }
    public double getY() { return y; }



}