package World.cutscene;

import java.util.List;

import static Battle.scene.BossBattlePanel.BossKind;

/**
 * Prebuilt narrative sequences.
 */
public final class CutsceneLibrary {
    private CutsceneLibrary() { }

    public static CutsceneScript prologue() {
        return new CutsceneScript(List.of(
                new CutsceneSlide("Hero",
                        "The Golden Knight stormed our capital and dragged Queen Aurelia below the earth."
                                + " His lieutenants clutch the sigils that seal her cell.",
                        CutscenePortrait.HERO,
                        CutsceneBackgrounds.heroResolve()),
                new CutsceneSlide("Queen Aurelia",
                        "Hero! His minions boast they'll break my will before dawn. Please... hurry!",
                        CutscenePortrait.PRINCESS,
                        CutsceneBackgrounds.dungeonCaptivity()),
                new CutsceneSlide("Hero",
                        "Hold fast, my queen. I'll carve a path through every lieutenant until the Golden Knight kneels.",
                        CutscenePortrait.HERO,
                        CutsceneBackgrounds.heroResolve())
        ));
    }

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

    public static CutsceneScript bossPrelude(BossKind kind, int chapterIndex) {
        return switch (kind) {
            case GOLLUM -> new CutsceneScript(List.of(
                    new CutsceneSlide("Hero",
                            chapterTitle(chapterIndex) + ": The hoarder's warrens reek of rust and greed."
                                    + " Gollum clutches the first sun-sigil to the queen's cell.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve()),
                    new CutsceneSlide("Gollum",
                            "Shiny queen, shiny chains! Mine to bargain, not yours, surface worm!",
                            CutscenePortrait.GOLLUM,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "No bargain. Release the sigil and crawl back into the dark, or be forced there.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case GRIM -> new CutsceneScript(List.of(
                    new CutsceneSlide("Hero",
                            chapterTitle(chapterIndex) + ": Grim's mausoleum smothers the air."
                                    + " He binds the spirits guiding me to Aurelia.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve()),
                    new CutsceneSlide("Grim",
                            "Hope is a candle I snuff with a whisper. Turn away before your light joins my collection.",
                            CutscenePortrait.GRIM,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Her plea cuts louder than your threats. Try me, warden.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case FIRE_FLINGER -> new CutsceneScript(List.of(
                    new CutsceneSlide("Hero",
                            chapterTitle(chapterIndex) + ": The Fire Flinger feeds the forges that plate the Golden Knight's armor.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve()),
                    new CutsceneSlide("Fire Flinger",
                            "I'll roast you before your blade ever reaches his radiant guard.",
                            CutscenePortrait.FIRE_FLINGER,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Flames can't scorch determination. Douse your embers or face mine.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case GOLD_MECH -> new CutsceneScript(List.of(
                    new CutsceneSlide("Hero",
                            chapterTitle(chapterIndex) + ": The Gold Mech patrols the smeltery halls, grinding every rebel into scrap.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve()),
                    new CutsceneSlide("Gold Mech",
                            "Directive: Preserve the Golden Knight. Intruder will be flattened and refined.",
                            CutscenePortrait.GOLD_MECH,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Your gears will power the lift to the queen's door. Stand aside.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case PURPLE_EMPRESS -> new CutsceneScript(List.of(
                    new CutsceneSlide("Hero",
                            chapterTitle(chapterIndex) + ": The Purple Empress twists minds to worship the false knight.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve()),
                    new CutsceneSlide("Purple Empress",
                            "The queen already kneels in her heart. Kneel with her and I'll spare your memory.",
                            CutscenePortrait.PURPLE_EMPRESS,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Her heart still beats defiance. Yours will, too, until I silence it.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case THE_WELCH -> new CutsceneScript(List.of(
                    new CutsceneSlide("Hero",
                            chapterTitle(chapterIndex) + ": The Welch weaves illusions to hide the final gate to Aurelia.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve()),
                    new CutsceneSlide("The Welch",
                            "A dream of victory so close... Wouldn't you rather rest in it forever?",
                            CutscenePortrait.THE_WELCH,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Dreams can wait. Reality needs saving.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case TOXIC_TREE -> new CutsceneScript(List.of(
                    new CutsceneSlide("Hero",
                            chapterTitle(chapterIndex) + ": Toxic roots choke the queen's final prison."
                                    + " The blighted tree must fall.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve()),
                    new CutsceneSlide("Toxic Tree",
                            "Feed me your hope and I'll bloom a tombstone in her name.",
                            CutscenePortrait.TOXIC_TREE,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Hope grows back. Your rot ends tonight.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case GOLDEN_KNIGHT -> goldenKnightMonologue();
            default -> null;
        };
    }

    public static CutsceneScript bossEpilogue(BossKind kind, int chapterIndex) {
        return switch (kind) {
            case GOLLUM -> new CutsceneScript(List.of(
                    new CutsceneSlide("Gollum",
                            "No! The shiny sigil slips away... the knight promised me a kingdom of scraps...",
                            CutscenePortrait.GOLLUM,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Promises crumble with their liars. One sigil reclaimed, one step closer to Aurelia.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case GRIM -> new CutsceneScript(List.of(
                    new CutsceneSlide("Grim",
                            "Your flame... refuses to die... May it guide her back...",
                            CutscenePortrait.GRIM,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Rest, guardian. I'll use your lantern to light the queen's path home.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case FIRE_FLINGER -> new CutsceneScript(List.of(
                    new CutsceneSlide("Fire Flinger",
                            "The forge gutters... Without me his armor will rust...",
                            CutscenePortrait.FIRE_FLINGER,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Then rust it shall. Your embers now warm the queen's return.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case GOLD_MECH -> new CutsceneScript(List.of(
                    new CutsceneSlide("Gold Mech",
                            "Systems... failing... Protective protocol... terminated...",
                            CutscenePortrait.GOLD_MECH,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Your gears now turn for freedom, not tyranny.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case PURPLE_EMPRESS -> new CutsceneScript(List.of(
                    new CutsceneSlide("Purple Empress",
                            "The veil shatters... she still believes in you...",
                            CutscenePortrait.PURPLE_EMPRESS,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Faith returns to its rightful throne. Only a few steps remain.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case THE_WELCH -> new CutsceneScript(List.of(
                    new CutsceneSlide("The Welch",
                            "Wakefulness burns... perhaps reality favors you after all...",
                            CutscenePortrait.THE_WELCH,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Reality is where she waits. I won't drift until she's free.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            case TOXIC_TREE -> new CutsceneScript(List.of(
                    new CutsceneSlide("Toxic Tree",
                            "Sap... runs clear again... May the queen breathe easy...",
                            CutscenePortrait.TOXIC_TREE,
                            bossBackdrop(kind)),
                    new CutsceneSlide("Hero",
                            "Fresh air at last. Only the Golden Knight stands between us now.",
                            CutscenePortrait.HERO,
                            CutsceneBackgrounds.heroResolve())
            ));
            default -> null;
        };
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

    private static String chapterTitle(int chapterIndex) {
        return "Chapter " + Math.max(1, chapterIndex + 1);
    }

    private static AnimatedBackdrop bossBackdrop(BossKind kind) {
        return switch (kind) {
            case GOLLUM, GRIM -> CutsceneBackgrounds.dungeonCaptivity();
            case FIRE_FLINGER, GOLDEN_KNIGHT -> CutsceneBackgrounds.goldenThrone();
            case GOLD_MECH -> CutsceneBackgrounds.shopInterior();
            case PURPLE_EMPRESS, THE_WELCH -> CutsceneBackgrounds.victoryCelebration();
            case TOXIC_TREE -> CutsceneBackgrounds.heroResolve();
            default -> CutsceneBackgrounds.emberSwirl();
        };
    }
}
