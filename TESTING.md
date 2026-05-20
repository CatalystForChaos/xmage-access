# Testing guide — post-audit release

This guide walks through how to manually verify the changes made in the 2026-05-20 audit (see commit history for the full diff). No automated tests exist, so each item below describes what to do and what to listen for.

## 0. Build & install

```sh
# Build environment (one-time):
export JAVA_HOME=~/.local/tools/jdk-21.0.6+7/Contents/Home
export PATH=$JAVA_HOME/bin:~/.local/tools/apache-maven-3.9.9/bin:$PATH
export MAVEN_OPTS="-Xmx2g"

# From the repo root:
mvn -DskipTests clean package
```

The shaded JAR lands at `target/xmage-access-0.1.0.jar` (~6.6 MB).

To install for a real-world test:

```sh
cp target/xmage-access-0.1.0.jar /path/to/xmage/mage-client/lib/
# then launch the client with: -javaagent:./lib/xmage-access-0.1.0.jar
```

Tip: for verbose diagnostic output, add `-Dxmageaccess.log=debug` to the launch args. With debug off (the default), only real warnings hit stderr.

## 1. Smoke test: nothing is obviously broken

Launch XMage with the new agent. Within 10 seconds of the lobby loading you should hear:

1. **"XMage Access loaded."** (from agent boot)
2. The connect dialog announcement
3. After connecting, the lobby announcement

If any of these are missing, something in `AccessibilityManager` / `UIWatcher` / `SpeechOutput` regressed.

## 2. Speech responsiveness (Stage 2 verification)

The big UX change: TTS is now async with dedup + interrupt-coalescing.

### What to listen for

- **No more EDT freezes.** Press a flurry of keyboard shortcuts in quick succession (e.g. Ctrl+F1 → Ctrl+F2 → Ctrl+F3 in under a second). The UI should remain responsive. Before the audit, each call blocked the EDT for ~50-200 ms while `ProcessBuilder.start()` ran.
- **Duplicate announcements collapse.** Trigger an action that historically caused two identical announcements within ~100 ms (e.g. opening a dialog that prompts and immediately re-announces on focus). You should hear it once, not twice.
- **Rapid bursts collapse to the latest.** Press the same Ctrl+F1 (read help) twice in 200 ms — the second press should win cleanly; you shouldn't hear two half-spoken overlaps.

### Configurable constants

In `speech/SpeechOutput.java`:

- `DEDUP_WINDOW_MS = 250` — identical text within this window is dropped before reaching the engine.

To experiment with sensitivity, change the constant and rebuild. The default is conservative.

### Negative test

Set the macOS rate to slow to make speech easy to inspect:

```sh
defaults write com.apple.speech.synthesis.general.prefs SelectedVoiceCreator -int 1888094803  # Samantha
```

Then trigger consecutive different announcements. Each distinct one should still come through; only identical duplicates within 250 ms should drop.

## 3. Process / pipe handling (Stage 1 verification)

Old behavior: each `say` / `spd-say` / `powershell` child held open `Process.getInputStream()` etc. and could in principle stall on a full pipe.

New behavior: those streams are redirected to `/dev/null` (or `NUL` on Windows) before `start()`.

### Verification

Launch XMage, play for a few minutes triggering lots of speech, then in a separate terminal:

```sh
# macOS / Linux
pgrep -af '(say|spd-say)' | head
ps -o pid,state,command -p "$(pgrep -af '(say|spd-say)' | awk '{print $1}' | head -10 | tr '\n' ',' | sed 's/,$//')"
```

Expect to see at most one or two short-lived processes at any moment, not a growing pile. `pgrep` should typically return empty between speech.

For the AccessibleLauncher path: launch via `run-accessible-launcher.*`, click "Launch XMage", then check that the launcher script's process exits cleanly and the spawned XMage child doesn't get a SIGPIPE when its parent dies.

## 4. Listener / timer leak check (Stage 1 verification)

The biggest known regression source per `CLAUDE.md` is listeners and timers accumulating across best-of-three matches.

