#!/usr/bin/env bash
# Build the native-debug-symbols.zip Play asks for, from a built AAB.
#
# AGP cannot produce this one. debugSymbolLevel only extracts symbols from
# native code the project itself compiles, and the only .so here are two
# prebuilt AndroidX libraries that Google ships stripped -- so
# extractReleaseNativeDebugMetadata runs and emits nothing.
#
# They do keep .dynsym (exported names) though, which is enough to satisfy the
# Play check and to symbolicate a crash inside them to a function name rather
# than a raw address. So package the shipped .so directly.
#
# Upload via the bundle row's kebab menu: "Upload native debug symbols".
# It attaches to an existing bundle -- no need to re-upload the AAB.
#
# Usage: scripts/make-native-symbols.sh [path/to/app.aab] [out.zip]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AAB="${1:-$ROOT/app/build/outputs/bundle/release/app-release.aab}"
OUT="${2:-$ROOT/native-debug-symbols.zip}"

[ -f "$AAB" ] || { echo "No AAB at $AAB" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q -o "$AAB" 'base/lib/*' -d "$TMP"
[ -d "$TMP/base/lib" ] || { echo "That bundle ships no native libraries." >&2; exit 1; }

mkdir -p "$TMP/symbols"
for abi in "$TMP"/base/lib/*/; do
  name="$(basename "$abi")"
  mkdir -p "$TMP/symbols/$name"
  cp "$abi"*.so "$TMP/symbols/$name/" 2>/dev/null || true
done

rm -f "$OUT"
( cd "$TMP/symbols" && zip -qr "$OUT" . -x ".*" )

echo "Wrote $OUT"
unzip -l "$OUT" | grep -c '\.so$' | xargs echo "  .so files:"
