package Battle.domain;

import java.util.HashMap;
import java.util.Map;

public class Fighter {
    public final String name;
    public final Affinity aura; //aspect (Ember/Verdent/Stone/Storm)
    public final Stats base;

    public int hp;
    public Status status = Status.OK;

    /**
     * Multipliers that allow specific fighters (the hero, individual bosses)
     * to lean more offensive/defensive without introducing new stat fields.
     */
    public final double offenseMod;
    public final double defenseMod;

    // cooldown tracker
    public final Map<Technique, Integer> cd = new HashMap<>();
    // charging technique (if any)
    public Technique charging = null;
    // most recent technique used (for AI variety)
    public Technique lastUsed = null;

    public Fighter(String name, Affinity aura, Stats stats) {
        this(name, aura, stats, 1.0, 1.0);
    }

    public Fighter(String name, Affinity aura, Stats stats, double offenseMod, double defenseMod) {
        this.name = name;
        this.aura = aura;
        this.base = stats.copy();
        this.hp = stats.hp;
        this.offenseMod = offenseMod;
        this.defenseMod = defenseMod;
    }

    public boolean isDown() { return hp <= 0;}
}