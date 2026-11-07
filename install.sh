#!/bin/bash
# Installs KeepAwake for this user and starts it at login (LaunchAgent). Only the menu bar icon, no Dock icon.
# Builds the jar and the native helper, copies them to ~/Library/Application Support/KeepAwake and starts it now.
# Run it again after pulling changes to update. Undo with ./uninstall.sh.
set -eu
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="$HOME/Library/Application Support/KeepAwake"
LABEL="com.keepawake"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"

JDK="$(brew --prefix openjdk@21 2>/dev/null || true)"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then
  echo "No se encontró openjdk@21. Instálalo con: brew install openjdk@21" >&2
  exit 1
fi

# Stop the running copy first: mvn package must not run while the app is open.
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true

(cd "$DIR" && JAVA_HOME="$JDK" mvn -q package)
mkdir -p "$DEST"
cp "$DIR/target/keepawake.jar" "$DEST/keepawake.jar"

BAR_OPT=""
if command -v swiftc >/dev/null 2>&1 && xcode-select -p >/dev/null 2>&1 \
    && "$DIR/native/build-bar.sh" "$DEST/KeepAwakeBar.app" >/dev/null; then
  BAR_OPT="<string>-Dkeepawake.bar=$DEST/KeepAwakeBar.app/Contents/MacOS/KeepAwakeBar</string>"
else
  echo "Aviso: no se pudo compilar el ayudante nativo; se usa el icono de Java." >&2
fi

mkdir -p "$(dirname "$PLIST")"
cat > "$PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>$JDK/bin/java</string>
    <string>-Dapple.awt.UIElement=true</string>
    $BAR_OPT
    <string>-jar</string>
    <string>$DEST/keepawake.jar</string>
  </array>
  <key>RunAtLoad</key><true/>
  <!-- Restart after a crash, but not after "Salir". -->
  <key>KeepAlive</key><dict><key>SuccessfulExit</key><false/></dict>
  <key>ProcessType</key><string>Interactive</string>
  <key>StandardErrorPath</key><string>$DEST/keepawake.log</string>
</dict>
</plist>
PLIST

launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "KeepAwake instalado en $DEST y se abrirá al iniciar sesión."
