#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle wrapper JAR is not present and no system Gradle was found." >&2
echo "Open this project in Android Studio, or install Gradle 9.6 and run: gradle wrapper --gradle-version 9.6.0" >&2
exit 1
