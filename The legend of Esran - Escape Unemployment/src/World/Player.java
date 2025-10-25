package World;

import Battle.Stats;

public class Player {

    private double x, y;
    private double speed;
    private Stats stats;

    public Player(double x, double y, double speed, Stats stats){
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.stats = stats;
    }

    public Player(double x, double y){
        this(x, y, 1.0, new Stats());
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public Stats getStats() { return stats; }

}