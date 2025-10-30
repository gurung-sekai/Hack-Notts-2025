# Welcome to our HackNotts team

![HackNotts logo](/Images/Hack_Notts25_logo.png "HackNotts")

### In our team we have Pritam, Esran, Jaleel, and Ola

# The Legend of Aetheria

> A procedurally generated Java dungeon crawler inspired by retro classics — built entirely from scratch.

<img width="1129" height="747" alt="image" src="https://github.com/user-attachments/assets/44190a5f-e4ab-4281-a0ef-b18bc0de0460" />


---

## Overview

**The Legend of Aetheria** is a 2D dungeon crawler built entirely in **pure Java**, without any engines or frameworks.
Each dungeon is procedurally generated with randomized obstacles, enemy placements, and door configurations — creating a fresh experience every time.

## Progression & Difficulty

- **Difficulty selection:** Every new adventure begins with a choice between **Easy** (respawn at the latest boss checkpoint) and **Hard** (permadeath) modes.
- **Vitality upgrades:** You now start too frail to challenge the first boss; collect coins and purchase at least **two Vitality Sigils** from the shop to unlock that battle. Later bosses demand even more sigils, so plan your farming routes.
- **Safe exploration:** The first rings of rooms (and any area while a boss door is waiting) stay combat-focused rather than boss-gated, letting you grind coins and strength upgrades before you choose to advance on a guardian.
- **Dungeon wards & healing:** Additional wards can be purchased to extend your dungeon health pool while keeping the original base hearts intact. Healing flasks remain for topping off between encounters.
- **Scaling combat:** Defeating enemies increases your damage tier, and every conquered boss raises both their future health pools and your own heroic stats—keeping encounters tense right to the finale.

Together these systems ensure a deliberate power curve: prepare in the dungeon, spend wisely in the shop, and only then tackle the guardians guarding each lair.

## Development Documentation

- [Legacy Maintenance Playbook](docs/LegacyMaintenancePlaybook.md) — structured guidance for keeping large changes organised, tagged, and well documented.
- [Trap System Overview](docs/TrapSystem.md) — explains the reusable animation loader, trap APIs, and how to export compatible Aseprite sprites.

> **Windows build note:** If you are working inside a OneDrive-synchronised folder, Gradle will automatically redirect its build output to `%LOCALAPPDATA%\HackNotts\legend-of-esran` to avoid the `Unable to delete directory … build/classes/java/main` error. You can override the location via the `legend.buildDir` Gradle property or the `GRADLE_BUILD_DIR` environment variable when needed.

> **Sprite utilities:** Boss frames placed under `src/resources/bosses/<Name>/…` are detected automatically. Use `./gradlew exportSpriteSlices` for a quick preview or `./gradlew :extractor:run --args="file=<sheet> outDir=<folder>"` to process new sprite sheets with the JavaFX inspector.

## Controls at a Glance

| Action | Default Binding |
|--------|-----------------|
| Move | **W A S D** (arrow keys also work) |
| Shoot | **Space** |
| Dash (brief i-frames) | **Left Shift** |
| Parry / Reflect | **E** |
| Area Burst (rotates Fire Ring ↔ Lightning Pulse) | **Q** |
| Reroll room obstacles | **R** |
| Pause / Menu | **Esc** |

You can remap every action from the launcher before starting a run.

## Combat Toolkit

- **Dash:** A swift burst that ignores damage for a quarter-second. Perfect for slipping through saw blades or dodging boss openers.
- **Parry:** Raises a short timing window that reflects the next projectile. Reflected shots become friendly, pierce traps, and deal bonus damage.
- **Area Bursts:** Q alternates between a Fire Ring (burns nearby foes and scorches traps) and a Lightning Pulse (locks onto multiple enemies, stunning them briefly).
- **Combo Meter:** Landing hits, disabling traps, and reflecting shots build a combo that boosts attack speed and damage. The HUD now shows combo level and the exact bonus.

## Enemy Synergies

- **Archers + Knights:** Shield-bearing knights soak up frontal damage while archers pepper the arena. Flank the knights or parry an arrow to turn their formation against them.
- **Necromancers:** Will resurrect any fallen ally that isn’t another necromancer. Interrupt them quickly or clear corpses with area bursts.
- **Bards:** Buff nearby enemies with damage and speed auras. They’re fragile but must be prioritised before the frontline overwhelms you.
- **Trap Combos:** Later rooms mix hazards with enemy packs—shoot traps to soften the arena or bait foes into their own machinery.

## Traps & Hazards

- Early rooms are safe for grinding; traps only appear once you’ve cleared a few encounters.
- Saws, spike plates, and fire vents animate directly from Aseprite exports. They can all be disabled with bullets, reflected shots, or area bursts.
- Trap integrity scales with depth, so the combo meter is invaluable for shredding them quickly.

## Boss Intel & Navigation

- Boss doorways glow red, display their vitality requirement, and the HUD now surfaces a **Guardian Hint** showing the direction of the nearest encounter (or the next likely spawn).
- Easy mode respawns you at the last defeated boss. Hard mode is permadeath, but the game-over dialog now tells you what dealt the final blow.
- Vitality upgrades gate the guardians—collect enough sigils from the shop before pushing deeper.

## Persistence & Difficulty

- Rooms, enemy states, traps, and your full ability cooldowns are serialized. Loading a save restores dash/parry timers, combo level, and the current burst in rotation.
- Easy mode keeps progress even after a defeat. Hard mode ends the campaign immediately, so plan those shop runs carefully.


## Gameplay Video

https://github.com/user-attachments/assets/db35e8b3-03a1-403c-a27f-1af4b79ce809

## Credits

Created by **Pritam, Esran, Jaleel, and Ola** for HackNotts 2025.
