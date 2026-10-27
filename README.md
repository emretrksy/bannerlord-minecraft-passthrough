# Bannerlord × Minecraft Passthrough

**English** · [Türkçe](README.tr.md)

by **Emre Türksoy** · [Psicodart Games Discord](https://discord.gg/R4qStgw9bk)

This mod puts Minecraft's Steve into *Mount & Blade II: Bannerlord* battles. Both games run side by
side and talk through shared memory. You play in Bannerlord's window, but your keyboard and mouse
drive the real Minecraft player, so movement, the elytra, items, the inventory and combat all work
by Minecraft's rules. Bannerlord renders from Minecraft's camera, and Steve is drawn into the scene
with proper depth: a soldier standing in front of him hides him.

![Steve in netherite armour on a rooftop above a Bannerlord city](docs/images/city-rooftop.jpg)

| | |
|---|---|
| ![Steve in diamond armour on his horse, in the middle of a desert cavalry fight](docs/images/mounted.jpg) | ![A creeper walking into a crowd of Vlandian soldiers](docs/images/creeper.jpg) |
| ![TNT going off among enemy soldiers](docs/images/explosion.jpg) | ![Mace](docs/images/wind-charge.jpg) |
| ![Steve gliding over a village with an elytra](docs/images/elytra.jpg) | ![Steve building with cobblestone on a rooftop](docs/images/blocks.jpg) |

## What it does

- **Steve is your character.** Bannerlord's keyboard and mouse go to Minecraft: Steve walks,
  sprints, sneaks, swims, glides, throws pearls, eats and blocks with his shield just like in
  Minecraft. Your Bannerlord character turns into an invisible, silent stand-in that follows him,
  so the enemy still has someone to fight.
- **One camera.** Bannerlord draws the battle from Minecraft's camera, in first person (with
  Minecraft's hand) or third person (F5).
- **Minecraft combat, Bannerlord consequences.**
  - Every soldier near Steve has an invisible stand-in inside Minecraft. Swords, sweeps, spears,
    arrows, the trident and the mace hit it with Minecraft's own rules (cooldown, crits, the mace
    smash), and the damage lands on the real soldier.
  - Your own troops are spared. Steve's arrows and pearls fly through them.
  - Soldiers you hit flash red like Minecraft mobs.
  - Enemy blows on Steve are applied in Minecraft, so his armour, shield and totems count.
  - If you ride into battle, Steve gets an armoured Minecraft horse. Low blows hit the horse.
  - When Steve dies, your Bannerlord character dies too.
- **A shared world.**
  - The ground and walls around Steve are rebuilt in Minecraft as invisible barrier blocks, so he
    walks on Bannerlord's terrain. He steps up one block without jumping.
  - You can place blocks to build towers, walls or bridges, and Steve stands on them. Bannerlord's
    soldiers, horses and arrows can still pass through them.
  - TNT, creepers and fire charges hurt and knock down soldiers. Creepers from your eggs go after
    the enemy and leave you and your troops alone.
  - Fire charges are thrown with a right click, Bed Wars style. Snowballs and eggs sting and make a
    soldier stagger.
- **Minecraft's HUD and inventory.** The hotbar, hearts, hunger and armour bars are drawn in
  Bannerlord. **E** opens the real Minecraft inventory with Bannerlord's cursor.
