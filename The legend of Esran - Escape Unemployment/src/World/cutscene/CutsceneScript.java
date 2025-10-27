package World.cutscene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable collection of cutscene slides.
 */
public final class CutsceneScript {
    private final List<CutsceneSlide> slides;

    public CutsceneScript(List<CutsceneSlide> slides) {
        if (slides == null || slides.isEmpty()) {
            throw new IllegalArgumentException("slides must not be empty");
        }
        this.slides = Collections.unmodifiableList(new ArrayList<>(slides));
    }

    public List<CutsceneSlide> slides() {
        return slides;
    }
}
