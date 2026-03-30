#!/usr/bin/env bash
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
[ ! -f "$WRAPPER_JAR" ] && echo "Run: bash setup.sh first" && exit 1
JAVA_EXE="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVA_EXE" &>/dev/null || { echo "Java not found"; exit 1; }
exec "$JAVA_EXE" -Xmx64m -Xms64m -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
