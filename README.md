# GHSGuns

Troll guns for the GHS SMP (Paper 26.3). It works for Java and Bedrock (Geyser) players because everything runs on the server.

## Guns
| Gun | What it does |
|-----|--------------|
| Pistol | Accurate single shots |
| SMG | Fast 3-round bursts, more spread |
| AR | 2-round bursts, long range |
| RPG | Straight-flying rocket that explodes, breaks blocks and launches players |
| Nuke | Arcing bomb: 3-second beeping countdown, then a big blast with a ring of extra explosions, a flash and a mushroom cloud. It's announced to the whole server. |

- **Right-click** to shoot (hold it for SMG/AR).
- **Sneak + right-click** to reload. Guns also auto-reload when empty.
- **Guns never kill players.** Hits, explosions and fall damage after being launched all leave players at `min-health` (half a heart by default) instead of killing them. Mobs can still die.

## Commands (permission `ghsguns.admin`, op by default)
- `/guns give <pistol|smg|ar|rpg|nuke|all> [player]`
- `/guns undo`: put back the blocks from the last gun explosion
- `/guns undo <amount>` / `/guns undo all` / `/guns undo player <name>`
- `/guns list`, `/guns reload`

Undo history is kept in memory only, so it clears on restart. It keeps the last 50 explosions; change this with `undo.max-history`.

Firing needs `ghsguns.use`, which everyone has by default.

## Build
Needs JDK 25: `mvn package`, then copy `target/GHSGuns-1.0.0.jar` to `plugins/`.

## Notes
- **VoidAC:** players launched by RPGs or nukes might get flagged. Set `anticheat-exempt-command` in `config.yml`, or give players VoidAC's bypass permission.
- **Custom gun models:** right now guns use the carrot-on-a-stick look. Set `item-model` per gun once you have a Java resource pack and a matching Geyser custom item mapping for Bedrock.
