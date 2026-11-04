#!/usr/bin/env bash
set -Eeuo pipefail

if [ -f .dmtools/scripts/gitlab-factory-select-teammate.sh ]; then
  . .dmtools/scripts/gitlab-factory-select-teammate.sh
  select_gitlab_teammate .dmtools/runners/fa-rework.json
fi
: "${GITLAB_TOKEN:?Set the GitLab development token}"
: "${CI_API_V4_URL:?This script must run in GitLab CI}"
: "${CI_PROJECT_ID:?This script must run in a project pipeline}"

mr_iid="${1:?Usage: gitlab-factory-rework.sh <merge-request-iid>}"
if [[ ! "$mr_iid" =~ ^[0-9]+$ ]]; then
  echo "Rework requires an existing merge request IID, not an issue ID." >&2
  exit 2
fi

context=$(mktemp -d)
orchestrator="$PWD"
checkout="$context/checkout"
common="$orchestrator/.dmtools/scripts/gitlab-factory-common.sh"
cleanup_rework() {
  local status=$?
  trap - EXIT
  cd "$orchestrator"
  if [ -e "$checkout" ]; then
    if ! git worktree remove --force "$checkout"; then
      echo "Failed to remove the isolated rework worktree: $checkout" >&2
      exit 1
    fi
  fi
  if ! rm -rf -- "$context"; then
    echo "Failed to remove the temporary rework context: $context" >&2
    exit 1
  fi
  exit "$status"
}
trap cleanup_rework EXIT
export DMTOOLS_REWORK_CONTEXT="$context"
cp .dmtools/runners/fa-rework.json "$context/runner.json"

gitlab_api() {
  local method="$1" path="$2" response body status
  shift 2
  if ! response=$(curl --silent --show-error --write-out $'\n%{http_code}' \
    --request "$method" --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "$@" "$CI_API_V4_URL$path"); then
    echo "GitLab rework API request failed: $method $path" >&2
    return 1
  fi
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [[ ! "$status" =~ ^2[0-9][0-9]$ ]]; then
    body="${body//"$GITLAB_TOKEN"/[REDACTED]}"
    printf 'GitLab rework API error: %s %s HTTP %s\n%.2000s\n' "$method" "$path" "$status" "$body" >&2
    return 1
  fi
  printf '%s' "$body"
}

mr_path="/projects/$CI_PROJECT_ID/merge_requests/$mr_iid"
mr=$(gitlab_api GET "$mr_path")
printf '%s' "$mr" | jq -e --arg project "$CI_PROJECT_ID" '
  .state == "opened" and (.source_project_id | tostring) == $project
  and .source_branch != .target_branch
' >/dev/null || {
  echo "Rework requires an open MR with a source branch in this project, different from its target branch." >&2
  exit 1
}
branch=$(printf '%s' "$mr" | jq -er '.source_branch | strings')
git check-ref-format "refs/heads/$branch"
head_sha=$(printf '%s' "$mr" | jq -er '.sha | strings | select(test("^[0-9a-f]{40,64}$"))')
printf '%s' "$mr" > "$context/merge_request.json"

discussions='[]'
page=1
while :; do
  batch=$(gitlab_api GET "$mr_path/discussions?per_page=100&page=$page")
  discussions=$(jq -cn --argjson previous "$discussions" --argjson batch "$batch" '$previous + $batch')
  [ "$(printf '%s' "$batch" | jq 'length')" -lt 100 ] && break
  page=$((page + 1))
done
printf '%s' "$discussions" > "$context/discussions.json"
printf '%s' "$discussions" | jq '{
  threads: [.[] | select(any(.notes[]; .resolvable == true and .resolved == false)) | {
    threadId: .id, rootCommentId: .notes[0].id, noteIds: [.notes[] | select(.system != true) | .id],
    body: (.notes | map(select(.system != true) | .body) | join("\n\n"))
  }]
}' > "$context/pr_discussions_raw.json"

