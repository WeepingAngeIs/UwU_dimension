# UwU Dimension

A standalone adventure mod for Minecraft Java 1.21.1. Flowering cliffs surround Heartwater, the first valley. Behind its waterfalls, a lamp-lit boardwalk leads to an underground bathhouse where three towels still wait for guests.

## Install and travel

Use Java 21 and NeoForge 21.1.249. Place **UwU-Dimension-1.21.1-1.0.0.jar** in the profile's `mods` folder on the client and server. Lootr is required. Cobblemon enables the Pokémon habitats. The target is Cobblemon Unyielding 1.0.10 with Lootr 1.11.38.125, Cobblemon 1.8.4+1.21.1 and Kotlin for Forge 5.12.0. The existing profile supplies these dependencies. For a fresh standalone installation, use the same versions.

Pick up a small flower to unlock the Heartwater Locket recipe. Craft **one compass, one amethyst shard and one small flower** anywhere in the crafting grid. Right-click the locket to travel. Carry the same locket and right-click it again to return to the place where you entered. A fresh unbound locket used inside takes you back to Overworld spawn.

At the arrival meadow, face the waterfalls. Follow the stepping stones through the middle curtain. Descend the mossy stairs and stay on the cherry boardwalk to reach the pink-tiled bathhouse. Open the golden Lootr chest beyond the towels. Every player can claim a Ribbonhare Scepter and another relic there. Aim at a solid ledge and use the scepter. The rabbit's thumps draw nearby monsters before it hops back. Take it to a deep lake. One gentle creature in each pool can answer the thump.

## Along the water

- Turn east from the first lake to reach darker water, luminous shores and the lower stairs. Look up across the bell lake for an inverted shrine. Its upright stair and level gallery lead to the reward.
- Leave the bell lake by the descending western passage for the drowned archive. The shrine and archive have different reward pools. Further valleys rotate, and shrines occur in roughly a quarter of them.
- Pink-roofed homes belong to Petal Keepers. The black-roofed settlement belongs to Dusk Keepers. Show a Dusk Keeper an emerald in either hand and interact to trade. Showing money gives you a 20-second grace period. Each faction has four offers, including a way to earn emeralds.
- To choose a faction and recruit, crouch and interact with four emeralds in hand. The first oath locks in that player's side. Crouch and interact with an empty hand to settle a recruit or ask them to follow again.
- Cobblemon selects from 19 added species through its native player spawner. Flower fields, settlement outskirts, upper caves, deep stone and submerged waterways have their own eligibility rules. All added entries specify this dimension. Server spawn caps, cooldowns and bucket weights still apply.

## Treasure

| Relic | Base damage | Ability |
| --- | ---: | --- |
| Cupid Bow | 14 | Draw and release to charm a mob for 40 seconds. It follows and guards its archer. Giant bosses resist it. |
| Rippleglass Bow | 17 | A splash slows and outlines enemies near the impact. |
| Ribbonhare Scepter | 14 | Call a pink thumping decoy at the ledge you aim at. |
| Blossom Greatsword | 24 | Land a charged hit, then face the hanging petals and use within three seconds for a longer sweep. |
| Bellheart Axe | 22 | Pull nearby foes toward the bell and slow them. |
| Lockkeeper's Axe | 21 | Drive enemies back in a wave ahead of you. |
| Vesper Scythe | 20 | Reap a broad arc and recover health for each enemy caught. |
| Satin Switch | 15 | Hook a creature up to eleven blocks away. |
| Lanternfall Spear | 19 | Throw a luminous copy that marks its target. |
| Tidelace Trident | 23 | Throw a spectral trident that pulls targets into its splash. |
| Blue Hour Wand | 16 | Trace a ray that reveals and slows its first target. |

The damage column gives the player's unenchanted melee attack total while holding the item. Projectile damage, attack speed and ability cooldowns are tuned separately. Hover over any relic for controls. All relic loot entries live in UwU Dimension chest tables.

## Edit and build

The source project includes the Gradle wrapper. With a Java 21 JDK, run `./gradlew build` or `gradlew.bat build`. The playable JAR appears in `build/libs`. To run the dependency-matched development client, put the three dependency JARs above in `libs` and run `./gradlew runClient`.

The [hosted expedition workflow](.github/workflows/expedition.yml) builds the distributable, launches a separate packaged NeoForge server with dependency checksums matched to the CurseForge profile, and launches a graphical Minecraft client on a GitHub-hosted Linux runner. Test code lives in `ci/probe` and ships separately from the playable JAR.
