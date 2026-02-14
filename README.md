# CTF - Capture the Flag

A Minecraft Paper plugin that adds a Capture the Flag minigame where Attackers must find and break the flag while Defenders protect it.

## Overview

CTF creates a dynamically generated game world where two teams compete:
- **Attackers** spawn far from the flag and must locate and destroy it (Ancient Debris block)
- **Defenders** spawn near the flag and must protect it from being broken

The game automatically handles world generation, team spawning, respawns, and win conditions.

## Game Rules

### Objective
- **Attackers win** by breaking the flag (Ancient Debris)
- **Defenders win** by preventing the flag from being broken

### Game Flow
1. An admin initializes the game with `/game init <border_size>`
2. Players join teams with `/team <attackers|defenders>`
3. Players select kits with `/kit <kit_name>`
4. Admin starts the game with `/game start`
5. After a countdown, players are unfrozen and combat begins
6. Game ends when the flag is broken or stopped with `/game stop`

### Respawning
- Players respawn after a configurable cooldown based on their team.
- **Attackers**: (default: 10 seconds) respawn at their original spawn area.
- **Defenders**: (default: 15 seconds) respawn near the flag.

### Late Join
Players joining mid-game can select a team and kit, then spawn into the match.

## Commands

| Command | Description | Usage |
|---------|-------------|-------|
| `/team` | Join a team | `/team <attackers\|defenders>` |
| `/kit` | Select a kit | `/kit <kit_name>` |
| `/chat` | Set chat mode | `/chat <global\|team>` |
| `/game` | Manage the game | `/game <init\|start\|stop>` |
| `/giveitem` | Give custom items (OP only) | `/giveitem <fireball\|tnt\|alarm> [amount]` |

## Kits

### Attacker Kits
| Kit | Equipment |
|-----|-----------|
| **Axe** | Leather armor, Iron Axe, Stone Pickaxe, Stone Shovel |
| **Sword** | Leather armor, Iron Sword, Shield, Stone Pickaxe, Stone Shovel |
| **Crossbow** | Leather armor, Crossbow, Stone Pickaxe, Stone Shovel, 16 Arrows |

### Defender Kits
| Kit | Equipment |
|-----|-----------|
| **Knight** | Diamond armor, Diamond Sword, Shield, Iron Axe |
| **Archer** | Iron armor, Iron Sword, Power I Bow, 64 Arrows |
| **Builder** | Chainmail armor, Iron Sword, Diamond Pickaxe, 128 Stone Bricks |

## Configuration

The `config.yml` file contains the following options:

```yaml
game:
  world_name: "ctf_arena"           # Name of the generated game world
  world_type: NORMAL                # World type (NORMAL, FLAT, etc.)
  world_seed: ""                    # Seed for world generation (empty = random)
  attacker_spawn_radius: 50         # Spawn spread radius for attackers
  defender_spawn_radius: 20         # Spawn spread radius for defenders
  attacker_respawn_cooldown_seconds: 10 # Time before attackers respawn
  defender_respawn_cooldown_seconds: 15 # Time before defenders respawn
  flag_location_max_attempts: 100   # Attempts to find valid flag location
  countdown_seconds: 5              # Pre-game countdown duration
```

### Custom Kits
Kits are defined in `config.yml` under the `kits` section. Each kit specifies:
- `team`: ATTACKERS or DEFENDERS
- `armor`: helmet, chestplate, leggings, boots
- `items`: list of materials with amounts and optional enchantments
