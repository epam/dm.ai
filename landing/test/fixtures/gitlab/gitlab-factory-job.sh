#!/usr/bin/env bash
set -Eeuo pipefail
. .dmtools/scripts/gitlab-factory-common.sh
factory_identity dev
issue="${1:?Issue IID (empty for unlinked MR)}"
leg="${2:?Machine job leg}"
shift 2
if [ "$issue" = '-' ]; then issue=''; fi
factory_progress "$issue" "$leg" running
finish() {
  local status=$? state=success
  trap - EXIT
  if [ "$status" -eq 130 ] || [ "$status" -eq 143 ]; then state=cancelled
  elif [ "$status" -ne 0 ]; then state=failure; fi
  if ! factory_progress "$issue" "$leg" "$state"; then
    echo "Failed to record Machine jobs completion." >&2
    exit 1
  fi
  exit "$status"
}
trap finish EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
"$@"
