package Battle.core;
import Battle.domain.*;
import Battle.util.Rng;

public class SimpleAi {
    private static volatile boolean strategicMode = true;

    public static void setStrategicMode(boolean enabled) {
        strategicMode = enabled;
    }

    public static Technique choose(Fighter me, Fighter opp, int momentum) {
        Technique random = pickRandomUsable(me);
        if (!strategicMode) {
            return random != null ? random : BaseMoves.MOVES[0];
        }

        if (random != null && Rng.d01() < 0.12) {
            return random;
        }

        Technique best = random;
        double bestScore = Double.NEGATIVE_INFINITY;

        Technique[] moves = BaseMoves.MOVES;
        Technique lastUsed = me.lastUsed;
        double myHpRatio = me.hp / (double) Math.max(1, me.base.hp);
        double oppHpRatio = opp.hp / (double) Math.max(1, opp.base.hp);
        boolean oppCharging = opp.charging != null;
        Status oppStatus = opp.status;
        Status myStatus = me.status;

        double guardHpBonus = myHpRatio < 0.45 ? 18.0 : (myHpRatio > 0.85 ? -10.0 : 0.0);
        double guardMomentumBonus = momentum < -1 ? 12.0 : 0.0; // combines original +7 and +5 bonuses
        boolean oppRooted = oppStatus == Status.ROOTED;
        boolean oppIgnited = oppStatus == Status.IGNITED;
        boolean oppShocked = oppStatus == Status.SHOCKED;

        for (Technique move : moves) {
            if (!isUsable(me, myStatus, move)) {
                continue;
            }

            double affinity = AffinityChart.mult(move.affinity, opp.aura);
            double score = 0.0;
            score += move.power * affinity;
            score += move.priority * 4.5;
            score += move.momentumDelta * 7.0;
            if (move.tag == Tag.GUARD) {
                score += 9.0 + guardHpBonus + guardMomentumBonus;
            }
            if (oppCharging && move.tag == Tag.INTERRUPT) {
                score += 40.0;
            }

            if (move == BaseMoves.FLAME_LASH) {
                if (oppRooted) {
                    score += 6.0;
                }
                if (oppIgnited) {
                    score += 8.0;
                }
                if (oppHpRatio < 0.35) {
                    score += 4.0;
                }
                if (oppStatus == Status.IGNITED) {
                    score -= 6.0; // penalty for redundant ignite
                }
            }

            if (move == BaseMoves.THORN_BIND) {
                if (!oppRooted) {
                    score += (momentum >= 0 ? 10.0 : 6.0);
                } else {
                    score -= 5.0;
                }
                if (oppCharging) {
                    score += 9.0;
                }
            }

            if (move == BaseMoves.DISRUPT_BOLT) {
                if (!oppShocked) {
                    score += 5.0;
                }
                if (momentum > 1) {
                    score += 3.0;
                }
                if (oppHpRatio > myHpRatio + 0.2) {
                    score += 4.0;
                }
            }

            if (lastUsed == move) {
                score -= 6.5;
            }
            if (lastUsed == BaseMoves.THORN_BIND && move == BaseMoves.FLAME_LASH && oppRooted) {
                score += 10.0;
            }
            if (lastUsed == BaseMoves.FLAME_LASH && move == BaseMoves.BRACE && myHpRatio < 0.5) {
                score += 6.0;
            }

            // Inject a small amount of variance so the boss occasionally makes
            // suboptimal choices and fights feel less deterministic.
            score += (Rng.d01() - 0.5) * 12.0;

            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }

        return best != null ? best : BaseMoves.MOVES[0];
    }

    private static Technique pickRandomUsable(Fighter me) {
        Technique[] moves = BaseMoves.MOVES;
        Technique fallback = null;
        for (int attempts = 0; attempts < 8; attempts++) {
            Technique candidate = moves[Rng.nextInt(moves.length)];
            if (isUsable(me, me.status, candidate)) {
                return candidate;
            }
            if (fallback == null && me.cd.getOrDefault(candidate, 0) == 0) {
                fallback = candidate;
            }
        }
        if (fallback != null && isUsable(me, me.status, fallback)) {
            return fallback;
        }
        for (Technique move : moves) {
            if (isUsable(me, me.status, move)) {
                return move;
            }
        }
        return null;
    }

    private static boolean isUsable(Fighter me, Status status, Technique move) {
        if (move == null) {
            return false;
        }
        if (me.cd.getOrDefault(move, 0) > 0) {
            return false;
        }
        return !(status == Status.ROOTED && move.tag == Tag.CHARGE);
    }
}
