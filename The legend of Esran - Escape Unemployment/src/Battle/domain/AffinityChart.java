package Battle.domain;

public class AffinityChart {
    public static double mult(Affinity atk, Affinity def) {
        if (atk == null || def == null) return 1.0;
        if (atk == Affinity.EMBER   && def == Affinity.VERDANT) return 1.5;
        if (atk == Affinity.VERDANT && def == Affinity.STONE)   return 1.5;
        if (atk == Affinity.STONE   && def == Affinity.EMBER)   return 1.5;
        if (atk == Affinity.STORM) return 1.25; // wildcard disruptor

        if (atk == Affinity.VERDANT && def == Affinity.EMBER)   return 0.75;
        if (atk == Affinity.STONE   && def == Affinity.VERDANT) return 0.75;
        if (atk == Affinity.EMBER   && def == Affinity.STONE)   return 0.75;
        return 1.0;
    }
}