### Procedure

1. Launch XMage with the agent.
2. Play a full best-of-three match: game 1 → sideboard → game 2 → sideboard → game 3 → resign / concede out.
3. Immediately start a second best-of-three match against the same or different opponent.
4. Mid-game-2 of the *second* match:

   ```sh
   jcmd <xmage-pid> Thread.print | grep -E 'Timer|EventQueue|AWT-Shutdown' | sort -u
   ```

5. Count: there should be **at most a small fixed number** of `javax.swing.Timer$DelayedExecutor` and AWT threads — they should *not* be growing per match.

6. Optional heap dump pass:

   ```sh
   jcmd <xmage-pid> GC.heap_dump leak.hprof
   ```

   Open in VisualVM or YourKit. Count instances of:
   - `xmageaccess.ui.GamePanelHandler`
   - `xmageaccess.ui.SideboardingHandler`
   - `xmageaccess.ui.AccessibleGameWindow`
   - `java.awt.event.AWTEventListener` (filter to xmageaccess subclasses)

   None should grow with match count.

### Watch for known sites

- `UIWatcher.scanTimer` — should be a single instance, stopped only when the JVM shuts down.
- `GamePanelHandler.pollTimer` — one per active game panel; stopped + nulled when the panel detaches.
- `SideboardingHandler.refreshTimer` — one at most per active sideboard; stopped + nulled on `stopPolling()`.
- `DraftPanelHandler.announceTimer` — same lifecycle as the draft.
- `TournamentPanelHandler.pollTimer` — same lifecycle as the tournament.

## 5. Reflection cache (Stage 3 verification)

The cache is internal — there's no user-visible behavior change. To confirm it's working:

### Optional micro-benchmark

Add this temporarily to any handler's `attach()`:

```java
long t = System.nanoTime();
for (int i = 0; i < 100000; i++) {
    xmageaccess.util.ReflectionUtils.findFieldDeep(targetPanel, "someKnownField");
}
System.err.println("100k lookups: " + (System.nanoTime() - t) / 1_000_000 + " ms");
```

Before the audit: hundreds of ms.
After: a few ms (mostly the cost of `Field.get` itself).

Remove the snippet before committing.

### Profiler pass (optional)

`async-profiler` for 60 seconds of steady-state gameplay:

```sh
~/.local/tools/async-profiler/profiler.sh -d 60 -e cpu -f flame.html <xmage-pid>
```

Open `flame.html` in a browser. `Method.invoke` / `Class.getDeclaredField` should not appear in the top frames. If they do, something in the codebase is bypassing `ReflectionUtils` — fix that and update `CLAUDE.md`.

## 6. Functional pass per UI surface

Walk through each major UI in order. Each section lists what to test and what specifically to listen for after the audit.

### 6.1 Connect dialog

- Launch agent → connect dialog appears.
- Hear: dialog announcement listing fields.
- Tab through fields → each field announced.
- Press Enter on a field → connection attempt.
- Watch for: silent failures. With `-Dxmageaccess.log=debug` you should see `[XMage Access][Connect] attach failed (...)` only if reflection drift hit a missing field.

### 6.2 Lobby

- Connect succeeds → lobby loads.
- Hear: lobby welcome announcement (after the connect dialog closes).
- Browse tables with arrow keys → each table announced.
- Join a table → table waiting dialog announces.

### 6.3 New table dialog

- Click "New" or invoke whatever opens NewTableDialog.
- Hear: dialog options.
- Tab through options.
- Confirm.
- Listen for: missing announcements — fall back to debug log if anything seems silent.

### 6.4 Deck editor

- Open deck editor (in / out of game).
- Hear: zone announcements, card count.
- Use D to read card detail.
- Use Ctrl+F1 for shortcut help.
- Watch: card detail no longer says raw HTML — the new `cleanHtml` path normalizes whitespace and entities.

### 6.5 Sideboarding

