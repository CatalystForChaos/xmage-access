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

**None of it has been tested in a running game yet.** The build is installed at
`~/Downloads/mage/xmage/mage-client/lib/xmage-access-0.1.0.jar`, with the
v0.1.13 build kept beside it as `.v0.1.13.bak` for rollback.

## Verified so far

- Builds on JDK 8, class file version 52, loads under `-javaagent`.
- Every field name, method signature, enum constant and string literal the new
  code depends on was checked against both the `../mage` source tree and the
  installed **1.4.61** client JARs with `javap`.
- `harness/` — reflection wiring (27 checks) and the filter defect, all passing.

## Known gaps, from the audit of the XMage client

Found by walking the client's UI classes and diffing against what `UIWatcher`
attaches to. Deliberately not addressed yet:

| Surface | Why it matters |
|---|---|
| Card Viewer (`deckeditor/collection/viewer/MageBook`) | Its own main-menu feature, drawn as images with hover buttons. No handler at all. Partly covered by the deck editor's set browsing. |
| `WhatsNewDialog` | Opens automatically at startup (`MageFrame:390`). |
| `RandomPacksSelectorDialog` | Blocks setting up a random-pack draft. |
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
