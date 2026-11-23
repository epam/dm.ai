#!/usr/bin/env bash
set -Eeuo pipefail

: "${GITLAB_TOKEN:?Set the GITLAB_TOKEN project CI/CD variable}"
: "${CI_API_V4_URL:?This script must run in GitLab CI}"
: "${CI_PROJECT_ID:?This script must run in a project pipeline}"
: "${CI_PIPELINE_ID:?GitLab pipeline ID is required to identify the review}"

mr_iid="${1:?Usage: gitlab-factory-publish-review.sh <merge-request-iid>}"
if [[ ! "$mr_iid" =~ ^[0-9]+$ ]]; then
  echo "Invalid merge request IID: expected a number." >&2
  exit 2
fi

review_json='outputs/pr_review.json'
general_comment='outputs/pr_review_general.md'
if [ ! -s "$review_json" ] || [ ! -s "$general_comment" ]; then
  echo "Review outputs are missing; expected outputs/pr_review.json and outputs/pr_review_general.md." >&2
  exit 1
fi
if ! jq -e '(.generalComment == "outputs/pr_review_general.md") and ((.inlineComments // []) | type == "array")' "$review_json" >/dev/null; then
  echo "Review JSON has an unexpected format; refusing to publish incomplete findings." >&2
  exit 1
fi

log_error_response() {
  local body="$1"
  body="${body//"$GITLAB_TOKEN"/[REDACTED]}"
  if [ "${#body}" -gt 2000 ]; then
    body="${body:0:2000}...[truncated]"
  fi
  printf '[gitlab-review] error response (max 2000 chars):\n%s\n' "$body" >&2
}

