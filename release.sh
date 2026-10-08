#!/usr/bin/env bash
# E7-2 Script build/ký/release (E7-1 keystore + E0-1 repo do Owner làm).
# Dùng: KEYSTORE=/path/harness.jks KEY_ALIAS=harness ./release.sh 0.1.0
# Yêu cầu env: KEYSTORE, KEY_ALIAS, KEYSTORE_PASS (không ghi vào file).
set -euo pipefail
VER="${1:?dùng: release.sh <version, vd 0.1.0>}"
: "${KEYSTORE:?thiếu KEYSTORE}" ; : "${KEY_ALIAS:?thiếu KEY_ALIAS}" ; : "${KEYSTORE_PASS:?thiếu KEYSTORE_PASS}"
cd "$(dirname "$0")/probe/vit-ngan-probe"
./gradlew :app:testDebugUnitTest :app:assembleRelease
APK="app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED="app/build/outputs/apk/release/vitngan-harness-$VER.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass:env KEYSTORE_PASS --out "$SIGNED" "$APK" 2>/dev/null \
  || "$ANDROID_HOME/build-tools/36.0.0/apksigner" sign --ks "$KEYSTORE" --ks-pass:env KEYSTORE_PASS --out "$SIGNED" "$APK"
apksigner verify --print-certs "$SIGNED" | head -5
{
  echo "# Release v$VER ($(date +%F))"
  echo "- APK: $(basename "$SIGNED")"
  ./gradlew :app:testDebugUnitTest --rerun-tasks 2>/dev/null | grep -cE "PASSED|passed" || true
} > CHANGELOG-v$VER.md
echo "OK: $SIGNED"
