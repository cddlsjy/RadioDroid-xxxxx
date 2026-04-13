#!/usr/bin/env sh

set -e

# 启用调试模式
set -x

dirname="$(cd "$(dirname "$0")" && pwd)"
echo "Current directory: $(pwd)"
echo "Script directory: $dirname"
echo "Checking for gradle-wrapper.jar at: $dirname/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$dirname/gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "ERROR: gradle-wrapper.jar not found!"
    echo "Listing contents of $dirname/gradle/wrapper:"
    ls -la "$dirname/gradle/wrapper/"
    exit 1
fi

echo "Found gradle-wrapper.jar"

if [ -z "$JAVA_HOME" ] ; then
    echo "JAVA_HOME not set, looking for java in PATH"
    JAVACMD=$(which java)
else
    echo "Using JAVA_HOME: $JAVA_HOME"
    JAVACMD="$JAVA_HOME/bin/java"
fi

echo "Java command: $JAVACMD"

if [ ! -x "$JAVACMD" ] ; then
    echo "Error: JAVA_HOME is not set and no 'java' command could be found in your PATH."
    echo "Please set the JAVA_HOME variable in your environment to match the"
    echo "location of your Java installation."
    exit 1
fi

echo "Java version: $($JAVACMD -version 2>&1)"
echo "Executing: $JAVACMD $JAVA_OPTS -jar $dirname/gradle/wrapper/gradle-wrapper.jar $@"

exec "$JAVACMD" "$JAVA_OPTS" -jar "$dirname/gradle/wrapper/gradle-wrapper.jar" "$@"