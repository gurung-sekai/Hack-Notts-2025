package Battle.domain;

public class Stats {

    private int hp, power, guard, speed;

    public Stats(int hp, int power, int guard, int speed) {
        this.hp = hp;
        this.power = power;
        this.guard = guard;
        this.speed = speed;
    }

    public Stats(){
        hp = 100;
        power = 1;
        guard = 1;
        speed = 1;
    }

    public Stats copy() {return new Stats(hp, power, guard, speed);}

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp;}

    public int getPower() { return power; }
    public void setPower(int power) {this.power = power; }

    public int getGuard() { return guard; }
    public void setGuard(int guard) { this.guard = guard; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

}