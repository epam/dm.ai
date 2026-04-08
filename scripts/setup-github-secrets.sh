#!/usr/bin/env bash
# setup-github-secrets.sh
# Reads dmtools.env and pushes mapped values to GitHub repo secrets and variables.
# Usage: ./scripts/setup-github-secrets.sh [--repo OWNER/REPO] [--env path/to/dmtools.env]

set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────
REPO=""
ENV_FILE="${DMTOOLS_ENV_FILE:-dmtools.env}"

# ── Parse args ────────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)  REPO="$2";     shift 2 ;;
    --env)   ENV_FILE="$2"; shift 2 ;;
    *)       echo "Unknown argument: $1"; exit 1 ;;
  esac
done

# ── Validate prerequisites ────────────────────────────────────────────────────
if ! command -v gh &>/dev/null; then
  echo "❌  GitHub CLI (gh) is not installed. Install from https://cli.github.com/"
  exit 1
fi

if [[ -z "$REPO" ]]; then
  REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)
  if [[ -z "$REPO" ]]; then
    echo "❌  Could not detect repo. Run inside a git repo or pass --repo OWNER/REPO"
    exit 1
  fi
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "❌  Env file not found: $ENV_FILE"
  exit 1
fi

# ── Use PAT from env file if GH_TOKEN not already set ────────────────────────
if [[ -z "${GH_TOKEN:-}" ]]; then
  _pat=$(grep -E "^SOURCE_GITHUB_TOKEN=" "$ENV_FILE" | tail -1 | cut -d'=' -f2- || true)
  if [[ -n "$_pat" ]]; then
    export GH_TOKEN="$_pat"
  fi
fi

# ── Helper: read a value from the env file ───────────────────────────────────
env_val() {
  local key="$1"
  grep -E "^${key}=" "$ENV_FILE" | tail -1 | cut -d'=' -f2- || true
}

# ── Secrets: "GH_SECRET_NAME:ENV_KEY" ────────────────────────────────────────
SECRETS=(
  "JIRA_EMAIL:JIRA_EMAIL"
  "JIRA_API_TOKEN:JIRA_API_TOKEN"
  "ADO_TOKEN:ADO_PAT_TOKEN"
  "FIGMA_TOKEN:FIGMA_TOKEN"
  "PAT_TOKEN:SOURCE_GITHUB_TOKEN"
  "COPILOT_GITHUB_TOKEN:SOURCE_GITHUB_TOKEN"
  "GEMINI_API_KEY:GEMINI_API_KEY"
)

# ── Variables from env: "GH_VAR_NAME:ENV_KEY" ─────────────────────────────────
VARS_FROM_ENV=(
  "JIRA_BASE_PATH:JIRA_BASE_PATH"
  "JIRA_AUTH_TYPE:JIRA_AUTH_TYPE"
  "JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES:JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES"
  "CONFLUENCE_BASE_PATH:CONFLUENCE_BASE_PATH"
  "CONFLUENCE_DEFAULT_SPACE:CONFLUENCE_DEFAULT_SPACE"
  "ADO_ORGANIZATION:ADO_ORGANIZATION"
  "ADO_PROJECT:ADO_PROJECT"
  "FIGMA_BASE_PATH:FIGMA_BASE_PATH"
  "DMTOOLS_CACHE_ENABLED:DMTOOLS_CACHE_ENABLED"
  "PROMPT_CHUNK_TOKEN_LIMIT:PROMPT_CHUNK_TOKEN_LIMIT"
  "GEMINI_MODEL:GEMINI_MODEL"
  "GEMINI_DEFAULT_MODEL:GEMINI_DEFAULT_MODEL"
  "GEMINI_BASE_PATH:GEMINI_BASE_PATH"
)

# ── Variables with static defaults: "GH_VAR_NAME:DEFAULT_VALUE" ──────────────
VARS_DEFAULTS=(
  "AI_AGENT_PROVIDER:copilot"
  "COPILOT_MODEL:gpt-5-mini"
)

# ── Preview ───────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Repository : $REPO"
echo "  Env file   : $ENV_FILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo ""
echo "🔐  SECRETS to be set:"
echo "────────────────────────────────────────────────────────────"
for entry in "${SECRETS[@]}"; do
  gh_name="${entry%%:*}"
  env_key="${entry#*:}"
  val="$(env_val "$env_key")"
  if [[ -n "$val" ]]; then
    masked="${val:0:4}****${val: -4}"
    echo "  ✅  $gh_name  <-  $env_key  ($masked)"
  else
    echo "  ⚠️   $gh_name  <-  $env_key  (EMPTY — will be skipped)"
  fi
done

echo ""
echo "⚙️   VARIABLES to be set:"
echo "────────────────────────────────────────────────────────────"
for entry in "${VARS_FROM_ENV[@]}"; do
  gh_name="${entry%%:*}"
  env_key="${entry#*:}"
  val="$(env_val "$env_key")"
  if [[ -n "$val" ]]; then
    echo "  ✅  $gh_name  =  $val"
  else
    echo "  ⚠️   $gh_name  <-  $env_key  (EMPTY — will be skipped)"
  fi
done
for entry in "${VARS_DEFAULTS[@]}"; do
  gh_name="${entry%%:*}"
  default_val="${entry#*:}"
  echo "  ✅  $gh_name  =  $default_val  (default)"
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
read -r -p "▶  Proceed? (yes/no): " confirm
echo ""

if [[ "$confirm" != "yes" ]]; then
  echo "Aborted."
  exit 0
fi

# ── Apply secrets ─────────────────────────────────────────────────────────────
echo "🔐  Setting secrets..."
for entry in "${SECRETS[@]}"; do
  gh_name="${entry%%:*}"
  env_key="${entry#*:}"
  val="$(env_val "$env_key")"
  if [[ -n "$val" ]]; then
    gh secret set "$gh_name" --repo "$REPO" --body "$val"
    echo "  ✅  Secret set: $gh_name"
  else
    echo "  ⏭️   Skipped (empty): $gh_name"
  fi
done

# ── Apply variables ───────────────────────────────────────────────────────────
echo ""
echo "⚙️   Setting variables..."
for entry in "${VARS_FROM_ENV[@]}"; do
  gh_name="${entry%%:*}"
  env_key="${entry#*:}"
  val="$(env_val "$env_key")"
  if [[ -n "$val" ]]; then
    gh variable set "$gh_name" --repo "$REPO" --body "$val"
    echo "  ✅  Variable set: $gh_name = $val"
  else
    echo "  ⏭️   Skipped (empty): $gh_name"
  fi
done
for entry in "${VARS_DEFAULTS[@]}"; do
  gh_name="${entry%%:*}"
  default_val="${entry#*:}"
  gh variable set "$gh_name" --repo "$REPO" --body "$default_val"
  echo "  ✅  Variable set: $gh_name = $default_val"
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅  Done! All secrets and variables have been pushed to $REPO"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
