#!/usr/bin/env bash
set -Eeuo pipefail

: "${GITLAB_TOKEN:?Set the GITLAB_TOKEN project CI/CD variable}"
: "${CI_API_V4_URL:?This script must run in GitLab CI}"
: "${CI_PROJECT_ID:?This script must run in a project pipeline}"
: "${CI_PIPELINE_ID:?GitLab pipeline ID is required to name the agent branch}"

current_issue='n/a'
trap 'status=$?; printf "[poller-error] line=%s exit=%s issue=%s pipeline=%s\n" "$LINENO" "$status" "$current_issue" "$CI_PIPELINE_ID" >&2' ERR

webhook_issue_iid=''
if [[ "${DMTOOLS_FACTORY_TRIGGER:-}" == 'issue_webhook' ]]; then
  : "${TRIGGER_PAYLOAD:?GitLab issue webhook payload is required}"
  webhook_issue_iid=$(jq -er 'select(.object_kind == "issue") | .object_attributes.iid | numbers' "$TRIGGER_PAYLOAD")
  if jq -e '
    (.changes.labels? != null)
    and (
      ((.changes.labels.current // []) | map(.title // .name // tostring)) as $current
      | ((.changes.labels.previous // []) | map(.title // .name // tostring)) as $previous
      | [$current[] | select(. as $label | ($previous | index($label)) == null)] | length == 0
    )
  ' "$TRIGGER_PAYLOAD" >/dev/null; then
    echo "Ignoring GitLab issue webhook: no labels were added to issue #$webhook_issue_iid"
    exit 0
  fi
fi

log_safe() {
  local message="$1"
  message="${message//"$GITLAB_TOKEN"/[REDACTED]}"
  printf '%s\n' "$message" >&2
}

log_error_response() {
  local body="$1"
  body="${body//"$GITLAB_TOKEN"/[REDACTED]}"
  if [ "${#body}" -gt 2000 ]; then
    body="${body:0:2000}...[truncated]"
  fi
  printf '[gitlab-api] error response (max 2000 chars):\n%s\n' "$body" >&2
}

gitlab_api() {
  local method="$1"
  local url="$2"
  local response body http_status curl_status
  shift 2

  if response=$(curl --silent --show-error --write-out $'\n%{http_code}' \
      --request "$method" "$@" "$url"); then
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
  if [ "$curl_status" -ne 0 ]; then
    log_safe "[gitlab-api] request failed method=$method url=$url curl_exit=$curl_status http_status=$http_status"
    log_error_response "$body"
    return "$curl_status"
  fi

  if [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
    log_safe "[gitlab-api] request failed method=$method url=$url http_status=$http_status"
    log_error_response "$body"
    return 22
  fi
  printf '%s' "$body"
}

machine='@@MACHINE@@'
base_branch='@@BASE_BRANCH@@'

run_agent() {
  local output_file status
  local -a pipeline_status
  output_file=$(mktemp)
  if dmtools run "$@" 2>&1 | tee "$output_file"; then
    status=0
  else
    pipeline_status=("${PIPESTATUS[@]}")
    status="${pipeline_status[0]}"
    if [ "$status" -eq 0 ]; then
      status="${pipeline_status[1]}"
    fi
  fi
  if [ "$status" -ne 0 ]; then
    rm -f "$output_file"
    return "$status"
  fi
  if grep -Eq 'Agent completed with exit code: [1-9][0-9]*|CLI command executed but did not produce output file:' "$output_file"; then
    rm -f "$output_file"
    echo "Agent execution failed; leaving issue/MR labels unchanged." >&2
    return 1
  fi
  rm -f "$output_file"
  return 0
}

clear_review_outputs() {
  mkdir -p outputs/pr_review_comments
  rm -f outputs/pr_review.json outputs/pr_review_general.md outputs/pr_review_comments/*.md
}

publish_issue_changes() {
  local iid="$1"
  local base_sha="$2"
  local branch="ai/issue-$iid-$CI_PIPELINE_ID"
  local changes commit_count remote_url git_remote_url credential_helper mr_url mr_response push_output push_status
  changes=$(git status --porcelain --untracked-files=all)
  commit_count=$(git rev-list --count "$base_sha"..HEAD)
  if [ -z "$changes" ] && [ "$commit_count" -eq 0 ]; then
    echo "Agent completed without repository changes; no branch or MR will be created." >&2
    return 1
  fi

  git switch -c "$branch"
  git config user.name "DMTools Factory"
  git config user.email "dmtools-factory@users.noreply.gitlab.com"
  git add -A
  if ! git diff --cached --quiet; then
    git commit -m "AI: implement issue #$iid"
  fi

  remote_url=$(git remote get-url origin | sed -E 's#(https?://)[^/@]*@#\1#')
  local identity identity_username git_username
  identity=$(gitlab_api GET "$CI_API_V4_URL/user" --header "PRIVATE-TOKEN: $GITLAB_TOKEN")
  identity_username=$(printf '%s' "$identity" | jq -er '.username')
  git_username="$identity_username"
  case "$remote_url" in
    https://*) git_remote_url="https://$git_username@${remote_url#https://}" ;;
    *)
      echo "GitLab push requires an HTTPS origin remote; refusing to send the token to a non-HTTPS URL." >&2
      return 1
      ;;
  esac
  credential_helper=$(mktemp)
  cat >"$credential_helper" <<'CREDENTIAL_HELPER'
#!/bin/sh
if [ "$1" = get ]; then
  printf 'username=%s\npassword=%s\n\n' "$GIT_HTTPS_USERNAME" "$GITLAB_TOKEN"
fi
CREDENTIAL_HELPER
  chmod 700 "$credential_helper"
  if push_output=$(GIT_HTTPS_USERNAME="$git_username" GIT_TERMINAL_PROMPT=0 \
      git -c credential.helper= -c credential.helper="!$credential_helper" push --porcelain \
      "$git_remote_url" "HEAD:refs/heads/$branch" 2>&1); then
    echo "Pushed branch $branch to GitLab."
  else
    push_status=$?
    log_safe "[git-push] failed exit=$push_status branch=$branch remote=$remote_url"
    log_safe "$push_output"
    echo "GitLab rejected the push. Check token permissions, project membership, Git-over-HTTPS authentication, and branch protection." >&2
    rm -f "$credential_helper"
    return "$push_status"
  fi
  rm -f "$credential_helper"

  mr_response=$(gitlab_api POST "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests" \
    --header "PRIVATE-TOKEN: $GITLAB_TOKEN" \
    --form-string "source_branch=$branch" \
    --form-string "target_branch=$base_branch" \
    --form-string "title=AI: implement issue #$iid" \
    --form-string "description=Automated changes for issue #$iid. Closes #$iid.")
  mr_url=$(printf '%s' "$mr_response" | jq -er '.web_url')
  echo "Created GitLab merge request: $mr_url"
  git switch --detach "$base_sha"
}

page=1
while :; do
  issues=$(gitlab_api GET "$CI_API_V4_URL/projects/$CI_PROJECT_ID/issues?state=opened&per_page=100&page=$page" \
    --header "PRIVATE-TOKEN: $GITLAB_TOKEN")
  count=$(printf '%s' "$issues" | jq 'length')
  printf '%s' "$issues" | jq -r --arg machine "$machine" --argjson webhook_issue_iid "${webhook_issue_iid:-null}" '
    .[]
    | select($webhook_issue_iid == null or .iid == $webhook_issue_iid)
    | select((.labels // []) | all(. != "agent:skip" and . != "blocked"))
    | select(
        ((.labels // []) | any(. == "agent:review"))
        or (
          ((.labels // []) | any(. == "agent:rework"))
          and ((.labels // []) | all(. != "status:In Rework"))
        )
        or (
          ((.labels // []) | all(. as $label | ["status:In Development", "status:In Review", "status:In Rework", "ai_developed", "ai_pr_reviewed"] | index($label) == null))
          and ((.labels // []) | all(test("in progress|_wip|rework-round-") | not))
          and (
            ((.assignees // []) | any(.username == $machine))
            or ((.labels // []) | any(. == "agent:dev"))
          )
        )
      )
    | [.iid, ((.labels // []) | join(","))] | @tsv
  ' | while IFS="$(printf '\t')" read -r iid labels; do
    case ",$labels," in
      *,agent:skip,*) continue ;;
    esac
    case ",$labels," in
      *,agent:review,*) config='.dmtools/runners/fa-review.json'; trigger='agent:review' ;;
      *,agent:rework,*)
        case ",$labels," in
          *,status:In\ Rework,*) continue ;;
        esac
        config='.dmtools/runners/fa-rework.json'
        trigger='agent:rework'
        ;;
      *)
        case ",$labels," in
          *,status:In\ Development,*|*,status:In\ Review,*|*,status:In\ Rework,*|*,in\ progress,*|*,ai_developed,*|*,ai_pr_reviewed,*|*,*_wip,*|*,*rework-round-*)
            continue
            ;;
        esac
        case ",$labels," in
          *,agent:dev,*) trigger='agent:dev' ;;
          *) trigger='' ;;
        esac
        case ",$labels," in
          *,bug,*) config='.dmtools/runners/fa-bug-dev.json' ;;
          *) config='.dmtools/runners/fa-story-dev.json' ;;
        esac
        ;;
    esac

    echo "Running $config for GitLab issue #$iid"
    current_issue="$iid"
    if [ "$trigger" != 'agent:review' ]; then
      base_sha=$(git rev-parse HEAD)
    fi
    if [ "$trigger" = 'agent:review' ]; then
      clear_review_outputs
    fi
    run_agent "$config" --inputTicketId "$iid"
    if [ "$trigger" != 'agent:review' ]; then
      publish_issue_changes "$iid" "$base_sha"
    else
      related_merge_requests=$(gitlab_api GET "$CI_API_V4_URL/projects/$CI_PROJECT_ID/issues/$iid/related_merge_requests" \
        --header "PRIVATE-TOKEN: $GITLAB_TOKEN")
      mr_iid=$(printf '%s' "$related_merge_requests" | jq -er '
        [.[] | select(.state == "opened")]
        | if length == 1 then .[0].iid
          elif length == 0 then error("no open merge request is linked to the issue")
          else error("multiple open merge requests are linked to the issue; cannot choose review target safely")
          end
      ')
      bash .dmtools/scripts/gitlab-factory-publish-review.sh "$mr_iid"
    fi
    result_label='ai_developed'
    if [ "$trigger" = 'agent:review' ]; then
      result_label='ai_pr_reviewed'
    fi
    args=(--header "PRIVATE-TOKEN: $GITLAB_TOKEN" --form-string "add_labels=$result_label")
    if [ -n "$trigger" ]; then
      args+=(--form-string "remove_labels=$trigger")
    fi
    gitlab_api PUT "$CI_API_V4_URL/projects/$CI_PROJECT_ID/issues/$iid" "${args[@]}" >/dev/null
    current_issue='n/a'
  done
  [ "$count" -lt 100 ] && break
  page=$((page + 1))
done

if [[ -n "$webhook_issue_iid" ]]; then
  exit 0
fi

page=1
while :; do
  merge_requests=$(gitlab_api GET "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests?state=opened&per_page=100&page=$page" \
    --header "PRIVATE-TOKEN: $GITLAB_TOKEN")
  count=$(printf '%s' "$merge_requests" | jq 'length')
  printf '%s' "$merge_requests" | jq -r --arg machine "$machine" '
    .[]
    | select((.labels // []) | all(. != "blocked"))
    | select(
        ((.labels // []) | any(. == "agent:review" or . == "agent:rework"))
        or (
          .author.username == $machine
          and ((.labels // []) | any(. == "pr_approved"))
        )
      )
    | [.iid, ((.labels // []) | join(",")), (.head_pipeline.status // "")] | @tsv
  ' | while IFS="$(printf '\t')" read -r iid labels pipeline_status; do
    case ",$labels," in
      *,agent:review,*)
        config='.dmtools/runners/fa-review.json'
        result_label='ai_pr_reviewed'
        trigger='agent:review'
        ;;
      *,agent:rework,*)
        config='.dmtools/runners/fa-rework.json'
        result_label='ai_developed'
        trigger='agent:rework'
        ;;
      *,pr_approved,*)
        if [ "$pipeline_status" != 'success' ]; then continue; fi
        current_issue="!$iid"
        gitlab_api PUT "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$iid/merge" \
          --header "PRIVATE-TOKEN: $GITLAB_TOKEN" \
          --form-string "merge_when_pipeline_succeeds=true" >/dev/null
        current_issue='n/a'
        continue
        ;;
      *) continue ;;
    esac

    echo "Running $config for GitLab merge request !$iid"
    current_issue="!$iid"
    run_agent "$config" --inputTicketId "$iid"
    gitlab_api PUT "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$iid" \
      --header "PRIVATE-TOKEN: $GITLAB_TOKEN" \
      --form-string "add_labels=$result_label" \
      --form-string "remove_labels=$trigger" >/dev/null
    current_issue='n/a'
  done
  [ "$count" -lt 100 ] && break
  page=$((page + 1))
done
