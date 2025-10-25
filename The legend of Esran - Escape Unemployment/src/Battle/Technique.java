package Battle;

import java.util.function.BiConsumer;

public class Technique {
    public final String name;   //damage aspect or null for pure utility
    public final Affinity affinity; // 0 for utility
    public final int power; // 1-100
    public final int priority;  // turn ordering base
    public final int momentumDelta; //how much momentum moves on hit ( + towards player)
    public final int cooldown;  //turns to refresh
    public final Tag tag;   //Charge, Interrupt, guard, break, none

    // effect hook which applies statuses, heals, or more
    // public final BiConsumer <Fighter, Fighter> onHit;

    public Technique(String name, Affinity affinity, int power, String name1, Affinity affinity1, int power1, int priority, int momentumDelta, int cooldown, Tag tag) {

        this.name = name1;
        this.affinity = affinity1;
        this.power = power1;
        this.priority = priority;
        this.momentumDelta = momentumDelta;
        this.cooldown = cooldown;
        this.tag = tag;
        // this.onHit = onHit;
    }



}
