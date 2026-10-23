# GHSGuns

Troll guns and troll items for the GHS SMP (Paper 26.3). It works for Java and Bedrock (Geyser) players because everything runs on the server.

**Nothing in this plugin can kill a player.** Bullets, explosions, fire and fall damage after being launched all leave players at `min-health` (half a heart by default). Mobs can still die.

## Guns
Right-click to shoot (hold it for automatic guns). Sneak + right-click to reload.

| Gun | What it does |
|-----|--------------|
| Pistol | Accurate single shots, headshot bonus |
| SMG | Fast 3-round bursts |
| AR | 2-round bursts, long range |
| Sniper | Huge damage and range, 2x headshots. **Left-click to scope** for perfect accuracy. |
| Shotgun | 8 pellets, heavy knockback |
| Minigun | 200-round belt, shreds everything, slows you down while firing |
| Flamethrower | Short-range cone that sets targets on fire |
| Grenade Launcher | Arcing grenades with small explosions |
| RPG | Rocket that flies straight, explodes and launches players |
| Nuke | Air raid siren and countdown, then a core blast with 3 rings of explosions rolling outwards, flying debris, a shockwave, a 55-block mushroom cloud and green "radiation" (poison, which can't kill) |

## Troll items
| Item | What it does |
|------|--------------|
| Yeet Stick | Launches the player you're looking at into the sky |
| Swap Stick | Swaps places with them |
| Freeze Ray | Freezes them in place with a frost effect for 5 seconds |
| Levitation Wand | Makes them float away |
| Anvil Rain | Drops harmless anvils on them |
| Chicken Bomb | Throw it: 20 chickens burst out, then pop into feathers |
| Totally Real TNT | Throw it: it hisses, "explodes" into confetti and does no damage |
| Disco Ball | Throw it: a 10-second dance party with lights, music, glowing and forced dancing |

## Commands (permission `ghsguns.admin`, op by default)
- `/guns give <item|guns|trolls|all> [player]`
- `/guns troll <player|all> <effect>`. Effects: `launch`, `anvil`, `chickens`, `freeze`, `levitate`, `disco`, `faketnt`, `fakeop` (fake "you are now op" message), `creeper` (hiss behind them), `spin`, `fakenuke` (siren and countdown, then "jk"), `fakeban` (kicks them with a fake ban message; they can rejoin), `demo` (shows the demo-mode screen)
- `/guns undo`: put back blocks from the last explosion
- `/guns undo <amount>` / `/guns undo all` / `/guns undo player <name>`
- `/guns list`, `/guns reload`

Undo covers the whole nuke, ring explosions included, as one undo. History is kept in memory only (the last 50 explosions), so it clears on restart.

## Build
Needs JDK 25: `mvn package`, then copy `target/GHSGuns-1.0.0.jar` to `plugins/`.

## Notes
- **Nuke size:** change it in the `nuke:` section of `config.yml`. More rings means a bigger crater but more lag.
- **VoidAC:** launched players might get flagged. Set `anticheat-exempt-command` in `config.yml`, or give players VoidAC's bypass permission.
- **Custom gun models:** set `item-model` per gun once you have a Java resource pack and a matching Geyser custom item mapping for Bedrock.
