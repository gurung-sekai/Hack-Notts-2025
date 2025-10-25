package Battle.domain;

public class Stats {
    public int hp, power, guard, speed;
    public Stats(int hp, int power, int guard, int speed) {
        this.hp = hp; this.power = power;
        this.guard = guard; this.speed = speed;
    }
    public Stats copy() {return new Stats(hp, power, guard, speed);}
}
