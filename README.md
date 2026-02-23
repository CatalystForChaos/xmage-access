# XMage Access

Screen reader accessibility agent for [XMage](https://xmage.today) — a free, open-source Magic: The Gathering client.

XMage Access makes XMage fully playable with a screen reader. It works with **NVDA, JAWS, and Windows SAPI** on Windows, and **VoiceOver** on macOS.

## Download

Get the latest release (zip with everything you need) from the [Releases page](https://github.com/CatalystForChaos/xmage-access/releases).

## How It Works

XMage Access is a Java agent that hooks into the XMage client at startup via `-javaagent`. It attaches keyboard shortcuts and accessible windows to each part of the XMage UI without modifying XMage itself.

When a game starts, an **Accessible Game Window** opens automatically alongside XMage, showing all game zones (hand, battlefield, stack, graveyard, exile, game log) in a navigable list format. Every zone updates in real time.

There is also an **Accessible Lobby Window** that lists all open games and players, with actions for joining, creating games, opening the deck editor, and more.

## What's Covered

- **Connect dialog** — field announcements, Tab navigation
- **Lobby** — list all games, join, watch, create game/tournament, deck editor, chat
- **New Game / New Tournament** — all settings announced, keyboard navigation
- **Deck Editor** — search, add/remove cards, load/save, color/type/rarity filters
- **Sideboarding** — move cards, read counts, submit deck
- **Draft** — navigate picks, read card details, time remaining
- **Gameplay** — prompts, hand, battlefield, stack, targets, combat, mana pool
- **Phase announcements** — turn changes, combat phase, second main phase
- **All choice dialogs** — pick one, pick number, checkboxes, pile split, distribute
- **Revealed cards** — scry, opponent reveals, targeting from card list
- **Tournament Panel** — standings, matches, watch
- **Download Images** — source/set selection, progress
- **Preferences** — tab navigation, read and understand all settings
- **Global** — Ctrl+Q to quit

## Installation

### Windows

1. Download XMage from [xmage.today](https://xmage.today) and install it.
2. Copy `xmage-access-0.1.0.jar` into `xmage\mage-client\lib\`.
3. Copy `run-accessible-launcher.cmd` into the XMage root folder.
4. Copy all files from `tolk\x64\` into the `mage-client` folder (for NVDA/JAWS).
5. Double-click `run-accessible-launcher.cmd`, check "Enable screen reader support", then click Launch XMage.

### macOS

1. Download XMage from [xmage.today](https://xmage.today) and install it.
2. Install [Azul Zulu 8 for macOS](https://www.azul.com/downloads/?version=java-8-lts&os=macos) (XMage requires Java 8).
3. Copy `xmage-access-0.1.0.jar` into `xmage/mage-client/lib/`.
4. Copy `run-accessible-launcher.command` into the XMage root folder.
5. In Terminal: `chmod +x run-accessible-launcher.command`
6. Double-click the script, check the checkbox, then launch.

No Tolk DLLs are needed on macOS — speech uses the built-in `say` command.

## Key Shortcuts

### Global
| Shortcut | Action |
|----------|--------|
| Ctrl+Q | Quit client |

### Gameplay
| Shortcut | Action |
|----------|--------|
| Ctrl+F1 | Read current prompt and phase |
| Ctrl+F2 | Read player life totals |
| Ctrl+F3 | Read hand |
| Ctrl+F4 | Read battlefield |
| Ctrl+F5 | Read stack |
| Ctrl+Left/Right | Navigate hand |
| Ctrl+Enter | Play selected card |
| Ctrl+1/2/3 | Click OK / Cancel / Special button |
| Ctrl+Z | Undo |

### Lobby
| Shortcut | Action |
|----------|--------|
| Ctrl+G | List games |
| Ctrl+Up/Down | Navigate games |
| Ctrl+J | Join selected game |
| Ctrl+N | Create new game |
| Ctrl+E | Open deck editor |

See `dist/README-accessible.txt` for the full shortcut reference.

## Building from Source

Requires Java 8 and Maven.

```
mvn package
```

The built jar is at `target/xmage-access-0.1.0.jar`. Copy it to `xmage/mage-client/lib/` in your XMage installation.

## License

This project is released for free use. XMage itself is licensed under the MIT License. Tolk is included under its own license (see `dist/tolk/LICENSE-Tolk.txt`).
