# LexCase

LexCase is a **Paper 1.21.11** plugin for a custom case system with keys, holograms, reward rolls, and a luck/debt mechanic.

## Features

- Case setup and management with `/lc setup`
- Virtual key storage per player
- Case opening animation with chest visuals and holograms
- Reward rarities with weighted rolls
- Luck/debt balancing to reduce extreme streaks
- YAML-based storage for keys and luck profiles
- Gradle-based build for modern Paper development
- Designed for **Java 21**

## Commands

- `/lc setup` — set or update the case location at your current block
- `/lc givekey <player> <amount>` — give virtual keys to a player
- `/lc removekey <player> <amount>` — remove keys from a player
- `/lc delete` — delete the case setup at the current location
- `/lc reload` — reload plugin config and data

## Configuration

Main values are stored in `src/main/resources/config.yml`.

### Case setup

The `case` section controls the visual side of the case and the animation timings.  
This is the part you usually edit first when placing the case in the world.

```yml
case:
  name: '&6&lLexCase'
  center-hologram-height: 1.00
  open-hologram-height: 1.00
  item-rise-height: 0.00
  chest-close-delay-ticks: 35
  chest-remove-step-ticks: 5
  chest-spawn-step-ticks: 5
  auto-open-step-ticks: 5
  final-reveal-delay-ticks: 60
  open-text: '&2ОТКРЫТЬ'
  case-item-text-offset: 0.50
```

### Rarities and drops

The `rarities` section defines what the case can drop and how often each rarity appears.

```yml
rarities:
  rare:
    display: '&aRare'
    color: '&a'
    chance: 55
    drops:
      - material: DIAMOND
        name: '&aDiamond'
        amount: 1
        chance: 50
        value: 12
        command: 'give {player} diamond 1'
      - material: EMERALD
        name: '&aEmerald'
        amount: 2
        chance: 50
        value: 14
        command: 'give {player} emerald 2'
  epic:
    display: '&bEpic'
    color: '&b'
    chance: 30
    drops:
      - material: EXPERIENCE_BOTTLE
        name: '&bXP Bottle'
        amount: 16
        chance: 70
        value: 25
        command: 'give {player} experience_bottle 16'
      - material: GOLD_INGOT
        name: '&bGold Ingot'
        amount: 8
        chance: 30
        value: 28
        command: 'give {player} gold_ingot 8'
  mythical:
    display: '&cMythical'
    color: '&c'
    chance: 12
    drops:
      - material: ENCHANTED_GOLDEN_APPLE
        name: '&cEnchanted Apple'
        amount: 1
        chance: 60
        value: 120
        command: 'give {player} enchanted_golden_apple 1'
      - material: NETHERITE_SCRAP
        name: '&cNetherite Scrap'
        amount: 2
        chance: 40
        value: 150
        command: 'give {player} netherite_scrap 2'
  legendary:
    display: '&6&lLegendary'
    color: '&6&l'
    chance: 3
    broadcast-legendary: true
    drops:
      - material: NETHER_STAR
        name: '&6&lNether Star'
        amount: 1
        chance: 60
        value: 260
        command: 'give {player} nether_star 1'
      - material: DIAMOND_BLOCK
        name: '&6&lDiamond Block'
        amount: 3
        chance: 40
        value: 340
        command: 'give {player} diamond_block 3'
```

### What these values mean

- `display` — rarity name shown to the player.
- `color` — color used for messages, names, and labels.
- `chance` — relative chance for the rarity itself to be selected.
- `drops` — list of rewards inside that rarity.
- `material` — item type given to the player.
- `name` — display name of the reward item.
- `amount` — number of items in the stack.
- `chance` — relative chance of this drop inside its rarity.
- `value` — reward value used by the luck/debt system.
- `command` — console command executed after the reward is rolled.
- `broadcast-legendary` — if `true`, this rarity can trigger a server broadcast.

### How to fill this section

1. Start with 3–4 rarities and give each one a different `chance`.
2. Put at least 1 drop inside every rarity.
3. Use higher `chance` values for common rewards and lower values for rare ones.
4. Set `broadcast-legendary: true` only for the top tier if you want a global announcement.
5. Keep `command` aligned with the item reward so the case gives both the item and the command reward when needed.

### Recommended balancing

- Use a higher total `chance` for common rarities like `rare`.
- Use smaller values for `mythical` and `legendary`.
- Put the biggest `value` on the rarest drops if you want the luck system to react more strongly.
- If a rarity has no `drops`, it should be treated as invalid and should not be used in production.

### Configuration workflow

1. Define the rarities first.
2. Then add the drops for each rarity.
3. Tune `chance` values until the case feels balanced.
4. Test the case in-game and adjust item amounts and values after checking the reward flow.

### Notes

If you want the case to feel more rewarding, increase the number of drops in lower tiers and keep the highest tiers very rare.  
If you want a more generous case, raise the `chance` of `epic` and `mythical`, or add more mid-tier drops.

## Requirements

- Minecraft **Paper 1.21.11**
- Java **21**
- Gradle

## Build

Open the project in IntelliJ IDEA as a **Gradle** project, then run:

```bash
./gradlew build
```

On Windows:

```bash
gradlew.bat build
```

The jar will be created in:

```bash
build/libs/
```

## Installation

1. Build the project.
2. Copy the jar from `build/libs/` into your server's `plugins/` folder.
3. Start or restart the server.
4. Configure a case location with `/lc setup`.

## Project structure

- `src/main/java/me/lex/lexcase/` — plugin source
- `src/main/resources/config.yml` — default configuration
- `src/main/resources/plugin.yml` — plugin metadata

## License

This project is licensed under the **MIT License**.  
You are free to use, modify, and redistribute it, provided that the copyright notice and license text are included.

Copyright (c) 2026 Just Lex

## Author

Just Lex
