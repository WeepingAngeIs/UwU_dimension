# UwU Dimension 1.2 â€” arsenal and Mirror Court

For Minecraft 1.21.1 / NeoForge 21.1.249, Cobblemon 1.8.4 and Simply Swords 1.70.2-1.21.1. Keep the existing Lootr, Another Furniture, Kotlin for Forge, Architectury, Fzzy Config and Simply Tooltips dependencies. Replace the old UwU JAR only after backing up your world; do not keep two UwU versions together. This delivery does not install itself.

## Travel

Craft the Heartwater Locket with one compass, one amethyst shard and one small flower. Use it from the Overworld to visit the current Reach; use it again to return. Existing lockets and prior dimension IDs remain supported. Crouch-use from the Overworld revisits old Heartwater; crouch-use with pink petals in the offhand revisits the early test Reach. New rooms and terrain appear in newly explored chunks.

## Finding weapons

The 15 named relics, 133 counter forms and ERROR // Glitch Bow have survival acquisition only through UwU dungeon chest tables, including the generated Lootr chests. No recipes, trades or ordinary mob drops grant them. Each chest rolls at most one weapon: Rose Bathhouse 5%, Inverted Shrine 8%, Tide Archive 12%, Mirror Court 12%. These are actual independent probabilities, with no pity guarantee. Each weapon is equally weighted within a successful roll. Tables reject weapon rewards outside Heartwater, Blossom Reach and Sakura Reach. Lootr keeps each player's own contents.

Creative inventory and administrator commands remain available for testing. Already-owned weapons are preserved; old opened Lootr inventories are not rewritten.

## Controls

- Bows: hold use, then release. Cupid converts an eligible struck creature temporarily; Rippleglass applies a slowing ripple.
- Scythes: attack launches a visible crescent shockwave. Hold use for 12 ticks (0.6 seconds) to throw the spinning scythe while still holding. Each throw cycles frost, soulfire, lightning, blossom, then repeats. The held aura changes with the element. Shockwaves have a 14-tick cooldown; throws have a 60-tick cooldown. The projectile returns after striking or after 12 ticks outbound. The owned item stays in your hand, with an in-flight lock; the flying copy cannot be collected.
- Whips: use extends a textured lash and then recoils. Twinfin summons exactly two temporary axolotls after a successful hit, pink and blue, for 30 seconds. A new pair replaces your previous nearby pair. Snowkiss freezes and strongly slows a struck target for three seconds.
- Ribbonstep: lash a target to mark it for eight seconds. Sneak-use teleports **you beside that marked target**, within 24 blocks and the same dimension. It requires clear space, solid footing and no water at the landing. Failure does not teleport you into blocks.
- Axes, spears, daggers, greatswords and the scepter: use throws a visual copy. The scepter calls its hare when it lands on suitable solid ground. Blue Hour Wand fires its cold ray.
- ERROR // Glitch Bow: draw to at least 40% power and release. The shot randomly selects a different form from the 148 other UwU weapons and immediately uses that form's ranged ability. Static briefly covers the held item; the selected form stays until your next fired shot. Rejected/cooldown/short draws do not reroll. The same stack retains its enchantments and cumulative durability; it does not grant other inventory items. A whip form lashes; a scythe form throws; other forms use their normal ranged dispatch.

All base weapon damage remains 14â€“24, before target armor, enchantments and ability effects. Summon follow-up damage and healing are separate effects.

## Matching Simply Swords

The expanded collection is intentionally retained with your approval. Its 133 one-to-one counterparts cover **53 developed unique combat forms**, **4 precursor relic forms**, **1 named ordinary wooden throwable (Sword on a Stick)**, and **75 ordinary/material/runic forms**. The 15 original/special UwU relics and Glitch Bow are additional weapons, giving 149 total. Simply Swords uses its own Unique/Legendary tooltip and awakening tier; this is different from Minecraft's generic rarity color. Separately registered evolved forms each have their own counterpart.

COUNTER-ROSTER.md lists every installed weapon/relic identity, including the four unevolved relic forms. The six crafting materials and absent optional compatibility weapons are not counted. Material variants share their weapon-archetype response; runic forms also cleanse harmful effects during their ward.

Using a counter grants an eight-second ward against the matching held Simply Swords identity. Its projectile marks a matching wielder for eight seconds and applies its counter family. The ward reduces attributed matching incoming damage by 65%; family behavior adds spacing, control, cleansing, anti-healing, projectile interception or summon pressure. The integration tunes supported Simply Swords ability settings for the exact matching stack, not all unrelated weapons the player carries.

Specific adapters interrupt Thunderbrand charging, safely reset Stormbringer parry, cancel Molten Edge venting, Shadowsting dance, Magiblade charge and Dawnquiver draw; Ionbound loses its reserve and active owner effects; Waxweaver/Wickpiercer receive a revival seal without shortening an existing longer cooldown. Chomp'olotl's shoulder aura refresh is suppressed while marked. Existing effects from every external manager are not globally deleted: owner-safe hooks and ward protection are used, avoiding removal of unrelated players' effects or pets. This is a counter kit, not guaranteed invulnerability against every configuration or gem combination.

## Finding the boss

Explore newly generated Blossom Reach/Sakura Reach dungeon sites: follow the surface entrance through the bathhouse, water cave and archive. The lit side passage leads to the underground Mirror Court. Approaching the court gives a nearby coordinate hint, and entering within eleven blocks of its bell starts the 500-health Velvet Vesper encounter automatically. The bell also starts repeat challenges. Its Lootr chest is sealed until that court has been defeated.

Watch the moth's folding telegraph. Hitting its defensive phase binds only that attacker with ten seconds of devotion; their attacks cannot hurt it during that bond. An unbound teammate can still fight in an open phase. Its boss bar follows actual health.

Previously generated terrain does not automatically gain new rooms/chests. Explore new chunks in an existing world. The known TEST profile had an earlier 1.1.0 JAR under the same filename as later 1.1 builds; the new version number distinguishes this release. No existing profile, installed JAR or live world was changed during development.

## Validation

Heavy builds and gameplay probes run on GitHub-hosted runners. Exact completed results and remaining limits accompany the delivery; compilation alone is not evidence of visual quality, full-pack performance or PvP balance.
