#!/bin/bash
# Undoes ./install.sh: stops KeepAwake, removes it from login and deletes the installed copy.
set -eu
LABEL="com.keepawake"
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
rm -f "$HOME/Library/LaunchAgents/$LABEL.plist"
rm -rf "$HOME/Library/Application Support/KeepAwake"
echo "KeepAwake desinstalado."
