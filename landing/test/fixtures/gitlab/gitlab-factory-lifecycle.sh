#!/usr/bin/env bash
set -Eeuo pipefail
. .dmtools/scripts/gitlab-factory-common.sh
factory_identity dev
: "${CI_PROJECT_ID:?GitLab project ID is required}"
: "${CI_API_V4_URL:?GitLab API URL is required}"
action="${1:?Usage: gitlab-factory-lifecycle.sh run|review|validate <MR IID> [issue IID] [review|rework]}"
if [ "$action" = wake ]; then
  : "${CI_DEFAULT_BRANCH:?The default branch is required to resume the factory}"
  factory_api POST "/projects/$CI_PROJECT_ID/pipeline" \
    --form-string "ref=$CI_DEFAULT_BRANCH" \
    --form-string 'variables[][key]=DMTOOLS_FACTORY_PHASE' \
    --form-string 'variables[][value]=reconcile' >/dev/null
  echo "Factory reconciliation requested after validation."
  exit 0
fi
mr_iid="${2:?An existing MR IID is required}"
[[ "$mr_iid" =~ ^[0-9]+$ ]] || { echo "Invalid MR IID." >&2; exit 2; }
mr_path="/projects/$CI_PROJECT_ID/merge_requests/$mr_iid"
mr=$(factory_api GET "$mr_path")
head=$(printf '%s' "$mr" | jq -er '.sha | strings | select(test("^[0-9a-f]{40,64}$"))')
branch=$(printf '%s' "$mr" | jq -er '.source_branch | strings')
printf '%s' "$mr" | jq -e --arg project "$CI_PROJECT_ID" '
  .state == "opened" and (.source_project_id | tostring) == $project
' >/dev/null || { echo "Factory requires an open MR in this project." >&2; exit 1; }

if [ "$action" = validate ]; then
  if [ '@@VALIDATION_ENABLED@@' != true ]; then
    echo "No generated quality gate configured; validation is explicitly disabled."
    exit 0
  fi
  issue="${3:-}"
  developer=$(factory_api GET /user)
  developer_id=$(printf '%s' "$developer" | jq -er '.id | numbers')
  notes=$(factory_list "$mr_path/notes")
  pipeline_id=$(printf '%s' "$notes" | jq -r --arg head "$head" --argjson developer "$developer_id" '
    [.[] | select(.author.id == $developer) |
      .body | select(startswith("<!-- dmtools-validation-pipeline:" + $head + ":")) |
      capture(":(?<id>[0-9]+) -->$").id] | last // ""')
  if [ -z "$pipeline_id" ]; then
    pipeline=$(factory_api POST "/projects/$CI_PROJECT_ID/pipeline" \
      --header 'Content-Type: application/json' \
      --data "$(jq -cn --arg ref "$branch" '{ref: $ref, variables: [{key: "DMTOOLS_FACTORY_PHASE", value: "validation"}]}')")
    pipeline_id=$(printf '%s' "$pipeline" | jq -er '.id | numbers')
    factory_api POST "$mr_path/notes" --data-urlencode \
      "body=<!-- dmtools-validation-pipeline:$head:$pipeline_id -->" >/dev/null
  fi
  pipeline=$(factory_api GET "/projects/$CI_PROJECT_ID/pipelines/$pipeline_id")
  pipeline_sha=$(printf '%s' "$pipeline" | jq -er '.sha')
  [ "$pipeline_sha" = "$head" ] || { echo "Validation started on a different MR head." >&2; exit 1; }
  export CI_PIPELINE_ID="$pipeline_id" CI_JOB_ID=0
  export CI_JOB_URL
  CI_JOB_URL=$(printf '%s' "$pipeline" | jq -er '.web_url')
  echo "Validation pipeline: $CI_JOB_URL"
  status=$(printf '%s' "$pipeline" | jq -er '.status')
  case "$status" in
    success) factory_progress "$issue" validation success; exit 0 ;;
    failed|canceled|skipped|manual)
      factory_progress "$issue" validation "$status"
      echo "Validation did not succeed: $status (pipeline $pipeline_id)." >&2; exit 74 ;;
    created|pending|running|waiting_for_resource|preparing|scheduled)
      factory_progress "$issue" validation running
      echo "Validation is pending; releasing the factory runner until callback/reconciliation."
      exit 75 ;;
    *)
      echo "Unknown validation pipeline status: $status." >&2
      exit 1 ;;
  esac