identity=$(gitlab_api GET /user)
export GIT_HTTPS_USERNAME
GIT_HTTPS_USERNAME=$(printf '%s' "$identity" | jq -er '.username | strings')
project=$(gitlab_api GET "/projects/$CI_PROJECT_ID")
remote_url=$(printf '%s' "$project" | jq -er '.http_url_to_repo | strings')
case "$remote_url" in
  https://*) ;;
  *) echo "Refusing to send the development token to a non-HTTPS Git remote." >&2; exit 1 ;;
esac
git_remote_url="https://$GIT_HTTPS_USERNAME@${remote_url#https://}"
cat > "$context/credential-helper" <<'CREDENTIAL_HELPER'
#!/bin/sh
if [ "$1" = get ]; then
  printf 'username=%s\npassword=%s\n\n' "$GIT_HTTPS_USERNAME" "$GITLAB_TOKEN"
fi
CREDENTIAL_HELPER
chmod 700 "$context/credential-helper"
git_authenticated() {
  GIT_TERMINAL_PROMPT=0 git -c credential.helper= -c credential.helper="!$context/credential-helper" "$@"
}

echo "Rework Git account: $GIT_HTTPS_USERNAME (development token)."
git_authenticated fetch --no-tags "$git_remote_url" "refs/heads/$branch"
if [ "$(git rev-parse FETCH_HEAD)" != "$head_sha" ]; then
  echo "MR source branch changed while preparing rework. Retry against its current head." >&2
  exit 1
fi
if ! git_authenticated push --dry-run --porcelain "$git_remote_url" "$head_sha:refs/heads/$branch"; then
  echo "Development-token Git push authorization failed before rework. Check GITLAB_DEV_TOKEN write_repository scope and project access." >&2
  exit 1
