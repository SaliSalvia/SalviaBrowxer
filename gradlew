#!/usr/bin/env sh

# Lightweight Gradle wrapper for CI environments.
# The repository pins the Gradle version in gradle/wrapper/gradle-wrapper.properties.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.7
DIST_NAME="gradle-${GRADLE_VERSION}-bin"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/${DIST_NAME}"
GRADLE_HOME="${CACHE_DIR}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${CACHE_DIR}/${DIST_NAME}.zip"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${CACHE_DIR}"
  if [ ! -f "${ZIP_FILE}" ]; then
    curl --fail --location --retry 3 \
      "https://services.gradle.org/distributions/${DIST_NAME}.zip" \
      --output "${ZIP_FILE}"
  fi
  rm -rf "${GRADLE_HOME}"
  unzip -q "${ZIP_FILE}" -d "${CACHE_DIR}"
fi

exec "${GRADLE_HOME}/bin/gradle" "$@"
