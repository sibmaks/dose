#!/bin/sh
# Bootstrap the official Gradle wrapper binary when missing (not bundled with the ZIP).
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo 'Downloading the official Gradle 8.10.2 wrapper JAR...'
  TMP="$JAR.tmp.$$"
  trap 'rm -f "$TMP"' EXIT HUP INT TERM
  URL='https://services.gradle.org/distributions/gradle-8.10.2-wrapper.jar'
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$URL" --output "$TMP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$TMP" "$URL"
  else
    echo 'Install curl or wget, or use an existing Gradle installation to generate the wrapper.' >&2
    exit 1
  fi
  EXPECTED='2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
  if command -v shasum >/dev/null 2>&1; then
    ACTUAL=$(shasum -a 256 "$TMP" | cut -d' ' -f1)
  elif command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$TMP" | cut -d' ' -f1)
  else
    echo 'A SHA-256 utility (shasum or sha256sum) is required.' >&2
    exit 1
  fi
  if [ "$EXPECTED" != "$ACTUAL" ]; then
    echo "Gradle wrapper verification failed: $ACTUAL" >&2
    exit 1
  fi
  mv "$TMP" "$JAR"
  trap - EXIT HUP INT TERM
fi
exec java -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
