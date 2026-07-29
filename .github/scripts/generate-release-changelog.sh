#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Generates a markdown changelog from conventional commits between two git tags.
# Usage: generate-release-changelog.sh <previous-tag> <new-tag> [output-file]

set -euo pipefail

PREV_TAG="${1:-}"
NEW_TAG="${2:-}"
OUTPUT="${3:-/dev/stdout}"

if [[ -z "$PREV_TAG" || -z "$NEW_TAG" ]]; then
    echo "Usage: $0 <previous-tag> <new-tag> [output-file]" >&2
    exit 1
fi

if ! git rev-parse "$PREV_TAG" >/dev/null 2>&1 || ! git rev-parse "$NEW_TAG" >/dev/null 2>&1; then
    echo "One or both tags not found: $PREV_TAG, $NEW_TAG" >&2
    exit 1
fi

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

FEATURES="$TMP_DIR/features.txt"
BUGFIXES="$TMP_DIR/bugfixes.txt"
PERF="$TMP_DIR/perf.txt"
REFACTOR="$TMP_DIR/refactor.txt"
TESTS="$TMP_DIR/tests.txt"
DOCS="$TMP_DIR/docs.txt"
DEPS="$TMP_DIR/deps.txt"
MAINT="$TMP_DIR/maint.txt"
OTHER="$TMP_DIR/other.txt"
BREAKING="$TMP_DIR/breaking.txt"

touch "$FEATURES" "$BUGFIXES" "$PERF" "$REFACTOR" "$TESTS" "$DOCS" "$DEPS" "$MAINT" "$OTHER" "$BREAKING"

# Format a single commit as markdown list item.
# Subject is everything after the conventional-commit prefix.
format_commit() {
    local hash="$1"
    local subject="$2"
    local scope_and_desc="${subject#*: }"        # strip prefix like "feat(jira): "
    scope_and_desc="${scope_and_desc#*:}"        # handle "feat: " (no scope)
    scope_and_desc=$(echo "$scope_and_desc" | sed 's/^[[:space:]]*//')
    # Capitalize first letter for nicer markdown.
    scope_and_desc="$(echo "$scope_and_desc" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')"
    echo "- ${scope_and_desc} (${hash})"
}

# Iterate over non-merge commits, excluding version-bump commits.
git log --no-merges --pretty=format:"%H %h %s" "$PREV_TAG..$NEW_TAG" | \
while read -r full_hash short_hash subject; do
    # Skip "version increased" and "Merge pull request" noise.
    if [[ "$subject" =~ ^version[[:space:]]increased[[:space:]]to[[:space:]] ]]; then
        continue
    fi
    if [[ "$subject" =~ ^Merge[[:space:]]pull[[:space:]]request[[:space:]] ]]; then
        continue
    fi

    body=$(git log -1 --pretty=format:"%b" "$full_hash" 2>/dev/null || true)

    # Detect breaking changes (e.g. "feat(scope)!:" or BREAKING CHANGE in body).
    if [[ "$subject" == *"!:"* ]] || [[ "$body" == *"BREAKING CHANGE"* ]] || [[ "$body" == *"BREAKING-CHANGE"* ]]; then
        format_commit "$short_hash" "$subject" >> "$BREAKING"
    fi

    # Categorize by conventional commit prefix.
    re_feat='^feat(\([^)]*\))?:'
    re_fix='^fix(\([^)]*\))?:'
    re_docs='^docs(\([^)]*\))?:'
    re_test='^test(\([^)]*\))?:'
    re_refactor='^refactor(\([^)]*\))?:'
    re_perf='^perf(\([^)]*\))?:'
    re_deps='^chore\(deps\)(\([^)]*\))?:|^build\(deps\)(\([^)]*\))?:|^chore\(deps\)\(deps\):'
    re_maint='^(chore|ci|build)(\([^)]*\))?:'

    if [[ "$subject" =~ $re_feat ]]; then
        format_commit "$short_hash" "$subject" >> "$FEATURES"
    elif [[ "$subject" =~ $re_fix ]]; then
        format_commit "$short_hash" "$subject" >> "$BUGFIXES"
    elif [[ "$subject" =~ $re_docs ]]; then
        format_commit "$short_hash" "$subject" >> "$DOCS"
    elif [[ "$subject" =~ $re_test ]]; then
        format_commit "$short_hash" "$subject" >> "$TESTS"
    elif [[ "$subject" =~ $re_refactor ]]; then
        format_commit "$short_hash" "$subject" >> "$REFACTOR"
    elif [[ "$subject" =~ $re_perf ]]; then
        format_commit "$short_hash" "$subject" >> "$PERF"
    elif [[ "$subject" =~ $re_deps ]]; then
        format_commit "$short_hash" "$subject" >> "$DEPS"
    elif [[ "$subject" =~ $re_maint ]]; then
        format_commit "$short_hash" "$subject" >> "$MAINT"
    else
        format_commit "$short_hash" "$subject" >> "$OTHER"
    fi
done

# Output markdown.
{
    echo "## 📝 What's Changed"
    echo ""
    repo_url="https://github.com/${GITHUB_REPOSITORY:-epam/dm.ai}"
    echo "Full diff: [$PREV_TAG...$NEW_TAG]($repo_url/compare/$PREV_TAG...$NEW_TAG)"
    echo ""

    if [[ -s "$BREAKING" ]]; then
        echo "### 💥 Breaking Changes"
        sort -u "$BREAKING"
        echo ""
    fi

    if [[ -s "$FEATURES" ]]; then
        echo "### 🚀 Features"
        sort -u "$FEATURES"
        echo ""
    fi

    if [[ -s "$BUGFIXES" ]]; then
        echo "### 🐛 Bug Fixes"
        sort -u "$BUGFIXES"
        echo ""
    fi

    if [[ -s "$PERF" ]]; then
        echo "### ⚡ Performance"
        sort -u "$PERF"
        echo ""
    fi

    if [[ -s "$REFACTOR" ]]; then
        echo "### 🔧 Refactoring"
        sort -u "$REFACTOR"
        echo ""
    fi

    if [[ -s "$TESTS" ]]; then
        echo "### ✅ Tests"
        sort -u "$TESTS"
        echo ""
    fi

    if [[ -s "$DOCS" ]]; then
        echo "### 📚 Documentation"
        sort -u "$DOCS"
        echo ""
    fi

    if [[ -s "$DEPS" ]]; then
        echo "### 📦 Dependencies"
        sort -u "$DEPS"
        echo ""
    fi

    if [[ -s "$MAINT" ]]; then
        echo "### 🛠 Maintenance"
        sort -u "$MAINT"
        echo ""
    fi

    if [[ -s "$OTHER" ]]; then
        echo "### 📋 Other Changes"
        sort -u "$OTHER"
        echo ""
    fi

    if [[ ! -s "$FEATURES" && ! -s "$BUGFIXES" && ! -s "$PERF" && ! -s "$REFACTOR" && ! -s "$TESTS" && ! -s "$DOCS" && ! -s "$DEPS" && ! -s "$MAINT" && ! -s "$OTHER" && ! -s "$BREAKING" ]]; then
        echo "No conventional commits found between $PREV_TAG and $NEW_TAG."
        echo ""
    fi
} > "$OUTPUT"
