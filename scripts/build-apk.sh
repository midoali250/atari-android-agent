#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"
BUILD_TYPE="debug"
BUNDLE_ROMS="false"
for argument in "$@"; do
  case "$argument" in
    debug|release) BUILD_TYPE="$argument" ;;
    --bundle-roms) BUNDLE_ROMS="true" ;;
    -h|--help)
      printf 'Usage: %s [debug|release] [--bundle-roms]\n' "$0"
      exit 0
      ;;
    *) printf 'Unknown argument: %s\nRun with --help for usage.\n' "$argument" >&2; exit 2 ;;
  esac
done
if [[ "$BUILD_TYPE" == "release" ]]; then
  TASK=":android:exportReleaseApk"
else
  TASK=":android:exportDebugApk"
fi
cd "$ROOT"
printf 'Building %s APK. ROM packaging: %s\n' "$BUILD_TYPE" "$BUNDLE_ROMS"
exec ./gradlew "$TASK" "-PbundleRoms=$BUNDLE_ROMS" --console=plain
