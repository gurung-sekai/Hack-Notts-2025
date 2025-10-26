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

> **Windows build note:** If you are working inside a OneDrive-synchronised folder, Gradle will automatically redirect its build output to `%LOCALAPPDATA%\HackNotts\legend-of-esran` to avoid the `Unable to delete directory … build/classes/java/main` error. You can override the location via the `legend.buildDir` Gradle property or the `GRADLE_BUILD_DIR` environment variable when needed.

The player explores interconnected rooms, fights monsters, collects bows, keys, and arrows, and survives until facing the **final boss** — a large red monster that signifies the beginning of an upcoming turn-based battle system.


Our gameplay Video is uploaded on Youtube! 

https://www.youtube.com/watch?v=2vcR7R3X1Wk

edited by Julil

---

## Architecture Overview

| File | Description |
|------|--------------|
| `DungeonRooms.java` | Core game logic: room generation, rendering, and collision detection |
| `Player.java` | Handles player input, movement, HP, and attacks |
| `Enemy.java` | Defines AI, attack behavior, and monster updates |
| `launcher/GameLauncher.java` | Desktop launcher for resolution, refresh rate, language, and control bindings |
| `World/DungeonRoomsSnapshot.java` | Serializable save-state used for resuming campaigns |

Each component has a clearly separated responsibility, making the project scalable and easy to maintain.

---

## Game Loop

The core loop runs at **~60 FPS** using a `Timer`-based update system:

```java
private final Timer timer = new Timer(1000 / FPS, this);
```

Each frame:
1. Reads user input (WASD / Arrows / Mouse Click).  
2. Updates entity states (player, enemies, fireballs).  
3. Checks collisions and applies damage.  
4. Renders visuals on the canvas.

This ensures consistent frame pacing and smooth motion across systems.

---

## Procedural Dungeon Generation

Each dungeon room is **generated on the fly** with random tiles, walls, and enemy layouts.  
Key design features:
- 1–3 randomly placed **doors** (N, S, E, or W)  
- **Randomized obstacles** and **enemy spawn patterns**  
- Persistent world — previously visited rooms are stored in memory  

Rooms are stored in a `HashMap`:
```java
Map<Coord, Room> world = new HashMap<>();
```

Returning to an earlier room restores its previous layout, ensuring continuity.

---

## Player System

The player can move, swing a sword, and fire arrows once a bow is obtained.  
The control system is designed for fluid movement and balanced combat.

| Attribute | Description |
|------------|-------------|
| `HP` | Player health |
| `keys` | Unlocks locked doors |
| `arrows` | Ammunition for the bow |
| `hasBow` | Whether the player owns a bow |
| `invuln` | Invulnerability frames after being hit |

### Controls:
- **WASD / Arrow Keys** — Movement  
- **Mouse Click** — Sword attack  
- **F** — Shoot arrow (requires bow + arrows)  
- **Space** — Restart after Game Over  

---

## Enemy System

The world is populated with four primary enemy types:

| Enemy | Speed | Attack Type | Behavior |
|--------|--------|-------------|-----------|
| **Slime** | Slow | Fireball | Basic ranged shooter |
| **Snake** | Medium | Melee | Pursues player directly |
| **Bat** | Fast | Fireball | Chaotic, high-speed ranged attacker |
| **Boss** | Very Fast | None | Spawns after 7 rooms; initiates battle freeze |

Unfortunately, we were unable to implement additional mobs due to time constraint but we hope to add these additional mobs in the future. 

Enemies have independent cooldowns and simple AI logic:
- Pathfinding toward player position  
- Telegraphed attacks (glow before firing)  
- Projectile spawning via internal cooldowns  

---

## Projectile System

All projectiles (arrows and fireballs) are stored and updated per frame.

```java
room.projectiles.add(new Projectile(ProjType.ARROW, cx, cy, vx, vy));
```

Each projectile:
- Uses velocity vectors for direction  
- Checks wall and entity collisions  
- Is deleted once out of bounds  

