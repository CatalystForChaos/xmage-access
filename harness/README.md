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

for h in GameActionsHarness FilterHarness WhatsNewHarness DraftWindowHarness \
         TournamentWindowHarness TableWaitingWindowHarness FocusHarness \
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

**`WhatsNewHarness`** — the news window. The page can only be read off a
`WebEngine` on the JavaFX application thread, so this shows the fetch really
goes through `Platform.runLater` and `executeScript`, with a script that walks
headings, paragraphs and list items; that the blocks come back as rows in page
order, headings and links saying what they are, and the headings as the
Sections list, where Enter selects the heading in the news; that Enter on a
line with a link hands exactly that link to `AppUtil.openUrlInSystemBrowser`,
and the first action XMage's own `WHATS_NEW_PAGE`; that copying gives the page
as text, closing goes through the dialog's Close button once, a page that
yielded nothing says so, and the handler holds no keyboard dispatcher any
more. The script itself needs WebKit; `NewsScriptProbe`, below, runs it.

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

**`TableWaitingWindowHarness`** — the waiting room, the window for XMage's
`TableWaitingDialog`. XMage lets the user reorder the seat table's columns in
its view, so this pins that the seats are read from the model by model
column: a taken seat by player and type, with rating and history left for D
and a computer player's rating of 0 left out, an open seat as empty. It pins
that game type and deck type come out of the title XMage sets after its first
table update, split at the last separator, and are left out before; that
Start is offered only to the table owner — XMage's Start button is visible
exactly for the owner and enabled exactly when the table is ready — and
presses that button only while it is enabled; that Leave presses Cancel and
says so when XMage keeps the dialog open because the table has started; what
is said as players come and go, where swapping seats and XMage's first fill of
the model are no news; and when the window lets the keyboard be handed on
after it closes: always after leaving, never from a table that was ready to
start, since the match or tournament window takes it then.

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
shows that — and that a window no longer showing is not brought back.

When a game ends, XMage shows the topmost pane that is left, not the lobby.
In the test of 15 September that was a deck editor opened before the game,
whose window had been closed, and the log said "handing the keyboard to
nobody". So the choice moves on to the panes behind, front to back as they sit
on `MageFrame.getDesktop()`, the z-order `MageFrame.getTopMost` ranks by. This
replays that evening — lobby, deck editor, game, each put in front, then the
game's pane hidden and removed — and pins that the panes are read in that
order, that a closed window is passed over to the lobby behind it, that a
pane nearer the front comes before the lobby, that a hidden pane is skipped,
and that nothing is chosen while XMage shows nothing. Its stubs, `MageFrame`
and `MagePane`, carry only that field, that desktop and that class; the wait
for the window manager is left to the test round.

## Probes

Some questions only the client's own runtime can answer. A probe is a plain
`main` class run in XMage's bundled Java against the built JAR — no running
client needed. Probes live in `probes/`, apart from the harnesses, so the
harness command above never compiles them.

**`NewsScriptProbe`** — the script the news window runs inside XMage's news
page. The harness can only check that the script is sent; what it returns
needs WebKit. The probe takes `READ_BLOCKS_SCRIPT` out of the built JAR by
reflection, loads a page into a `WebView`, runs the script and prints the
result.

```sh
JRE=~/Downloads/mage/java/jre1.8.0_201    # the client's Java 8, with JavaFX
OUT=/tmp/xmage-access-probe
javac -cp "$JRE/lib/ext/jfxrt.jar;target/xmage-access-0.1.0.jar" -d "$OUT" \
  harness/probes/NewsScriptProbe.java
"$JRE/bin/java" -cp "$OUT;target/xmage-access-0.1.0.jar" NewsScriptProbe news.html 60
```

The `WebView` has to sit in a `Scene` in a `JFXPanel` inside a Swing frame,
the way `WhatsNewDialog` builds its own: a bare `WebEngine` stayed in RUNNING
and never loaded even a ten-line local page. Set up like XMage's, a copy of
the live news page saved on 15 September — its external scripts removed, so
nothing waited on the network — loaded in seconds, and the script returned 415
blocks, 26 of them headings and 15 with a link.
