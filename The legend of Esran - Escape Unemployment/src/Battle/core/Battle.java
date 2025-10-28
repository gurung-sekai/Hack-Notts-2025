package Battle.core;
import Battle.domain.*;
import Battle.util.Rng;

import java.util.ArrayList;
import java.util.Scanner;

public class Battle {
    private final Fighter a, b;        // <-- add types
    // Momentum tug-of-war: -3..+3 (positive favors a, negative favors b)
    private int momentum = 0;

    private static int clampMomentum(int value) {
        return Math.max(-3, Math.min(3, value));
    }

    public Battle(Fighter a, Fighter b) { this.a = a; this.b = b; }

    public void run(Scanner in) {
        System.out.println("A duel begins! " + a.name + " vs " + b.name);
        while (!a.isDown() && !b.isDown()) {
            Technique tA = chooseHuman(a, in);
            Technique tB = SimpleAi.choose(b, a, momentum);

            // Order = priority + speed + momentum bias (+ small shock penalty)
            double biasA = (momentum > 0 ? 0.3 : 0.0) + (a.status == Status.SHOCKED ? -0.2 : 0);
            double biasB = (momentum < 0 ? 0.3 : 0.0) + (b.status == Status.SHOCKED ? -0.2 : 0);
            double orderA = tA.priority + a.base.speed / 10.0 + biasA + (a.charging != null ? 0.5 : 0);
            double orderB = tB.priority + b.base.speed / 10.0 + biasB + (b.charging != null ? 0.5 : 0);
            boolean aFirst = (orderA == orderB) ? (Math.random() < 0.5) : (orderA > orderB);

            Act first  = aFirst ? new Act(a, b, tA) : new Act(b, a, tB);
            Act second = aFirst ? new Act(b, a, tB) : new Act(a, b, tA);

            resolve(first);
            if (!a.isDown() && !b.isDown()) resolve(second);

            endOfRound(a); endOfRound(b);
            System.out.printf("==> %s [%d/%d]  ||  %s [%d/%d]%n",
                    a.name, a.hp, a.base.hp, b.name, b.hp, b.base.hp);
        }
        System.out.println((a.isDown() ? a.name : b.name) + " falls!");
        System.out.println("Winner: " + (a.isDown() ? b.name : a.name));
    }

    private static class Act {
        Fighter user, target; Technique tech;
        Act(Fighter u, Fighter t, Technique tech) { this.user = u; this.target = t; this.tech = tech; }
    }

    private void resolve(Act act) {
        Fighter u = act.user, t = act.target; Technique tech = act.tech;

        if (u.charging != null) {
            tech = u.charging; u.charging = null;
            System.out.println(u.name + " unleashes " + tech.name + "!");
        } else {
            System.out.println(u.name + " uses " + tech.name + "!");
        }

        if (u.status == Status.ROOTED && tech.tag == Tag.CHARGE) {
            System.out.println(u.name + " is rooted and cannot charge!");
            return;
        }

        if (!Rng.roll(tech.accuracy)) { System.out.println("It misses!"); setCooldown(u, tech); return; }

        if (tech.tag == Tag.CHARGE) {
            u.charging = tech;
            System.out.println(u.name + " gathers power...");
            setCooldown(u, tech);
            return;
        }

        if (tech.tag == Tag.INTERRUPT && t.charging != null) {
            System.out.println("Interrupt! " + t.name + "'s charge is canceled.");
            t.charging = null;
            momentum = clampMomentum(momentum + (u == a ? 1 : -1));
        }

        if (tech.isUtility()) {
            if (tech.onHit != null) tech.onHit.accept(u, t);
        } else {
            var res = DamageCalc.compute(u, t, tech, (u == a ? momentum : -momentum));
            int dmg = res.damage;

            // If you later add a 'counter-guard' scenario, halve here. For now GUARD is a utility move.
            if (tech.tag == Tag.GUARD) dmg = (int) Math.ceil(dmg * 0.5);

            t.hp = Math.max(0, t.hp - dmg);
            if (res.crit) System.out.println("Critical strike!");
            if (res.mult > 1.0) System.out.println("It resonates strongly!");
            else if (res.mult < 1.0) System.out.println("Resisted.");

            momentum = clampMomentum(momentum + (u == a ? tech.momentumDelta : -tech.momentumDelta));

            if (tech.onHit != null) tech.onHit.accept(u, t);
        }

        setCooldown(u, tech);
    }

    private void endOfRound(Fighter f) {
        if (f.status == Status.IGNITED) {
            int burn = Math.max(1, f.base.hp / 20);
            f.hp = Math.max(0, f.hp - burn);
            System.out.println(f.name + " takes " + burn + " burn damage.");
        }
        if (f.status == Status.SHOCKED && Rng.roll(25)) {
            f.status = Status.OK;
            System.out.println(f.name + " recovers from shock.");
        }
        var keys = new ArrayList<>(f.cd.keySet());
        for (Technique t : keys) {
            int left = f.cd.get(t);
            if (left > 0) f.cd.put(t, left - 1);
        }
    }

    private void setCooldown(Fighter f, Technique t) {
        if (t.cooldown > 0) f.cd.put(t, t.cooldown);
    }

    private Technique chooseHuman(Fighter f, Scanner in) {
        if (f.status == Status.SHOCKED && Rng.roll(25)) {
            System.out.println(f.name + " is shocked and fumbles the turn!");
            return new Technique("Fumble", null, 0, 100, 0, 0, 0, Tag.NONE, null);
        }

        System.out.printf("%n[MOMENTUM: %s%d]%n", (momentum > 0 ? "+" : momentum < 0 ? "" : ""), momentum);
        System.out.println(f.name + " HP " + f.hp + "/" + f.base.hp + "  Status: " + f.status + (f.charging != null ? " (charging)" : ""));
        Technique[] list = BaseMoves.MOVES;
        for (int i = 0; i < list.length; i++) {
            Technique t = list[i];
            int cd = f.cd.getOrDefault(t, 0);
            System.out.printf("%d) %s  {%s pwr:%d acc:%d pri:%d cd:%d tag:%s}%s%n",
                    (i + 1), t.name, t.affinity, t.power, t.accuracy, t.priority, t.cooldown, t.tag,
                    (cd > 0 ? " [WAIT:" + cd + "]" : ""));
        }
        System.out.print("Choose (1-4): ");
        int idx = 0; try { idx = Integer.parseInt(in.nextLine()) - 1; } catch (Exception ignored) {}
        idx = Math.max(0, Math.min(3, idx));
        Technique chosen = list[idx];
        if (f.cd.getOrDefault(chosen, 0) > 0) {
            System.out.println("Still recharging! You wait.");
            return new Technique("Wait", null, 0, 100, 0, 0, 0, Tag.NONE, null);
        }
        return chosen;
    }
}