fi

if [ "$action" = review ]; then
  factory_identity .dmtools/runners/fa-review.json
  context=$(mktemp -d)
  checkout="$context/checkout"
  publisher="$PWD/.dmtools/scripts/gitlab-factory-publish-review.sh"
  cleanup_review() {
    local status=$?
    trap - EXIT
    cd "$orchestrator"
    if [ -e "$checkout" ]; then
      if ! git worktree remove --force "$checkout"; then
        echo "Failed to remove the isolated review worktree: $checkout" >&2
        exit 1
      fi
    fi
    rm -f "$context/merge_request.json" "$context/changes.json" "$context/discussions.json" "$context/runner.json" "$context/agent.log"
    rmdir "$context"
    exit "$status"
  }
  orchestrator="$PWD"
  trap cleanup_review EXIT
  export DMTOOLS_REVIEW_CONTEXT="$context"
  printf '%s' "$mr" > "$context/merge_request.json"
  factory_api GET "$mr_path/changes" > "$context/changes.json"
  jq -e '(.changes | type == "array") and .overflow != true' "$context/changes.json" >/dev/null || {
    echo "GitLab returned an incomplete MR diff; refusing to publish a partial review." >&2; exit 1;
  }
  factory_list "$mr_path/discussions" > "$context/discussions.json"
  cp .dmtools/runners/fa-review.json "$context/runner.json"
  git fetch --no-tags origin "refs/merge-requests/$mr_iid/head"
  [ "$(git rev-parse FETCH_HEAD)" = "$head" ] || {
    echo "MR changed while preparing the review checkout." >&2; exit 1;
  }
  git worktree add --detach "$checkout" "$head"
  cd "$checkout"
  mkdir -p outputs/pr_review_comments
  rm -f outputs/pr_review.json outputs/pr_review_general.md
  dmtools run "$context/runner.json" --inputTicketId "$mr_iid" 2>&1 | tee "$context/agent.log"
  if grep -Eq 'Agent completed with exit code: [1-9][0-9]*|CLI command executed but did not produce output file:' "$context/agent.log"; then
    echo "Review agent failed." >&2; exit 1
  fi
  jq -e '.decision == "approve" or .decision == "request_changes"' outputs/pr_review.json >/dev/null || {
    echo "Review must return an explicit approve or request_changes decision." >&2; exit 1;
  }
  current=$(factory_api GET "$mr_path")
  [ "$(printf '%s' "$current" | jq -r '.sha')" = "$head" ] || {
    echo "MR changed during review; refusing to publish a stale verdict." >&2; exit 1;
  }
  bash "$publisher" "$mr_iid"
  decision=$(jq -r '.decision' outputs/pr_review.json)
  factory_api POST "$mr_path/notes" --data-urlencode \
    "body=<!-- dmtools-review:$head:$decision -->" >/dev/null
  exit 0
fi

