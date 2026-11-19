set -euo pipefail
for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "Required tool missing: $tool" >&2; exit 1; }
done
token="${DMTOOLS_MERGE_TOKEN:-${GITLAB_TOKEN:-}}"
case "$token" in
  ''|'$'*) echo "Merge token is missing; add ${DMTOOLS_MERGE_TOKEN_NAME:-GITLAB_TOKEN} as a masked CI/CD variable" >&2; exit 1 ;;
esac
case "${DMTOOLS_MERGE_DRY_RUN:-false}" in true|false) ;; *) echo "DMTOOLS_MERGE_DRY_RUN must be true or false" >&2; exit 1 ;; esac
target="${DMTOOLS_MERGE_TARGET_BRANCH:-$CI_DEFAULT_BRANCH}"
api() {
  local path="$1"
  shift
  curl --fail --silent --show-error --header "PRIVATE-TOKEN: $token" "$@" "$CI_API_V4_URL/projects/$CI_PROJECT_ID$path"
}
# GitLab timestamps may carry fractional seconds and a numeric UTC offset.
epoch='def epoch: sub("\\.[0-9]+"; "") |
  if test("Z$") then fromdateiso8601
  else capture("^(?<d>.*)(?<s>[+-])(?<h>[0-9]{2}):?(?<m>[0-9]{2})$") |
    ((.d + "Z") | fromdateiso8601) - ((if .s == "+" then 1 else -1 end) * ((.h | tonumber) * 3600 + (.m | tonumber) * 60))
  end;'
squash_option="$(api '' | jq -er '.squash_option // "default_off"')"
squash=true
if [ "$squash_option" = never ]; then squash=false; fi
user="$(curl --fail --silent --header "PRIVATE-TOKEN: $token" "$CI_API_V4_URL/user" 2>/dev/null | jq -r '.username // empty' 2>/dev/null || true)"
echo "Merge bot: approved, validated, green merge requests into $target (GitLab user: ${user:-unknown}, squash: $squash)"
candidates=""
page=1
while :; do
  response="$(api /merge_requests --get \
    --data-urlencode state=opened \
    --data-urlencode "target_branch=$target" \
    --data-urlencode labels=pr_approved,ai_validated \
    --data-urlencode per_page=100 \
    --data-urlencode "page=$page")"
  count="$(printf '%s' "$response" | jq -er 'if type == "array" then length else error("Expected a merge request array") end')"
  [ "$count" -gt 0 ] || break
  candidates="$candidates$(printf '%s' "$response" | jq -r '.[].iid')"$'\n'
  [ "$count" -eq 100 ] || break
  page=$((page + 1))
done
merged=0
failed=0
while read -r iid; do
  [ -n "$iid" ] || continue
  if ! mr="$(api "/merge_requests/$iid")"; then
    echo "!$iid: unable to read the merge request" >&2
    failed=$((failed + 1))
    continue
  fi
  reason="$(printf '%s' "$mr" | jq -r --arg target "$target" '
    (.labels // []) as $labels |
    ([$labels[] | select(. == "blocked" or . == "agent:skip" or . == "provider_quota" or . == "needs-human"
      or . == "agent:review" or . == "agent:rework" or . == "status:In Review" or . == "status:In Rework"
      or . == "validation_failed" or . == "ai_validating")] | first) as $hold |
    if .state != "opened" then "not open"
    elif .draft == true or .work_in_progress == true then "draft"
    elif .target_branch != $target then "targets \(.target_branch)"
    elif ($labels | index("pr_approved") | not) then "not approved"
    elif ($labels | index("ai_validated") | not) then "not validated"
    elif $hold != null then "held by \($hold)"
    elif (.head_pipeline.sha // "") != .sha then "head pipeline is not for \(.sha)"
    elif (.head_pipeline.status // "") != "success" then "head pipeline is \(.head_pipeline.status // "missing")"
    elif (.detailed_merge_status // "") != "mergeable" then "merge status is \(.detailed_merge_status // "unknown")"
    else "" end')"
  if [ -n "$reason" ]; then
    echo "!$iid: skipped ($reason)"
    continue
  fi
  sha="$(printf '%s' "$mr" | jq -r '.sha')"
  if ! versions="$(api "/merge_requests/$iid/versions" --get --data-urlencode per_page=100)"; then
    echo "!$iid: unable to read diff versions" >&2
    failed=$((failed + 1))
    continue
  fi
  head_at="$(printf '%s' "$versions" | jq -r --arg sha "$sha" "$epoch"'
    (if type == "array" then . else [] end | max_by(.id)) as $v |
    if $v != null and $v.head_commit_sha == $sha then ($v.created_at | epoch) else empty end')"
  if [ -z "$head_at" ]; then
    echo "!$iid: skipped (latest diff version is not $sha)"
    continue
  fi
  approved_at=""
  page=1
  events_ok=true
  while :; do
    if ! events="$(api "/merge_requests/$iid/resource_label_events" --get --data-urlencode per_page=100 --data-urlencode "page=$page")"; then
      events_ok=false
      break
    fi
    latest="$(printf '%s' "$events" | jq -r "$epoch"'
      [.[]? | select(.action == "add" and .label.name == "pr_approved") | .created_at | epoch] | max // empty')"
    if [ -n "$latest" ] && { [ -z "$approved_at" ] || [ "$latest" -gt "$approved_at" ]; }; then approved_at="$latest"; fi
    [ "$(printf '%s' "$events" | jq 'length')" -eq 100 ] || break
    page=$((page + 1))
  done
  if [ "$events_ok" != true ]; then
    echo "!$iid: unable to read label events" >&2
    failed=$((failed + 1))
    continue
  fi
  if [ -z "$approved_at" ] || [ "$approved_at" -lt "$head_at" ]; then
    echo "!$iid: skipped (pr_approved predates head $sha)"
    continue
  fi
  if [ "${DMTOOLS_MERGE_DRY_RUN:-false}" = true ]; then
    echo "!$iid: would merge $sha (dry run)"
    continue
  fi
  if result="$(api "/merge_requests/$iid/merge" --request PUT \
      --data-urlencode "sha=$sha" \
      --data-urlencode "squash=$squash")"; then
    echo "!$iid: merged $sha ($(printf '%s' "$result" | jq -r '.state // "unknown"'))"
    merged=$((merged + 1))
  else
    echo "!$iid: merge failed" >&2
    failed=$((failed + 1))
  fi
done <<< "$candidates"
echo "Merge bot finished: $merged merged, $failed failed"
[ "$failed" -eq 0 ]
