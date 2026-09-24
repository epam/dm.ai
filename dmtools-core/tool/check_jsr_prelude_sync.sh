#!/usr/bin/env bash
# Verifies that the vendored jsr Node-compat preludes under
# dmtools-core/src/main/resources/jsr/ are byte-identical to the JS
# prelude sources in IstiN/quickjs_runtime at the ref pinned in
# JSR_PRELUDES_PIN (the canonical source of truth).
#
# quickjs_runtime embeds the preludes as raw Dart string constants
# (r'''...'''), one per lib/src/node_compat*.dart file. This script
# extracts them exactly the way the runtime ships them and diffs
# against the vendored copies.
#
# Usage:
#   check_jsr_prelude_sync.sh          # compare (CI mode, exits 1 on drift)
#   check_jsr_prelude_sync.sh --update # overwrite vendored copies from the pin
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-}"
REF="$(cat JSR_PRELUDES_PIN)"
RES="src/main/resources/jsr"
SRC=(
  "node_compat.dart:node_compat.js"
  "node_compat_async.dart:node_compat_async.js"
  "node_compat_buffer.dart:node_compat_buffer.js"
  "node_compat_url.dart:node_compat_url.js"
  "node_compat_fetch.dart:node_compat_fetch.js"
)

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

for pair in "${SRC[@]}"; do
  dart="${pair%%:*}"; js="${pair##*:}"
  curl -fsSL "https://raw.githubusercontent.com/IstiN/quickjs_runtime/${REF}/lib/src/${dart}" -o "$TMP/$dart"
  python3 - "$TMP/$dart" "$TMP/$js" <<'PYEOF'
import re, sys
text = open(sys.argv[1]).read()
m = re.search(r"r'''(.*?)''';", text, re.S)
if not m:
    sys.exit(f"no raw string prelude found in {sys.argv[1]}")
open(sys.argv[2], "w").write(m.group(1))
PYEOF
done

STATUS=0
for pair in "${SRC[@]}"; do
  dart="${pair%%:*}"; js="${pair##*:}"
  if [ ! -f "$RES/$js" ]; then
    echo "MISSING vendored copy: $RES/$js"
    STATUS=1
  elif [ "$MODE" = "--update" ]; then
    cp "$TMP/$js" "$RES/$js"
    echo "updated $RES/$js"
  elif ! diff -q "$TMP/$js" "$RES/$js" >/dev/null; then
    echo "DRIFT: $RES/$js != IstiN/quickjs_runtime@${REF}:lib/src/${dart}"
    diff "$TMP/$js" "$RES/$js" | head -20
    STATUS=1
  else
    echo "ok: $RES/$js == quickjs_runtime@${REF}"
  fi
done

if [ "$STATUS" -ne 0 ]; then
  echo ""
  echo "Fix: bump JSR_PRELUDES_PIN to the adopted quickjs_runtime release and run:"
  echo "  dmtools-core/tool/check_jsr_prelude_sync.sh --update"
fi
exit "$STATUS"
