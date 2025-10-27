package World.cutscene;

import java.util.List;

/**
 * Prebuilt narrative sequences.
 */
public final class CutsceneLibrary {
    private CutsceneLibrary() { }

    public static CutsceneScript goldenKnightMonologue() {
        return new CutsceneScript(List.of(
                new CutsceneSlide("Golden Knight",
                        "Ah, the would-be hero finally crosses the threshold. The princess glitters beneath my eternal guard now.",
                        CutscenePortrait.GOLDEN_KNIGHT,
                        CutsceneBackgrounds.goldenThrone()),
                new CutsceneSlide("Princess",
                        "HELP ME! His chains are binding me to the dungeon stones!",
                        CutscenePortrait.PRINCESS,
                        CutsceneBackgrounds.dungeonCaptivity()),
                new CutsceneSlide("Hero",
                        "Your tyranny ends tonight, Golden Knight. Release her and face justice!",
                        CutscenePortrait.HERO,
                        CutsceneBackgrounds.heroResolve()),
                new CutsceneSlide("Golden Knight",
                        "Justice? Ha! Only power decides the realm. Witness the radiance of my gilded armor!",
                        CutscenePortrait.GOLDEN_KNIGHT,
                        CutsceneBackgrounds.goldenThrone()),
                new CutsceneSlide("Hero",
                        "Hold on, princess! I'll break these chains and end his reign.",
                        CutscenePortrait.HERO,
                        CutsceneBackgrounds.heroResolve())
        ));
    }

    public static CutsceneScript queenRescued() {
        return new CutsceneScript(List.of(
                new CutsceneSlide("Princess",
                        "You shattered his arrogance and freed me!",
                        CutscenePortrait.PRINCESS,
                        CutsceneBackgrounds.victoryCelebration()),
                new CutsceneSlide("Hero",
                        "No throne can cage hope. Come, let's return to the daylight together.",
                        CutscenePortrait.HERO,
                        CutsceneBackgrounds.heroResolve()),
                new CutsceneSlide("Golden Knight",
                        "This is not the end...", // fading words
                        CutscenePortrait.GOLDEN_KNIGHT,
                        CutsceneBackgrounds.goldenThrone())
        ));
    }
}
