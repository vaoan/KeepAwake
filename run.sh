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
exec "$JDK/bin/java" -jar "$JAR"
