#!/usr/bin/env sh

set -e

if [ -n "$DEBUG" ]; then
  set -x
fi

dirname="$(cd "$(dirname "$0")" && pwd)"

if [ -f "$dirname/gradle/wrapper/gradle-wrapper.jar" ]; then
  if [ "$(uname)" = "Darwin" ]; then
    # Add workaround for issue with JDK 14 on macOS
    # See: https://github.com/gradle/gradle/issues/12518
    export JAVA_OPTS="$JAVA_OPTS -Xmx4096m"
  fi

  if [ -n "$JAVA_HOME" ]; then
    exec "$JAVA_HOME/bin/java" "$JAVA_OPTS" -jar "$dirname/gradle/wrapper/gradle-wrapper.jar" "$@"
  else
    exec java "$JAVA_OPTS" -jar "$dirname/gradle/wrapper/gradle-wrapper.jar" "$@"
  fi
else
  echo "Error: Could not find gradle-wrapper.jar"
  exit 1
fi