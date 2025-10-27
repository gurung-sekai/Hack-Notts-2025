package World.cutscene;

/**
 * A single dialogue slide for a cutscene.
 */
public record CutsceneSlide(String speaker,
                            String text,
                            CutscenePortrait portrait,
                            AnimatedBackdrop backdrop) {
    public CutsceneSlide {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }
}
