#!/usr/bin/env sh

set -eu

APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
  echo "Gradle Wrapper jar is missing: $CLASSPATH" >&2
  echo "Generate it locally with: gradle wrapper" >&2
  exit 1
fi

JAVACMD=${JAVACMD:-java}

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
