# Devlog

Current state of the work, so it can be picked up from a cold start. Reasoning
for individual changes lives in the commit messages; this file carries what git
cannot: what is unfinished, untested, or deliberately left alone.

Last updated: 2026-08-29.

## Where things stand

Released: **v0.1.13** (tag + GitHub release with `xmage-accessible.zip`).

Committed since, unreleased:

- **Game actions** (`ui/GameActions.java`) — skip actions, hold priority, mana
  payment options, auto-answer resets, rollback, view a player's deck or
  sideboard, hand-card permissions, concede, stop watching. Reached with
  `Ctrl+Shift+F3…F11` (mirroring XMage's own F3–F11), `Ctrl+Shift+H`, and the
  `Ctrl+K` action menu.
- **Five dialogs that used to be silent** — `JoinTableDialog`, `AddLandDialog`,
  `ErrorDialog`, plus registration and password reset via the reusable
  `FormDialogHandler`.
- **Deck editor filter fix** — toggling a filter used to invert itself and could
  take the client down. See the commit for the mechanism; `harness/` pins it.
- **What's new dialog** — the modal news page that opens by itself at startup.
  Its content is a JavaFX WebView, invisible to a screen reader, so the client
  just looked frozen. Now announced, read out of the WebView's own DOM line by
  line (Ctrl+Down/Up), copyable (Ctrl+C), and openable in the system browser
  (Ctrl+B), which is the better place to read it — links and all.
- **Random packs selector** — the set pool for random, rich man and chaos
  reshuffled drafts: several hundred checkboxes labelled with bare set codes,
  in a heavyweight AWT panel. Now a spoken cursor (Ctrl+Up/Down, Ctrl+Space),
  with Ctrl+F to find a set by name or code, since browsing a few hundred sets
  one at a time is not a plan.
- **No agent shortcuts inside XMage's own window.** They were never used
  there — the work happens in the accessible windows — and swallowing keys
  there only took bindings away from XMage. `GamePanelHandler` and
  `LobbyHandler` now gate on `UiUtils.isActiveWindow(theirOwnWindow)`; the
  deck editor's three shortcuts were already unreachable and are gone; dialog
  handlers and `Ctrl+Q` are unaffected.
- **Accessible draft and tournament windows.** The two surfaces that had no
  window of their own, and therefore lost their keyboard entirely in the
  change above. `AccessibleDraftWindow` is the draft status, the booster and
  your pool as three lists — Enter picks, through the same `sendCardPick`
  call XMage's own click makes. `AccessibleTournamentWindow` is the
  tournament status, standings, matches and chat, with Enter watching a
  match exactly when XMage's own action cell offers it.
- **Both were reworked to the shape of the existing windows.** They were
  written first with a shortcut set of their own — Ctrl+R for the status,
  Ctrl+T for the draft clock, Ctrl+M for the chat box, Ctrl+F1 for help —
  which was the wrong instinct: it carried XMage's panel shortcuts across
  instead of building the window the way the lobby is built. What those keys
  read is now a zone of its own in each window, `Draft` and `Tournament`,
  refreshed by the same poll, so it is found with Tab and the arrow keys
  like everything else. Enter and D are bound only on the zones that hold
  cards. What is left in both windows is Tab, the arrows, Enter, D and
  Escape. The rule is written down in `CLAUDE.md`.
- **Accessible windows take the keyboard when they open.** The counterpart
  the shortcut change needed, and a real defect rather than a worry: each
  window focused a list inside itself with `requestFocusInWindow()`, which
  the JDK grants only if that window "is already the focused Window". A
  window opening behind XMage's frame — or behind the still-modal connect
  dialog — therefore got neither the focus nor, now, its shortcuts.
  `UiUtils.focusAgentWindow` raises the window first and holds the component
  request until the window reports the focus. The lobby takes it when it is
  announced rather than when it is built, and again when it returns from a
  game, unless another accessible window is active — which is where the user
  is while building a deck after a draft.

**None of it has been tested in a running game yet.** The build is installed at
`~/Downloads/mage/xmage/mage-client/lib/xmage-access-0.1.0.jar`, with the
v0.1.13 build kept beside it as `.v0.1.13.bak` for rollback.

`TESTPLAN.txt` in the repo root is the plan for that round: 32 numbered
checks in German, each with a `Ergebnis:` line to fill in, written so the
results can be read back into this file afterwards. It is plain text and
carries the installed build's MD5 so it is obvious which build was tested.

## The first test round, 2026-08-29

`TESTPLAN.txt` carries the results in full; this is what came of them.

**Two failures still open, and they are the same failure.** The lobby window
does not take the keyboard after connecting (A4), and does not take it back
when a match ends (D10). In both cases the focus stays in XMage's own frame
and Alt+Tab reaches the window. `toFront()` plus `requestFocus()` is a
request to the window manager, and something is turning it down or taking
the focus straight back — the What's New dialog, which XMage opens by itself
right after connecting, is one candidate, but that is a guess and guesses are
not what this file is for. `util/Log` now writes to a file for exactly this
reason (see below), with the focus path logging what it asked for, what was
active at the time, and whether the window ever reported gaining it. The next
run should say which.

**A log file.** Launched through XMage's own launcher the client has no
console, so everything the agent printed was lost; `mageclient.log` is log4j
only and never saw it. `Log` now writes to `xmage-access.log` in the client's
working directory (falling back to the user's home), truncated at every
start. `Log.event` is the level that is always written: windows opening,
focus being asked for, handlers attaching.

**Fixed: Ctrl+A in the deck editor did nothing (C5).** Every look and feel
binds Ctrl+A `WHEN_FOCUSED` on JList to `selectAll` and on JTextField to
`select-all`, and that map is consulted before `WHEN_IN_FOCUSED_WINDOW` — so
the window's Add Lands never fired while a list or the search field had the
focus, which is always. The zone lists now bind Ctrl+A to Add Lands
themselves (select-all means nothing in a single-selection list); the search
field keeps its own, where it is worth having.

**Fixed: the zones talked over themselves (G).** `ZoneListPanel.updateItems`
now drops a refresh that would change nothing, comparing text, detail text,
source object and action type. The timers rebuild the lists on a fixed
schedule; clearing and refilling a JList fires accessibility events at the
screen reader whether or not anything is different.

**Shortcuts removed, on the same rule as the draft and tournament windows.**
Everything below had a route through the accessible windows already:

- The lobby's thirteen keys are gone, and `LobbyHandler` with them shrank
  from 709 lines to 151. Ctrl+G, Ctrl+J, Ctrl+W, Ctrl+N, Ctrl+E, Ctrl+D,
  Ctrl+R, Ctrl+P, Ctrl+I, Ctrl+M, Ctrl+Shift+M and the Ctrl+Up/Down cursor
  all repeated rows of `AccessibleLobbyWindow`. Marcel named eleven of them;
  Ctrl+W and Ctrl+I went too, because the window's Enter already joins *or*
  watches and the zones announce their own counts.
- The join-table dialog keeps Ctrl+D, which stands in for XMage's
  JFileChooser, and nothing else: the password field and the OK button are
  in the dialog's own Tab order.
- In game, the navigation and clicking keys are gone — the hand cursor,
  Ctrl+Enter to play, Ctrl+D, the battlefield walk, Ctrl+T and
  Ctrl+Shift+1-9 for targets, Ctrl+1/2/3 for the buttons, Ctrl+Z, Ctrl+M.
  `AccessibleGameWindow`'s Actions zone already carries the prompt, the
  OK/Cancel/Special/Undo buttons, the ability and target choices; the Hand
  and battlefield zones carry the rest. What stays is what has no equally
  quick route: the Ctrl+F1-F11 zone reads, the game log, XMage's skip keys
  and the Ctrl+K menu. `GamePanelHandler` lost 241 lines.

**Still open from the round:**

- **Ctrl+F1 in game (D4)** — reported as the one read that misbehaves, but
  not how. It now logs what it found: the helper panel, the feedback text,
  the buttons and what it spoke.
- **The What's New page (A2)** — works, but should be a window with the text
  as navigable rows rather than a dialog driven by Ctrl+Down/Up.
- **The whole draft and tournament block (E1-E5) and F1-F3** were not
  reached.

What the focus change still cannot prove without a client: `toFront()` is a
request to the window manager, not a guarantee. Watch, in that test round,
whether each accessible window really comes up with the keyboard — the lobby
after connecting, the game window when a game starts, the draft window when
the first booster arrives, and the lobby again when a game ends. If one of
them does not, Alt+Tab still reaches it; the agent has no key of its own for
that, deliberately, since it takes no keys inside XMage's window.

## Verified so far

- Builds on JDK 8, class file version 52, loads under `-javaagent`.
- Every field name, method signature, enum constant and string literal the new
  code depends on was checked against both the `../mage` source tree and the
  installed **1.4.61** client JARs with `javap`. That includes the JavaFX
  signatures the news dialog leans on, checked against the `javafx-*-11.0.2`
  JARs the client ships.
- `harness/` — eight harnesses, 116 checks, all passing: game actions (27),
  the deck editor filter defect (13), the news dialog's reading path (12), the
  pack selector's cursor (14), the draft window's pick (15), the tournament
  window's watch (11), the window-focus helpers (15) and the zone refresh
  (9).

## Known gaps, from the audit of the XMage client

Found by walking the client's UI classes and diffing against what `UIWatcher`
attaches to. Deliberately not addressed yet:

| Surface | Why it matters |
|---|---|
| Card Viewer (`deckeditor/collection/viewer/MageBook`) | Its own main-menu feature, drawn as images with hover buttons. No handler at all. Partly covered by the deck editor's set browsing. |
| `DeckImportClipboardDialog` / `DeckExportClipboardDialog` | Deck import/export via clipboard. |
| `AboutDialog`, `FeedbackDialog`, `CardHintsHelperDialog`, `CustomOptionsDialog`, `CardInfoWindowDialog` | Minor. |

Two smaller ideas that came out of using the deck editor:

- XMage's **Alt+click on a filter button means "select only this one"** (see the
  toolbar tooltips and `CardSelector.inverter`). Now that the agent controls the
  modifiers it sends, this could be offered deliberately — "only creatures" in
  one keystroke instead of six.
- The **unique-names checkbox** (`chkUnique`) is read out but cannot be toggled.
  It roughly halves the result list: the card database holds 92,030 printings,
  43,536 of them creatures, but only 17,827 distinct creature names.

## Things worth knowing before touching this

- `hooks/`, `handlers/GameStateTracker.java` and the ByteBuddy dependency are
  dead code — no transformer ever installs the advice.
- The deck editor window is open during sideboarding *while the game panel is
  still visible*, so its shortcuts and the game shortcuts share a keyboard.
  `Ctrl+Shift+E/N/F/T/C` are taken there; that is why the skip actions use
  function keys.
- Only the set selection narrows XMage's card query. The search term is applied
  afterwards in Java, over cards already materialised, so it costs the same
  database pass as no search term at all.
