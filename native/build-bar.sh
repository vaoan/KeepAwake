#!/bin/sh
# Builds KeepAwakeBar.app, the native macOS helper: the menu-bar item (narrower than Java's TrayIcon, real NSMenu).
# KeepAwake falls back to the Java tray icon when it is missing. Needs swiftc (Xcode Command Line Tools).
# Usage: native/build-bar.sh [out .app]   (default: ~/Library/Caches/keepawake-build/KeepAwakeBar.app)
# Prints the path of the executable inside the bundle.
set -e
cd "$(dirname "$0")"
OUT="${1:-$HOME/Library/Caches/keepawake-build/KeepAwakeBar.app}"
# Built beside the target and moved into place on success, so a failed rebuild keeps the working helper.
FINAL="$OUT"
mkdir -p "$(dirname "$FINAL")"
OUT="$FINAL.new.$$"
trap 'rm -rf "$OUT"' EXIT
rm -rf "$OUT"
mkdir -p "$OUT/Contents/MacOS" "$OUT/Contents/Resources"
swiftc -O KeepAwakeBar.swift -o "$OUT/Contents/MacOS/KeepAwakeBar"

cat > "$OUT/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleIdentifier</key><string>com.keepawake.helper</string>
  <key>CFBundleName</key><string>KeepAwake</string>
  <key>CFBundleDisplayName</key><string>KeepAwake</string>
  <key>CFBundleExecutable</key><string>KeepAwakeBar</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSMinimumSystemVersion</key><string>13.0</string>
  <!-- No Dock icon, no menu bar of its own. -->
  <key>LSUIElement</key><true/>
</dict>
</plist>
PLIST
codesign --force --deep -s - "$OUT" 2>/dev/null || true
rm -rf "$FINAL"
mv "$OUT" "$FINAL"
echo "$FINAL/Contents/MacOS/KeepAwakeBar"