- Trigger between-game sideboard.
- Hear: "Sideboarding. X cards in main deck, Y in sideboard. …"
- Tab/Shift+Tab between zones.
- Move cards Main → Sideboard with Enter, then back.
- Ctrl+R for summary, Ctrl+Enter to submit.
- Listen: after submit, no stuck timers in the next game.

### 6.6 Game panel

- Start a game.
- Hear: turn change, phase, life totals announced.
- Press Ctrl+F1..Ctrl+F11 game shortcuts — each should respond immediately (the reflection cache makes these effectively free now).
- Press Ctrl+1, Ctrl+2, Ctrl+3, Ctrl+Z — each button-by-shortcut press should respond.
- Damage assignment / combat — verify combat summary still announces correctly.

### 6.7 Card dialogs (PickChoice / PickNumber / PickCheckBox / PickPile / PickMultiNumber / ShowCards / UserRequest / GameEnd)

For each, when the dialog opens:

- Hear the dialog announcement.
- Navigate with arrows / Tab.
- Confirm with Ctrl+Enter (or whatever shortcut applies).
- Watch: text should be cleanly stripped of HTML — no `<b>` or `&nbsp;` in spoken output.

### 6.8 Draft

- Join a draft pod.
- Hear: pack announcement, pick countdown.
- Pick cards via the announced shortcuts.

### 6.9 Tournament

- Join / spectate a tournament.
- Hear: tournament state announcements.
- Verify: no error spam in stderr unless `-Dxmageaccess.log=debug` is set.

### 6.10 Preferences dialog

- Open Preferences.
- Hear announcement of the dialog and tabs.

### 6.11 Quit shortcut

- From anywhere, press Ctrl+Q.
- Hear: "Quitting." and the client exits.

## 7. Regression watchlist

Things most likely to break given the changes. Eyes open for:

- **Speech sometimes silent** — if the new dedup window is firing too aggressively for some announcement pair, you may miss content. Bump `DEDUP_WINDOW_MS` in `SpeechOutput.java` from 250 to 100 if so.
- **Speech sometimes lagged** — single-thread executor means a long synthesizer call (Windows SAPI with a slow voice) blocks subsequent calls. If this becomes a problem, consider increasing the executor's pool size (currently 1 thread to preserve ordering).
- **`GameStateTrackerBridge` initialization fails** — if the bridge can't find `xmageaccess.handlers.GameStateTracker` at agent boot, hooks silently no-op. Symptom: in-game hooks don't fire (no "your turn" / phase announcements). Look for `[XMage Access] Bridge init failed: …` in stderr.
- **Cached negative reflection results** — `ReflectionUtils` caches "this field doesn't exist" as well as "it does." If XMage adds a field that didn't exist when the agent first looked, the cache will keep returning null until the JVM restarts. This is intentional — XMage classes are stable per JVM lifetime — but be aware if you're hot-reloading.
- **Process redirect to `/dev/null` on a system without that device** — extremely unlikely on macOS/Linux/Windows, but if it happens, speech fails silently. The redirect targets are baked into the OS-specific engine classes.

## 8. What's *not* covered by this build

The audit deliberately deferred these items because they touch many files and the project has no test infrastructure:

- **`AbstractDialogHandler` base class refactor.** 15 dialog handlers share an identical scaffolding (~30 lines each). Extracting that into a base saves ~450 LOC but is invasive. Recommend doing this one handler at a time after each is manually verified.
- **`<minimizeJar>` on the shade plugin.** Would strip unused ByteBuddy classes from the agent JAR (~3-5 MB win), but ByteBuddy's `@Advice` classes are referenced only by annotation, so minimize without correct filters will silently break runtime instrumentation.
- **ByteBuddy version bump.** Currently pinned at 1.14.18; no urgent reason to bump.
- **API tightening (`public` → package-private, narrowing `throws Exception`).** Low value relative to the risk of breaking a ByteBuddy reflective lookup.

## 9. Reverting if needed

The old prebuilt JAR is still at `dist/xmage-access-0.1.0.jar`. To roll back: copy that back into `mage-client/lib/`. The plan, audit findings, and diff are preserved in git history.
