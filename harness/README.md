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
  harness/stubs/mage/constants/PlayerAction.java \
  harness/stubs/mage/client/SessionHandler.java \
  harness/stubs/mage/client/dialog/PreferencesDialog.java \
  harness/src/xmageaccess/ui/GameActionsHarness.java \
  harness/src/xmageaccess/ui/FilterHarness.java

java -cp "$OUT;target/xmage-access-0.1.0.jar" xmageaccess.ui.GameActionsHarness
java -cp "$OUT;target/xmage-access-0.1.0.jar" xmageaccess.ui.FilterHarness
```

Both exit non-zero on failure. The harness classes sit in package
`xmageaccess.ui` so they can reach package-private members such as
`GameActions` and `AccessibleDeckEditorWindow.fireUnmodified`.

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
