package Battle.core;
import Battle.domain.*;

public class SimpleAi {
    public static Technique choose(Fighter me, Fighter opp, int momentum) {
        Technique best = BaseMoves.MOVES[0];
        double bestScore = -1;

        for (Technique t : BaseMoves.MOVES) {
            int cooldown = me.cd.getOrDefault(t, 0);
            if (cooldown > 0) continue;
            if (me.status == Status.ROOTED && t.tag == Tag.CHARGE) continue;

            double aff = AffinityChart.mult(t.affinity, opp.aura);
            // Heuristic: value power by affinity, prefer higher priority,
            // like momentum-shifting moves a bit, guard has utility value,
            // and interrupts are great if opponent is charging.
            double score = (t.power * aff)
                    + (t.priority * 5)
                    + (t.momentumDelta * 8)
                    + (t.tag == Tag.GUARD ? 12 : 0)
                    + ((opp.charging != null && t.tag == Tag.INTERRUPT) ? 40 : 0);

            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }
}
