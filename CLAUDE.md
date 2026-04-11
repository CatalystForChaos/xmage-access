# CLAUDE.md

This file provides context for AI assistants working on the XMage Access codebase.

## Project Overview

**XMage Access** is a Java agent that adds screen reader accessibility to [XMage](https://xmage.today), an open-source Magic: The Gathering client. It hooks into the XMage client at startup via `-javaagent` and overlays accessible Swing windows with keyboard navigation, without modifying XMage itself.

**Supported screen readers:** NVDA, JAWS, Windows SAPI (Windows); VoiceOver (macOS); speech-dispatcher (Linux).

## Build & Run

- **Requirements:** Java 8 (JDK 1.8), Maven
- **Build:** `mvn package`
- **Output:** `target/xmage-access-0.1.0.jar`
- **No test suite exists** — there are no tests, no test framework, and no `src/test` directory.
- **No CI/CD pipeline** — no GitHub Actions or other CI configuration.
- **No linter or formatter** is configured.

To test manually, copy the built JAR to `xmage/mage-client/lib/` in an XMage installation and launch with `-javaagent:./lib/xmage-access-0.1.0.jar`.

## Repository Structure

```
xmage-access/
├── pom.xml                         # Maven build config (Java 8, shade plugin)
├── README.md                       # User-facing documentation
├── DEVLOG-deck-editor.md           # Dev notes for deck editor feature
├── dist/                           # Prebuilt JAR, launcher scripts, Tolk DLLs
│   ├── xmage-access-0.1.0.jar
│   ├── run-accessible-launcher.*   # Launcher scripts (Win/Mac)
│   ├── startClient-accessible.*    # Direct-start scripts (Win/Mac)
│   ├── README-accessible.txt       # Full user guide
│   └── tolk/                       # Native DLLs for NVDA/JAWS (x86/x64)
└── src/main/java/xmageaccess/
    ├── XMageAccessAgent.java       # Java agent entry point (premain)
    ├── AccessibilityManager.java   # Singleton coordinator
    ├── handlers/
    │   └── GameStateTracker.java   # Tracks game state, generates announcements
    ├── hooks/
    │   ├── GamePanelHooks.java     # ByteBuddy instrumentation hooks
    │   └── GameStateTrackerBridge.java # Cached reflection bridge for hooks
    ├── launcher/
    │   └── AccessibleLauncher.java # Toggle-checkbox launcher UI
    ├── speech/                     # Platform-specific TTS
    │   ├── SpeechEngine.java       # Interface
    │   ├── SpeechOutput.java       # Platform detection & routing
    │   ├── WindowsSpeech.java      # Tolk/SAPI (Windows)
    │   ├── TolkSpeech.java         # Tolk DLL wrapper
    │   ├── TolkLibrary.java        # JNA bindings for Tolk.dll
    │   ├── MacOSSpeech.java        # macOS 'say' command
    │   └── LinuxSpeech.java        # speech-dispatcher
    ├── util/                        # Shared utilities
    │   ├── ReflectionUtils.java    # Static reflection helpers (findFieldTyped, callMethod, etc.)
    │   └── TextUtils.java          # Text formatting (cleanHtml, formatManaCost)
    └── ui/                         # Accessible UI layer (~30 files)
        ├── UIWatcher.java          # AWT event listener, UI scanner
        ├── AccessibleGameWindow.java       # Main gameplay window
        ├── AccessibleLobbyWindow.java      # Lobby browser
        ├── AccessibleDeckEditorWindow.java # Deck builder
        ├── AccessibleDeckPicker.java       # Deck selection dialog
        ├── GamePanelHandler.java           # Game panel master handler
        ├── LobbyHandler.java              # Lobby handler
        ├── SideboardingHandler.java        # Sideboard UI
        ├── DraftPanelHandler.java          # Draft UI
        ├── *DialogHandler.java             # Various dialog handlers
        ├── ChatAccessHelper.java           # Chat accessibility
        ├── ZoneListPanel.java              # Accessible JList wrapper
        └── ZoneItem.java                   # Card/zone item model
```

## Architecture

### How the agent loads

1. JVM loads `XMageAccessAgent.premain()` before XMage's main class
2. `AccessibilityManager` initializes speech output and starts `UIWatcher` on the Swing EDT
3. `UIWatcher` listens for AWT window/focus/key events and attaches handler classes to XMage panels as they appear
4. `GamePanelHooks` uses ByteBuddy to instrument XMage game panel methods for real-time state tracking

### Key patterns

- **Java Agent (javaagent):** Entry point via `premain()` with `Instrumentation` API
- **Singleton:** `AccessibilityManager`, `GameStateTracker` — global state coordinators
- **Strategy:** `SpeechEngine` interface with OS-specific implementations (`WindowsSpeech`, `MacOSSpeech`, `LinuxSpeech`)
- **Reflection:** Extensively used to access XMage classes not on the agent's compile-time classpath — all XMage UI interaction goes through reflection
- **Swing EDT safety:** UI operations use `SwingUtilities.invokeLater()`
- **Concurrent collections:** `ConcurrentHashMap` for thread-safe handler management

### Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| ByteBuddy | 1.14.18 | Bytecode instrumentation (shaded into JAR) |
| JNA | 5.14.0 | Native access for Tolk DLLs (NVDA/JAWS) |

All dependencies are shaded into the final JAR via `maven-shade-plugin`.

## Key Conventions

### Code style

- Java 8 source level — no lambdas beyond what Java 8 supports, no `var`, no records
- Standard Java naming conventions (camelCase methods, PascalCase classes)
- Handler classes follow the pattern `*Handler.java` for XMage panel handlers, `*DialogHandler.java` for dialog handlers
- Accessible windows follow the pattern `Accessible*Window.java`

### Reflection usage

All interaction with XMage classes uses reflection since XMage is not a compile-time dependency. Shared reflection helpers live in `xmageaccess.util.ReflectionUtils`:
- `findFieldTyped(target, name, type)` — walk class hierarchy, return typed field value
- `findFieldDeep(target, name)` — walk class hierarchy, return field value as Object
- `callMethod(obj, methodName)` / `callString` / `callInt` / `callBool` — invoke no-arg methods
- `callMethodWithArg(obj, name, argType, arg)` — invoke single-arg method
- Import via `import static xmageaccess.util.ReflectionUtils.*;`
- Always wrap reflection calls in try-catch — XMage internals may change between versions

### Speech output

- Call `AccessibilityManager.getInstance().getSpeech().speak(text)` to announce text (interrupts current speech)
- Call `.speakQueued(text)` for non-interrupting speech
- Call `.silence()` to stop speech
- Keep announcements concise — screen reader users rely on brevity

### UI handlers

- Each XMage panel/dialog gets its own handler class in `ui/`
- Handlers are attached by `UIWatcher` when it detects the corresponding XMage component
- Handlers must clean up AWT listeners and timers when their panel is closed to avoid leaks
- Use `SwingUtilities.invokeLater()` for any Swing component access from non-EDT threads

## Common Tasks

### Adding a new dialog handler

1. Create `src/main/java/xmageaccess/ui/NewDialogHandler.java`
2. Identify the XMage dialog by class name (use reflection to inspect `component.getClass().getName()`)
3. Register detection in `UIWatcher.java`
4. Extract dialog state via reflection and announce via `SpeechOutput`
5. Add keyboard shortcuts for navigation

### Adding a new keyboard shortcut

- Global shortcuts are registered in `UIWatcher.java` via `KeyEventDispatcher`
- Game-specific shortcuts are in `AccessibleGameWindow.java` or `GamePanelHandler.java`
- Always check for modifier keys (Ctrl, Alt) to avoid conflicts with normal text input

### Modifying speech behavior

- Platform detection is in `SpeechOutput.initialize()`
- Windows speech routing: `WindowsSpeech` tries Tolk (NVDA/JAWS) first, falls back to SAPI
- Add new engines by implementing `SpeechEngine` interface

## Gotchas

- **No compile-time XMage dependency:** You cannot import XMage classes directly. All access is via reflection.
- **Java 8 only:** The target JVM is Java 8. Do not use Java 9+ features.
- **Swing threading:** Never access Swing components off the EDT. This causes subtle, hard-to-reproduce bugs.
- **Resource leaks:** AWT `KeyEventDispatcher` instances, `Timer` objects, and window listeners must be explicitly removed when no longer needed. Several past bugs were caused by leaked listeners persisting across games.
- **Game state lifecycle:** Game state must be fully reset between games in a match (best-of-three). Stale references to previous game panels cause crashes or wrong announcements.
- **dist/ JAR is prebuilt:** The JAR in `dist/` is a release artifact, not auto-generated by the build. Update it manually when cutting a release.
