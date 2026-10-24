# Shared-memory protocol

The three parts never call each other. They meet in three named, pagefile-backed Win32 file
mappings in the `Local\` namespace. Each side calls `CreateFileMapping` with the full size, so the
games can start in any order. All values are little-endian.

| Block | Size | Writer | Readers |
|---|---|---|---|
| `Local\BLMC_Host` | 16 KiB | Bannerlord mod (C#), every frame | Minecraft mod, compositor |
| `Local\BLMC_Player` | 4 KiB | Minecraft mod (Java), every frame | Bannerlord mod |
| `Local\BLMC_Frame` | ~83 MiB | Minecraft mod, every frame | compositor |
| `Local\BLMC_Events` | 16 KiB | Minecraft mod, when something happens | Bannerlord mod |
| `Local\BLMC_Inventory` | 128 KiB | both: Bannerlord at mission start, Minecraft when Steve's inventory changes | the other |

Minecraft plays Steve. Bannerlord forwards its keyboard and mouse in the host block. Minecraft
answers with its camera and Steve's position in the player block. Bannerlord then renders from
that camera and moves its hidden stand-in agent to Steve.

The offsets below are spelled out in `bannerlord-mod/src/HostPublisher.cs`,
`minecraft-mod/src/main/java/blpassthrough/shm/Protocol.java`, `compositor/src/protocol.hpp` and
`tools/blmc.py`. Change them together and bump the version.

## Coordinates

Bannerlord is right-handed with Z up. Minecraft is right-handed with Y up and +Z pointing south.
Bannerlord `(x, y, z)` maps to Minecraft `(x, z + yOffset, -y)`. That mapping is a proper rotation,
so handedness survives, and one Bannerlord metre is one block. When a mission starts, Minecraft
chooses `yOffset` so that the player's feet land at y = 100, from the first host frame of that mission
that carries the player flag.

Everything in the host and frame blocks is in **Bannerlord** world space. Events also carry
Bannerlord coordinates; Minecraft converts them before writing.

## Seqlock

The host block and each frame slot start with a `u32` sequence number. The writer:

1. makes the number odd,
2. writes the body,
3. issues a release fence,
4. makes the number even again.

A reader copies the body only if it saw the same even number before and after the copy. Otherwise
it retries.

## `BLMC_Host` (version 3)

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | magic `0x434D4C42` ("BLMC") |
| 4 | u32 | version = 3 |
| 8 | u32 | sequence |
| 12 | u32 | flags: 1 in mission, 2 player agent exists, 8 the player's character started the battle on a horse, 16 Bannerlord's UI is in front (Minecraft hides its HUD), 32 not a mission: Steve's inventory over the campaign map , 64 Bannerlord's language is Turkish, 128 the battle is paused (engine paused or mission time not moving; not set for the developer panel's own pause): Minecraft pauses |
| 16 | u64 | Bannerlord frame counter |
| 24 | f64 | mission time (s) |
| 32 | f64×3 | camera Bannerlord rendered with: position |
| 56 | f32×3 | … right (`Frame.rotation.s`) |
| 68 | f32×3 | … up (`Frame.rotation.f`) |
| 80 | f32×3 | … forward (`-Frame.rotation.u`) |
| 92 | f32 | vertical field of view (rad) |
| 96 | f32 | aspect ratio (width / height) |
| 100 | f32 | near plane |
| 104 | f32 | far plane |
| 108 | u32×2 | screen width, height |
| 116 | u32 | mission id (changes when a new mission starts) |
| 120 | f64×3 | stand-in agent position |
| 144 | f32×3 | its facing |
| 156 | f32×3 | its look direction; until Steve is in the battle, towards the middle of the enemy army (he spawns facing it) |
| 168 | f32×3 | its velocity |
| 180 | f32×2 | its health, max health |
| 188 | u32 | teleport count (running): +1 each time Bannerlord itself moves the stand-in (`Agent.TeleportToPosition` from a cinematic, a duel and so on) |
| 192 | f64×3 | where it moved it; Minecraft teleports Steve there and the stand-in waits for him |
| 220 | u32 | ground version (+1 per new sample) |
| 224 | i32×2 | ground origin: Bannerlord cell (x, y) of sample 0 |
| 232 | u32 | ground grid size N (49) |
| 256 | f32×N² | top surface height (Bannerlord z) of each 1 m cell, row by row; NaN = none |
| 10240 | u32 | agent count (≤ 128, nearest first) |
| 10256 | 32 B × 128 | agents: u32 index, u32 flags (1 enemy, 4 rider, 8 mount), f32×3 position, f32×2 facing, f32 height |
| 14400 | u32 | hurt count (running) |
| 14416 | 16 B × 16 | hurt ring, index = count mod 16: f32 damage in Minecraft health points (negative: for Steve's horse, a blow that landed low on the stand-in), i32 attacker agent index (−1 none), f32×2 horizontal direction the blow came from (so a Minecraft shield facing it blocks it) |
| 14848 | | input (below) |
| 15104 | u32 | loot count (running) |
| 15120 | 16 B × 32 | loot ring, index = count mod 32: a soldier Steve brought down. f32×3 where he fell, u8 armour tier (0 leather … 4 netherite), u8 weapon (1 sword, 2 axe, 3 mace, 4 spear, 5 bow, 6 crossbow, 7 thrown), u8 flags (1 shield, 2 arrows or bolts, 4 mounted) |

Input, relative to offset 14848:

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | flags: 1 controlling Steve, 2 a Minecraft screen has the cursor |
| 4 | u32 | keys held, one bit per key index |
| 8 | u32×24 | presses per key (running counts) |
| 104 | f64×3 | mouse x, mouse y, wheel (running totals) |
| 128 | f32×2 | cursor position, 0–1 across the window |
| 136 | u32 | typed count (running) |
| 140 | u32×16 | typed ring, index = count mod 16: a Unicode code point typed while a Minecraft screen has the cursor; 8 backspace, 13 enter, 27 escape |

Key indices:

| Index | Key | Minecraft action |
|---|---|---|
| 0–3 | W, S, A, D | forward, back, left, right |
| 4 | Space | jump |
| 5 | Left Shift | sneak |
| 6 | Left Ctrl | sprint |
| 7 | left mouse | attack |
| 8 | right mouse | use |
| 9 | middle mouse | pick block |
| 10 | Q | drop |
| 11 | E | inventory |
| 12 | F | swap hands |
| 13 | F5 | perspective |
| 14–22 | 1–9 | hotbar |
| 23 | F10 | open or close the developer panel |

Esc while a Minecraft screen is open is sent as a typed escape (27), which closes the screen. A
press of E (index 11) also closes it, unless a text box in it has the focus.

Running counts and totals mean a reader acts on the difference since its last read. A tap shorter
than a Minecraft tick is never lost.

## `BLMC_Player` (version 1)

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | magic `0x504D4C42` ("BLMP") |
| 4 | u32 | version = 1 |
| 8 | u32 | sequence |
| 12 | u32 | flags: 1 Steve is in this mission and alive, 2 first person, 4 dead, 8 a screen is open, 16 focus is valid, 32 Steve rides a horse (feet are then the horse's), 64 the developer panel is open: Bannerlord pauses the battle |
| 16 | u64 | Minecraft frame counter |
| 24 | f64×3 | camera position |
| 48 | f32×3×3 | camera right, up, forward |
| 84 | f32 | vertical field of view to render with (rad). This is Minecraft's own FOV, narrowed by the 5 % overscan. |
| 88 | u32 | mission id this applies to |
| 96 | f64×3 | Steve's feet |
| 120 | f32×3 | Steve's body facing |
| 136 | f64×3 | focus: where else Bannerlord should sample ground (Steve's ender pearl in flight) |

On horseback, Steve's feet are the horse's: the stand-in stays on the ground.

Bannerlord samples the ground around Steve, and around the focus in turn. Minecraft replaces the
barriers only inside each sample's square, so the ground under Steve survives a sample around the
pearl. Barriers further than 128 blocks from Steve are removed.

## `BLMC_Frame` (version 2)

The header (offset 0):

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | magic `0x464D4C42` ("BLMF") |
| 4 | u32 | version = 2 |
| 8 | i32 | latest complete slot, −1 = none |
| 12 | u32 | slot count (3) |
| 16 | u64 | slot stride |
| 24 | u32×2 | max width, max height (1920 × 1200) |
| 32 | u64 | frames written |

The slots start at offset 4096. Each slot is a 256-byte header followed by its pixel data:

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | sequence |
| 4 | u32×2 | width, height |
| 12 | u32 | flags: 1 rows bottom-up, 2 depth zero-to-one, 4 has HUD layer |
| 16 | u64 | Bannerlord frame whose camera was rendered |
| 24 | f64×3 | that camera's position |
| 48 | f32×3×3 | its right, up, forward |
| 84 | f32 | tan(vertical FOV / 2) Minecraft actually rendered with (10 % overscan) |
| 88 | f32 | Minecraft's aspect ratio |
| 92 | f32×2 | projection matrix `m22`, `m32` |
| 100 | f32 | depth value of empty pixels (0: Minecraft 26 uses reversed Z) |
| 104 | u32×3 | offsets of the colour, depth and HUD data from the slot start |

- **Colour** is RGBA8, holding the level only.
- **Depth** is raw D32F. View distance is `m32 / (ndc + m22)`, where `ndc` is the raw depth when the
  zero-to-one flag is set and `2d − 1` otherwise. That formula holds for any perspective matrix
  whose `m23` is −1.
- **HUD** is premultiplied RGBA8, drawn over a transparent target.

The writer always writes the slot after the latest one, so the slot a reader is copying is not
overwritten for two more frames.

## `BLMC_Events` (version 1)

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | magic `0x454D4C42` ("BLME") |
| 4 | u32 | version = 1 |
| 8 | u64 | events written |
| 64 | 256 × 32 B | ring of events, index = count mod 256 |

Each event:

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | type: 1 explosion, 4 agent hit, 5 Steve died (2 and 3 are unused) |
| 4 | i32 | for a hit: the agent index. For an explosion: 1 if a creeper's or a thrown fire charge's, which only hurts enemies. |
| 8 | f32×3 | position: blast centre or a hit's push direction (Bannerlord space) |
| 20 | f32 | radius (m) that Bannerlord should apply damage over. For a hit: the weapon kind (1 sword, 2 axe, 3 mace, 4 spear, 5 bow, 6 crossbow, 7 thrown, 0 other), +16 on horseback, +32 the soldier staggers (snowball, egg) |
| 24 | f32 | power: Minecraft explosion radius (TNT 4, creeper 3), or a hit's damage in Bannerlord health points |

The writer fills the event, issues a release fence, then bumps the count. A reader that falls more
than 256 events behind skips ahead.

## `BLMC_Inventory` (version 1)

Steve's inventory, so a campaign keeps it in its save. It travels as Minecraft's own item data,
written as SNBT text: `{Items:[{Slot:0,Item:{id:"minecraft:stone_sword",count:1}}, …]}`, where slots
0–35 are the hotbar and inventory, 36–39 the boots, leggings, chestplate and helmet, and 40 the off hand.

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | magic `0x494D4C42` ("BLMI") |
| 4 | u32 | version = 1 |
| 64 | u32 | Bannerlord's offer: sequence (seqlock) |
| 68 | u32 | … mission id it is for |
| 72 | u32 | … mode: 1 custom battle (kit 5, netherite), 2 campaign, 3 tournament or arena practice (wooden kit, campaign inventory untouched) |
| 76 | u32 | … inventory text length |
| 80 | u32 | … goods text length |
| 84 | f32 | … the hero's health as a share of his most (a wounded hero starts Steve wounded) |
| 128 | u8[] | … the campaign inventory (empty means the starter kit), then the goods bought from the Minecraft trader since: one `minecraft:id count` per line; `#repair 0` mends everything (the Minecraft smith); `#captured 0` empties the bag, slots 9–35 (the hero was taken prisoner) |
| 65536 | u32 | Steve's report: sequence (seqlock) |
| 65540 | u32 | … mission id |
| 65544 | u32 | … text length |
| 65548 | f32 | … Steve's health as a share of his most (the hero leaves the battle with it) |
| 65600 | u8[] | … what Steve carries now |

Minecraft reads the offer when Steve spawns into a mission, and puts the campaign inventory on him only
once per mission: dying and coming back in the same battle keeps what he has. In a campaign it reports
the inventory every two seconds when it has changed.
