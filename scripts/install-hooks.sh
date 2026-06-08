#!/bin/sh
# Install shared git hooks for this repository.
#
# Usage:
#   ./scripts/install-hooks.sh

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Installing git hooks from scripts/hooks/ ..."
git -C "$REPO_ROOT" config core.hooksPath "scripts/hooks"
echo "✅ Git hooks installed. Path: scripts/hooks"
echo ""
echo "Active hooks:"
ls -1 "$REPO_ROOT/scripts/hooks"
