#!/usr/bin/env bash

factory_identity() {
  if [ -f .dmtools/scripts/gitlab-factory-select-teammate.sh ]; then
    . .dmtools/scripts/gitlab-factory-select-teammate.sh
    select_gitlab_teammate "$1"
  fi
  : "${GITLAB_TOKEN:?Set the GitLab factory token}"
}

factory_api() {
  local method="$1" path="$2" response status body secret
  shift 2
  if ! response=$(curl --silent --show-error --write-out $'\n%{http_code}' \
    --request "$method" --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "$@" "$CI_API_V4_URL$path"); then
    echo "GitLab factory request failed: $method $path" >&2
    return 1
  fi
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [[ ! "$status" =~ ^2[0-9][0-9]$ ]]; then
    for secret in "$GITLAB_TOKEN" "${GITLAB_DEV_TOKEN:-}" "${GITLAB_REVIEWER_TOKEN:-}"; do
      if [ -n "$secret" ]; then body="${body//"$secret"/[REDACTED]}"; fi
    done
    printf 'GitLab factory error: %s %s HTTP %s\n%.2000s\n' "$method" "$path" "$status" "$body" >&2
    return 1
  fi
  printf '%s' "$body"
}

factory_list() {
  local path="$1" page=1 batch result='[]' separator='?'
  [[ "$path" != *'?'* ]] || separator='&'
  while :; do
    batch=$(factory_api GET "$path${separator}per_page=100&page=$page") || return 1
    printf '%s' "$batch" | jq -e 'type == "array"' >/dev/null || {
      echo "GitLab factory list response is not an array: $path" >&2
      return 1
    }
    result=$(jq -cn --argjson previous "$result" --argjson batch "$batch" '$previous + $batch') || return 1
    [ "$(printf '%s' "$batch" | jq 'length')" -lt 100 ] && break
    page=$((page + 1))
  done
  printf '%s' "$result"
}

factory_labels() {
  local mr="$1" issue="$2" add="$3" remove="$4"
  factory_api PUT "/projects/$CI_PROJECT_ID/merge_requests/$mr" \
    --data-urlencode "add_labels=$add" --data-urlencode "remove_labels=$remove" >/dev/null
  if [ -n "$issue" ]; then
    factory_api PUT "/projects/$CI_PROJECT_ID/issues/$issue" \
      --data-urlencode "add_labels=$add" --data-urlencode "remove_labels=$remove" >/dev/null
  fi
}

factory_progress() {
  local issue="$1" leg="$2" state="$3" current description updated marker line timestamp existing
  [ -n "$issue" ] || return 0
  current=$(factory_api GET "/projects/$CI_PROJECT_ID/issues/$issue")
  description=$(printf '%s' "$current" | jq -r '.description // ""')
  marker="<!-- dmtools-job:$CI_PIPELINE_ID:${CI_JOB_ID:-0}:$leg -->"
  timestamp=$(date -u '+%H:%M UTC')
  existing=$(jq -nr --arg body "$description" --arg marker "$marker" '
    $body | split("\n") | map(select(contains($marker))) | first // ""')
  if [ "$state" = running ]; then
    [ -z "$existing" ] || return 0
    line="- [ ] [$leg](${CI_JOB_URL:-$CI_PIPELINE_URL}) - started $timestamp $marker"
  else
    if [ -n "$existing" ]; then
      line="${existing/- \[ \]/- [x]}"
      line="${line/ $marker/ - done $timestamp $state $marker}"
    else
      line="- [x] [$leg](${CI_JOB_URL:-$CI_PIPELINE_URL}) - $state at $timestamp $marker"
    fi
  fi
  updated=$(jq -nr --arg body "$description" --arg marker "$marker" --arg line "$line" '
    if $body | contains($marker) then
      $body | split("\n") | map(if contains($marker) then $line else . end) | join("\n")
    elif $body | contains("<!-- dmtools-machine-jobs -->") then
      $body | sub("<!-- /dmtools-machine-jobs -->"; $line + "\n<!-- /dmtools-machine-jobs -->")
    else $body + "\n\n<!-- dmtools-machine-jobs -->\n## Machine jobs\n\n" + $line + "\n<!-- /dmtools-machine-jobs -->"
    end')
  factory_api PUT "/projects/$CI_PROJECT_ID/issues/$issue" \
    --data-urlencode "description=$updated" >/dev/null
}
