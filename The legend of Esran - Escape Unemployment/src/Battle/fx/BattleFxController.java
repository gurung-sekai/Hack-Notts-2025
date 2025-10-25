package Battle.fx;

import Battle.core.DamageCalc;
import Battle.core.SimpleAi;
import Battle.domain.*;
import Battle.util.Rng;

public class BattleFxController {
    private final Fighter a, b;
    private int momentum = 0;
    private boolean over = false;

    public BattleFxController(Fighter a, Fighter b) {
        this.a = a; this.b = b;
    }

    public boolean isOver() { return over; }
    public int getMomentum() { return momentum; }
    public String getWinnerName() { return a.isDown() ? b.name : a.name; }

    /** Called when the player clicks a move button (index 0..3). Resolves a full round and returns log text. */
    public String playerTurn(int playerMoveIndex) {
        if (over) return "Battle already over.";
        playerMoveIndex = Math.max(0, Math.min(3, playerMoveIndex));

        Technique tA = BaseMoves.MOVES[playerMoveIndex];
        if (a.cd.getOrDefault(tA, 0) > 0) return "Move on cooldown.";

        Technique tB = SimpleAi.choose(b, a, momentum);

        StringBuilder log = new StringBuilder();

        // Determine order (same logic as console Battle)
        double biasA = (momentum > 0 ? 0.3 : 0.0) + (a.status == Status.SHOCKED ? -0.2 : 0);
        double biasB = (momentum < 0 ? 0.3 : 0.0) + (b.status == Status.SHOCKED ? -0.2 : 0);
        double orderA = tA.priority + a.base.speed/10.0 + biasA + (a.charging!=null?0.5:0);
        double orderB = tB.priority + b.base.speed/10.0 + biasB + (b.charging!=null?0.5:0);
        boolean aFirst = (orderA == orderB) ? (Math.random() < 0.5) : (orderA > orderB);

        Act first  = aFirst ? new Act(a, b, tA) : new Act(b, a, tB);
        Act second = aFirst ? new Act(b, a, tB) : new Act(a, b, tA);

        log.append(resolve(first));
        if (!a.isDown() && !b.isDown()) log.append(resolve(second));

        endOfRound(a, log); endOfRound(b, log);

        if (a.isDown() || b.isDown()) over = true;
        return log.toString();
    }

    private static class Act { Fighter user, target; Technique tech;
        Act(Fighter u, Fighter t, Technique tech){ this.user=u; this.target=t; this.tech=tech; } }

    private String resolve(Act act) {
        Fighter u = act.user, t = act.target; Technique tech = act.tech;
        StringBuilder s = new StringBuilder();

        if (u.charging != null) { tech = u.charging; u.charging = null; s.append(u.name).append(" unleashes ").append(tech.name).append("!\n"); }
        else { s.append(u.name).append(" uses ").append(tech.name).append("!\n"); }

        if (u.status == Status.ROOTED && tech.tag == Tag.CHARGE) {
            s.append(u.name).append(" is rooted and cannot charge!\n");
            return s.toString();
        }

        if (!Rng.roll(tech.accuracy)) {
            s.append("It misses!\n");
            setCooldown(u, tech);
            return s.toString();
        }

        if (tech.tag == Tag.CHARGE) {
            u.charging = tech;
            s.append(u.name).append(" gathers power...\n");
            setCooldown(u, tech);
            return s.toString();
        }

        if (tech.tag == Tag.INTERRUPT && t.charging != null) {
            s.append("Interrupt! ").append(t.name).append("'s charge is canceled.\n");
            t.charging = null;
            momentum += (u == a ? 1 : -1);
        }

        if (tech.isUtility()) {
            if (tech.onHit != null) tech.onHit.accept(u, t);
        } else {
            var res = DamageCalc.compute(u, t, tech, (u == a ? momentum : -momentum));
            int dmg = res.damage;
            if (tech.tag == Tag.GUARD) dmg = (int)Math.ceil(dmg * 0.5); // reserved for future counter scenarios
            t.hp = Math.max(0, t.hp - dmg);
            if (res.crit) s.append("Critical strike!\n");
            if (res.mult > 1.0) s.append("It resonates strongly!\n");
            else if (res.mult < 1.0) s.append("Resisted.\n");
            momentum += (u == a ? tech.momentumDelta : -tech.momentumDelta);
            momentum = Math.max(-3, Math.min(3, momentum));
            if (tech.onHit != null) tech.onHit.accept(u, t);
        }

        setCooldown(u, tech);
        return s.toString();
    }

    private void endOfRound(Fighter f, StringBuilder s) {
        if (f.status == Status.IGNITED) {
            int burn = Math.max(1, f.base.hp / 20);
            f.hp = Math.max(0, f.hp - burn);
            s.append(f.name).append(" takes ").append(burn).append(" burn damage.\n");
        }
        if (f.status == Status.SHOCKED && Rng.roll(25)) {
            f.status = Status.OK;
            s.append(f.name).append(" recovers from shock.\n");
        }
        for (Technique t : f.cd.keySet().toArray(new Technique[0])) {
            int left = f.cd.get(t);
            if (left > 0) f.cd.put(t, left - 1);
        }
    }

    private void setCooldown(Fighter f, Technique t) {
        if (t.cooldown > 0) f.cd.put(t, t.cooldown);
    }
}
