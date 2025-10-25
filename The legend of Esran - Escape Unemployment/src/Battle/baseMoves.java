package battle;

public class BaseMoves {
    // 4 techniques for all of the characters
    public static final Technique FLAME_LASH = new Technique (
            "Flame Lash", Affinity.EMBER, 36, 100, 1, 1, 1, Tag.NONE,
            (u,t)->{if (Rng.roll(30) && t.status==Status.OK){ t.status=Status.IGNITED; System.out.println(t.name+" is ignited!"); }});
    )

    public static final Technique THORN_BIND = new Technique(
            "Thorn Bind", Affinity.VERDANT, 22, 95, 0, 1, 2, Tag.NONE,
            (u,t)->{ if (Rng.roll(50)){ t.status=Status.ROOTED; System.out.println(t.name+" is rooted in place!"); }});

    public static final Technique BRACE = new Technique(
            "Brace", null, 0, 100, 2, -1, 1, Tag.GUARD,
            (u,t)->{ u.base.guard += 2; System.out.println(u.name+" braces (+Guard)!"); });

    public static final Technique DISRUPT_BOLT = new Technique(
            "Disrupt Bolt", Affinity.STORM, 18, 100, 3, 1, 2, Tag.INTERRUPT,
            (u,t)->{ if (Rng.roll(35) && t.status==Status.OK){ t.status=Status.SHOCKED; System.out.println(t.name+" is shocked!"); }});

    public static final Technique[] MOVES = new Technique[] {
            FLAME_LASH, THORN_BIND, BRACE, DISRUPT_BOLT
}