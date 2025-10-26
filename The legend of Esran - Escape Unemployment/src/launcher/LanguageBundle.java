package launcher;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Tiny localisation helper backed by in-memory maps.
 */
public final class LanguageBundle {
    private final Locale locale;
    private final Map<String, String> entries;

    public LanguageBundle(Locale locale) {
        this.locale = Objects.requireNonNullElse(locale, Locale.UK);
        this.entries = buildEntries(this.locale);
    }

    private Map<String, String> buildEntries(Locale locale) {
        Map<String, String> en = new HashMap<>();
        en.put("story", "Quest: Restore harmony to the Ember Caverns");
        en.put("intro", "The guild pleads: chart the caverns and bring back the lost light!");
        en.put("key_drop", "An enemy dropped a key!");
        en.put("need_key", "A sturdy door blocks the way. It needs a key.");
        en.put("unlock", "You unlock the reinforced door.");
        en.put("boss_warning", "A guardian lurks ahead: %s");
        en.put("boss_unlock", "The unlocked path reveals a guardian: %s");
        en.put("victory", "Guardian defeated! A relic shimmers into view.");
        en.put("relic", "You claim a radiant relic!");
        en.put("defeat", "You collapse... the caverns fall silent.");
        en.put("resume", "Resume");
        en.put("save_and_exit", "Save and exit");
        en.put("quit_without_saving", "Quit without saving");
        en.put("pause_title", "Game paused");
        en.put("room_cleared", "Chamber secure! Gather any keys and push onward.");
        en.put("key_obtained", "You pocket a cavern key! Keys: %d");
        en.put("boss_challenge", "Guardian challenge: %s");
        en.put("victory_key", "Guardian bested! Keys: %d");
        en.put("respawn", "You gather your breath at the entrance. The caverns still await!");
        en.put("boss_repelled", "The guardian drove you back! Regroup at the entrance.");
        en.put("door_locked", "The door is sealed. A key is required.");
        en.put("door_unlock", "Lock released. Keys remaining: %d");

        Map<String, String> cy = new HashMap<>(en);
        cy.put("story", "Taith: Adfer cytgord i Ogofeydd Ember");
        cy.put("intro", "Mae'r urdd yn erfyn: mapiwch yr ogofeydd a dychwelwch y goleuni coll!");
        cy.put("key_drop", "Gollwng allwedd gan elyn!");
        cy.put("need_key", "Mae drws cadarn yn blocio'r ffordd. Mae angen allwedd.");
        cy.put("unlock", "Rydych yn datgloi'r drws wedi'i atgyfnerthu.");
        cy.put("boss_warning", "Mae gwarcheidwad yn cuddio o'ch blaen: %s");
        cy.put("boss_unlock", "Mae'r llwybr wedi'i ddatgloi yn datgelu gwarcheidwad: %s");
        cy.put("victory", "Gorchfygwyd y gwarcheidwad! Mae reliq yn disgleirio.");
        cy.put("relic", "Rydych yn hawlio reliq disglair!");
        cy.put("defeat", "Rydych yn cwympo... mae'r ogofeydd yn tawelu.");
        cy.put("resume", "Ail-ddechrau");
        cy.put("save_and_exit", "Cadw a gadael");
        cy.put("quit_without_saving", "Gadael heb gadw");
        cy.put("pause_title", "Gêm wedi'i hoedi");
        cy.put("room_cleared", "Ystafell yn ddiogel! Casglwch unrhyw allweddi a symud ymlaen.");
        cy.put("key_obtained", "Rydych yn codi allwedd ogof! Allweddi: %d");
        cy.put("boss_challenge", "Her gwarcheidwad: %s");
        cy.put("victory_key", "Gorchfygiad gwarcheidwad! Allweddi: %d");
        cy.put("respawn", "Rydych yn ymgynnull wrth y fynedfa. Mae'r ogofeydd yn disgwyl!");
        cy.put("boss_repelled", "Gorfododd y gwarcheidwad chi'n ôl! Ail-drefnwch wrth y fynedfa.");
        cy.put("door_locked", "Mae'r drws wedi'i selio. Mae angen allwedd.");
        cy.put("door_unlock", "Clo wedi'i ryddhau. Allweddi'n weddill: %d");

        if ("cy".equalsIgnoreCase(locale.getLanguage())) {
            return cy;
        }
        return en;
    }

    public String text(String key, Object... args) {
        String value = entries.getOrDefault(key, key);
        return args.length == 0 ? value : String.format(locale, value, args);
    }
}
