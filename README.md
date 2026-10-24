# GHSGuns

Troll guns and troll items for the GHS SMP (Paper 26.3). It works for Java and Bedrock (Geyser) players because everything runs on the server.

**Nothing in this plugin can kill a player.** Bullets, explosions, fire and fall damage after being launched all leave players at `min-health` (half a heart by default). Mobs can still die.

## Guns
Right-click to shoot (hold it for automatic guns). Sneak + right-click to reload.
Guns have **no cooldown** by default, so they fire as fast as you click. Turn cooldowns back on with `gun-cooldowns: true`, or skip reloading entirely with `infinite-ammo: true`.

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
| Shrink Ray | Shrinks a player to a quarter size for 30 seconds. **Sneak** to make them a 3x giant instead. |
| Magnet | Yanks the player you're looking at over to you |
| The Fish | Hit someone with it (or right-click up close) to slap them across the map |
| Hot Potato | Gives someone the hot potato: they have 15 seconds to hit someone else to pass it, or it blows up on them (harmlessly) |
| Jail Wand | Locks a player in a glass cage for 10 seconds |
| Banana Peel | Throw it on the ground. The next player to walk over it slips, spins and goes flying. |
| Web Shooter | Throw it to make a cobweb trap that disappears after 6 seconds |
| Smite Wand | Strikes a player with lightning that looks real but is harmless |
| Boing Gun | Makes a player bounce nonstop for 15 seconds |
| Flashbang | Throw it: everyone within 14 blocks is blinded and hears ringing |
| Stink Bomb | Throw it: a green cloud that gives everyone nearby nausea |
| Tornado in a Bottle | Throw it: a tornado that sucks in and spins everyone nearby for 6 seconds |
| Sheep Launcher | Fires a rainbow sheep that explodes into wool and confetti (no block damage) |

## Tools
| Tool | What it does |
|------|--------------|
| Drill | Mines 3x3. Hold right-click on a wall to bore a tunnel. |
| Mega Drill | Mines 5x5. Hold right-click on a wall to bore a huge tunnel. |
| Tree Feller | Chop one log and the whole tree comes down |
| Grappling Hook | Cast it, then reel in to fly to the hook. Hook a player to yank them over. |
| Jetpack | Hold right-click to fly where you're looking (no fall damage) |
| Teleport Gun | Right-click to teleport where you're looking (up to 80 blocks) |

The drills and tree feller break blocks as if you mined them yourself, so land-claim plugins still protect builds and you get the normal drops.

## Commands (permission `ghsguns.admin`, op by default)
- `/guns give <item|guns|trolls|tools|all> [player]`
- `/guns troll <player|all> <effect>`. Effects: `launch`, `anvil`, `chickens`, `freeze`, `levitate`, `disco`, `faketnt`, `fakeop` (fake "you are now op" message), `creeper` (hiss behind them), `spin`, `fakenuke` (siren and countdown, then "jk"), `fakeban` (kicks them with a fake ban message; they can rejoin), `demo` (shows the demo-mode screen), `shrink`, `giant`, `cage`, `skydrop` (teleports them 120 blocks up; the fall can't kill), `potato`, `drunk` (wobbly walking and slurred chat for 30s), `uwu` (their chat turns uwu for 60s), `jumpscare`, `paranoia` (creepy sounds around them for 30s), `fakediamonds` (diamonds that vanish when they get close), `fakeleave` (fake "left the game" message), `fakedeath` (fake funny death message), `shuffle` (shuffles their hotbar; no items lost), `pigrain`, `webs`, `lightning`, `bouncy`, `flashbang`, `stinkbomb`, `tornado`, `sheep`, `fakeadvancement` (fake advancement like "[Skill Issue]"), `slowmo`, `zoomies`, `screenshake`, `chickenhat` (a chicken rides on their head for 20s), `cakerain` (food they can't pick up)
- `/guns undo`: put back blocks from the last explosion
- `/guns undo <amount>` / `/guns undo all` / `/guns undo player <name>`
- `/guns list`, `/guns reload`

Blocks placed by trolls (cages, webs) only fill empty air. They remove themselves after a few seconds, never drop items, and are cleaned up if the server stops. Size changes are also cleared on restart or rejoin.

Undo covers the whole nuke, ring explosions included, as one undo. History is kept in memory only (the last 50 explosions), so it clears on restart.

## Build
Needs JDK 25: `mvn package`, then copy `target/GHSGuns-1.0.0.jar` to `plugins/`.

## Notes
- **Nuke size:** change it in the `nuke:` section of `config.yml`. More rings means a bigger crater but more lag.
- **VoidAC:** launched players might get flagged. Set `anticheat-exempt-command` in `config.yml`, or give players VoidAC's bypass permission.
- **Custom gun models:** set `item-model` per gun once you have a Java resource pack and a matching Geyser custom item mapping for Bedrock.
