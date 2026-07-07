#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOOK="$REPO_ROOT/.git/hooks/pre-commit"
if [ -e "$HOOK" ] && [ ! -L "$HOOK" ]; then
  echo "Backup existing pre-commit hook to $HOOK.bak"
  mv "$HOOK" "$HOOK.bak"
fi
ln -sf "$REPO_ROOT/scripts/pre-commit" "$HOOK"
echo "pre-commit hook installed: $HOOK"
