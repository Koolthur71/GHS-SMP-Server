# GHSGuns

Guns, nukes, sci-fi weapons, tools and a lot of trolls for the GHS SMP (Paper 26.3). It works for Java and Bedrock (Geyser) players because everything runs on the server, and every item has its own particle effects and sounds.

**Nothing in this plugin can kill a player.** Bullets, lasers, explosions, fire and fall damage after being launched all leave players at `min-health` (half a heart by default). Mobs can still die.

## Guns
Right-click to shoot (hold it for automatic guns). Sneak + right-click to reload. There's **no cooldown** by default (`gun-cooldowns: false`), and `infinite-ammo: true` turns off reloading.

| Gun | What it does |
|-----|--------------|
| Pistol / SMG / AR | Classic guns with bullet tracers and headshots |
| Sniper | Huge damage and range, 2x headshots. **Left-click to scope.** |
| Shotgun | 8 pellets, heavy knockback |
| Minigun | 200-round belt, shreds everything |
| Flamethrower | Short-range cone of fire |
| Grenade Launcher / RPG | Explosive projectiles |
| **Raygun** | Green beam with a double helix that makes targets glow and shimmer |
| **Laser Rifle** | Rapid red laser that pierces through everything in a line and sets it on fire |
| **Railgun** | Charges up with sparks, then fires a beam that goes through walls and everything in its path |
| **Tesla Gun** | Chain lightning that jumps between up to 6 targets |
| **Plasma Cannon** | Glowing blue plasma ball that explodes in rings |
| **Homing Missile Launcher** | Locks onto what you're aiming at (or the nearest mob or player) and chases it |
| **Gravity Gun** | Right-click to pick up any mob, player or item. Right-click again to throw it. |
| **Black Hole Gun** | Fires a mini black hole that sucks everything in, then pops (no block damage) |
| **Bubble Gun** | Traps whoever it hits in a floating bubble |
| **Confetti Cannon** | Confetti and fireworks that push people back (no damage) |

## Nukes
| Nuke | What it does |
|------|--------------|
| Nuke | Siren and countdown, then a fireball, rings of explosions, flying debris, a shockwave, a mushroom cloud and green "radiation" |
| **Tsar Bomba** | The same but way bigger: 4 rings, an 85-block cloud and a 150-block shockwave. Expect lag. |
| **Cluster Nuke** | Explodes, then flings 14 bomblets everywhere |
| **Cryo Nuke** | Ice explosion that freezes everyone in 35 blocks, plus a 15-second snowstorm and a temporary blanket of snow |
| **Chicken Nuke** | No block damage. Feather mushroom cloud and 60 chickens. |
| **Party Nuke** | No block damage. A 10-second firework show with rainbow shells, music and glowing. |
| **Black Hole Bomb** | Sucks everything in for 11 seconds, rips up the ground, then collapses in a big explosion |

## Tools
| Tool | What it does |
|------|--------------|
| Drill / Mega Drill / Excavator | Mines 3x3 (5x5 for the Mega Drill). Hold right-click on a wall to bore a tunnel. |
| Tree Feller | Chop one log and the whole tree comes down |
| Vein Miner | Mine one ore and the whole vein comes out |
| Smelter Pick | Everything you mine drops already smelted |
| Harvest Scythe | Right-click to harvest and replant a 7x7 area of crops |
| Builder's Wand | Right-click a block face to extend it (up to 25 blocks). Uses blocks from your inventory. |
| Ore Scanner | Highlights every ore within 16 blocks for 10 seconds: glowing outlines through walls on Java, particles on Bedrock |
| Item Magnet | Hold it in either hand to pull in nearby items and XP |
| Mob Catcher | Right-click a mob to catch it and carry it around. Right-click a block to release it. |
| Healing Staff | Heals the player you're looking at (or yourself) |
| Dash Blade | Dash forward, slicing anything in your path |
| Quake Hammer | Slam the ground for a shockwave. In the air, it dive-bombs and slams harder. |
| Sky Walker | Hold it in either hand to walk on air, water and lava (temporary glass appears under you). Sneak to drop down. |
| Grappling Hook | Cast it and reel in to fly to the hook. Hook a player to yank them over. |
| Jetpack | Hold right-click to fly |
| Teleport Gun | Teleports you where you're looking |
| Night Vision Goggles | Toggles night vision |
| Recall Compass | Stand still for 3 seconds to teleport home (bed or spawn) |
| Time Wand / Weather Wand | Day/night and clear/rain/thunder (admins only) |

