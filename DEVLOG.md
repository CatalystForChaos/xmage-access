# Devlog

Current state of the work, so it can be picked up from a cold start. Reasoning
for individual changes lives in the commit messages; this file carries what git
cannot: what is unfinished, untested, or deliberately left alone.

Last updated: 2026-08-28.

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
  `LobbyHandler` now gate on `UiUtils.isAgentWindowActive()`; the deck
  editor's three shortcuts were already unreachable and are gone; dialog
  handlers and `Ctrl+Q` are unaffected.

**None of it has been tested in a running game yet.** The build is installed at
`~/Downloads/mage/xmage/mage-client/lib/xmage-access-0.1.0.jar`, with the
v0.1.13 build kept beside it as `.v0.1.13.bak` for rollback.

One thing to watch in that test round, introduced by the shortcut change:
whether focus actually lands in the accessible window when it opens. The lobby
window is created while the connect dialog is still up, so after connecting
the client window may well hold focus — and there the lobby shortcuts now do
nothing. If that happens, the fix is for the agent to bring the window forward
when it announces, not to hand the keys back.

## Verified so far

- Builds on JDK 8, class file version 52, loads under `-javaagent`.
- Every field name, method signature, enum constant and string literal the new
  code depends on was checked against both the `../mage` source tree and the
  installed **1.4.61** client JARs with `javap`. That includes the JavaFX
  signatures the news dialog leans on, checked against the `javafx-*-11.0.2`
  JARs the client ships.
- `harness/` — four harnesses, 66 checks, all passing: game actions (27), the
  deck editor filter defect (13), the news dialog's reading path (12) and the
  pack selector's cursor (14).

## Known gaps, from the audit of the XMage client

Found by walking the client's UI classes and diffing against what `UIWatcher`
attaches to. Deliberately not addressed yet:

| Surface | Why it matters |
|---|---|
| Draft panel | Announcements only since the shortcuts left XMage's window. Picking a card needs an accessible draft window, which does not exist yet. |
| Tournament panel | Same: state and chat are announced, standings and "watch this match" are not reachable from the keyboard. |
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
