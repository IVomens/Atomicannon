# Atomicannon (NeoForge 26.2)

A standalone port of **only the schematic system** from the Create mod, for Minecraft **26.2 / NeoForge**, with
support for **HBM's Nuclear Tech Mod (ntm-next)** multiblock machines. Not affiliated with the Create team.

## Install

Put `build/libs/schematicannon-<version>.jar` in the `mods/` folder of **the server and every player's client**
(the hologram, tools and screens are client side). NTM is optional; without it the mod works on its own.

Build it yourself with JDK 25 available (Gradle downloads one automatically if needed):

```bash
./gradlew build
```

## How it works (same flow as Create)

| Item / block | What it does |
|---|---|
| **Empty Schematic** | paper + light blue dye |
| **Schematic and Quill** | Empty Schematic + feather. Right-click two corners (hold **Ctrl** to pick in mid-air, **Ctrl-scroll** a face to resize), right-click again to name and save. Saves to `.minecraft/schematics/`. "Upload" also writes it straight onto the item. Shift-right-click clears the selection. |
| **Schematic Table** | Put an Empty Schematic in, pick a file from `.minecraft/schematics/`, press the arrow to upload it to the server. |
| **Schematic** | Hold it to see the hologram. Hold **Left Alt** for the tool menu (Position, Move XZ, Move Y, Rotate, Mirror, and Print in creative); **Ctrl-scroll** uses the tool. Shift-right-click opens exact coordinates. |
| **Schematicannon** | Put the positioned schematic in, add gunpowder (or let it pull from neighbouring inventories), press Start. It pulls blocks from any adjacent chest, barrel or crate and fires them into place. A Book in the checklist slot prints a material list. |
| **Clipboard** | Planks + paper + iron nugget. Right-click to write a checklist: type a line and press Enter for the next, click a box to tick it, arrows for more pages. Put one in the Schematicannon's checklist slot to get the material list on it (item icon + amount still needed); put it back in later to update it: items the attached inventories now cover drop off, your ticks and notes stay. |
| **Creative Crate** | Placed next to a cannon: unlimited materials and no gunpowder needed. No recipe, unbreakable in survival, admin and creative only. |

Existing Create `.nbt` schematics work as-is: the folder layout is the same (`schematics/` on the client,
`schematics/uploaded/<player>/` on the server).

## NTM multiblocks

NTM machines are one **core** block plus invisible **cell** blocks. A schematic captures all of them; the cannon:

* skips the cells (they cost nothing),
* charges **one machine item** for the core,
* places the core and asks NTM to rebuild the cells, exactly as NTM's own structure loader does.

This can be turned off with `assembleNtmMultiblocks` in the server config.

**Settings are kept, contents are not.** NTM blocks paste with their configuration: fluid identifier types (machines
and ducts), recipes (chemical plant/factory, assembly machine/factory), filters and modes (ejectors, inserters,
funnels), radio (RoR) channels, mass storage item type, paint on cables/ducts/tubes, turbine steam density, and the
wiring of pylons and links of pipe anchors (when both ends are in the schematic). Before pasting, the copy is emptied of
every item (filter slots aside), fluid, power and mass storage stock. Paint blocks and recipe blueprints in machines are
charged like any other item. `copyNtmMachineSettings` in the server config turns this off.

**Linked blocks are re-aimed.** Blocks that point at another block by position (RBMK consoles and displays, the RBMK
crane console, solar mirrors, radar linkers in radars) are pointed at the same spot in the paste, rotation included.
Redstone-over-radio blocks (senders, receivers, readers, controllers, logic, telex, AUTOCAL) and RBMK gauges,
indicators and levers keep their channels, labels and scripts.

**Local saves get the server's data.** A Schematic and Quill save made on a player's computer only holds what the
server had sent that client (e.g. no filter items or RBMK display links), so after saving the client asks the server for
the full machine settings of that area and adds them to the file. The server only shares NTM machine settings
(contents emptied), only for blocks the player may build on (land claims), within the instant-save limits.

NTM conveyors are paid for with conveyor wands (one per belt; regular wands also cover lifts and chutes).

## Basic and Advanced Atomicannon

* **Basic Atomicannon**: the Create-style cannon, fuelled by gunpowder.
* **Advanced Atomicannon** (only exists when NTM is installed): runs on NTM power (HE) instead of gunpowder and fires
  faster. Connect NTM cables to the red power ports on the sides or bottom of its base (sized and centred to match
  NTM cables exactly), or put an NTM battery in its battery slot, which it drains like an NTM machine. Each block costs
  `advancedEnergyPerShot` HE (default 500), it stores `advancedEnergyCapacity` HE (default 100k) and fires every
  `advancedDelay` ticks (default 3, vs 10 for the basic cannon). Crafted from a Basic Atomicannon, steel plates, a motor,
  copper coils and a basic circuit. A Creative Crate still powers it for free.

