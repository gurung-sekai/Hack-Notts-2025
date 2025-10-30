# Trap System Overview

The dungeon now exposes a reusable trap framework under `World.trap`. The core
pieces are:

| Class | Responsibility |
| ----- | -------------- |
| `Animation` | Lightweight frame sequencer driven by `deltaSeconds` from the game loop. |
| `SpriteLoader` | Loads sequential Aseprite exports (`frame_0.png`, `frame_1.png`, …) via the classpath. Fallback art is generated automatically if no sprites are available. |
| `Trap` / `BaseTrap` | Contract for time-stepped traps plus a convenience superclass that handles rendering, collision bounds, cooldowns, and damage delivery. |
| `SawTrap`, `SpikeTrap`, `FireVentTrap` | Concrete trap behaviours with tuned damage and activation patterns. |
| `TrapManager` | Holds the active traps for a room, updates them each tick, renders them, and applies collision damage to a `World.trap.Player` implementation. |

Traps stay dormant for the opening skirmishes—only after you clear a few combat
rooms do saws, spikes, and vents begin spawning. That ramp keeps early
exploration approachable while still letting late-game layouts mix hazards with
enemy packs.

Player bullets, reflected enemy projectiles, and the hero’s area abilities all funnel into
`TrapManager.damageTrap(...)`, so every hazard can be dismantled mid-combat. Tougher
floors simply increase the integrity value, encouraging you to leverage the combo meter
for faster takedowns.

## Usage Example

The snippet below demonstrates how to wire traps into a standalone room. The
`SimplePlayer` stub mirrors the contract expected by the manager: it exposes a
bounding box, tracks health, and honours invulnerability frames.

```java
import World.trap.Animation;
import World.trap.FireVentTrap;
import World.trap.Player;
import World.trap.SawTrap;
import World.trap.SpikeTrap;
import World.trap.SpriteLoader;
import World.trap.TrapManager;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

class SimplePlayer implements Player {
    private final Rectangle bounds;
    private int health = 10;
    private double iFrames = 0.0;

    SimplePlayer(int x, int y, int size) {
        this.bounds = new Rectangle(x, y, size, size);
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(bounds);
    }

    @Override
    public boolean isInvulnerable() {
        return iFrames > 0.0;
    }

    @Override
    public void grantInvulnerability(double seconds) {
        iFrames = Math.max(iFrames, seconds);
    }

    @Override
    public void takeDamage(int damage) {
        health = Math.max(0, health - damage);
        System.out.println("Player HP: " + health);
    }

    @Override
    public void update(double dt) {
        if (iFrames > 0.0) {
            iFrames = Math.max(0.0, iFrames - dt);
        }
    }
}

TrapManager traps = new TrapManager();
Animation sawAnim = new Animation(
        SpriteLoader.loadDefault("resources/traps/Saw Trap/idle"), 0.07);
Animation spikeAnim = new Animation(
        SpriteLoader.loadDefault("resources/traps/Spike Trap/cycle"), 0.08);
Animation fireAnim = new Animation(
        SpriteLoader.loadDefault("resources/traps/Fire Trap/attack"), 0.06);

traps.add(new SawTrap(128, 96, sawAnim));
traps.add(new SpikeTrap(64, 160, spikeAnim, 2.0, 0.55));
traps.add(new FireVentTrap(192, 96, fireAnim, 3.0, 1.2));

SimplePlayer player = new SimplePlayer(110, 110, 28);

double dt = 1.0 / 60.0; // 60 FPS update step
for (int i = 0; i < 600; i++) { // simulate 10 seconds
    traps.update(dt, player);
    // render step if you have a Graphics2D:
    // traps.render(graphics2D);
}
```

## Aseprite Export Cheatsheet

Exporting trap animations is easiest when the filenames already match the
loader’s expectations. The commands below split each tag into its own folder and
emit `frame_#.png` sprites under `src/resources/traps/…`.

```bash
aseprite -b "art/saw_trap.aseprite" \
  --split-tags \
  --save-as "resources/traps/Saw Trap/{tag}/frame_{frame}.png"

aseprite -b "art/spike_trap.aseprite" \
  --split-tags \
  --save-as "resources/traps/Spike Trap/{tag}/frame_{frame}.png"

aseprite -b "art/fire_trap.aseprite" \
  --split-tags \
  --save-as "resources/traps/Fire Trap/{tag}/frame_{frame}.png"
```

Place the exported folders under `The legend of Esran - Escape
Unemployment/src/resources/traps/` so they are visible on the runtime classpath.
The loader will automatically consume up to 100 sequential frames and stop when
it hits a gap, keeping the overhead low even for longer animations.
