# UwU Dimension

A standalone adventure mod for Minecraft Java 1.21.1. Flowering cliffs surround Heartwater, the first valley. Behind its waterfalls, a lamp-lit boardwalk leads to an underground bathhouse where three towels still wait for guests.

## Install and travel

Use Java 21 and NeoForge 21.1.249. Place **UwU-Dimension-1.21.1-1.1.0.jar** in the profile's `mods` folder on the client and server. Lootr is required. Cobblemon enables the PokÃ©mon habitats. The target is Cobblemon Unyielding 1.0.10 with Lootr 1.11.38.125, Cobblemon 1.8.4+1.21.1 and Kotlin for Forge 5.12.0. The existing profile supplies these dependencies. For a fresh standalone installation, use the same versions.

Pick up a small flower to unlock the Heartwater Locket recipe. Craft **one compass, one amethyst shard and one small flower** anywhere in the crafting grid. Right-click the locket to travel. Carry the same locket and right-click it again to return to the place where you entered. A fresh unbound locket used inside takes you back to Overworld spawn.

### Upgrading an existing world

Close the game and stop the server before replacing the old UwU Dimension JAR. Keep a backup of the world. Keep only one enabled UwU Dimension JAR per installation. Open your existing save and use your existing locket normally from the Overworld to visit **Blossom Reach**, the newly generated dimension. If you logged out in old Heartwater, first use your locket to return home, then use it again after the cooldown.

To revisit your original builds, **crouch and use the locket from the Overworld**. Old Heartwater keeps its original dimension ID, explored chunks, chests and residents. Terrain defects already saved in those chunks are preserved along with player builds. Use the locket normally in either realm to return. You can keep exploring without deleting dimension folders or resetting a save.

At the arrival meadow, face the waterfalls. Follow the stepping stones through the middle curtain. Descend the mossy stairs and stay on the cherry boardwalk to reach the pink-tiled bathhouse. Open the golden Lootr chest beyond the towels. Every player can claim a Ribbonhare Scepter and another relic there. Aim at a solid ledge and use the scepter. The rabbit's thumps draw nearby monsters before it hops back. Take it to a deep lake. One gentle creature in each pool can answer the thump.

## Along the water

- Turn east from the first lake to reach darker water, luminous shores and the lower stairs. Look up across the bell lake for an inverted shrine. Its upright stair and level gallery lead to the reward.
- Leave the bell lake by the descending western passage for the drowned archive. The shrine and archive have different reward pools. Further valleys rotate, and shrines occur in roughly a quarter of them.
- Pink-roofed homes belong to Petal Keepers. The black-roofed settlement belongs to Dusk Keepers. Show a Dusk Keeper an emerald in either hand and interact to trade. Showing money gives you a 20-second grace period. Each faction has four offers, including a way to earn emeralds.
- To choose a faction and recruit, crouch and interact with four emeralds in hand. The first oath locks in that player's side. Crouch and interact with an empty hand to settle a recruit or ask them to follow again.
- Cobblemon has 27 added spawn entries through its native player spawner. Flower fields, settlement outskirts, upper caves, deep stone and submerged waterways have their own eligibility rules. Each entry limits spawning to the intended UwU realms. Server spawn caps, cooldowns and bucket weights still apply.

## Villages, reefs and the Mirror Court

Walk the surface paths to explore cherry groves and furnished houses. Adult keepers use beds and workstations. To heal a wounded friendly keeper, hold food and interact. For a Dusk Keeper, showing an emerald in the other hand can keep the interaction friendly. A meal restores health based on its nutrition.

A flower guardian pursues whoever hurts it, including hostile mobs. If it cannot reach its attacker within about 30 seconds, it closes the distance, grabs them and launches them roughly 8,000 blocks upward. Take care when aiming near one.

While inside Blossom Reach, **crouch and use the locket** to receive coordinates for nearby Blush Reef water. Swim down to see the living coral, luminous Rose Nacre, fish and Ribbon Dolphins. Aquatic Pokémon use Cobblemon's normal player spawner. Allow time around deep open water for a school or Pokémon to arrive.

In the lower galleries, follow the lit walkway to the Mirror Court and ring its bell. Velvet Vesper has 500 health. Watch its boss bar and step away from its aimed needle and pollen attacks. Strike while its mirror is open. Hitting the raised mirror charms the attacker for 200 game ticks (10 seconds at normal speed), including when firing a projectile. Other players keep their own independent status. Ringing an occupied court cannot summon a second boss.

The installed Reactive Music songpack identifies the new mainland by its actual Minecraft cherry-grove biome and makes *Pinwheel* eligible through the floral rule. Its underwater rule makes *Distant Shores* eligible while submerged. Its special nighttime cherry-grove rule for *River Night* also requires the Overworld. Track selection still follows the pack's priorities and playback timing.

## Treasure

| Relic | Melee damage | Bow, throw or use ability | Ability |
| --- | ---: | ---: | --- |
| Cupid Bow | 14 | 14 on a full draw against a charm-immune target, 0 on a successful charm | Draw and release to charm a mob for 40 seconds. It follows and guards its archer. Giant bosses resist it. |
| Rippleglass Bow | 17 | 17 on a full draw | A splash slows and outlines enemies near the impact. |
| Ribbonhare Scepter | 14 | 0 on summon or thump | Call a pink thumping decoy at the ledge you aim at. |
| Blossom Greatsword | 24 | 16 on the return sweep | Land a charged hit, then face the hanging petals and use within three seconds for a longer sweep. |
| Bellheart Axe | 22 | 0 on the gathering toll | Pull nearby foes toward the bell and slow them. |
| Lockkeeper's Axe | 21 | 21 on the wave | Drive enemies back in a wave ahead of you. |
| Vesper Scythe | 20 | 20 on the reap | Reap a broad arc and recover health for each enemy caught. |
| Satin Switch | 15 | 15 on the hook | Hook a creature up to eleven blocks away. |
| Lanternfall Spear | 19 | 19 on the throw | Throw a luminous copy that marks its target. |
| Tidelace Trident | 23 | 23 on the throw | Throw a spectral trident that pulls targets into its splash. |
| Blue Hour Wand | 16 | 16 on the ray | Trace a ray that reveals and slows its first target. |

Values are health points before armor or outside modifiers. Two points equal one heart. Melee assumes a fully charged, noncritical swing. Bows scale down on partial draws. Cupid charms an eligible creature without taking health, even at full draw. Hover over any relic for controls. All relic loot entries live in UwU Dimension chest tables.

## Edit and build

The source project includes the Gradle wrapper. With a Java 21 JDK, run `./gradlew build` or `gradlew.bat build`. The playable JAR appears in `build/libs`. To run the dependency-matched development client, put the three dependency JARs above in `libs` and run `./gradlew runClient`.

The [hosted expedition workflow](.github/workflows/expedition.yml) builds the distributable, launches a separate packaged NeoForge server with dependency checksums matched to the CurseForge profile, and launches a graphical Minecraft client on a GitHub-hosted Linux runner. Test code lives in `ci/probe` and ships separately from the playable JAR.
