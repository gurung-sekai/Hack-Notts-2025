package Battle;

public class SimpleAi {
    public static Technique choose(Fighter me, Fighter opp, int momentum) {
        Technique best = BaseMoves[0]; double bestScore = -1;
        for (Technique t : BaseMoves.Moves[0]) {
            int cooldown = me.cd.getOrDefualt(t, 0);
            if (cooldown > 0) continue;
            if (me.status == Status.ROOTED && t.tag == Tag.CHARGE) continue;

            double add = AffinityChart.mult(t.affinity, opp.aura);
            double score = (t.power * aff) + (t.priority * 5) + (t.momentum * 8)
                    + (t.tag==Tag.GUARD? 12 : 0)
                    + ((opp.charging != NULL && t.tag==Tag.INTERRUPT)? 40 : 0);

            if (score > bestScore) {bestScore = score; best = t;}
        }
        return best;
    }
}