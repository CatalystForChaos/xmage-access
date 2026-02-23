#!/bin/bash
# XMage Client with Accessibility Agent (macOS)
# This script launches XMage with screen reader support.
# Place this file in the mage-client folder next to startClient.command.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

AGENT_JAR="$SCRIPT_DIR/lib/xmage-access-0.1.0.jar"

if [ ! -f "$AGENT_JAR" ]; then
    say "Error. Accessibility agent not found."
    echo ""
    echo "ERROR: Accessibility agent not found."
    echo "Expected location: $AGENT_JAR"
    echo ""
    echo "Please copy xmage-access-0.1.0.jar into the lib folder."
    echo ""
    read -p "Press Enter to close..."
    exit 1
fi

echo "Starting XMage with accessibility support..."
java -Xmx2000m \
    -Dfile.encoding=UTF-8 \
    -Dsun.jnu.encoding=UTF-8 \
    -Djava.net.preferIPv4Stack=true \
    -javaagent:"$AGENT_JAR" \
    -jar ./lib/mage-client-1.4.58.jar &