fi
git worktree add --detach "$checkout" "$head_sha"
cd "$checkout"
gitlab_api GET "$mr_path/changes" > "$context/changes.json"
if [ -f "$common" ]; then
  . "$common"
  pipelines=$(factory_list "/projects/$CI_PROJECT_ID/pipelines?sha=$head_sha&status=failed")
  pipeline_id=$(printf '%s' "$pipelines" | jq -r --arg sha "$head_sha" '
    [.[] | select(.sha == $sha and .status == "failed")] | sort_by(.id) | last | .id // ""')
  if [ -n "$pipeline_id" ]; then
    factory_list "/projects/$CI_PROJECT_ID/pipelines/$pipeline_id/jobs" > "$context/ci_failures.json"
  else
    printf '[]' > "$context/ci_failures.json"
  fi
fi

mkdir -p outputs
rm -f outputs/response.md outputs/review_replies.json
output_log="$context/agent.log"
dmtools run "$context/runner.json" --inputTicketId "$mr_iid" 2>&1 | tee "$output_log"
if grep -Eq 'Agent completed with exit code: [1-9][0-9]*|CLI command executed but did not produce output file:' "$output_log"; then
  echo "Rework agent failed; branch and review threads were not published." >&2
  exit 1
fi
if [ ! -s outputs/response.md ]; then
  echo "Rework must produce outputs/response.md." >&2
  exit 1
fi
if grep -q '^BLOCKED:' outputs/response.md; then
  gitlab_api POST "$mr_path/notes" --data-urlencode "body@outputs/response.md" >/dev/null
  echo "Rework is blocked; review threads and trigger labels remain unchanged." >&2
  exit 1
fi
open_threads=$(jq '.threads | length' "$context/pr_discussions_raw.json")
if [ ! -s outputs/review_replies.json ]; then
  if [ "$open_threads" -gt 0 ]; then
    echo "Rework must write outputs/review_replies.json for the $open_threads open review thread(s)." >&2
    exit 1
  fi
  printf '{"replies":[]}' > outputs/review_replies.json
fi

# Resolve every agent reply to one open discussion. threadId is authoritative; inReplyToId (number or digit
# string, any note in the thread) is the fallback. Replies to non-thread notes such as review summaries are ignored.
if ! jq -e '.replies | type == "array"' outputs/review_replies.json >/dev/null 2>&1; then
  echo "outputs/review_replies.json must be a JSON object with a \"replies\" array." >&2
  exit 1
fi
if [ -L outputs/review_replies ]; then
  echo "outputs/review_replies must not be a symlink." >&2
  exit 1
fi
replies_dir=$(cd outputs && mkdir -p review_replies && cd review_replies && pwd -P)
: > "$context/replies.tsv"
reply_problems=()
reply_index=0
while IFS= read -r entry; do
  reply_index=$((reply_index + 1))
  thread=$(jq -r --argjson entry "$entry" '
    ($entry.threadId // "" | tostring) as $tid
    | ($entry.inReplyToId // "" | tostring) as $note
    | ([.threads[] | select(.threadId == $tid)] | first // empty | .threadId)
      // ([.threads[] | select(($note | test("^[0-9]+$")) and ((.noteIds // [.rootCommentId]) | map(tostring) | index($note)))] | first // empty | .threadId)
      // ""' "$context/pr_discussions_raw.json")
  if [ -z "$thread" ]; then
    echo "Ignoring rework reply #$reply_index: it does not target an open review thread ($(printf '%s' "$entry" | jq -c '{threadId, inReplyToId}'))."
    continue
  fi
  reply_value=$(printf '%s' "$entry" | jq -r '.reply // "" | tostring')
  if [[ "$reply_value" == *.md ]] && [[ "$reply_value" != *$'\n'* ]]; then
    reply_file="$reply_value"
    [[ "$reply_file" = /* ]] || reply_file="$PWD/$reply_file"
    reply_parent=$(cd "$(dirname "$reply_file")" 2>/dev/null && pwd -P || true)
    if [ "$reply_parent" != "$replies_dir" ] || [ -L "$reply_file" ] || [ ! -f "$reply_file" ] || [ ! -s "$reply_file" ]; then
      reply_problems+=("reply #$reply_index for thread $thread: '$reply_value' is not a non-empty regular file in outputs/review_replies/")
      continue
    fi
    reply_file="$replies_dir/$(basename "$reply_file")"
  elif [ -n "${reply_value//[[:space:]]/}" ]; then
    reply_file="$replies_dir/inline_reply_$reply_index.md"
    printf '%s\n' "$reply_value" > "$reply_file"
  else
    reply_problems+=("reply #$reply_index for thread $thread has no reply text or file")
    continue
  fi
  if grep -q "^$thread"$'\t' "$context/replies.tsv"; then
    reply_problems+=("thread $thread has more than one reply")
    continue
  fi
  printf '%s\t%s\n' "$thread" "$reply_file" >> "$context/replies.tsv"
done < <(jq -c '.replies[] | if type == "object" then . else {reply: .} end' outputs/review_replies.json)
while IFS= read -r thread; do
  grep -q "^$thread"$'\t' "$context/replies.tsv" || reply_problems+=("open thread $thread has no reply")
done < <(jq -r '.threads[].threadId' "$context/pr_discussions_raw.json")
if [ "${#reply_problems[@]}" -gt 0 ]; then
  echo "Rework replies must answer every open review thread exactly once; nothing was published:" >&2
  printf '  - %s\n' "${reply_problems[@]}" >&2
  echo "Open threads: $(jq -c '[.threads[] | {threadId, rootCommentId}]' "$context/pr_discussions_raw.json")" >&2
  echo "Agent replies: $(jq -c '.replies' outputs/review_replies.json | cut -c1-2000)" >&2
  exit 1
fi

current_branch=$(git branch --show-current)
if [ -n "$current_branch" ] && [ "$current_branch" != "$branch" ]; then
  echo "Rework switched to unexpected branch '$current_branch' (expected '$branch' or detached HEAD); refusing to publish changes." >&2
  exit 1
fi
if ! git merge-base --is-ancestor "$head_sha" HEAD; then
  echo "Rework HEAD no longer descends from the original MR head; refusing to publish changes." >&2
  exit 1
fi
git config user.name "DMTools Factory"
git config user.email "dmtools-factory@users.noreply.gitlab.com"
git add -A -- . ':!input' ':!outputs' ':!logs'
if git diff --cached --name-only | grep -Eq '^(input|outputs|logs)/'; then
  echo "Agent staged runtime output files; refusing to publish them to the MR branch." >&2
  exit 1
fi
if ! git diff --cached --quiet; then
  git commit -m "AI: address review feedback for MR !$mr_iid"
fi
current_mr=$(gitlab_api GET "$mr_path")
printf '%s' "$current_mr" | jq -e --arg branch "$branch" '
  .state == "opened" and .source_branch == $branch
' >/dev/null || {
  echo "MR was closed or its source branch changed during rework." >&2
  exit 1
}
if [ "$(printf '%s' "$current_mr" | jq -r '.sha')" != "$head_sha" ]; then
  echo "MR head changed during rework; refusing to overwrite newer work." >&2
  exit 1
fi
remote_head=$(git_authenticated ls-remote --exit-code "$git_remote_url" "refs/heads/$branch" | cut -f1)
if [ "$remote_head" != "$head_sha" ]; then
  echo "MR source branch changed during rework; refusing to overwrite newer work." >&2
  exit 1
fi
published_head=$(git rev-parse HEAD)
git_authenticated push --porcelain "$git_remote_url" "HEAD:refs/heads/$branch"
for ((attempt=1; attempt<=12; attempt++)); do
  remote_head=$(git_authenticated ls-remote --exit-code "$git_remote_url" "refs/heads/$branch" | cut -f1)
  if [ "$remote_head" != "$published_head" ]; then
    echo "MR source branch no longer matches published commit $published_head (observed $remote_head); review threads remain open." >&2
    exit 1
  fi
  published_mr=$(gitlab_api GET "$mr_path")
  printf '%s' "$published_mr" | jq -e --arg branch "$branch" --arg project "$CI_PROJECT_ID" '
    .state == "opened" and .source_branch == $branch and (.source_project_id | tostring) == $project
  ' >/dev/null || {
    echo "MR was closed or its source changed after publication; review threads remain open." >&2
    exit 1
  }
  observed_head=$(printf '%s' "$published_mr" | jq -r '.sha // ""')
  [ "$observed_head" = "$published_head" ] && break
  if [ -n "$observed_head" ] && [ "$observed_head" != "$head_sha" ]; then
    echo "MR head changed to unexpected commit $observed_head after publication; review threads remain open." >&2
    exit 1
  fi
  if [ "$attempt" -eq 12 ]; then
    echo "Timed out waiting for GitLab MR metadata to reach published commit $published_head (observed ${observed_head:-empty}); the push succeeded, but review threads remain open." >&2
    exit 1
  fi
  echo "Waiting for GitLab MR metadata to reach $published_head (attempt $attempt/12)."
  sleep 5
done

while IFS=$'\t' read -r thread reply_file; do
  if [[ ! "$thread" =~ ^[0-9a-f]+$ ]]; then
    echo "Invalid GitLab discussion ID." >&2
    exit 1
  fi
  gitlab_api POST "$mr_path/discussions/$thread/notes" --data-urlencode "body@$reply_file" >/dev/null
  gitlab_api PUT "$mr_path/discussions/$thread" --data-urlencode 'resolved=true' >/dev/null
done < "$context/replies.tsv"
gitlab_api POST "$mr_path/notes" --data-urlencode "body@outputs/response.md" >/dev/null
gitlab_api PUT "$mr_path" --data-urlencode 'add_labels=ai_developed' \
  --data-urlencode 'remove_labels=agent:rework' >/dev/null
echo "Updated existing GitLab MR !$mr_iid on $branch and replied to every open review thread."
