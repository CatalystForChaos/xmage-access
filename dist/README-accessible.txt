XMage Access - Screen Reader Support for XMage
================================================

XMage Access makes the Magic: The Gathering client XMage playable
with a screen reader. It works with NVDA, JAWS, and Windows SAPI
on Windows, and VoiceOver on macOS.


WHAT'S INCLUDED
---------------

- xmage-access-0.1.0.jar  (the accessibility agent)
- run-accessible-launcher.cmd  (Windows launcher with accessibility checkbox)
- run-accessible-launcher.command  (macOS launcher with accessibility checkbox)
- startClient-accessible.bat  (Windows direct launch, always accessible)
- startClient-accessible.command  (macOS direct launch, always accessible)
- tolk/x64/  (64-bit Tolk DLLs for NVDA/JAWS on Windows)
- tolk/x86/  (32-bit Tolk DLLs, use only if running 32-bit Java on Windows)
- This README


INSTALLATION (WINDOWS)
----------------------

Step 1: Install XMage
  Download XMage from xmage.today and install it.
  You should have a folder like C:\XMage\ with subfolders including
  xmage\mage-client\lib\.

Step 2: Copy the agent JAR
  Copy xmage-access-0.1.0.jar into the lib folder inside mage-client.
  Example: C:\XMage\xmage\mage-client\lib\xmage-access-0.1.0.jar

Step 3: Copy the launcher script
  Copy run-accessible-launcher.cmd into the XMage root folder
  (the same folder where XMageLauncher and run-LAUNCHER.cmd live).
  Example: C:\XMage\run-accessible-launcher.cmd

Step 4: Copy Tolk DLLs for NVDA/JAWS support
  Tolk lets the mod talk directly to your screen reader (NVDA or JAWS)
  instead of using the Windows built-in voice.
  The DLLs are included in this ZIP in the tolk folder.
  Most XMage installs use 64-bit Java. Copy ALL files from tolk\x64\
  into the mage-client folder (next to startClient.bat):
    - Tolk.dll
    - SAAPI64.dll
    - nvdaControllerClient64.dll
  Example: C:\XMage\xmage\mage-client\Tolk.dll
  If you use 32-bit Java, copy from tolk\x86\ instead.
  Without Tolk, speech falls back to Windows SAPI (built-in voice).

Step 5: Launch
  Double-click run-accessible-launcher.cmd to start.
  A small window opens with a checkbox and a Launch button.
  Tab to the checkbox and press Space to enable accessibility.
  Tab to Launch XMage and press Enter.
  The normal XMage launcher opens with accessibility enabled.
  Your choice is saved - next time the checkbox remembers.


INSTALLATION (macOS)
--------------------

Step 1: Install XMage
  Download XMage from xmage.today and install it.
  You should have a folder like ~/XMage/ with subfolders including
  xmage/mage-client/lib/.

Step 2: Install Java 8
  XMage needs Java 8. Install Azul Zulu 8 for macOS:
  https://www.azul.com/downloads/?version=java-8-lts&os=macos
  Pick the .dmg installer for your Mac (Apple Silicon or Intel).
  After installing, open Terminal and type: java -version
  You should see version 1.8 (also called Java 8).

Step 3: Copy the agent JAR
  Copy xmage-access-0.1.0.jar into the lib folder inside mage-client.
  Example: ~/XMage/xmage/mage-client/lib/xmage-access-0.1.0.jar

Step 4: Copy the launcher script
  Copy run-accessible-launcher.command into the XMage root folder
  (the same folder where XMageLauncher JAR lives).
  Example: ~/XMage/run-accessible-launcher.command

Step 5: Make the script executable
  Open Terminal and run:
  chmod +x ~/XMage/run-accessible-launcher.command

Step 6: Launch
  Double-click run-accessible-launcher.command in Finder to start.
  A small window opens with a checkbox and a Launch button.
  VoiceOver will announce "Enable screen reader support, unchecked".
  Press VO+Space to check the box, then Tab to Launch XMage
  and press VO+Space or Enter.
  The normal XMage launcher opens with accessibility enabled.
  Your choice is saved - next time the checkbox remembers.

Note: On macOS, speech uses the built-in say command (VoiceOver voice).
  No Tolk DLLs are needed on Mac.


TWO WAYS TO LAUNCH
------------------

Option A: Accessible Launcher (recommended)
  Windows: run-accessible-launcher.cmd in the XMage root folder.
  macOS: run-accessible-launcher.command in the XMage root folder.
  This shows a checkbox to toggle accessibility on or off,
  then opens the normal XMage launcher. Your choice is saved
  in installed.properties so it persists across sessions.