- **The campaign has its own Steve.** His Minecraft inventory is separate from your hero's gear and
  is kept in your save. Soldiers drop Minecraft loot, towns have a Minecraft trader and smith, and
  Steve's hits train your hero's skills. More in [Campaign](#campaign).
- **Developer panel (F10).** Pauses the battle while it's open. Revive on death, invulnerability,
  five kits (leather, iron, gold, diamond, netherite), heal, kill nearby enemies, clear your blocks,
  and every item in the game with a search box. It follows Bannerlord's language: Turkish if the
  game is in Turkish, English otherwise.
- **F9** hands control back to Bannerlord and takes it again.
- **Pausing Bannerlord pauses Minecraft.** When the battle stops (Esc menu or any other pause),
  Steve freezes too, and carries on when Bannerlord does.

![First person: diamond sword and shield, horse archers charging down a dune](docs/images/first-person.jpg)

## Download and install

You need the Steam version of Bannerlord and Minecraft: Java Edition with the official Minecraft
Launcher (the one from minecraft.net or the Xbox app both work). Open the Minecraft Launcher once
and sign in before installing.

1. Grab the zip from the
   [Releases page](https://github.com/emretrksy/bannerlord-minecraft-passthrough/releases) (or Nexus
   Mods) and unzip it anywhere.
2. Close Bannerlord and the Minecraft Launcher, then run **`Install.bat`**. It does everything:
   - finds Bannerlord through Steam and installs the module, ReShade (the add-on build, as
     `dxgi.dll`) and the compositor,
   - adds a **Bannerlord x Minecraft** profile to the Minecraft Launcher, with Fabric, the mod and
     the settings that keep Minecraft stable, in a game folder of its own
     (`%APPDATA%\.minecraft\bannerlord-passthrough`), so your other profiles are untouched,
   - puts a **Bannerlord x Minecraft** shortcut on your desktop.
3. That's it. To play, see [Starting a battle](#starting-a-battle).

`Uninstall.bat` takes everything back out (Steve's Minecraft folder is kept).

If you already have your own ReShade in Bannerlord (a `dxgi.dll` in Bannerlord's
`bin\Win64_Shipping_Client` folder), the installer doesn't delete or overwrite it. For Steve to
show up, that ReShade then has to be version 6.x, installed from the build **with add-on
support**. The regular ReShade build doesn't load add-ons, and the part that draws Steve is an
add-on.

<details>
<summary>Installing the Minecraft side by hand (optional)</summary>

You don't need to do this: `Install.bat` already does it for you. Set it up by hand if you also
want to play from another launcher such as Prism Launcher, MultiMC or CurseForge, or would rather
manage the profile yourself.

Make a **Fabric 26.3** profile (Fabric Loader 0.19.5+) with its own game directory, put
`blpassthrough-<version>.jar` into its `mods` folder, and add this to its JVM arguments:

```
-Xmx2G -Dmax.bg.threads=4 -XX:TieredStopAtLevel=1 -Dblpassthrough.preload=true
```

These aren't required either, but they're recommended. `-Dmax.bg.threads=4` and
`-XX:TieredStopAtLevel=1` make Minecraft crash much less on some machines (see
[Known limitations](#known-limitations)); `-Dblpassthrough.preload=true` creates the world at
startup instead of when the first battle begins, so battles start faster.
</details>

## How to play

### Starting a battle

1. Double-click the **Bannerlord x Minecraft** shortcut on your desktop. The Minecraft Launcher
   opens; pick the **Bannerlord x Minecraft** profile (it's usually already selected) and press
   **Play**.
2. Once Minecraft is up, Bannerlord starts by itself, with the mods you normally use plus this one.
   Minecraft can sit behind Bannerlord, but **don't minimise it**: a minimised Minecraft stops
   rendering.
3. Go into any battle. Expect a few seconds of waiting at the start.
4. During deployment and conversations Bannerlord keeps its own camera and controls. Steve takes
   over when the battle starts.

You can also start the two by hand: Minecraft first (the Bannerlord x Minecraft profile), then
Bannerlord with **Minecraft Passthrough** ticked in its launcher.

### Controls

| Key | In battle |
|---|---|
| W A S D | Walk |
| Space | Jump; in mid-air with an elytra: glide |
| Left Ctrl | Sprint |
| Left Shift | Sneak |
| Left mouse | Attack, break blocks |
| Right mouse | Use: place blocks, draw the bow, shield, eat, throw (pearls, fire charges, snowballs, eggs), fireworks |
| Middle mouse | Pick block (takes it into your hand if it's in your inventory) |
| 1 – 9, mouse wheel | Hotbar slot |
| Q | Drop the held item |
| F | Swap hands |
| E | Minecraft inventory. Esc closes it |
| F5 | First / third person |
| F9 | Give control back to Bannerlord, and take it again |
| F10 | Developer panel (pauses the battle) |

Bannerlord's keys for your own character do nothing while you play Steve. Orders to your troops
and the rest of the battle UI work as usual.

On the **campaign map**, **E** opens Steve's inventory over the map. Minecraft has to be running.

### What happens where

| Where | What Steve gets |
|---|---|
| Campaign battles | His **campaign inventory**, saved with your game |
| Custom Battle | **Kit 5 (netherite)**: netherite armour and weapons, a mace, an elytra with fireworks, pearls, TNT, creeper eggs, fire charges, totems… Nothing is saved. F10 switches to another kit or gives any item |
| Tournaments, arena practice | Steve gets wooden weapons; his campaign inventory isn't touched |
| Towns | **Minecraft trader** and **Minecraft smith** in the town menu |
| Campaign map | **E**: Steve's inventory |
| Walking around towns and villages | Steve plays here too; also **F9** lets you walk as your own character |

### Combat numbers

- **Your hits** use Minecraft's rules and deal Minecraft damage × 6 to the soldier. A fully charged
  diamond sword does 42 to a soldier with about 100 health; a crit (63+) knocks him down.
- **Their armour** softens your hits: 50 body armour halves them. A recruit with 10 armour takes
  83 %, a mid-tier soldier with 25 about 67 %.
- **Their hits on you** are the blow's damage before armour ÷ 5, applied in Minecraft. Only your
  **Minecraft armour**, shield and totems protect you; your Bannerlord armour doesn't. A sword cut
  costs roughly 3–5 hearts without armour, about 1–1.5 in full diamond.
- **Explosions** (TNT, creepers, fire charges) hurt and knock down soldiers. Only TNT also
  damages your own soldiers. Everything else only damages the enemy.

### Campaign

| | |
|---|---|
| ![Steve in diamond armour with an elytra, in front of a castle under siege](docs/images/castle-siege.jpg) | ![Steve in netherite armour on a cobblestone tower he built on the walls of an Imperial castle](docs/images/castle-walk.jpg) |

- Steve's inventory is his own and lives in your save. His first battle starts him with a stone
  sword, a bow, leather armour and a shield.
- **Loot:** soldiers you bring down drop Minecraft items: weapons and armour in a material that
  matches what they wore (leather up to netherite), arrows, food, sometimes something rare.
- **Minecraft trader** (every town), paid in denars. The basics are everywhere, each culture has
  its own specialities (Aserai pearls and wind charges, Sturgian TNT, Imperial diamonds…), and the
  rarest things (elytra, totem, mace, netherite) only show up in rich towns. What you buy reaches
  Steve at the start of the next battle.
- **Minecraft smith** (every town): mends all of Steve's worn gear, one denar per point of wear.
- **Skills:** Steve's hits train the matching skill of your hero (one-handed, two-handed, polearm,
  bow, crossbow, throwing, riding). Your hero's skill levels don't make Steve any stronger, though.
- **Wounds:** Steve starts a battle as hurt as your hero currently is, and however hurt he leaves
  it, your hero is that hurt on the map.
- **Captured:** captors take what was carried and leave what was worn. Steve loses his bag (27
  slots) and keeps his hotbar, armour and off hand.

### Troubleshooting

| Problem | Fix |
|---|---|
| "Cannot load: ...\MinecraftPassthrough.dll" at startup | Windows blocked the downloaded files. Right-click `MinecraftPassthrough.dll` and `0Harmony.dll` in `Modules\MinecraftPassthrough\bin\Win64_Shipping_Client` > Properties > tick **Unblock**, or run `Install.bat` again. |
| Steve isn't drawn | Press **Home** in Bannerlord and tick the **MCPassthrough** technique. Check that Minecraft is running and not minimised. |
| The battle starts but nothing happens for a bit | Minecraft is still loading; give it a few seconds. |
| "Minecraft did not answer" on the map | Start Minecraft (the Bannerlord x Minecraft profile). |
| Steve disappears at high resolutions | Frames are shared up to 1920 × 1200. Play Bannerlord windowed at that size or smaller. |
| Minecraft closes when a battle starts | Make sure you start it with the Bannerlord x Minecraft profile; it has the settings that prevent this. |
| Running out of memory | Lower Bannerlord's texture quality; the two games need about 5 GB together. |

> ⚠️ **Single player only.** ReShade's add-on build isn't meant for online games with anti-cheat,
> and the module changes how combat works. Uninstall before playing multiplayer.

## How it works

```
 Bannerlord (C#, DX11)                       Minecraft 26.3 (Java, Fabric)
 ┌─────────────────────────┐   BLMC_Host   ┌───────────────────────────────┐
 │ MinecraftPassthrough    │ ────────────► │ keys/mouse → Steve's controls  │
 │  · keyboard and mouse   │   per frame   │ ground grid → barrier columns  │
 │  · ground, nearby       │               │ agents → invisible stand-ins   │
 │    agents, hits on him  │               │ hits → damage (armour, shield) │
 │  · camera ← Minecraft   │ ◄──────────── │ camera, Steve's position       │
 │  · stand-in ← Steve     │  BLMC_Player  │                                │
 │  · hits, blasts, blocks │ ◄──────────── │ hits, explosions, blocks       │
 └──────────┬──────────────┘  BLMC_Events  │ readback: level colour+depth,  │
            │ present                      │           HUD + hand layer     │
            ▼                              └───────────────┬────────────────┘
 ┌─────────────────────────────────────────────────────────▼──────────────┐
 │ ReShade add-on (C++) + MCPassthrough.fx                     BLMC_Frame │
 │  · uploads Minecraft's latest frame to textures                        │
 │  · per pixel: reproject Bannerlord's view ray into Minecraft's frame,  │
 │    compare Minecraft depth with Bannerlord depth, keep the nearer      │
 │  · lays Minecraft's HUD (and first-person hand) on top                 │
 └────────────────────────────────────────────────────────────────────────┘
```

The three parts never call each other. They only meet in five named shared-memory blocks (the
fifth, `BLMC_Inventory`, carries the campaign inventory). Every byte is in
[docs/PROTOCOL.md](docs/PROTOCOL.md).

| Part | Language | Where |
|---|---|---|
| Bannerlord module | C# (.NET Framework 4.7.2, Harmony) | [`bannerlord-mod/`](bannerlord-mod) |
| Minecraft mod | Java 25 (Fabric, mixins, FFM API) | [`minecraft-mod/`](minecraft-mod) |
| Compositor | C++17 ReShade add-on, ReShade FX shader | [`compositor/`](compositor) |
| Tooling | PowerShell, Python | [`tools/`](tools) |

### Shared memory

- **Five named file mappings:** `Local\BLMC_Host`, `Local\BLMC_Player`, `Local\BLMC_Events`,
  `Local\BLMC_Frame` and `Local\BLMC_Inventory`. C# opens them with `MemoryMappedFile`, Java with
  the Foreign Function & Memory API (`MemorySegment`, `ValueLayout`), C++ with `OpenFileMappingW`
  and `MapViewOfFile`. The `Local\` prefix keeps them inside the current user session.
- **Why not sockets or pipes:** every frame carries colour, depth and the HUD at full resolution,
  about 27 MB at 1920 × 1200. Shared memory writes it once and the reader reads it in place; small
  state updates arrive within microseconds.
- **Seqlocks:** the writer makes the counter odd, writes, then makes it even. The reader reads the
  counter, copies the data and reads the counter again; if both are equal and even the copy is
  consistent, otherwise it skips that frame. `BLMC_Host`, `BLMC_Player` and the frame slots work
  this way.
- **Single-writer ring buffers** carry events (hits, explosions, blocks, deaths) and the blows on
  Steve. The writer fills an entry and then bumps the total; the reader catches up from its own
  last count. Missed events can be counted, and none is ever read half-written.
- **Memory barriers:** `VarHandle.releaseFence()` in Java and volatile reads in C# and C++, so the
  CPU or compiler can't reorder the writes under the reader.
- **Magic and version:** every block starts with a magic (`"BLMH"` and so on) and a version, so
  mismatched builds refuse each other's data instead of misreading it.
- **Bounds checks:** every length and offset coming from the other process (inventory text, agent
  count, frame slot offsets) is checked before use. There is no network connection at all.

### Problems I ran into while building it

- **One camera for two engines.** Bannerlord has to render from Minecraft's camera, so the override
  goes in before `MissionScreen` hands the camera to the engine (a Harmony prefix on
  `SceneView.SetCamera`). My first try was a postfix on `UpdateCamera`, which was one step late: it
  changed the published camera but not the rendered one, and F5 only moved Minecraft. The two
  engines also disagree on the up axis and handedness (`Coords.java`).
- **Input without focus.** Key presses travel as running counters, so a quick tap between two
  Minecraft ticks isn't lost. Mixins make Minecraft act focused without stealing the cursor from
  Bannerlord. With an inventory open, Bannerlord's UI swallows left clicks, so mouse buttons are
  read from Windows directly. Text for the panel's search box goes through Windows' keyboard layout
  (`ToUnicodeEx`), so Turkish letters work.
- **Fighting soldiers that only exist in the other game.** Every agent near Steve gets a frozen,
  silent husk in Minecraft. It's never drawn but can still be hit, so Minecraft's own attack code
  works on it. A mixin turns the hit into an event and Bannerlord applies it as a blow. The client
  copy of each husk is moved to the soldier's current position every frame; the server's
  position updates arrive a few ticks late, which made fast cavalry very hard to hit.
- **Damage both ways.** Steve's hits land as Bannerlord blows (`Agent.RegisterBlow`) at Minecraft
  damage × 6, reduced by the soldier's body armour (`50 / (50 + armour)`), with the weapon type
  attached so the matching skill gains experience. Blows on the stand-in go to Minecraft as their
  damage before armour (`InflictedDamage + AbsorbedByArmor`) ÷ 5, roughly a soldier's 100 health
  against Steve's 20. Blows are sorted by height: when mounted a low one hits the horse, and one
  above Steve's head misses, since he may be standing on his own tower.
- **A silent stand-in.** The player's Bannerlord character becomes invisible and immortal. Its voice
  (`MBAgentVisuals.SetVoiceDefinitionIndex(-1)` and a Harmony prefix on `Agent.MakeVoice`) and the
  sound of blows on it (a prefix on `Agent.HandleBlow` that adds `BlowFlags.NoSound`) are off.
- **The red flash.** `GameEntity.SetFactorColor` overwrites every mesh's colour, and the team colours
  of the clothes live there too (`Mesh.Color`/`Color2`). An agent's meshes hang off its `Skeleton`,
  not its entity, so the colours are read from there first and written back afterwards. The first
  version only looked at the entity and left everyone's clothes white.
- **Two depth conventions.** Both games use reversed Z. The shader turns both into metres along the
  view axis (using only `m22` and `m32` from Minecraft's projection) before comparing them.
- **Reprojection.** The Minecraft frame can be a frame or two old, so each Bannerlord pixel's ray is
  rotated into the pose Minecraft rendered with. Bannerlord renders a 5 % narrower field of view so
  turning never shows an empty edge.
- **GPU readback without stalls.** Minecraft 26's `renderpearl` API copies textures into a ring of
  mapped buffers asynchronously. The level is copied before the 3D HUD pass clears depth, and the
  GUI goes into its own premultiplied layer.
- **A vanilla Minecraft bug.** On OpenGL, a depth readback leaves the read buffer at `GL_NONE` and
  the next colour readback fails with "No color buffer".
  [`GlCommandEncoderMixin`](minecraft-mod/src/main/java/blpassthrough/mixin/GlCommandEncoderMixin.java)
  fixes it.
- **Steve vanishing.** Minecraft culls entities in sections without a compiled mesh, which in an
  empty world is every section
  ([`LevelExtractorMixin`](minecraft-mod/src/main/java/blpassthrough/mixin/LevelExtractorMixin.java)).
- **Falling through the ground.** Barrier blocks placed into chunks that weren't loaded yet were
  silently dropped. Steve fell into the void, respawned at the world spawn and dragged his stand-in
  off the battlefield. Now the chunks load first, Steve hovers until there's a block under him,
  and Bannerlord ignores any jump over 100 m.
- **Battlefields too high for Minecraft.** Minecraft can only place blocks up to y = 319, so each
  battle is shifted to put Steve's feet at y = 100. The shift used to be taken from the first frame
  of the mission, before Bannerlord had spawned the player, so it was really measured from
  Bannerlord's zero height. In a mountain hideout Steve then landed around y = 370: no ground could
  be built under him, and he hovered as if in creative mode. Now the shift waits for the player.
- **When Bannerlord moves the player.** The stand-in is put on Steve's feet every frame, so when
  Bannerlord itself teleported the player, for example to the spot of a hideout boss duel, the next
  frame pulled him straight back. A Harmony postfix on `Agent.TeleportToPosition` now catches those
  moves and sends the target to Minecraft. Steve is teleported there, and the stand-in waits until
  he arrives.
- **Stutter while flying.** With the elytra, Steve crosses the 49 × 49 m ground grid in about a
  second, and every resample cost 2401 scene queries in Bannerlord plus a scan of every barrier
  block in Minecraft. Bannerlord now caches each cell and only measures again where Steve could
  stand under something, so a resample mostly measures the new strip. Minecraft keeps the barriers
  per chunk and only looks at the chunks the new grid touches.
- **Pausing both games.** Bannerlord sets a pause flag when its engine is paused or mission time
  stops moving, and Minecraft then pauses as if a pause screen were open, without opening one. If
  Bannerlord stops publishing in the middle of a battle, Minecraft pauses too.
- **No locks.** A lock would make one game wait for the other, so there are none (see
  [Shared memory](#shared-memory)).
- **The campaign.** Steve's inventory is stored in the save as Minecraft's own item data (SNBT text)
  by a `CampaignBehaviorBase`. The trader and smith are town menu options (`AddGameMenuOption`)
  that open Bannerlord's multi-selection window, with a Harmony postfix on its view model for the
  running total. Purchases wait in a pending list until the next battle. Being captured
  (`CampaignEvents.HeroPrisonerTaken`) empties Steve's bag. Loot is read from the soldier's
  equipment (`LootDrop`) and turned into Minecraft items by material (`LootTable`).
- **Not crashing Bannerlord.** Every callback the module hands to Bannerlord (ticks, blows, menus)
  runs inside `Guard.Run`: an exception is logged and that feature switches off instead of taking
  the game down. No references to engine objects are kept once an agent is removed or a mission
  ends. Minecraft does nothing while Bannerlord isn't in a battle.
- **Patching closed code.** Bannerlord ships without source, so I decompiled its DLLs with ILSpy to
  find the methods to patch and patched them at runtime with Harmony (prefixes, postfixes,
  `AccessTools`). On the Minecraft side, Fabric's mappings and SpongePowered Mixin (`@Inject`,
  `CallbackInfo.cancel()`, `@Accessor`) do the same job, for example to silence the music, throw
  fire charges and report explosions.

## Requirements

- Windows 10/11.
- *Mount & Blade II: Bannerlord* v1.4.8 on Steam, **windowed or borderless**.
- *Minecraft: Java Edition* 26.3 and the official Minecraft Launcher (Fabric Loader 0.19.5 is set
  up by the installer).
- About 5 GB of free RAM while both games run.
- To build: .NET SDK, Visual Studio 2022 C++ tools and CMake, Python 3 (for the test tools), git.

## Building

```powershell
# JDK 25, ReShade 6.8.0 (add-on build) and its headers, into .tools/
powershell -ExecutionPolicy Bypass -File tools\fetch-deps.ps1

# Bannerlord module -> build\Modules\MinecraftPassthrough
dotnet build bannerlord-mod -c Release            # -p:BannerlordDir=... if not in the default Steam path

# Compositor -> .tools\build-compositor\Release\MCPassthrough.addon64
cmake -S compositor -B .tools\build-compositor -G "Visual Studio 17 2022" -A x64
cmake --build .tools\build-compositor --config Release

# Minecraft mod -> minecraft-mod\build\libs\blpassthrough-0.1.0.jar
$env:JAVA_HOME = (Resolve-Path .tools\jdk\jdk-25*).Path
cd minecraft-mod; .\gradlew build
```

## Running a build from source

1. Run `tools\install.ps1` (add `-MinecraftModsDir <mods folder>` to copy the jar too). It installs
   the Bannerlord module, ReShade as `dxgi.dll` (an existing one is left alone), the add-on and its
   shader. `tools\uninstall.ps1` takes it all back out.
2. Run `tools\create-shortcut.ps1` once for a "Bannerlord x Minecraft" desktop shortcut. It starts
   the Minecraft dev client with the arguments above, waits for it, opens Bannerlord with the
   module, and restarts Minecraft if it crashes while Bannerlord is open (`tools\play.ps1`).
3. Go into any battle; Custom Battle is the quickest.

Until Bannerlord is in a battle the mod does nothing. While it's active it turns off clouds, view
bobbing and pause-on-focus-loss in Minecraft and sets the GUI scale to 2. It also turns off
Bannerlord's "stop game on focus lost" so Bannerlord keeps running when you click over to
Minecraft.

## Development tools

| Tool | What for |
|---|---|
| `tools/run-minecraft-dev.sh` | Starts the dev client straight from Java (no Gradle daemon). Run `gradlew runClient` once first. |
| `tools/dev-cycle.ps1` | Closes both games, installs the builds, starts the dev client, then Bannerlord with `BLMC_AUTOSTART=1`. |
| `BLMC_AUTOSTART=1` | Bannerlord environment variable: goes straight into a custom battle, skips deployment and plays a scripted input sequence (walk, sprint, jump, swing, creeper egg, bow, F5, pearl, elytra). |
| `-Dblpassthrough.devtest=true` | Minecraft property: once an enemy is in reach, teleports Steve next to it, hits it and summons a creeper. |
| `tools/fakehost.py` | Pretends to be Bannerlord (orbiting camera, walking player) to test the Minecraft side alone. |
| `tools/fakeminecraft.py` | Pretends to be Minecraft (ray-casts a Steve-sized box) to test the compositor alone. |
| `tools/mcframe.py` | Dumps the latest exported frame (colour, depth, HUD) to PNGs. |
| `tools/screenshot.ps1` | Captures one game window, even behind others. |

The ReShade add-on logs its state to `ReShade.log`. The Minecraft mod logs more with
`-Dblpassthrough.debug=true`.

## Known limitations

- **Lighting.** Steve is lit by Minecraft. Bannerlord's sun, shadows and fog don't reach him, and he
  casts no shadow.
- **Ground.** The terrain is rebuilt in 1 m blocks, so on slopes Steve can stand half a block above
  or below it, and overhangs like gates and bridges are solid. Only 49 × 49 m around Steve exists in
  Minecraft at a time.
- **Blocks.** Blocks you place are only solid for Steve. Bannerlord's soldiers, horses and arrows
  pass through them.
- **Inventory.** Shift-click and number keys inside the inventory aren't forwarded yet; plain left
  and right clicks are.
- **Resolution.** Frames are shared up to 1920 × 1200. Above that Steve isn't drawn.
- **Crashes.** On my laptop (Intel i9-13980HX) Minecraft sometimes crashes inside `jvm.dll`: once at
  startup while loading resources, and again when the battle world is created. Both happen under
  heavy parallel load, and the crash report I managed to catch points at the JVM's C2 compiler. `-Dmax.bg.threads=4`
  stopped the startup crashes for me. `-XX:TieredStopAtLevel=1` turns C2 off for a small speed cost,
  and preloading the world moves the heavy part out of the battle start. 13th/14th-gen Intel CPUs
  are known to be unstable under this kind of load, so a BIOS update may help too.

## Releasing

`tools\package.ps1` builds all three parts and writes `dist\MinecraftPassthrough-<version>.zip` with
the module, ReShade and the add-on, the Minecraft jar and the installer from `tools\release`. The
version comes from `bannerlord-mod\Module\SubModule.xml`.

## Credits

The idea of *passthrough modding*, bridging two unmodified games at runtime, comes from community
projects that brought Minecraft into GTA V and Elden Ring. This is my own implementation for
Bannerlord. Minecraft is a trademark of Mojang/Microsoft and *Mount & Blade* is a trademark of
TaleWorlds Entertainment. This is an unofficial fan project and contains no assets from either game.

## Community

Questions, bug reports and videos of Steve in battle are welcome on the
[Psicodart Games Discord](https://discord.gg/R4qStgw9bk).

## License

[MIT](LICENSE). The release package also ships ReShade (BSD 3-Clause) and Harmony (MIT); see
`tools/release/THIRD-PARTY-NOTICES.txt`.
