# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**XMage Access** is a Java agent that adds screen reader accessibility to [XMage](https://xmage.today), an open-source Magic: The Gathering client. It hooks into the XMage client at startup via `-javaagent` and overlays accessible Swing windows with keyboard navigation, without modifying XMage itself.

**Supported screen readers:** NVDA, JAWS, Windows SAPI (Windows); VoiceOver (macOS); speech-dispatcher (Linux).

## Working rules

These are binding, not preferences.

1. **Never modify XMage.** The XMage sources are read-only reference material. Every fix and feature lives in the agent, even when patching XMage would be easier — the agent has to keep working against stock XMage installs. The deck editor filter fix (`fireUnmodified`) is what this constraint looks like in practice.
2. **Invent nothing.** Every claim about XMage's behaviour must be traceable to its source, its bytecode, or a documented API, and named in the code or commit message where it matters. When something cannot be checked, say so instead of assuming. Field names, method signatures, enum constants and magic strings get verified before shipping — see *Verifying against XMage* below.
3. **Document for the next session.** Work is written down so it can be picked up cold: commit messages carry the reasoning, `DEVLOG.md` carries the current state and what is deliberately left undone, and `CLAUDE.md` carries anything durable. Nothing important should live only in a chat log.

### Verifying against XMage

Two levels, both cheap:

- **Source:** the full XMage tree (magefree/mage) is checked out at `../mage`. Grep it for every field name a handler looks up and every class name `UIWatcher` matches on.
- **Installed client:** the source tree is `master`; users run a release. Check names against the actual JARs before shipping:

```sh
CP=$(ls <xmage>/mage-client/lib/*.jar | tr '\n' ';')
javap -p -classpath "$CP" mage.client.deckeditor.CardSelector
javap -p -constants -classpath "$CP" mage.client.util.sets.ConstructedFormats   # string literals
```

Reflection logic itself is testable without XMage at all: compile stub `mage.*` classes plus a harness in package `xmageaccess.ui` against the built JAR. See `harness/`.

## Build & Run

- **Requirements:** Java 8 (JDK 1.8), Maven
- **Build:** `mvn package`
- **Output:** `target/xmage-access-0.1.0.jar` (all dependencies shaded in)
- **No test suite exists** — there are no tests, no test framework, and no `src/test` directory. There is also no CI, linter, or formatter.

To test manually, copy the built JAR to `xmage/mage-client/lib/` in an XMage installation and launch with `-javaagent:./lib/xmage-access-0.1.0.jar` (or use the launcher scripts in `dist/`).

### Versioning / releases

- The Maven version and JAR filename stay at `0.1.0`; actual releases are git tags (`v0.1.x`). Don't "fix" the pom version to match a tag.
- The JAR in `dist/` is a prebuilt release artifact, not produced by the build. Update it manually when cutting a release.

## Architecture

### How the agent loads

1. JVM calls `XMageAccessAgent.premain()` (declared via `Premain-Class` in `pom.xml`) before XMage's main class
2. `AccessibilityManager` (singleton) initializes `SpeechOutput` and starts `UIWatcher` on the Swing EDT
3. `UIWatcher` (`ui/UIWatcher.java`) listens for AWT window/focus/key events **and** runs a 1-second Swing Timer scan (`scanForKnownUI()`), detecting XMage panels by reflected class name and attaching the matching handler class to each
4. Handlers extract state via reflection and announce changes through speech

### State tracking is polling, not instrumentation

Game state announcements come from **polling with change detection**: `GamePanelHandler`, `AccessibleGameWindow`, and `AccessibleLobbyWindow` each run 5-second Swing Timers, diff the current reflected state against cached "last seen" fields, and announce differences.

**Note:** `hooks/GamePanelHooks.java` (ByteBuddy `@Advice` classes), `hooks/GameStateTrackerBridge.java`, and `handlers/GameStateTracker.java` are currently **dead code** — no `AgentBuilder`/transformer ever installs the advice, so none of it executes. ByteBuddy is in the pom for this unfinished path. Don't assume these hooks fire; wire them up explicitly if you want event-driven tracking.

### Layout

```
src/main/java/xmageaccess/
├── XMageAccessAgent.java       # premain() entry point
├── AccessibilityManager.java   # Singleton coordinator (speech + UIWatcher)
├── handlers/, hooks/           # Dead code — unwired ByteBuddy path (see above)
├── launcher/AccessibleLauncher.java  # Toggle-checkbox launcher UI
├── speech/                     # SpeechEngine interface + per-OS implementations
├── util/                       # ReflectionUtils, TextUtils (shared helpers)
└── ui/                         # ~40 files: UIWatcher + all handlers/windows
```

