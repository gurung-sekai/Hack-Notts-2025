package Battle;

import java.util.function.BiConsumer;
import Battle.domain.Affinity;
import Battle.domain.Tag;
import Battle.domain.Fighter;

public class Technique {
    public final String name;  
    public final Affinity affinity; 
    public final int power; 
    public final int priority;  
    public final int momentumDelta; 
    public final int cooldown;  
    public final Tag tag;

    // effect hook which applies statuses, heals, or more
    public final BiConsumer<Fighter, Fighter> onHit;

    public Technique(String name,
                     Affinity affinity,
                     int power,
                     int priority,
                     int momentumDelta,
                     int cooldown,
                     Tag tag,
                     BiConsumer<Fighter, Fighter> onHit) {
        this.name = name;
        this.affinity = affinity;
        this.power = power;
        this.priority = priority;
        this.momentumDelta = momentumDelta;
        this.cooldown = cooldown;
        this.tag = tag;
        this.onHit = onHit;
    }



}
