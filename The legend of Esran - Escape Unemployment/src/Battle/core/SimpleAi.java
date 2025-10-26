package Battle.core;
import Battle.domain.*;
import Battle.util.Rng;

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

            double hpRatio = me.hp / (double) Math.max(1, me.base.hp);
            if (t.tag == Tag.GUARD) {
                if (hpRatio < 0.45) score += 18;
                else if (hpRatio > 0.85) score -= 6;
            }
            if (opp.status == Status.IGNITED && t == BaseMoves.FLAME_LASH) {
                score -= 6;
            }
            if (opp.status == Status.ROOTED && t == BaseMoves.THORN_BIND) {
                score -= 5;
            }
            if (momentum < -1 && t.tag == Tag.GUARD) {
                score += 6;
            }

            // Inject a small amount of variance so the boss occasionally
            // makes suboptimal choices and fights feel less deterministic.
            score += (Rng.d01() - 0.5) * 8.0;

            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }
}