Option B: Direct Launch
  Windows: startClient-accessible.bat in the mage-client folder.
  macOS: startClient-accessible.command in the mage-client folder.
  This skips the XMage launcher entirely and starts the client
  directly with accessibility always on.


HOW IT WORKS
------------

The mod adds two layers of accessibility:

1. Keyboard shortcuts (work anywhere in XMage)
2. Accessible Game Window (opens automatically when a game starts)

Both work at the same time. Use whichever feels more natural.


ACCESSIBLE GAME WINDOW
-----------------------

When a game starts, a second window called "XMage Accessible Game"
opens automatically. This window has 8 zones you navigate with
standard screen reader keys:

  Tab / Shift+Tab  = Move between zones
  Up / Down arrow  = Move between items within a zone
  Enter            = Activate the selected item (play card, click button)
  D                = Read detailed card text
  Escape           = Return focus to the main XMage window

The zones (in Tab order):

  1. Actions
     Shows the current game prompt (like "Select attackers"),
     available buttons (OK, Cancel, Special, Undo),
     and any targets or abilities you need to choose from.
     Press Enter on a button to click it.
     Press Enter on a target or ability to select it.

  2. Hand
     Your cards in hand. Each item shows the card name and mana cost.
     Press Enter to play/cast the selected card.
     Press D to hear full card text including rules.

  3. Your Battlefield
     Your permanents (creatures, lands, other permanents).
     Creatures are listed first, then other permanents, then lands.
     Creatures show power/toughness and tapped status.
     Press Enter to click a permanent (attack, block, activate, target).

  4. Opponent Battlefield
     Your opponent's permanents, same format as yours.
     In multiplayer, each item shows the player name prefix.
     Press Enter to target an opponent's permanent.

  5. Stack
     Spells and abilities currently on the stack.
     Press D to read what each spell does.
     Press Enter when prompted to target something on the stack.

  6. Graveyards
     All players' graveyard cards, with player name prefix.
     Press D to read full card details.

  7. Exile
     Exiled cards, grouped by exile zone.
     Press D to read full card details.

  8. Game Log
     The last 20 lines of game activity.
     Use arrow keys to read through recent events.
     No action on Enter (informational only).

All zones update automatically every half second.


KEYBOARD SHORTCUTS (ORIGINAL)
-----------------------------

These work anywhere when a game is active, even without
the accessible window focused:

Reading game state:
  Ctrl+F1           Read current prompt and phase
  Ctrl+F2           Read all player life totals and info
  Ctrl+F3           Read your hand (full list)
  Ctrl+F4           Read the battlefield summary
  Ctrl+F5           Read the stack
  Ctrl+F6           Read graveyards
  Ctrl+F7           Read exile zones
  Ctrl+F8           Read combat info (attackers/blockers)
  Ctrl+F9           Read your mana pool

Hand navigation:
  Ctrl+Left/Right   Move through cards in hand
  Ctrl+Enter        Play/cast the card at your cursor
  Ctrl+D            Read detailed card info

Battlefield navigation:
  Ctrl+Shift+Left/Right    Move through permanents
  Ctrl+Shift+Up/Down       Switch between players
  Ctrl+Shift+Enter         Click the permanent at your cursor
  Ctrl+Shift+D             Read permanent detail

Buttons:
  Ctrl+1            Click OK / Yes / left button
  Ctrl+2            Click Cancel / No / right button
  Ctrl+3            Click Special button
  Ctrl+Z            Undo

Targets and abilities:
  Ctrl+T                  Read available targets or abilities
  Ctrl+Shift+1 through 9  Select target or ability by number

Game log:
  Ctrl+L            Read last 3 game log entries
  Ctrl+Shift+L      Read last 10 game log entries


BEFORE THE GAME (LOBBY)
------------------------

Connect dialog:
  Tab through the fields (server, port, username, password).
  The mod announces each field name and its current value.
  Press Enter or click Connect to join.

Lobby:
  Ctrl+G            List active games
  Ctrl+Up/Down      Navigate games
  Ctrl+J            Join selected game
  Ctrl+N            Create new game


DECK EDITOR
-----------

Open the deck editor from the lobby with Ctrl+E. An accessible
deck editor window opens with zones you navigate with Tab.
Press Ctrl+F1 to hear all available shortcuts.

