#!/bin/bash
# Launches KeepAwake in the foreground from any directory.
set -eu
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/target/keepawake.jar"
if [ ! -f "$JAR" ]; then
  echo "No se encontró $JAR. Compila primero con: JAVA_HOME=\$(brew --prefix openjdk@21) mvn package" >&2
  exit 1
fi
JDK="$(brew --prefix openjdk@21 2>/dev/null || true)"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then
  echo "No se encontró openjdk@21. Instálalo con: brew install openjdk@21" >&2
  exit 1
fi
# Native menu-bar item (native/build-bar.sh, needs swiftc); rebuilt when the Swift source or the build script change.
# Without it (or if the build fails) KeepAwake uses the Java tray icon.
JAVA_OPTS=()
if [ "$(uname -s)" = Darwin ] && command -v swiftc >/dev/null 2>&1 && xcode-select -p >/dev/null 2>&1; then
  APP="$HOME/Library/Caches/keepawake-build/KeepAwakeBar.app"
  BAR="$APP/Contents/MacOS/KeepAwakeBar"
  if ! { [ -x "$BAR" ] && [ "$BAR" -nt "$DIR/native/KeepAwakeBar.swift" ] && [ "$BAR" -nt "$DIR/native/build-bar.sh" ]; }; then
    "$DIR/native/build-bar.sh" "$APP" >/dev/null 2>&1 \
      || echo "Aviso: no se pudo compilar el ayudante nativo; se usa el icono de Java." >&2
  fi
  [ -x "$BAR" ] && JAVA_OPTS+=("-Dkeepawake.bar=$BAR")
fi
exec "$JDK/bin/java" ${JAVA_OPTS[@]+"${JAVA_OPTS[@]}"} -jar "$JAR"
