package Battle.core;
import Battle.domain.*;
import Battle.util.Rng;

public class DamageCalc {
    public static class Result {
        public final int damage;
        public final double mult;
        public final boolean crit;
        public Result(int damage, double mult, boolean crit) {
            this.damage=damage;this.mult=mult;this.crit=crit;
        }
    }

    //  momentum: attacker’s perspective (−3..+3). We cap to keep numbers sane.
    public static Result compute (Fighter atk, Fighter def, Technique t, int momentum) {
        if(t.isUtility())return new Result(0,1.0,false);
        int A=atk.base.power, G=def.base.guard;

        //small status tweaks
        if(def.status== Status.ROOTED)G=(int)(G*0.9);
        if(def.status== Status.IGNITED && t.affinity== Affinity.EMBER)A=(int)(A*1.2);

        boolean crit= Rng.d01()<0.10; // 10% crit
        double critMod=crit?1.4:1.0;
        double aff= AffinityChart.mult(t.affinity,def.aura);
        double mom=1.0+Math.max(-3,Math.min(3,momentum))*0.05;
        double rand=0.90+ Rng.d01()*0.20; //0.90..1.10

        //smooth division by adding a constant to guard
        double base = t.power*(A/Math.max(1.0,(G+10.0)));
        int dmg = (int)Math.max(1,Math.floor(base*aff*mom*critMod*rand));
        return new Result(dmg,aff,crit);
    }
}