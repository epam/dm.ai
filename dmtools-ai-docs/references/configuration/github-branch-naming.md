# GitHub Branch Naming Restrictions

How to enforce branch naming conventions in GitHub using **Rulesets** — the native, proactive way to block non-conforming branch names at creation time.

---

## Recommended Approach: GitHub Rulesets

**GitHub Rulesets** support regex-based branch name validation and block branch creation if the name doesn't match. Available on:
- GitHub Free (public repositories)
- GitHub Pro / Team / Enterprise (public and private repositories)

### Steps

1. Go to **Settings → Rules → Rulesets** in your repository (or organization for repo-wide policies).
2. Click **New Ruleset → New Branch Ruleset**.
3. Set **Enforcement status** to `Active` (use `Evaluate` first to test without blocking).
4. Under **Targets**, select the branches to apply the rule to (e.g., all branches).
5. Enable **Restrict branch names** → **Add restriction** → **Must match a given regex pattern**.
6. Enter your regex (see examples below) and save.

---

## Regex Patterns

### Basic prefix enforcement (`story/`, `task/`, `bug/`)

```regex
^(story|task|bug)/.+$
```

### With Jira ticket number (`MAPC-123`)

```regex
^(story|task|bug)/MAPC-\d+.*$
```

Valid examples:
- `story/MAPC-123`
- `bug/MAPC-456-fix-login`
- `task/MAPC-789`

### Strict: prefix + ticket + kebab description

```regex
^(story|task|bug)/MAPC-\d+(-[a-z0-9]+)+$
```

Valid examples:
- `story/MAPC-123-add-login-page`
- `bug/MAPC-456-fix-null-pointer`
- `task/MAPC-789-update-api-docs`

---

## Organization-Level Policy

To enforce the same naming convention across **all repositories** in the organization:

1. Go to **Organization Settings → Rules → Rulesets**.
2. Create a Branch Ruleset with the same steps above.
3. Under **Targets**, choose specific repositories or apply to all.

---

## Alternative: GitHub Actions (post-creation check)

If Rulesets are not available, you can use a GitHub Actions workflow to reject PRs with non-conforming branch names. This does **not** block branch creation but fails the CI check:

```yaml
# .github/workflows/branch-name-check.yml
name: Branch Name Check

on:
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  check-branch-name:
    runs-on: ubuntu-latest
    steps:
      - name: Check branch name
        run: |
          BRANCH="${{ github.head_ref }}"
          if ! echo "$BRANCH" | grep -qE '^(story|task|bug)/MAPC-[0-9]+'; then
            echo "❌ Branch name '$BRANCH' does not match required pattern: (story|task|bug)/MAPC-<number>"
            exit 1
          fi
          echo "✅ Branch name is valid: $BRANCH"
```

---

## Comparison

| Method              | Blocks creation | Requires Admin | Notes                              |
|---------------------|-----------------|----------------|------------------------------------|
| GitHub Rulesets     | ✅ Yes          | Yes            | Native, regex support, recommended |
| Branch Protection   | ❌ No           | Yes            | Glob patterns only, no name rules  |
| GitHub Actions      | ❌ No (CI fail) | No             | Only rejects PRs, not branches     |
| Client-side hooks   | ❌ No (remote)  | No             | Can be bypassed, requires setup    |
