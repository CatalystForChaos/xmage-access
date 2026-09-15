# Devlog

Current state of the work, so it can be picked up from a cold start. Reasoning
for individual changes lives in the commit messages; this file carries what git
cannot: what is unfinished, untested, or deliberately left alone.

Last updated: 2026-09-15.

## Where things stand

Released: **v0.1.13** (tag + GitHub release with `xmage-accessible.zip`).

Committed since, unreleased, oldest first:

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
- **Random packs selector** — the set pool for random, rich man and chaos
  reshuffled drafts, as a spoken cursor with a search by name or code.
- **No agent shortcuts inside XMage's own window.** Panel handlers gate on
  their own accessible window being active; dialog handlers and `Ctrl+Q` are
  exempt.
- **Accessible draft and tournament windows**, built like the lobby window:
  status, booster and picks, or status, standings, matches and chat, as lists.
- **What the first test round turned up** — accessible windows take the
  keyboard when they open; `xmage-access.log`; Ctrl+A in the deck editor; the
  keys that only repeated a row are gone from the lobby, the join-table dialog
  and the game. A review before committing found announcements that still
  named the removed keys and about 300 orphaned lines; both fixed there.
- **The lobby takes the keyboard back after a match** (test D10). XMage never
  hides the lobby for a game, so the agent cannot notice it coming back;
  `UIWatcher` hands the keyboard on whenever one of the agent's windows closes.
- **List refreshes no longer move the selection.** Rows are replaced in place,
  so a change elsewhere in a list no longer re-announces the row you are on. A
  row that stands for something and changes under the cursor is read out.
- **The news page is a window of its own.** `AccessibleNewsWindow`: the page as
  rows, its headings as a Sections list to jump from, and Actions to open it in
  the browser, copy it or close it. The dialog's shortcuts are gone.

Installed at `~/Downloads/mage/xmage/mage-client/lib/xmage-access-0.1.0.jar`:
the build of 15 September, MD5 `8b4a03d640b9c63bd86a6907759e15c0`. Beside it,
for rollback: `.2026-08-29.bak`, the build meant for round two, and
`.v0.1.13.bak`, the last release.

## Test rounds

`TESTPLAN.txt` holds all of them, in German, with a result line per check.

**Round one, 29 August** (sections A–G). What came of it is in the commit
"fix: what the first test round turned up", apart from D10, which has a commit
of its own.

**Round two, planned 29 August** (sections I and J) — never run as a whole.
The client ran once afterwards, on 2 September: connected, opened the deck
editor, played no game; the server log shows none either. The agent log of
that run shows the lobby window reporting the focus 400 ms after asking for
it, and still being the active window 30 seconds later when the deck editor
took the keyboard. That is the first evidence that A4 is fixed, though nobody
has listened to it yet.

**Round three, planned 15 September** (section K) — what to test next. It
carries everything from round two over, plus the changes of 15 September.

## Open

- **Nothing of 15 September has run in a client.** The hand-over after a
  match, the in-place refresh and the news window are verified against the JDK
  sources, the installed client's bytecode, the harnesses and a probe — not by
  ear.
- **What NVDA does with the list events that remain.** An in-place change
  fires a contents change and no selection event. Whether NVDA reacts to that
  at all can only be heard (K5).
- **The rule for reading out a changed row** — rows with a source object are
  read when they change under the cursor, rows without one change silently. It
  is a judgement about what is wanted; K5 asks.
- **Ctrl+F1 in game (D4)** — reported as misbehaving, not how. It logs what it
  found; K4 asks what is heard.
- **The draft and tournament windows (E1–E5) and F1–F3** have never been
  reached in a test.
- **Where the keyboard lands after a tournament match.** The hand-over gives it
  to the tournament window when XMage shows the tournament pane in front.
  Whether XMage does at that moment is not checked (K9).
- **Draft to deck building.** The draft window is deliberately left out of the
  hand-over, because deck building takes the keyboard itself. If E4 finds the
  keyboard in XMage's frame between the two, that assumption was wrong.

## Verified so far

- Builds on JDK 8, class file version 52, loads under `-javaagent`.
- Every field name, method signature, enum constant and string literal the code
  depends on was checked against both the `../mage` source tree and the
  installed **1.4.61** client JARs with `javap`. On 15 September that added
  `MageFrame.activeFrame` and the bytecode of `setActive`, `deactivate`,
  `MagePane.removeFrame` and `TablesPanel.hideTables`, the draft and tournament
  status fields, and `MageDialog.hideDialog`.
- `harness/` — nine harnesses, 141 checks, all passing: game actions (27), the
  deck editor filter defect (13), the news window (19), the pack selector's
  cursor (14), the draft window's pick (15), the tournament window's watch
  (11), the window-focus helpers (15), the zone refresh (20) and the keyboard
  hand-over (7). Run against the previous refresh, seven of the zone refresh
  checks fail, so they do tell the two apart.
- `harness/probes/NewsScriptProbe.java` — the news window's page script, run in
  the client's own Java 8 and WebKit against a copy of the live page: 415
  blocks, 26 headings, 15 with a link.

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
- XMage does not hide the pane it switches away from; `MageFrame.activeFrame`
  is the only record of what is in front. Written down in `CLAUDE.md`.
- A bare JavaFX `WebEngine` never finished loading a page in the probe, while
  one in a `WebView` in a `Scene` in a `JFXPanel` did — the way XMage builds its
  news dialog. See `harness/README.md`.
- Harness runs write `xmage-access.log` into the working directory; `*.log` is
  in `.gitignore`.
