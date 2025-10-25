package Battle.domain;

import java.util.HashMap;
import java.util.Map;

public class Fighter {
    public final String name;
    public final Affinity aura; //aspect (Ember/Verdent/Stone/Storm)
    public final Stats base;

    public int hp;
    public Status status = Status.OK;

    // cooldown tracker
    public final Map<Technique, Integer> cd = new HashMap<>();
    // charging technique (if any)
    public Technique charging = null;

    public Fighter(String name, Affinity aura, Stats stats) {
        this.name = name;
        this.aura = aura;
        this.base = stats.copy();
        this.hp = stats.hp;

    }

    public boolean isDown() { return hp <= 0;}
}