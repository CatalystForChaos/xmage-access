# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**XMage Access** is a Java agent that adds screen reader accessibility to [XMage](https://xmage.today), an open-source Magic: The Gathering client. It hooks into the XMage client at startup via `-javaagent` and overlays accessible Swing windows with keyboard navigation, without modifying XMage itself.

**Supported screen readers:** NVDA, JAWS, Windows SAPI (Windows); VoiceOver (macOS); speech-dispatcher (Linux).

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
└── ui/                         # ~30 files: UIWatcher + all handlers/windows
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

### Speech output

- `AccessibilityManager.getInstance().getSpeech().speak(text)` — announce (interrupts current speech)
- `.speakQueued(text)` — non-interrupting; `.silence()` — stop speech
- Platform detection/routing is in `SpeechOutput.initialize()`; Windows tries Tolk (NVDA/JAWS) first, falls back to SAPI. New engines implement the `SpeechEngine` interface.
- Keep announcements concise — screen reader users rely on brevity

### UI handlers

- Each XMage panel/dialog gets its own handler class in `ui/`, attached by `UIWatcher` when it detects the component (register detection there for new handlers)
- Handlers must remove their AWT `KeyEventDispatcher`s, stop their `Timer`s, and drop listeners when their panel closes — several past bugs were leaked listeners persisting across games
- Global shortcuts live in `UIWatcher`; gameplay shortcuts in `GamePanelHandler`/`AccessibleGameWindow` (the javadoc atop `GamePanelHandler` lists them all). Always require a modifier (Ctrl/Alt) to avoid clashing with text input.

## Gotchas

- **Java 8 only:** Do not use Java 9+ language features or APIs.
- **Swing threading:** Never touch Swing components off the EDT — use `SwingUtilities.invokeLater()`. Violations cause subtle, hard-to-reproduce bugs.
- **Game state lifecycle:** Game state must fully reset between games in a match (best-of-three). `GamePanelHandler` tracks the game UUID (`lastGameId`) to detect the switch. Stale references to previous game panels have repeatedly caused crashes and wrong announcements (see git history — this is the most recurrent bug class alongside listener leaks).
- **DEVLOG-deck-editor.md** is a stale stub; the deck editor work it describes shipped in `AccessibleDeckEditorWindow.java`.
