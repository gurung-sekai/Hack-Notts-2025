package gfx;

/**
 * Creates sprites using your exact folder/filename layout.
 * Adjust only if your resources root is different.
 */
public final class SpriteFactory {
    private SpriteFactory() {}

    // ---------- Wizard (flat folder) ----------
    public static AnimatedSprite wizardMale(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Wizard/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "wizzard_m_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "wizzard_m_run_anim_f");
        s.addFromPrefix(AnimatedSprite.State.HIT,  base + "wizzard_m_hit_anim_f");
        s.setFps(8);
        return s;
    }
    public static AnimatedSprite wizardFemale(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Wizard/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "wizzard_f_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "wizzard_f_run_anim_f");
        s.addFromPrefix(AnimatedSprite.State.HIT,  base + "wizzard_f_hit_anim_f");
        s.setFps(8);
        return s;
    }

    // ---------- Knight (subfolders Idle/Run/Hit) ----------
    public static AnimatedSprite knightMale(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Knight/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "Idle/knight_m_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "Run/knight_m_run_anim_f");
        s.addFromPrefix(AnimatedSprite.State.HIT,  base + "Hit/knight_m_hit_anim_f");
        s.setFps(8);
        return s;
    }
    public static AnimatedSprite knightFemale(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Knight/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "Idle/knight_f_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "Run/knight_f_run_anim_f");
        s.addFromPrefix(AnimatedSprite.State.HIT,  base + "Hit/knight_f_hit_anim_f");
        s.setFps(8);
        return s;
    }

    // ---------- Enemies (flat folders) ----------
    public static AnimatedSprite imp(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Imp/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "imp_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "imp_run_anim_f");
        s.setFps(10);
        return s;
    }

    public static AnimatedSprite ogre(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Ogre/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "ogre_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "ogre_run_anim_f");
        s.setFps(10);
        return s;
    }

    public static AnimatedSprite skeleton(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Skeleton/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "skelet_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "skelet_run_anim_f");
        s.setFps(10);
        return s;
    }

    public static AnimatedSprite pumpkinDude(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Pumpkin/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "pumpkin_dude_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "pumpkin_dude_run_anim_f");
        s.setFps(10);
        return s;
    }

    public static AnimatedSprite bigZombie(int tile) {
        AnimatedSprite s = new AnimatedSprite(tile, tile);
        String base = "/resources/sprites/Bigzombie/";
        s.addFromPrefix(AnimatedSprite.State.IDLE, base + "big_zombie_idle_anim_f");
        s.addFromPrefix(AnimatedSprite.State.RUN,  base + "big_zombie_run_anim_f");
        s.setFps(10);
        return s;
    }
}
