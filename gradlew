#!/bin/sh
DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
JAVACMD="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}/bin/java"
if [ ! -x "$JAVACMD" ]; then
  JAVACMD=$(command -v java)
fi
exec "$JAVACMD" -Xmx64m -Xms64m -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