Naming in `ui/`: `*Handler.java` attaches to an XMage panel, `*DialogHandler.java` to a dialog, `Accessible*Window.java` is a standalone accessible Swing window the agent creates. The largest and most central files are `GamePanelHandler.java` (gameplay shortcuts + polling), `AccessibleDeckEditorWindow.java`, and `AccessibleGameWindow.java`.

### Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| ByteBuddy | 1.14.18 | Only used by the unwired hooks path (shaded into JAR) |
| JNA | 5.14.0 | Native access for Tolk DLLs (NVDA/JAWS) |

## Key Conventions

### Reflection usage

XMage is **not a compile-time dependency** — you cannot import XMage classes. All interaction goes through reflection, using helpers in `xmageaccess.util.ReflectionUtils`:

- `findFieldTyped(target, name, type)` — walk class hierarchy, return typed field value
- `findFieldDeep(target, name)` — walk class hierarchy, return field value as Object
- `callMethod(obj, name)` / `callString` / `callInt` / `callBool` — invoke no-arg methods
- `callMethodWithArg(obj, name, argType, arg)` — invoke single-arg method
- Import via `import static xmageaccess.util.ReflectionUtils.*;`
- Always wrap reflection calls in try-catch — XMage internals may change between versions

All lookups (Method/Field by name, with and without class-hierarchy walking) are cached in `ConcurrentHashMap`s inside `ReflectionUtils`. Negative results are cached too, so a missing member is never re-resolved. **Do not introduce ad-hoc `Class.getMethod` / `getDeclaredField` calls in hot paths** — go through `ReflectionUtils` so the cache covers them.

### Speech output

- `AccessibilityManager.getInstance().getSpeech().speak(text)` — announce (interrupts current speech)
- `.speakQueued(text)` — non-interrupting; `.silence()` — stop speech
- Platform detection/routing is in `SpeechOutput.initialize()`; Windows tries Tolk (NVDA/JAWS) first, falls back to SAPI. New engines implement the `SpeechEngine` interface.
- Keep announcements concise — screen reader users rely on brevity

All engine calls run on a single daemon background thread, so callers (EDT or otherwise) never block on TTS. Two coalescing rules apply:
- **Dedup window (250 ms):** identical text within this window is dropped before it reaches the engine.
- **Interrupt-speak coalescing:** if a new `speak(text)` arrives while a previous one is still queued (but not yet executed), the queued one is cancelled — only the newest text gets spoken. `speakQueued` does *not* coalesce.

### Logging

`xmageaccess.util.Log.warn(category, message, throwable)` for recoverable failures; `Log.debug(...)` for diagnostic-only output (gated by `-Dxmageaccess.log=debug`). Prefer these over `System.err.println` + `e.printStackTrace()` in new code.

### UI handlers

- Each XMage panel/dialog gets its own handler class in `ui/`, attached by `UIWatcher` when it detects the component (register detection there for new handlers). Plain "fill in fields, press a button" dialogs (registration, password reset) instead reuse `FormDialogHandler`, configured with a field-name → spoken-label map at the `UIWatcher` attach site
- Actions XMage only exposes through the play area's right-click menu or through hotkeys it binds `WHEN_IN_FOCUSED_WINDOW` (the F3–F11 skips) go through `ui/GameActions.java`, which drives the static `SessionHandler.sendPlayerAction(PlayerAction, UUID, Object)` — the same path XMage's own popup menu uses
- Handlers must remove their AWT `KeyEventDispatcher`s, stop their `Timer`s, and drop listeners when their panel closes — several past bugs were leaked listeners persisting across games
- Global shortcuts live in `UIWatcher`; gameplay shortcuts in `GamePanelHandler`/`AccessibleGameWindow` (the javadoc atop `GamePanelHandler` lists them all). Always require a modifier (Ctrl/Alt) to avoid clashing with text input.

## Gotchas

- **Java 8 only:** Do not use Java 9+ language features or APIs.
- **Swing threading:** Never touch Swing components off the EDT — use `SwingUtilities.invokeLater()`. Violations cause subtle, hard-to-reproduce bugs.
- **Game state lifecycle:** Game state must fully reset between games in a match (best-of-three). `GamePanelHandler` tracks the game UUID (`lastGameId`) to detect the switch. Stale references to previous game panels have repeatedly caused crashes and wrong announcements (see git history — this is the most recurrent bug class alongside listener leaks).
