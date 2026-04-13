#!/usr/bin/env sh

set -e

dirname="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$JAVA_HOME" ] ; then
    JAVACMD=$(which java)
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
    echo "Error: JAVA_HOME is not set and no 'java' command could be found in your PATH."
    echo "Please set the JAVA_HOME variable in your environment to match the"
    echo "location of your Java installation."
    exit 1
fi

if [ -f "$dirname/gradle/wrapper/gradle-wrapper.jar" ]; then
    exec "$JAVACMD" "$JAVA_OPTS" -jar "$dirname/gradle/wrapper/gradle-wrapper.jar" "$@"
else
    echo "Error: Could not find gradle-wrapper.jar"
    exit 1
fi