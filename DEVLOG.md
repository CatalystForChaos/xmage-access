# Devlog

Current state of the work, so it can be picked up from a cold start. Reasoning
for individual changes lives in the commit messages; this file carries what git
cannot: what is unfinished, untested, or deliberately left alone.

Last updated: 2026-09-16.

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
- **No agent shortcuts inside XMage's own window.** Panel handlers gate on
  their own accessible window being active; dialog handlers and `Ctrl+Q` are
  exempt.
- **Accessible draft and tournament windows**, built like the lobby window:
  status, booster and picks, or status, standings, matches and chat, as lists.
- **What the first test round turned up** — accessible windows take the
  keyboard when they open; `xmage-access.log`; Ctrl+A in the deck editor; the
  keys that only repeated a row are gone from the lobby, the join-table dialog
  and the game.
- **The lobby takes the keyboard back after a match.** XMage never hides the
  lobby for a game; `UIWatcher` hands the keyboard on whenever one of the
  agent's windows closes.
- **List refreshes no longer move the selection.** Rows are replaced in place;
  a row that stands for something and changes under the cursor is read out.
- **The news page is a window of its own**, `AccessibleNewsWindow`.
- **What the third test round turned up** (16 September):
  - The hand-over looks past a pane whose window is closed, front to back in
    XMage's z-order, instead of giving up.
  - The random packs selector's handler is gone again. The tester found XMage's
    own dialog usable through the Java Access Bridge and asked for the agent to
    stay out.
  - The waiting room is a window of its own, `AccessibleTableWaitingWindow`:
    Table, Seats, Actions and Chat. It replaces `TableWaitingDialogHandler` and
    its Ctrl+Enter and Ctrl+Shift+I.

Installed at `~/Downloads/mage/xmage/mage-client/lib/xmage-access-0.1.0.jar`:
the build of 16 September, MD5 `bb2549b07eed9887f309d63f00884f80`. Beside it,
for rollback: `.2026-09-15.bak`, round three's build; `.2026-08-29.bak`, the
build meant for round two; and `.v0.1.13.bak`, the last release.

## Test rounds

`TESTPLAN.txt` holds only what is still open, in German, with a result line per
check. On 15 September the tester asked for finished checks to be deleted. The
plan as returned from round three, with every earlier round and result, is
commit `6d9b3e9`.

**Round three, the evening of 15 September.** One client session, 20:04 to
about 20:55, with the build of 15 September. The results went into the open
checks of rounds one and two, not into section K, which carried them over.
Marked OK and consistent with the agent log where it writes anything:

- the lobby takes the keyboard after connecting
- the deck editor, game and draft windows take it when they open
- Ctrl+F1 in game
- Ctrl+A in the deck editor
- quiet lists
- the lobby, join dialog and game without the removed keys
- picking and the Draft zone
- registration, password reset, the error dialog and quitting

Marked OK but not borne out by the log:

- **The lobby after a match (D10, J2).** The log has "handing the keyboard to
  nobody". Probable cause and fix are in the commit "feat: a waiting room
  window, and what else round three turned up".
- **Deck building after the draft (E4).** The log ends with the draft window
  taking the keyboard; no deck editor window appears after it.

Not tested: the tournament window (E5), the news window (K6), where the keyboard
lands after a tournament match. Asked for:

- no handler in the random packs selector (E1)
- an accessible overview before a game or a draft starts (E2 and G); asked
  which, the tester chose the waiting room over the new tournament dialog

**Round four, planned 16 September** (section L). The waiting room, the
hand-over with a deck editor opened before the game, and what round three left
open.

## Open

- **Nothing of 16 September has run in a client.** The waiting room and the new
  hand-over are verified against the source, the installed client's bytecode
  and the harnesses, not by ear.
- **The waiting room's hand-over is a judgement from the table's last state.**
  A table that was ready to start when its dialog closed is taken to be
  starting, so the keyboard is left for the match or tournament window. If the
  owner of a full table leaves it while you are a guest, the keyboard stays in
  XMage's frame.
- **Whether the lobby speaks between Start and the game** (L1). The agent hands
  nothing on then, but where Windows puts the focus when the waiting room closes
  is not the agent's to choose.
- **Seats cannot be moved** from the waiting room; XMage's Move Up and Move
  Down are not offered.
- **The new tournament dialog** is still the old shortcut handler,
  `NewTournamentDialogHandler`. The tester chose the waiting room first.
- **Draft and tournament windows both ask for the keyboard** when a draft
  starts, 0.8 seconds apart on 15 September. The draft window asked second and
  won, which is right, but nothing in the code enforces that order.
- **Draft to deck building** (L6). The draft window is left out of the
  hand-over because deck building takes the keyboard itself; untested.
- **Where the keyboard lands after a tournament match** (L7).

## Verified so far

- Builds on JDK 8, class file version 52. This build was checked by building
  and by the harnesses only; it has not been started under `-javaagent` yet.
  The previous build ran in the client on 15 September.
- Every field name, method signature, enum constant and string literal the code
  depends on was checked against both the `../mage` source tree and the
  installed **1.4.61** client JARs with `javap`. On 16 September that added:
  - `TableWaitingDialog`'s fields and its title strings
  - the logic of `update`, `showDialog` and the Cancel handler
  - `TableWaitModel`'s columns and `PlayerType`'s descriptions
  - `MageDialog.removeDialog`, `MageFrame.getDesktop` and `getTopMost`
  - `ChatPanelBasic`'s chat fields
- `harness/` — nine harnesses, 172 checks, all passing:
  - game actions (27)
  - the deck editor filter defect (13)
  - the news window (19)
  - the draft window's pick (15)
  - the tournament window's watch (11)
  - the waiting room (39)
  - the window-focus helpers (15)
  - the zone refresh (20)
  - the keyboard hand-over (13)
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

Before building for any of these, find out whether the dialog is already usable
through the Java Access Bridge — see `CLAUDE.md`.

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
  is the only record of what is in front, and when a pane goes XMage shows the
  topmost one left, not the lobby. Written down in `CLAUDE.md`.
- The Java Access Bridge is enabled on the test machine
  (`~/.accessibility.properties`, written 27 August), so NVDA reads parts of
  XMage's own Swing dialogs without the agent.
- The deck editor window hides on close, and XMage's deck editor pane stays on
  the desktop until XMage's own Exit. An old deck editor can therefore be what
  XMage puts in front after a game.
- A bare JavaFX `WebEngine` never finished loading a page in the probe, while
  one in a `WebView` in a `Scene` in a `JFXPanel` did — the way XMage builds its
  news dialog. See `harness/README.md`.
- Harness runs write `xmage-access.log` into the working directory; `*.log` is
  in `.gitignore`.
