# Map Zones

Markierte Bereiche ("Zonen") auf **Xaero's World Map** – serverseitig gespeichert und für
alle Spieler sichtbar. Bereiche werden auf der Karte ausgewählt, benannt und gefärbt.

* Minecraft 1.21.1 · NeoForge 21.1.248
* Benötigt auf dem Client: **Xaero's World Map** (getestet mit 1.47.0)
* Auf dem Server wird Xaero **nicht** gebraucht

## Installation

| Wohin | Was |
|---|---|
| Server `mods/` | `apx-mapzones-neoforge-1.21.1-1.0.1+mc1.21.1.jar` |
| Jeder Client `mods/` | dieselbe Jar |

Der Server speichert alle Zonen in `world/data/mapzones.dat`. Clients ohne die Mod können
weiterhin spielen – sie sehen die Zonen nur nicht.

## Bedienung auf der Karte

1. Weltkarte öffnen (Standard: `M`).
2. **Rechte Maustaste gedrückt halten und ziehen** – Xaero markiert die Chunk-Auswahl.
3. Maustaste loslassen → im Kontextmenü **„Bereich als Zone markieren…"** wählen.
4. Namen eintippen, Farbe aus der Palette oder als `#RRGGBB` wählen, **Speichern**.

Die Zone erscheint sofort bei allen Spielern auf dem Server.

* Rechtsklick auf eine bestehende Zone → **„Zone bearbeiten: …"** (Name, Farbe, Löschen).
* Rechtsklick → **„Zonen ausblenden/einblenden"** schaltet die Anzeige lokal um.
* Beim Überfahren einer Zone zeigt die Karte unten `■ Zonenname (Ersteller)`.

### Flächen zusammensetzen

* Auswahl ziehen, die eine vorhandene Zone berührt → **„Auswahl zu Zone hinzufügen: …"**.
  So wächst eine Zone über beliebige Formen, ohne dass eine Trennlinie entsteht.
* Rechtsklick auf eine Zone → **„Verbinden mit: …"** für jede angrenzende Nachbarzone.
  Name, Farbe und Besitzer der angeklickten Zone bleiben erhalten, die Umrandung zwischen
  beiden verschwindet.
* **Automatisch:** Berühren sich zwei Zonen mit *identischem Namen und identischer Farbe*,
  werden sie sofort zu einer Zone zusammengefasst – auch beim Umbenennen oder Umfärben.

## Befehle

Alle Koordinaten sind Blockkoordinaten; `~` funktioniert wie gewohnt.

```
/mapzone list                                  Zonen der aktuellen Dimension
/mapzone info <name>
/mapzone create <name> <x1> <z1> <x2> <z2> [farbe]
/mapzone here <name> <radiusInChunks> [farbe]
/mapzone add <name> <x1> <z1> <x2> <z2>        Teilfläche zu einer Zone hinzufügen
/mapzone merge <name> <andereZone>             zwei angrenzende Zonen verbinden
/mapzone rename <name> <neuerName>
/mapzone color <name> <farbe>
/mapzone delete <name>
```

Farbe = Palettenname (`rot`, `orange`, `gelb`, `limette`, `gruen`, `tuerkis`, `cyan`,
`hellblau`, `blau`, `violett`, `magenta`, `pink`, `braun`, `weiss`, `grau`, `schwarz`)
oder Hex, z. B. `#3FA9F5`.

## Rechte und Grenzen

* Jeder Spieler darf Zonen anlegen und **seine eigenen** bearbeiten oder löschen.
* Spieler mit Permission-Level 2 (OP) dürfen **alle** Zonen bearbeiten und löschen.
* Grenzen (in `ZoneRules.java` anpassbar): 500 Zonen je Server, 50 je Spieler,
  4096 Chunks je Zone, 256 Teilflächen je Zone, 48 Zeichen je Name.

## Clienteinstellungen

`config/mapzones-client.toml`:

| Option | Standard | Bedeutung |
|---|---|---|
| `showZones` | `true` | Zonen anzeigen |
| `fillOpacity` | `45` | Deckkraft der Fläche in % |
| `borderOpacity` | `95` | Deckkraft des Randes in % |
| `showOwnerInTooltip` | `true` | Ersteller im Tooltip |

## Technische Hinweise

* Die Zonen werden über Xaeros Highlighter-System gezeichnet
  (`xaero.map.highlight.ChunkHighlighter`). Dieselben Kartenkacheln benutzt auch die
  Minimap – Zonen können dort also ebenfalls erscheinen.
* Zwei Mixins greifen in Xaero: `HighlighterRegistry#end` (Highlighter registrieren) und
  `GuiMap#getRightClickOptions` (Kontextmenü). Bei einem größeren Xaero-Update kann es
  sein, dass diese Namen angepasst werden müssen.
* Fehlersuche: Start mit `-Dmapzones.debug=true` protokolliert die ersten eingefärbten
  Chunks pro Sitzung.

## Bauen

Voraussetzungen: JDK 21, Gradle 8.14+.

Xaero's World Map wird zum Kompilieren gebraucht (Highlighter-API und Mixin-Ziele), darf
aber aus Lizenzgruenden nicht im Repo liegen. Vor dem ersten Build die Mod-Jar selbst
ablegen:

```
libs/xaeroworldmap-neoforge-1.21.1-1.47.0.jar
```

Fuer den Testclient zusaetzlich nach `run/client/mods/` kopieren.

```
gradle build          # Ergebnis in build/libs/
gradle runClient      # Testclient
gradle runServer      # Testserver
```
