#!/bin/bash
# XMage Accessible Launcher (macOS)
# Shows a checkbox to enable screen reader support, then launches XMage.
# Place this file in the same folder as XMageLauncher (the mage root folder).

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

AGENT_JAR="xmage/mage-client/lib/xmage-access-0.1.0.jar"

if [ ! -f "$AGENT_JAR" ]; then
    say "Error. xmage access jar not found."
    echo ""
    echo "ERROR: xmage-access-0.1.0.jar not found."
    echo "Expected at: $AGENT_JAR"
    echo ""
    echo "Please copy xmage-access-0.1.0.jar into the xmage/mage-client/lib/ folder."
    echo ""
    read -p "Press Enter to close..."
    exit 1
fi

java -Djava.net.preferIPv4Stack=true -cp "$AGENT_JAR" xmageaccess.launcher.AccessibleLauncher
