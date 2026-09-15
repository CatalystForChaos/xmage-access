# Harnesses

The project has no test framework, but the parts of the agent that reach into
XMage by reflection can still be exercised without XMage: `stubs/` holds
stand-in `mage.*` classes with the same names and signatures as the real ones,
and each harness drives the agent's own code against them and prints PASS/FAIL.

They are plain `main` classes on purpose — no dependency, no runner, and they
work against the built JAR, so they check what actually ships.

## Running them

```sh
JAVA_HOME="C:/Program Files/BellSoft/LibericaJDK-8" \
  ~/tools/apache-maven-3.9.9/bin/mvn -B clean package

OUT=/tmp/xmage-access-harness
javac -cp target/xmage-access-0.1.0.jar -d "$OUT" \
  $(find harness/stubs harness/src -name '*.java')

for h in GameActionsHarness FilterHarness WhatsNewHarness PacksSelectorHarness \
         DraftWindowHarness TournamentWindowHarness FocusHarness \
         ZoneListHarness HandoverHarness; do
  java -cp "$OUT;target/xmage-access-0.1.0.jar" xmageaccess.ui.$h
done
```

Each exits non-zero on failure. The harness classes sit in package
`xmageaccess.ui` so they can reach package-private members such as
`GameActions`, `AccessibleDeckEditorWindow.fireUnmodified` and the action
methods of the dialog handlers.

Two of the stubs stand in for JavaFX rather than XMage (`javafx.application
.Platform`, `javafx.scene.web.WebEngine`). That only works because the JDK 8
in use here ships no JavaFX of its own — on a JDK that bundles it, the real
classes would shadow the stubs and `WhatsNewHarness` would need the toolkit.

## What each one pins down

**`GameActionsHarness`** — the reflection path behind the in-game action
shortcuts: reading `gameId` / `playerId` off the game panel, resolving
`PlayerAction` constants by name, invoking the static
`SessionHandler.sendPlayerAction(PlayerAction, UUID, Object)` with exactly the
right parameter types, carrying payloads (rollback turn counts, player ids),
reading the mana toggles off the play area's checkbox menu items, pushing menu
states back through `setMenuStates`, persisting the mana preferences, and
failing quietly when an action or a game is missing.

**`FilterHarness`** — the deck editor filter defect and its fix. It dispatches a
real Ctrl-modified `InputEvent` and shows that `doClick()` inside it makes XMage
see a Ctrl+click (which means "select all the *other* filters"), that
`setSelected` plus `fireUnmodified` delivers a clean toggle with exactly one
filter pass, and that `setSelected` alone notifies nothing — the property that
makes the batched reset safe.

A mouse event stands in for the key event there only because key events posted
to an unfocused component are swallowed by the focus manager;
`DefaultButtonModel.setPressed` treats both the same, as `InputEvent`.

**`WhatsNewHarness`** — the news dialog's reading path. The page text can only
be taken off a `WebEngine` on the JavaFX application thread, so this shows the
fetch really goes through `Platform.runLater` and `executeScript`, that the
reading cursor gets paragraphs rather than markup, that Ctrl+B hands XMage's
own `WHATS_NEW_PAGE` constant to `AppUtil.openUrlInSystemBrowser`, that Ctrl+C
copies the page with its line breaks intact, that Ctrl+Enter reaches the
dialog's Close button, and that a page which yielded nothing is reported as
missing instead of copied as an empty string.

**`DraftWindowHarness`** — the draft window's pick. It shows the booster is
read by asking each card panel in the grid for its CardView, that the pool
comes from the client's own CardsList rather than the nameless
SimpleCardViews the server returns with a pick, that XMage's own
`isAllowedToPick` really blocks one, that an allowed pick reaches
`SessionHandler.sendCardPick` with the draft id, the card id and the
hidden-card set, and that the panel's private picked-cards redraw runs
afterwards — but not when the server refused.

**`TournamentWindowHarness`** — the tournament window's watch. The matches
model carries three columns past the five it shows; this pins that the rows
are read from the model so those ids come along, that a match is offered for
watching exactly when XMage's own action cell says "Watch", and that watching
hands that row's table id to `SessionHandler.watchTournamentTable`.

**`ZoneListHarness`** — the refresh every zone in every window goes
through, and what it tells the screen reader. Clearing and refilling a JList
moves the selection off its row and back, and JList's accessible context
reports each move as a new active descendant — which is what made the zones
talk over themselves while you were reading. This counts the selection
events and the accessible selection and active-descendant changes a refresh
fires, and pins that there are none when rows are unchanged, replaced in
place, added at the end or removed below the cursor; the selection moves
only when its own row goes. Identity shows which rows were replaced: an
unchanged row is still the very same object. It also pins what counts as a
change — a row that reads the same but points at a different object is
replaced, since Enter would otherwise act on the previous game, while a
button row rebuilt around an equal array is not — and what the agent reads
out: a row that stands for something and changed under the cursor while its
list has the keyboard, never an information row such as the draft clock.

**`FocusHarness`** — the window-focus helpers, and the only harness that
needs no stubs: it is about the JDK, not about XMage. Every panel-level
shortcut is gated on its own window being the active one, and
`requestFocusInWindow()` is documented to focus nothing unless its window "is
already the focused Window" — so showing a window and then asking it to focus
a list, in that order, gets neither. This shows the component request is held
back until the window reports the focus, that the listener carrying it takes
itself off again, that a call from off the EDT is moved onto it, that a
minimised window is restored rather than merely raised, and that the two
gating predicates answer for the right windows. Its frames are non-focusable
and placed far off-screen, so the run neither shows anything nor takes the
keyboard from the terminal; that also forces the branch worth testing, the one
where the window is not focused.

**`HandoverHarness`** — where the keyboard goes when one of the agent's
windows closes. XMage never hides the lobby for a game — `MageFrame.setActive`
only moves the game's pane in front, in the source and in the installed
client's bytecode alike — so the lobby cannot take the keyboard back by
noticing that it reappeared. `UIWatcher` instead waits for a window to close
and hands the keyboard to the window belonging to the pane XMage shows in
front. This pins that the pane is read from `MageFrame`'s private static
`activeFrame`, that the window chosen is the one whose panel sits inside that
pane — the lobby's while XMage shows the lobby, the tournament's while it
shows that — and that a window no longer showing is not brought back. Its
stubs, `MageFrame` and `MagePane`, carry only that field and that class; the
wait for the window manager is left to the test round.

**`PacksSelectorHarness`** — the pack selector's cursor. It shows the
checkboxes are reachable at all (they sit in a heavyweight `java.awt.Panel`
that the handler looks up as a `Container`), that the cursor clamps at both
ends, that toggling writes through to what `getSelectedPacks` reads *without*
notifying any `ActionListener` — the same defect `FilterHarness` pins, avoided
by construction this time — that select-all and select-none go through XMage's
own buttons, and that an empty pool is refused before the Apply button is
clicked, since XMage answers that with a `JOptionPane` nobody would hear.
