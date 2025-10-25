package Battle.domain;

import java.util.function.BiConsumer;

public class Technique {
    public final String name;
    public final Affinity affinity;     // damage aspect; null for pure utility
    public final int power;             // 0 for pure utility moves
    public final int accuracy;          // 1–100
    public final int priority;          // higher acts earlier
    public final int momentumDelta;     // how much momentum shifts on a hit (toward the user)
    public final int cooldown;          // turns before this can be used again
    public final Tag tag;               // NONE, CHARGE, INTERRUPT, GUARD, BREAK

    // Optional effect hook (apply statuses, heal, etc.)
    public final BiConsumer<Fighter, Fighter> onHit;

    public Technique(String name, Affinity affinity, int power,
                     int accuracy, int priority, int momentumDelta,
                     int cooldown, Tag tag,
                     BiConsumer<Fighter, Fighter> onHit) {
        this.name = name;
        this.affinity = affinity;
        this.power = power;
        this.accuracy = accuracy;
        this.priority = priority;
        this.momentumDelta = momentumDelta;
        this.cooldown = cooldown;
        this.tag = tag;
        this.onHit = onHit;
    }

    public boolean isUtility() {
        return power <= 0 && affinity == null;
    }
}