gitlab_api() {
  local method="$1"
  local url="$2"
  local response body http_status curl_status failure_status
  shift 2

  if response=$(curl --silent --show-error --write-out $'\n%{http_code}' \
      --request "$method" --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "$@" "$url"); then
    curl_status=0
  else
    curl_status=$?
  fi
  if [[ "$response" == *$'\n'* ]]; then
    http_status="${response##*$'\n'}"
    body="${response%$'\n'*}"
  else
    http_status='000'
    body="$response"
  fi
  if [ "$curl_status" -ne 0 ] || [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
    printf '[gitlab-review] request failed method=%s url=%s curl_exit=%s http_status=%s\n' \
      "$method" "$url" "$curl_status" "$http_status" >&2
    log_error_response "$body"
    failure_status=22
    if [ "$curl_status" -ne 0 ]; then
      failure_status="$curl_status"
    fi
    return "$failure_status"
  fi
  printf '%s' "$body"
}

mr=$(gitlab_api GET "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$mr_iid")
diff_refs=$(printf '%s' "$mr" | jq -ce '.diff_refs | select(.base_sha and .head_sha and .start_sha)')
base_sha=$(printf '%s' "$diff_refs" | jq -er '.base_sha')
head_sha=$(printf '%s' "$diff_refs" | jq -er '.head_sha')
start_sha=$(printf '%s' "$diff_refs" | jq -er '.start_sha')

review_context=$(mktemp -d)
trap 'rm -rf "$review_context"' EXIT
mr_diffs="$review_context/diffs.json"
printf '[]' > "$mr_diffs"
page=1
while :; do
  batch=$(gitlab_api GET "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$mr_iid/diffs?per_page=100&page=$page")
  jq -s '.[0] + .[1]' "$mr_diffs" <(printf '%s' "$batch") > "$review_context/merged.json"
  mv "$review_context/merged.json" "$mr_diffs"
  [ "$(printf '%s' "$batch" | jq 'length')" -lt 100 ] && break
  page=$((page + 1))
done

# GitLab needs both old_line and new_line for unchanged context lines, so anchors are resolved from the MR diff.
# Prints "<old_path>\t<new_path>\t<old_line|->\t<new_line|->", or nothing when the line is not in the diff.
resolve_diff_line() {
  local path="$1" side="$2" line="$3" entry
  entry=$(jq -c --arg path "$path" --arg side "$side" '
    [.[] | select(if $side == "LEFT" then .old_path == $path else .new_path == $path end)][0] // empty' "$mr_diffs")
  [ -n "$entry" ] || return 0
  printf '%s' "$entry" | jq -r '.diff // ""' | awk -v side="$side" -v target="$line" \
    -v old_path="$(printf '%s' "$entry" | jq -r '.old_path')" \
    -v new_path="$(printf '%s' "$entry" | jq -r '.new_path')" '
    function emit(o, n) { printf "%s\t%s\t%s\t%s\n", old_path, new_path, o, n; found = 1; exit }
    /^@@ / {
      split($2, o, ","); split($3, n, ",")
      old = substr(o[1], 2) + 0; new = substr(n[1], 2) + 0; in_hunk = 1; next
    }
    !in_hunk { next }
    /^\\/ { next }
    /^\+/ { if (side == "RIGHT" && new == target) emit("-", new); new++; next }
    /^-/ { if (side == "LEFT" && old == target) emit(old, "-"); old++; next }
    {
      if ((side == "RIGHT" && new == target) || (side == "LEFT" && old == target)) emit(old, new)
      old++; new++
    }'
}

general_body=$(cat "$general_comment")
gitlab_api POST "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$mr_iid/notes" \
  --header 'Content-Type: application/json' \
  --data "$(jq -cn --arg body "$general_body" '{body: $body}')" >/dev/null
printf 'Posted general FA review to GitLab merge request !%s.\n' "$mr_iid"

while IFS= read -r inline_comment; do
  file_path=$(printf '%s' "$inline_comment" | jq -er '.path | strings')
  line=$(printf '%s' "$inline_comment" | jq -er '.line | numbers | select(. > 0 and floor == .)')
  side=$(printf '%s' "$inline_comment" | jq -r '.side // "RIGHT"')
  comment_file=$(printf '%s' "$inline_comment" | jq -er '.comment | strings')
  case "$comment_file" in
    outputs/pr_review_comments/*.md) ;;
    *)
      echo "Invalid inline review comment path; expected a markdown file under outputs/pr_review_comments/." >&2
      exit 1
      ;;
  esac
  comment_name="${comment_file##*/}"
  if [ "$comment_file" != "outputs/pr_review_comments/$comment_name" ]; then
    echo "Invalid inline review comment path; nested or parent paths are not allowed." >&2
    exit 1
  fi
  if [ ! -s "$comment_file" ]; then
    echo "Inline review comment file is missing or empty: $comment_file" >&2
    exit 1
  fi
  if [ "$side" != 'LEFT' ] && [ "$side" != 'RIGHT' ]; then
    echo "Invalid inline review side '$side'; expected LEFT or RIGHT." >&2
    exit 1
  fi
  comment_body=$(cat "$comment_file")
  anchor=$(resolve_diff_line "$file_path" "$side" "$line")
  if [ -n "$anchor" ]; then
    IFS=$'\t' read -r old_path new_path old_line new_line <<< "$anchor"
    payload=$(jq -cn \
      --arg body "$comment_body" \
      --arg base_sha "$base_sha" \
      --arg head_sha "$head_sha" \
      --arg start_sha "$start_sha" \
      --arg old_path "$old_path" \
      --arg new_path "$new_path" \
      --arg old_line "$old_line" \
      --arg new_line "$new_line" \
      '{body: $body, position: ({position_type: "text", base_sha: $base_sha, head_sha: $head_sha, start_sha: $start_sha, old_path: $old_path, new_path: $new_path}
        + (if $old_line == "-" then {} else {old_line: ($old_line | tonumber)} end)
        + (if $new_line == "-" then {} else {new_line: ($new_line | tonumber)} end))}')
    location='inline'
  else
    # Lines outside the MR diff cannot be anchored; keep the finding as a resolvable MR thread.
    payload=$(jq -cn --arg body "$comment_body" --arg path "$file_path" --arg line "$line" --arg side "$side" \
      '{body: ("**`\($path):\($line)`**" + (if $side == "LEFT" then " (old version)" else "" end) + "\n\n" + $body)}')
    location='general thread (line is outside the MR diff)'
  fi
  gitlab_api POST "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$mr_iid/discussions" \
    --header 'Content-Type: application/json' \
    --data "$payload" >/dev/null
  printf 'Posted FA review comment on %s:%s as %s to GitLab merge request !%s.\n' "$file_path" "$line" "$location" "$mr_iid"
done < <(jq -c '.inlineComments[]?' "$review_json")
