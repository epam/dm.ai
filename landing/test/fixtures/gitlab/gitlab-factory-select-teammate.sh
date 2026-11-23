#!/usr/bin/env bash
select_gitlab_teammate() {
  case "$1" in
    review-merge-request|merge-approved-merge-request|*/fa-review.json|merge)
      export GITLAB_TOKEN="${GITLAB_REVIEWER_TOKEN:?Set the GITLAB_REVIEWER_TOKEN project CI/CD variable}"
      ;;
    *)
      export GITLAB_TOKEN="${GITLAB_DEV_TOKEN:?Set the GITLAB_DEV_TOKEN project CI/CD variable}"
      ;;
  esac
}