Navigation:
  Tab / Shift+Tab   Move between zones (search, results, deck, sideboard)
  Up / Down arrow   Move through items in a zone
  Enter             Add card (in results) or remove card (in deck/sideboard)
  D                 Read detailed card text for selected card
  Escape            Return focus to main XMage window

File operations:
  Ctrl+N            New deck (clear current)
  Ctrl+O            Load deck from file
  Ctrl+S            Save deck to file
  Ctrl+I            Import deck (external formats)
  Ctrl+Shift+E      Export deck

Deck tools:
  Ctrl+G            Generate random deck
  Ctrl+A            Add lands dialog
  Ctrl+L            Check deck legality (reads format results)
  Ctrl+R            Read full deck summary (card counts by type)
  Ctrl+Shift+N      Set deck name
  Ctrl+Enter        Submit deck (during sideboarding)
  Ctrl+F1           Read all shortcuts

Color filters (toggle search results by color):
  Ctrl+1            White
  Ctrl+2            Blue
  Ctrl+3            Black
  Ctrl+4            Red
  Ctrl+5            Green
  Ctrl+6            Colorless

Type filters (toggle search results by card type):
  Ctrl+Shift+1      Creatures
  Ctrl+Shift+2      Instants
  Ctrl+Shift+3      Sorceries
  Ctrl+Shift+4      Enchantments
  Ctrl+Shift+5      Artifacts
  Ctrl+Shift+6      Planeswalkers
  Ctrl+Shift+7      Lands

Rarity filters:
  Ctrl+F2           Common
  Ctrl+F3           Uncommon
  Ctrl+F4           Rare
  Ctrl+F5           Mythic
  Ctrl+F6           Special

Expansion set:
  Ctrl+T            Next expansion set
  Ctrl+Shift+T      Previous expansion set

Filter management:
  Ctrl+F            Read active filters
  Ctrl+Shift+F      Clear all filters
  Ctrl+Shift+C      Cycle search mode (names, types, rules text, all)

Note: In Commander format, the commander card goes in the sideboard zone.


TYPICAL GAME FLOW
-----------------

1. Launch with run-accessible-launcher.cmd (Windows) or
   run-accessible-launcher.command (macOS)
2. Check "Enable screen reader support", then Launch XMage
3. In the XMage launcher, click Launch Client (or Client+Server)
4. Connect to a server (or localhost if running your own)
5. In the lobby, create a new game (Ctrl+N)
6. Pick deck settings, start the game
7. The accessible window opens automatically
8. Tab to the Actions zone to hear the current prompt
9. Tab to Hand to see your cards, press Enter to play one
10. Tab to Actions to click OK/Cancel when prompted
11. When attacking: Tab to Your Battlefield, arrow to a creature, Enter
12. When blocking: same process
13. Use D anytime to hear full card text


TROUBLESHOOTING
---------------

No speech at all:
  Make sure you used the accessible launcher and checked
  the "Enable screen reader support" checkbox. Verify that
  xmage-access-0.1.0.jar is in xmage/mage-client/lib/.
  On macOS, make sure VoiceOver is enabled (Cmd+F5).

Speech is Windows built-in voice instead of NVDA/JAWS:
  Copy the Tolk DLLs from the tolk\x64\ folder in this ZIP
  into the mage-client folder. Make sure Tolk.dll, SAAPI64.dll,
  and nvdaControllerClient64.dll are all present.

Accessible window does not open:
  The window only opens when a game starts. Make sure you are
  in an active game (not the lobby).

Double speech (screen reader + SAPI both talking):
  This can happen if Tolk is sending to both your screen reader
  and SAPI. Open NVDA settings or JAWS settings and adjust
  the speech output, or remove Tolk.dll to use only SAPI.

Game feels unresponsive:
  XMage needs Java 8. Make sure Java is installed and on your PATH.
  Open a command prompt (Windows) or Terminal (macOS) and type:
  java -version
  You should see version 1.8 (also called Java 8).
  On macOS, install Azul Zulu 8 from azul.com/downloads.

Script won't open on macOS:
  If double-clicking the .command file does nothing, open Terminal
  and run: chmod +x run-accessible-launcher.command
  If macOS blocks it as unidentified developer, right-click the
  file in Finder, choose Open, then click Open in the dialog.

Buttons say "not available":
  Some buttons only appear during certain game phases.
  Press Ctrl+F1 to hear the current prompt and see which
  buttons are available.

Checkbox does not save:
  The accessible launcher saves settings to installed.properties
  in the XMage root folder. Make sure you have write permission
  to that file. On Windows, try running as administrator.
  On macOS, check file permissions with: ls -la installed.properties