The drills, tree feller and vein miner break blocks as if you mined them yourself, so you get normal drops and land-claim plugins still protect builds.

## Troll items
| Item | What it does |
|------|--------------|
| Yeet Stick | Launches the player you're looking at into the sky |
| Swap Stick | Swaps places with them |
| Freeze Ray | Freezes them in place for 5 seconds |
| Levitation Wand | Makes them float away |
| Anvil Rain | Drops harmless anvils on them |
| Chicken Bomb | Throw it: 20 chickens burst out |
| Totally Real TNT | Throw it: it "explodes" into confetti |
| Disco Ball | Throw it: a 10-second dance party |
| Shrink Ray | Shrinks a player for 30 seconds (sneak for giant) |
| **Size Wand** | **Permanent** size change: each click makes them bigger (tiny → small → normal → big → huge → giant). Sneak + click resets to normal. |
| Magnet | Yanks a player over to you |
| The Fish | Slaps people across the map |
| Hot Potato | They have 15 seconds to pass it before it blows up on them (harmlessly) |
| Jail Wand | Locks a player in a glass cage for 10 seconds |
| Banana Peel | The next player to walk over it slips and goes flying |
| Web Shooter | A cobweb trap that disappears after 6 seconds |
| Smite Wand | Harmless lightning |
| Boing Gun | Nonstop bouncing for 15 seconds |
| Flashbang | Blinds everyone nearby |
| Stink Bomb | A nausea cloud |
| Tornado in a Bottle | A tornado that sucks in and spins everyone |
| Sheep Launcher | Fires an exploding rainbow sheep |
| **Meteor Caller** | A meteor shower on a player (no block damage) |
| **Rocket Wand** | Blasts them off with a firework |
| **Rainbow Wand** | A rainbow trail and halo for 30 seconds |
| **Piñata Stick** | Turns a player into a piñata: candy and confetti fly out every time someone hits them |
| **Party Creepers** | Charged creepers that explode into confetti and fireworks |
| **UFO Remote** | A UFO abducts them with a tractor beam, then flies off |
| **Orbit Wand** | Sends them spinning in orbit, then flings them |

## Commands (permission `ghsguns.admin`, op by default)
- `/guns give <item|guns|nukes|trolls|tools|all> [player]`
- `/guns size <player|all> <tiny|small|normal|big|huge|giant|number>`: **permanent** size that stays through relogs and restarts. `normal` puts them back to regular size.
- `/guns troll <player|all> <effect>`, with these effects:
  - **Physical:** `launch`, `anvil`, `chickens`, `freeze`, `levitate`, `disco`, `faketnt`, `shrink`, `giant`, `cage`, `skydrop`, `potato`, `pigrain`, `webs`, `lightning`, `bouncy`, `flashbang`, `stinkbomb`, `tornado`, `sheep`, `meteor`, `rocket`, `rainbow`, `pinata`, `creeperparty`, `ufo`, `orbit`, `hiccups`, `lowgravity`, `earthquake`, `blackhole`, `fireworks`, `explode`
  - **Mind games:** `fakeop`, `creeper`, `spin`, `fakenuke`, `fakeban`, `demo`, `drunk`, `uwu`, `jumpscare`, `paranoia`, `fakediamonds`, `fakeleave`, `fakedeath`, `shuffle`, `fakeadvancement`, `slowmo`, `zoomies`, `screenshake`, `chickenhat`, `cakerain`
- `/guns undo [amount|all|player <name>]`: put back blocks broken by explosions
- `/guns list`, `/guns reload`

Undo covers whole nukes (rings, bomblets and black holes included) as one step. History is kept in memory only (the last 50 explosions), so it clears on restart. Blocks placed by trolls and tools (cages, webs, snow, sky walker glass) only go into empty space (or over water and lava for the sky walker), remove themselves and are cleaned up if the server stops.

## Build
Needs JDK 25: `mvn package`, then copy `target/GHSGuns-1.0.0.jar` to `plugins/`. New settings are added to an existing `config.yml` automatically.

## Notes
- **Nuke size:** change it in the `nuke:`, `tsar-bomba:` and `cluster-nuke:` sections of `config.yml`. Bigger means more lag.
- **VoidAC:** launched players might get flagged. Set `anticheat-exempt-command` in `config.yml`, or give players VoidAC's bypass permission.
- **Custom gun models:** set `item-model` per gun once you have a Java resource pack and a matching Geyser custom item mapping for Bedrock.
