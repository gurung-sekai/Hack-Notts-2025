#!/usr/bin/env sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/The legend of Esran - Escape Unemployment"
WRAPPER="$PROJECT_DIR/gradlew"
cd "$PROJECT_DIR" || exit 1
if [ -x "$WRAPPER" ]; then
  "$WRAPPER" "$@"
  status=$?
  if [ $status -eq 0 ]; then
    exit 0
  fi
  if command -v gradle >/dev/null 2>&1; then
    echo "Gradle wrapper failed with status $status, falling back to system Gradle." >&2
    exec gradle "$@"
  fi
  exit $status
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle wrapper not found and no system Gradle available" >&2
exit 1