Without NTM the Advanced Atomicannon, its recipe and loot table are simply absent; everything else works unchanged.

## WarForge claims

With WarForge installed, schematics follow faction claims exactly like building by hand:

* The player who presses **Start** on a cannon is its owner; every block it places (or replaces / clears) is checked
  against WarForge's place/break protection for that player at that spot. Creative instant-print uses the printing player.
* Outsiders get nothing inside a foreign claim, even from a cannon parked outside it; members build normally.
  Sieges, allies, safe zones, war zones and WarForge's op override all behave as they do for hand placement.
* NTM machines are only placed if every block of the machine is allowed.
* Blocked blocks are skipped; the cannon shows "(N blocked by claims)". If the claim check itself ever fails, the cannon
  blocks everything rather than risk building into a base.

## Creative to survival safety

Like Create, block entity data is **not** copied by default: chests arrive empty and machines arrive freshly
built (no fuel, fluids, items or power). Signs and banners keep their text and patterns (sign click-commands are
stripped). Item frames and armor stands are placed empty. To allow more blocks to carry their data, add them to
the `schematicannon:safe_nbt` block tag with a datapack (inventory, fluid and power keys are still removed).

## Running on a server

Built to stay out of the way of the server tick:

* **Cannons** share a small time budget per tick for scanning past blocks that are already right
  (`cannonScanBudgetMs`), count their material list over several ticks, and at most two cannons load a schematic per
  tick. Schematic files are cached after the first read. Blocks in flight wait instead of loading chunks to land.
* **Creative instant print** places a schematic over several ticks within `instantPrintTickBudgetMs`, only near the
  player, one print per player at a time.
* **Schematic and Quill instant save** is limited to `maxInstantSaveVolume` blocks, loaded chunks only, with a short
  cooldown. Bigger saves still work through a Schematic Table upload.
* **Safety:** uploaded files are size-checked, decompressed with a 64 MB cap and rejected beyond 1024 blocks per side.
  An error while printing stops only that cannon (logged) instead of crashing the server; a block that fails to place
  is skipped.

Measured on a test server (`gradlew runGameTestServer -Pstress`): 16 cannons scanning a 27k-block schematic at once
average ~15 ms per tick, 8 cannons firing ~3 ms, and a creative instant print of the same schematic ~7 ms.

## Server config (`world/serverconfig/schematicannon-server.toml`)

`maxSchematics`, `maxTotalSchematicSize` (KB), `maxSchematicPacketSize`, `schematicIdleTimeout`,
`schematicannonDelay`, `schematicannonShotsPerGunpowder`, `creativePrintIncludesAir`, `assembleNtmMultiblocks`.
All defaults match Create. Advanced Atomicannon: `advancedDelay`, `advancedEnergyPerShot`, `advancedEnergyCapacity`.
Server load: `cannonScanBudgetMs`, `instantPrintTickBudgetMs`, `maxInstantSaveVolume`. NTM: `copyNtmMachineSettings`.

## Differences from Create

* **Art:** Create's textures, models and GUI art are "All Rights Reserved", so this mod ships its own original
  art in a similar brass / andesite style (blocks, items, GUIs, icons). Layout and controls follow Create.
  The art is generated by `tools/art/*.ps1` (`BlocksItems.ps1`, `Gui.ps1`), so it can be tweaked and re-run.
* **Not ported:** Create-only content that the schematic system special-cases (belts, super glue, Create
  contraptions) and the Clipboard checklist output (Books still work).
* **Preview:** block-entity-rendered blocks (NTM machines, chests, signs...) are drawn by their own renderers in
  the hologram, like Create. A renderer that cannot work outside a real world falls back to an outline box.

## Development

* `libs/` holds WarForge and LDLib2 jars used only to compile and test the WarForge integration; they are never bundled.


* `./gradlew runGameTestServer`: headless automated tests. One rebuilds a structure through the cannon and checks
  the chest arrives empty; with NTM on the dev classpath, another prints a real NTM chemical plant and checks the
  core and all 26 cells are rebuilt.
* `./gradlew runPreviewTest`: opens `run/saves/preview_test`, builds an NTM chemical plant, deploys it as a schematic hologram, screenshots it to `run/screenshots/`, then quits.
* `./gradlew runClient` / `runServer`: dev client and server.

## License

Code is MIT, as is Create's code it is ported from (Copyright (c) The Create Team). See `LICENSE`.