Fireballs appear as **clean glowing orbs**, rendered using Java’s 2D graphics:

```java
gg.setColor(new Color(255, 150, 0, 180));
gg.fillOval(x, y, 8, 8);
```

---

## Collision & Movement

Collision detection uses simple rectangle intersection checks (`java.awt.Rectangle`).  
Movement vectors are normalized to maintain consistent speed in all directions.

```java
if (playerRect.intersects(enemyRect)) hp--;
```

This ensures fair and accurate physics without complex engines.

---

## Persistence & Progression

- **Rooms are saved** in a `HashMap` to ensure persistence between visits.  
- **Keys, bows, and arrows** carry over between rooms.  
- **Enemies drop items** randomly on defeat.  
- After **7 doors**, the **Boss Room** spawns automatically.  

This structure allows infinite replay and progressive difficulty scaling.

---

## Game Over & Restart System

When the player’s HP reaches 0:
- The game pauses and displays a message:  
  > “GAME OVER — Click or Space to Retry”
- Clicking or pressing Space resets the dungeon and regenerates new rooms.  
- Bow progress is retained between runs to encourage replayability.

---

## Architecture Diagram

```plaintext
DungeonRooms.java
 ├── main() → initializes window and loop
 ├── generateRoom() → procedural layout logic
 ├── draw() → renders game elements
 ├── handleInput() → movement and combat
 ├── checkCollisions() → player/enemy/projectile interactions
 └── restart() → resets game state

Player.java
 ├── move()
 ├── attack()
 ├── shootArrow()
 └── draw()

Enemy.java
 ├── updateAI()
 ├── fireProjectile()
 └── draw()
```

---

## Data Structures Used

| Data Type | Purpose |
|------------|----------|
| `HashMap<Coord, Room>` | Persistent room storage |
| `ArrayList<Enemy>` | Active enemies in current room |
| `ArrayList<Projectile>` | Active fireballs/arrows |
| `EnumSet<Dir>` | Tracks available door directions |
| `Random` | RNG for procedural generation |

---


## Gameplay Video 


https://github.com/user-attachments/assets/db35e8b3-03a1-403c-a27f-1af4b79ce809


---

## Core Features

- Procedurally generated dungeon rooms  
- Real-time sword and bow combat  
- Multiple enemy AI patterns  
- Dynamic obstacles and keys  
- Boss encounter system  
- Persistent world memory  
- Game Over + Restart loop  
- Glowing projectile effects  

---

## Future Roadmap

| Feature | Description |
|----------|-------------|
| Sprite Integration | Replace shapes with proper character & tile art |
| Sound System | Add music, sword swings, and hit sounds |
| Turn-Based Boss Battles | Pokémon-style fight system for bosses |
| Save System | Add persistent saves and profiles |
| Co-op Mode | Local two-player support |
| UI Upgrade | Health bars, inventory screen, and minimap |

---

## Lessons Learned

- Procedural generation improves replayability but complicates debugging.  
- AI tuning is critical for fair, engaging combat.  
- Manual frame control deepens understanding of Java’s graphics pipeline.  
- A modular structure makes iterative development much faster.

---

## Conclusion

**The Legend of Aetheria** is proof that a complete dungeon-crawling experience can be built entirely in Java — with no game engine, just code.  
It captures retro aesthetics, procedural depth, and action gameplay within a self-contained system.  

This project serves as a foundation for future extensions into textured graphics, sound integration, and turn-based combat.

---

## Built With

| Tool | Purpose |
|------|----------|
| **Java (JDK 25)** | Core language |
| **IntelliJ IDEA** | IDE and debugger |
| **Java 2D Graphics / AWT** | Rendering system |
| **macOS & Windows** | Cross-platform tested |

---

## Developer

**Created by:**  
**Pritam, Esran, Jaleel, and Ola**  
*Computer Science Student, University of Nottingham*  
Hackathon 2025 Project — *Retro Java Dungeon Adventure*

---




