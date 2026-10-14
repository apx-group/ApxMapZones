# Apx: Zone extention

Marked areas ("zones") on **Xaero's World Map** — stored server-side and visible to all players. Areas are selected, named, and colored directly on the map.

* Minecraft 1.21.1 · NeoForge 21.1.248
* Required on the client: **Xaero's World Map** (tested with 1.47.0)
* Xaero is **not** required on the server

## Two builds

The same code base produces two JARs. Install **one** of them - they share the mod id, so
the game refuses to start with both.

| JAR | Zones | Build/break permissions |
|---|---|---|
| `apx-mapzones-neoforge-...` | yes | no |
| `apx-mapzones-protect-neoforge-...` | yes | yes |

Zone data is compatible in both directions, so you can switch builds without losing zones.

## Installation

| Location | What |
|---|---|
| Server `mods/` | `apx-mapzones-neoforge-1.21.1-1.1.0+mc1.21.1.jar` (or the `-protect-` build) |
| Every client `mods/` | The same JAR |

The server stores all zones in `world/data/mapzones.dat`. Clients without the mod can still play — they just won't see the zones.

## Using the Map

1. Open the world map (default: `M`).
2. **Hold the right mouse button and drag** — Xaero selects the chunks.
3. Release the mouse button → select **"Mark Area as Zone..."** from the context menu.
4. Enter a name, choose a color from the palette or enter a `#RRGGBB` value, then click **Save**.

The zone immediately appears for all players on the server.

* Right-click an existing zone → **"Edit Zone: ..."** (name, color, delete).
* Right-click → **"Hide/Show Zones"** toggles zone visibility locally.
* Hovering over a zone displays `■ Zone Name (Creator)` at the bottom of the map.

### Combining Areas

* Drag a selection that touches an existing zone → **"Add Selection to Zone: ..."**. This allows a zone to grow into any shape without creating a dividing line.
* Right-click a zone → **"Merge ... into ..."** for each adjacent neighboring zone. The clicked zone is absorbed into the neighbor: the neighbor keeps its name, color and owner, the clicked zone disappears and the border between them is gone.
* **Automatic:** If two zones with *identical names and identical colors* touch, they are immediately merged into a single zone — including when renaming or recoloring them.

## Permissions inside a zone (Protect build only)

By default every zone is open: anyone may build, break and use blocks. The owner can
restrict this per zone.

1. Right-click the zone on the world map → **"Edit Zone: ..."**
2. Click **"Permissions in this zone..."**
3. Turn off *Everyone may build / break / use blocks* as needed.
4. Add single players by name and tick **build**, **break** and **use** individually.

* The zone owner always keeps full access to their own zone.
* The restriction applies to **everyone else, including server operators**. Operators can
  still edit any zone, so they can add themselves to the list when they need access.
* Changing permissions has a 10 second cooldown per player (operators are exempt).
* A player must have joined the server at least once so the server knows their UUID.
* Blocked actions: breaking and placing blocks, and using blocks such as chests, furnaces,
  doors, buttons and levers.

Fallback command: `/mapzone perms <zone> [public <build|break|use> <true|false> | player <name> <build> <break> <use>]`

## Commands

All coordinates are block coordinates; `~` works as usual.

```
/mapzone list                                  List zones in the current dimension
/mapzone info <name>
/mapzone create <name> <x1> <z1> <x2> <z2> [color]
/mapzone here <name> <radiusInChunks> [color]
/mapzone add <name> <x1> <z1> <x2> <z2>        Add a sub-area to a zone
/mapzone merge <name> into <otherZone>          Merge a zone into an adjacent zone
/mapzone rename <name> <newName>
/mapzone color <name> <color>
/mapzone delete <name>
```

Color = palette name (`red`, `orange`, `yellow`, `lime`, `green`, `turquoise`, `cyan`, `lightblue`, `blue`, `purple`, `magenta`, `pink`, `brown`, `white`, `gray`, `black`) or hex, e.g. `#3FA9F5`.

## Permissions and Limits

* Every player can create zones on **unclaimed area** and edit or delete **their own** zones.
* Only the owner may extend a zone. Creating **another zone on top of** an existing
  zone's area (for example a smaller zone inside it) is likewise owner-only.
  Foreign zones can only be bordered, never covered.
* Zones with the same name and color merge on contact only when both belong to the
  same player.
* Players with permission level 2 (OP) can edit, extend and delete **all** zones.
* Limits (configurable in `ZoneRules.java`): 500 zones per server, 50 per player, 4096 chunks per zone, 256 sub-areas per zone, 48 characters per name.

## Client Settings

`config/mapzones-client.toml`:

| Option | Default | Description |
|---|---|---|
| `showZones` | `true` | Show zones |
| `fillOpacity` | `45` | Area fill opacity in % |
| `borderOpacity` | `95` | Border opacity in % |
| `showOwnerInTooltip` | `true` | Show creator in tooltip |

## Technical Notes

* Zones are rendered using Xaero's highlighter system (`xaero.map.highlight.ChunkHighlighter`). The minimap also uses the same map tiles, so zones may appear there as well.
* Two mixins interact with Xaero: `HighlighterRegistry#end` (registering the highlighter) and `GuiMap#getRightClickOptions` (context menu). A major Xaero update may require these names to be adjusted.
* Troubleshooting: Starting with `-Dmapzones.debug=true` logs the first colored chunks per session.

## Building

Requirements: JDK 21, Gradle 8.14+.

Xaero's World Map is required for compilation (highlighter API and mixin targets), but cannot be included in the repository for licensing reasons. Before the first build, place the mod JAR at:

```
libs/xaeroworldmap-neoforge-1.21.1-1.47.0.jar
```

For the test client, also copy it to `run/client/mods/`.

```
gradle build          # Output in build/libs/
gradle runClient      # Test client
gradle runServer      # Test server
```