[ "$action" = run ] || { echo "Unknown lifecycle action: $action" >&2; exit 2; }
developer=$(factory_api GET /user)
developer_id=$(printf '%s' "$developer" | jq -er '.id | numbers')
factory_identity .dmtools/runners/fa-review.json
reviewer=$(factory_api GET /user)
reviewer_id=$(printf '%s' "$reviewer" | jq -er '.id | numbers')
factory_identity dev
issue="${3:-}"
force="${4:-}"
if [ -z "$issue" ]; then
  issues=$(factory_list "$mr_path/closes_issues")
  issue=$(printf '%s' "$issues" | jq -er --arg project "$CI_PROJECT_ID" '
    [.[] | select((.project_id | tostring) == $project and .state == "opened")]
    | if length == 1 then .[0].iid elif length == 0 then "" else error("MR closes multiple issues; specify the issue IID") end')
fi
[[ -z "$issue" || "$issue" =~ ^[0-9]+$ ]] || { echo "Invalid linked issue IID." >&2; exit 2; }
finish_lifecycle() {
  local status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    factory_identity dev
    if ! factory_labels "$mr_iid" "$issue" needs-human 'ai_validating,pr_approved'; then
      echo "Failed to record lifecycle escalation; inspect this job before retrying." >&2
    fi
  fi
  exit "$status"
}
trap finish_lifecycle EXIT
if printf '%s' "$mr" | jq -e 'any(.labels[]?; . == "blocked" or . == "needs-human" or . == "agent:skip")' >/dev/null; then
  echo "Factory paused by an MR stop label."; exit 0
fi
if printf '%s' "$mr" | jq -e '.draft == true' >/dev/null; then
  echo "Factory paused while the MR is a draft."; exit 0
fi
if [ -n "$issue" ]; then
  ticket=$(factory_api GET "/projects/$CI_PROJECT_ID/issues/$issue")
  if printf '%s' "$ticket" | jq -e '.state != "opened" or any(.labels[]?; . == "blocked" or . == "needs-human" or . == "agent:skip")' >/dev/null; then
    echo "Factory paused by issue state or stop label."; exit 0
  fi
fi
if [ -z "$force" ]; then
  if printf '%s' "$mr" | jq -e 'any(.labels[]?; . == "agent:rework")' >/dev/null ||
    { [ -n "$issue" ] && printf '%s' "$ticket" | jq -e 'any(.labels[]?; . == "agent:rework")' >/dev/null; }; then
    force=rework
  elif printf '%s' "$mr" | jq -e 'any(.labels[]?; . == "agent:review")' >/dev/null; then
    force=review
  fi
fi

job_issue="${issue:--}"
round=0
mr=$(factory_api GET "$mr_path")
if printf '%s' "$mr" | jq -e '.state != "opened" or .draft == true or any(.labels[]?; . == "blocked" or . == "needs-human" or . == "agent:skip")' >/dev/null; then
  echo "MR lifecycle paused by its current state or stop labels."; exit 0
fi
head=$(printf '%s' "$mr" | jq -er '.sha')
notes=$(factory_list "$mr_path/notes")
decision=$(printf '%s' "$notes" | jq -r --arg head "$head" --argjson reviewer "$reviewer_id" '
  [.[] | select(.author.id == $reviewer) | select(.body == ("<!-- dmtools-review:" + $head + ":approve -->") or
                    .body == ("<!-- dmtools-review:" + $head + ":request_changes -->"))]
  | sort_by(.id) | last | .body // ""')
if [ "$force" = review ]; then decision=''; fi
discussions=$(factory_list "$mr_path/discussions")
unresolved=$(printf '%s' "$discussions" | jq '[.[] | select(any(.notes[]; .resolvable == true and .resolved == false))] | length')
validated=$(printf '%s' "$notes" | jq --arg head "$head" --argjson developer "$developer_id" '
  any(.[]; .author.id == $developer and .body == ("<!-- dmtools-validation:" + $head + ":success -->"))')
if [ -z "$force" ] && [[ "$decision" == *':approve -->' ]] && [ "$unresolved" -eq 0 ] && [ "$validated" = true ]; then
  factory_labels "$mr_iid" "$issue" 'ai_pr_reviewed,pr_approved' 'agent:dev,agent:review,agent:rework,ai_validating'
  echo "Current MR head is already reviewed and validated; human merge is pending."
  exit 0
fi
if [ -z "$force" ] && [ -n "$decision" ]; then
  factory_labels "$mr_iid" "$issue" ai_pr_reviewed 'agent:review,pr_approved,ai_validating'
  echo "Review completed for this head. Add agent:rework explicitly to request fixes."
  exit 0
fi
if [ "$force" = rework ]; then
  factory_labels "$mr_iid" "$issue" '' 'agent:rework,agent:review,pr_approved,ai_validated,ai_pr_reviewed'
  factory_api POST "$mr_path/notes" --data-urlencode "body=<!-- dmtools-rework:$head:$CI_PIPELINE_ID -->" >/dev/null
  if ! bash .dmtools/scripts/gitlab-factory-job.sh "$job_issue" "rework-$round" \
    bash .dmtools/scripts/gitlab-factory-rework.sh "$mr_iid"; then
    factory_labels "$mr_iid" "$issue" needs-human 'pr_approved'
    exit 1
  fi
  updated=$(factory_api GET "$mr_path")
  if [ "$(printf '%s' "$updated" | jq -r '.sha')" = "$head" ] && [ "$unresolved" -eq 0 ]; then
    factory_labels "$mr_iid" "$issue" needs-human 'agent:rework,pr_approved'
    echo "Rework made no progress; human input is required." >&2; exit 1
  fi
  force=''
  decision=''
fi
mr=$(factory_api GET "$mr_path")
head=$(printf '%s' "$mr" | jq -er '.sha')
factory_labels "$mr_iid" "$issue" ai_validating 'agent:rework,pr_approved,validation_failed'
validation_status=0
bash .dmtools/scripts/gitlab-factory-lifecycle.sh validate "$mr_iid" "$issue" || validation_status=$?
if [ "$validation_status" -eq 75 ]; then exit 0; fi
if [ "$validation_status" -eq 74 ]; then
  factory_labels "$mr_iid" "$issue" validation_failed 'ai_validating,pr_approved,agent:review'
  echo "Validation failed. Add agent:rework explicitly to request fixes."
  exit 0
fi
[ "$validation_status" -eq 0 ] || exit "$validation_status"
factory_api POST "$mr_path/notes" --data-urlencode "body=<!-- dmtools-validation:$head:success -->" >/dev/null
factory_labels "$mr_iid" "$issue" 'ai_validated,agent:review' 'ai_validating,agent:rework'
if ! bash .dmtools/scripts/gitlab-factory-job.sh "$job_issue" "review-$round" \
  bash .dmtools/scripts/gitlab-factory-lifecycle.sh review "$mr_iid"; then
  factory_labels "$mr_iid" "$issue" needs-human 'pr_approved'
  exit 1
fi
force=''
mr=$(factory_api GET "$mr_path")
reviewed_head=$(printf '%s' "$mr" | jq -er '.sha')
[ "$reviewed_head" = "$head" ] || {
  factory_labels "$mr_iid" "$issue" needs-human 'pr_approved'
  echo "MR changed between validation and review; no approval will be armed." >&2; exit 1;
}
if printf '%s' "$mr" | jq -e '.state != "opened" or .draft == true or any(.labels[]?; . == "blocked" or . == "needs-human" or . == "agent:skip")' >/dev/null; then
  echo "MR was paused during review; approval is not armed."; exit 0
fi
if [ -n "$issue" ]; then
  ticket=$(factory_api GET "/projects/$CI_PROJECT_ID/issues/$issue")
  if printf '%s' "$ticket" | jq -e '.state != "opened" or any(.labels[]?; . == "blocked" or . == "needs-human" or . == "agent:skip")' >/dev/null; then
    echo "Issue was paused during review; approval is not armed."; exit 0
  fi
fi
notes=$(factory_list "$mr_path/notes")
approved=$(printf '%s' "$notes" | jq --arg head "$reviewed_head" --argjson reviewer "$reviewer_id" '
  [.[] | select(.author.id == $reviewer) | select(.body == ("<!-- dmtools-review:" + $head + ":approve -->") or
    .body == ("<!-- dmtools-review:" + $head + ":request_changes -->"))] | sort_by(.id) | last |
    .body == ("<!-- dmtools-review:" + $head + ":approve -->")')
discussions=$(factory_list "$mr_path/discussions")
unresolved=$(printf '%s' "$discussions" | jq '[.[] | select(any(.notes[]; .resolvable == true and .resolved == false))] | length')
if [ "$approved" = true ] && [ "$unresolved" -eq 0 ]; then
  factory_labels "$mr_iid" "$issue" 'ai_pr_reviewed,pr_approved' 'agent:dev,agent:review,agent:rework,ai_validating'
  echo "MR !$mr_iid is approved and validated. It remains OPEN for a human to merge."
  exit 0
fi
factory_labels "$mr_iid" "$issue" ai_pr_reviewed 'agent:review,pr_approved'
echo "Review requires changes. Add agent:rework explicitly to request fixes."
