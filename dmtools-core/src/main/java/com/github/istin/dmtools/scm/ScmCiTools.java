// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.scm;

import com.github.istin.dmtools.common.utils.JSONUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vendor-neutral {@code scm_*} (data plane) and {@code ci_*} (control plane) tools (epam/dm.ai#630;
 * Java mirror of dmtools-dart gh-339 {@code ScmCiSyncTools}).
 *
 * <p>Each alias is a first-class normalized tool with its own schema, additive next to the untouched
 * {@code github_*} / {@code gitlab_*} tools. Routing: {@code DEFAULT_SCM} ({@code github|gitlab}) picks
 * the data-plane carrier, {@code DEFAULT_CI} ({@code actions|gitlab-ci}) the control-plane carrier —
 * read once per instance, so an alias never changes routing mid-process. The concrete tools are invoked
 * through the injected {@link ToolInvoker} (the MCP executor), so their behaviour and schemas are
 * reused bit-for-bit. Provider error envelopes pass through unchanged (never normalized into
 * {@code none}/{@code UNKNOWN}); operations a provider has no tool for return an honest gap error.
 */
public class ScmCiTools {

    /** Calls a concrete tool by name with already-built arguments. */
    @FunctionalInterface
    public interface ToolInvoker {
        Object invoke(String toolName, Map<String, Object> args) throws Exception;
    }

    public static final String PROVIDER_GITHUB = "github";
    public static final String PROVIDER_GITLAB = "gitlab";
    private static final String SCM_STATES = "open, closed, merged, all";
    private static final long RUN_LOOKUP_SKEW_SECONDS = 120;

    private final ToolInvoker invoker;
    private final String scm;
    private final String ci;

    public ScmCiTools(ToolInvoker invoker) {
        this(invoker, new PropertyReader());
    }

    public ScmCiTools(ToolInvoker invoker, PropertyReader reader) {
        this(invoker, resolveScmProvider(reader.getValue("DEFAULT_SCM")), resolveCiProvider(reader.getValue("DEFAULT_CI")));
    }

    /** Explicit routing (tests). */
    public ScmCiTools(ToolInvoker invoker, String scmProvider, String ciProvider) {
        this.invoker = invoker;
        this.scm = scmProvider;
        this.ci = ciProvider;
    }

    /** {@code DEFAULT_SCM} value to provider, null when unset/unknown (trimmed + lowercased). */
    public static String resolveScmProvider(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        return "github".equals(v) ? PROVIDER_GITHUB : "gitlab".equals(v) ? PROVIDER_GITLAB : null;
    }

    /** {@code DEFAULT_CI} value ({@code actions|gitlab-ci}) to provider, null when unset/unknown. */
    public static String resolveCiProvider(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        return "actions".equals(v) ? PROVIDER_GITHUB : "gitlab-ci".equals(v) ? PROVIDER_GITLAB : null;
    }

    // ───────────────────────────── scm_* (data plane) ─────────────────────────────

    @MCPTool(name = "scm_list_prs", integration = "scm", category = "scm",
            description = "List pull requests / merge requests. State filter is the normalized enum: open (default), closed, merged, all.")
    public String listPrs(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "state", description = "Normalized state filter: open, closed, merged, all", required = false) String state) {
        String s = state == null || state.trim().isEmpty() ? "open" : state.trim().toLowerCase();
        if (!SCM_STATES.contains(s) || s.contains(",")) {
            return err("scm_list_prs: state must be one of: " + SCM_STATES + " (got '" + s + "') — the normalized enum is the only accepted shape");
        }
        return route("scm", scm,
                () -> call("github_list_prs", args("workspace", workspace, "repository", repository, "state", s)),
                () -> call("gitlab_list_mrs", args("workspace", workspace, "repository", repository, "state", "open".equals(s) ? "opened" : s)));
    }

    @MCPTool(name = "scm_get_pr", integration = "scm", category = "scm", description = "Fetch one pull request / merge request (provider PR payload).")
    public String getPr(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr) {
        return route("scm", scm,
                () -> call("github_get_pr", args("workspace", workspace, "repository", repository, "pullRequestId", pr)),
                () -> call("gitlab_get_mr", args("workspace", workspace, "repository", repository, "pullRequestId", pr)));
    }

    @MCPTool(name = "scm_merge_pr", integration = "scm", category = "scm",
            description = "Merge a pull request / merge request. Merge method per provider config (GitHub: mergeMethod merge|squash|rebase).")
    public String mergePr(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr,
            @MCPParam(name = "mergeMethod", description = "merge | squash | rebase (GitHub)", required = false) String mergeMethod,
            @MCPParam(name = "commitTitle", description = "Override the merge commit title (GitHub)", required = false) String commitTitle,
            @MCPParam(name = "commitMessage", description = "Override the merge commit message (GitHub)", required = false) String commitMessage) {
        return route("scm", scm,
                () -> call("github_merge_pr", args("workspace", workspace, "repository", repository, "pullRequestId", pr,
                        "mergeMethod", mergeMethod, "commitTitle", commitTitle, "commitMessage", commitMessage)),
                () -> call("gitlab_merge_mr", args("workspace", workspace, "repository", repository, "pullRequestId", pr)));
    }

    @MCPTool(name = "scm_get_diff", integration = "scm", category = "scm", description = "Raw unified diff text of a pull request / merge request.")
    public String getDiff(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr) {
        return route("scm", scm,
                () -> call("github_get_pr_diff_text", args("workspace", workspace, "repository", repository, "pullRequestID", pr)),
                () -> call("gitlab_get_mr_diff_text", args("workspace", workspace, "repository", repository, "pullRequestID", pr)));
    }

    @MCPTool(name = "scm_add_labels", integration = "scm", category = "scm", description = "Add labels to a PR/MR or issue. Labels are plain strings.")
    public String addLabels(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = false) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr,
            @MCPParam(name = "labels", description = "Labels to add", required = true) String[] labels) {
        if (labels == null || labels.length == 0) {
            return err("scm_add_labels requires a non-empty labels array");
        }
        return route("scm", scm,
                () -> call("github_add_labels", args("owner", workspace, "repo", repository, "number", toInt(pr), "labels", labels)),
                () -> {
                    String last = "";
                    for (String label : labels) {
                        last = call("gitlab_add_mr_label", args("workspace", workspace, "repository", repository, "pullRequestId", pr, "label", label));
                        if (isErr(last)) return last;
                    }
                    return last;
                });
    }

    @MCPTool(name = "scm_remove_label", integration = "scm", category = "scm",
            description = "Remove one label from a PR/MR or issue (an absent label is a no-op on GitLab-shaped providers).")
    public String removeLabel(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = false) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr,
            @MCPParam(name = "label", description = "The label to remove", required = true) String label) {
        return route("scm", scm,
                () -> call("github_remove_label", args("owner", workspace, "repo", repository, "number", toInt(pr), "label", label)),
                () -> call("gitlab_remove_mr_label", args("workspace", workspace, "repository", repository, "pullRequestId", pr, "label", label)));
    }

    @MCPTool(name = "scm_add_pr_comment", integration = "scm", category = "scm", description = "Comment on a pull request / merge request.")
    public String addPrComment(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr,
            @MCPParam(name = "text", description = "Comment text", required = true) String text) {
        return route("scm", scm,
                () -> call("github_add_pr_comment", args("workspace", workspace, "repository", repository, "pullRequestId", pr, "text", text)),
                () -> call("gitlab_add_mr_comment", args("workspace", workspace, "repository", repository, "pullRequestId", pr, "text", text)));
    }

    @MCPTool(name = "scm_get_pr_comments", integration = "scm", category = "scm", description = "List comments of a pull request / merge request.")
    public String getPrComments(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr) {
        return route("scm", scm,
                () -> call("github_get_pr_comments", args("workspace", workspace, "repository", repository, "pullRequestId", pr)),
                () -> call("gitlab_get_mr_comments", args("workspace", workspace, "repository", repository, "pullRequestId", pr)));
    }

    @MCPTool(name = "scm_create_comment", integration = "scm", category = "scm",
            description = "Comment on an issue (the SM issue-carrier channel). GitLab: rides the MR-note shape — `issue` is the MR iid, not a GitLab issue number.")
    public String createComment(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = false) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false) String repository,
            @MCPParam(name = "issue", description = "Issue number (GitLab: MR iid)", required = true) String issue,
            @MCPParam(name = "body", description = "Comment body", required = true) String body) {
        return route("scm", scm,
                () -> call("github_create_comment", args("workspace", workspace, "repository", repository, "pullRequestId", issue, "body", body)),
                () -> call("gitlab_create_mr_note", args("workspace", workspace, "repository", repository, "pullRequestId", issue, "text", body)));
    }

    @MCPTool(name = "scm_get_issue", integration = "scm", category = "scm", description = "Fetch one issue by number.")
    public String getIssue(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = false) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false) String repository,
            @MCPParam(name = "issue", description = "Issue number", required = true) String issue) {
        return route("scm", scm,
                () -> call("github_get_issue", args("workspace", workspace, "repository", repository, "issueNumber", issue)),
                () -> gap("scm_get_issue"));
    }

    @MCPTool(name = "scm_search_issues", integration = "scm", category = "scm",
            description = "Search issues (GitHub search syntax; scoped to the configured repository when the query has no repo: qualifier).")
    public String searchIssues(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = false) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false) String repository,
            @MCPParam(name = "query", description = "Search query", required = true) String query) {
        return route("scm", scm,
                () -> call("github_search_issues", args("query", query, "workspace", workspace, "repository", repository)),
                () -> gap("scm_search_issues"));
    }

    @MCPTool(name = "scm_close_issue", integration = "scm", category = "scm", description = "Close an issue (SM close-on-merge finishing move).")
    public String closeIssue(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "issue", description = "Issue number", required = true) String issue) {
        return route("scm", scm,
                () -> call("github_close_issue", args("owner", workspace, "repo", repository, "number", toInt(issue))),
                () -> gap("scm_close_issue"));
    }

    @MCPTool(name = "scm_list_branches", integration = "scm", category = "scm", description = "List repository branches (name + head sha).")
    public String listBranches(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository) {
        return route("scm", scm,
                () -> call("github_list_branches", args("owner", workspace, "repo", repository)),
                () -> gap("scm_list_branches"));
    }

    @MCPTool(name = "scm_get_reviews", integration = "scm", category = "scm", description = "List reviews / approvals of a pull request / merge request.")
    public String getReviews(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr) {
        return route("scm", scm,
                () -> call("github_list_pr_reviews", args("workspace", workspace, "repository", repository, "pullRequestId", pr)),
                () -> gap("scm_get_reviews"));
    }

    @MCPTool(name = "scm_approve", integration = "scm", category = "scm", description = "Approve a pull request / merge request.")
    public String approve(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr,
            @MCPParam(name = "body", description = "Optional approval summary text", required = false) String body) {
        return route("scm", scm,
                () -> call("github_submit_pr_review", args("workspace", workspace, "repository", repository, "pullRequestId", pr, "event", "APPROVE", "body", body)),
                () -> call("gitlab_approve_mr", args("workspace", workspace, "repository", repository, "pullRequestId", pr)));
    }

    // ───────────────────────────── ci_* (control plane) ─────────────────────────────

    @MCPTool(name = "ci_trigger_workflow", integration = "ci", category = "ci",
            description = "Trigger a CI run (fire-and-return run handle). `inputs` is a flat string map (JSON object string); non-string values are stringified at the alias boundary.")
    public String triggerWorkflow(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "workflow", description = "Workflow file / id", required = true) String workflow,
            @MCPParam(name = "ref", description = "Branch or tag ref (default: main)", required = false) String ref,
            @MCPParam(name = "inputs", description = "Flat string map of run inputs (JSON object)", required = false) String inputs) {
        String flat = stringifiedInputs(inputs);
        if (flat == null) {
            return err("ci_trigger_workflow: invalid inputs JSON");
        }
        return route("ci", ci,
                () -> {
                    Instant dispatchedAt = Instant.now();
                    String raw = call("github_trigger_workflow", args("workspace", workspace, "repository", repository,
                            "workflowId", workflow, "ref", ref, "inputs", flat.isEmpty() ? null : flat));
                    if (isErr(raw)) return raw;
                    return triggerResult(PROVIDER_GITHUB, ghRunIdLookup(workspace, repository, workflow, ref == null ? "main" : ref, dispatchedAt), raw);
                },
                () -> {
                    String raw = call("gitlab_trigger_pipeline", args("workspace", workspace, "repository", repository,
                            "ref", ref == null ? "main" : ref, "variablesJson", flat.isEmpty() ? null : flat));
                    if (isErr(raw)) return raw;
                    Object id = decodeObject(raw).opt("id");
                    return triggerResult(PROVIDER_GITLAB, id, raw);
                });
    }

    @MCPTool(name = "ci_list_runs", integration = "ci", category = "ci",
            description = "List CI runs (newest first). Each run: runId, status, verdict (pass|fail|pending|none), sha, url, startedAt, event.")
    public String listRuns(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "workflow", description = "Workflow file / id filter", required = false) String workflow,
            @MCPParam(name = "ref", description = "Branch or tag ref filter", required = false) String ref,
            @MCPParam(name = "status", description = "Provider run-status filter", required = false) String status,
            @MCPParam(name = "limit", description = "Max runs to return (default 30)", required = false) String limit) {
        return route("ci", ci,
                () -> {
                    String raw = call("github_list_workflow_runs", args("workspace", workspace, "repository", repository,
                            "workflowId", workflow, "status", status, "perPage", toInt(limit)));
                    if (isErr(raw)) return raw;
                    // The concrete tool has no server-side ref filter: filter client-side so a wrong-branch run
                    // is never presented as ref-filtered.
                    JSONArray runs = decodeObject(raw).optJSONArray("workflow_runs");
                    JSONArray out = new JSONArray();
                    for (int i = 0; runs != null && i < runs.length(); i++) {
                        JSONObject r = runs.optJSONObject(i);
                        if (r == null || (ref != null && !ref.equals(r.optString("head_branch", null)))) continue;
                        out.put(new JSONObject()
                                .put("runId", r.opt("id"))
                                .put("status", r.opt("status"))
                                .put("verdict", CiNormalization.ghRunVerdict(r.optString("status", null), r.optString("conclusion", null)))
                                .put("sha", r.opt("head_sha"))
                                .put("url", r.opt("html_url"))
                                .put("startedAt", r.opt("created_at"))
                                .put("event", r.opt("event"))
                                .put("path", r.opt("path"))
                                .put("name", r.opt("name")));
                    }
                    return new JSONObject().put("runs", out).toString();
                },
                () -> {
                    String raw = call("gitlab_list_pipeline_runs", args("workspace", workspace, "repository", repository,
                            "status", status, "ref", ref, "limit", limit));
                    if (isErr(raw)) return raw;
                    JSONArray runs = decodeArray(raw);
                    JSONArray out = new JSONArray();
                    for (int i = 0; runs != null && i < runs.length(); i++) {
                        JSONObject r = runs.optJSONObject(i);
                        if (r == null) continue;
                        out.put(new JSONObject()
                                .put("runId", r.opt("id"))
                                .put("status", r.opt("status"))
                                .put("verdict", CiNormalization.gitlabStatusVerdict(r.optString("status", null)))
                                .put("sha", r.opt("sha"))
                                .put("url", r.opt("web_url"))
                                .put("startedAt", r.opt("created_at")));
                    }
                    return new JSONObject().put("runs", out).toString();
                });
    }

    @MCPTool(name = "ci_get_verdict", integration = "ci", category = "ci",
            description = "THE CI verdict for a run: pass | fail | pending | none. Call with `runId`, or probe a head with `sha` (plus optional `workflow` for the stale-verdict fallback), or on GitLab with `pr`.")
    public String getVerdict(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "runId", description = "Run handle (from ci_trigger_workflow/ci_list_runs)", required = false) String runId,
            @MCPParam(name = "sha", description = "Head commit sha to probe", required = false) String sha,
            @MCPParam(name = "workflow", description = "Workflow file for the sha probe fallback", required = false) String workflow,
            @MCPParam(name = "pr", description = "PR/MR number (GitLab pipelines-of-MR probe)", required = false) String pr) {
        return route("ci", ci,
                () -> ghVerdict(workspace, repository, runId, sha, workflow),
                () -> glVerdict(workspace, repository, runId, sha, pr));
    }

    @MCPTool(name = "ci_get_merge_state", integration = "ci", category = "ci",
            description = "Mergeability of a PR/MR in the enum the SM speaks: CLEAN | BEHIND | DIRTY | BLOCKED | UNKNOWN. Both vendors' checks-settling transients return BLOCKED + reason.")
    public String getMergeState(
            @MCPParam(name = "workspace", description = "Repository owner / namespace", required = true) String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true) String repository,
            @MCPParam(name = "pr", description = "PR / MR number", required = true) String pr) {
        return route("ci", ci,
                () -> {
                    String raw = call("github_get_pr", args("workspace", workspace, "repository", repository, "pullRequestId", pr));
                    if (isErr(raw)) return raw;
                    JSONObject b = decodeObject(raw);
                    return CiNormalization.ghMergeState(b.has("mergeable") && !b.isNull("mergeable") ? b.optBoolean("mergeable") : null,
                            b.optString("mergeable_state", null), b.optString("mergeStateStatus", null))
                            .put("provider", PROVIDER_GITHUB).toString();
                },
                () -> {
                    String raw = call("gitlab_get_mr", args("workspace", workspace, "repository", repository, "pullRequestId", pr));
                    if (isErr(raw)) return raw;
                    JSONObject b = decodeObject(raw);
                    return CiNormalization.gitlabMergeState(b.optString("merge_status", null), b.optString("detailed_merge_status", null),
                            b.has("has_conflicts") && !b.isNull("has_conflicts") ? b.optBoolean("has_conflicts") : null)
                            .put("provider", PROVIDER_GITLAB).toString();
                });
    }

    // ───────────────────────────── verdict helpers ─────────────────────────────

    private String ghVerdict(String ws, String repo, String runId, String sha, String workflow) throws Exception {
        if (runId != null && !runId.isEmpty()) {
            String raw = call("github_get_workflow_run", args("workspace", ws, "repository", repo, "runId", runId));
            JSONObject b = decodeObject(raw);
            if (b.has("error")) return runMismatch(raw, PROVIDER_GITHUB, runId);
            return verdictJson(PROVIDER_GITHUB, CiNormalization.ghRunVerdict(b.optString("status", null), b.optString("conclusion", null)));
        }
        if (sha == null || sha.isEmpty()) {
            return err("ci_get_verdict requires runId, or sha (+ optional workflow) for the head probe");
        }
        String crRaw = call("github_get_commit_check_runs", args("workspace", ws, "repository", repo, "commitSha", sha));
        if (isErr(crRaw)) return crRaw;
        JSONArray checks = decodeObject(crRaw).optJSONArray("check_runs");
        if (checks != null && checks.length() > 0) {
            return verdictJson(PROVIDER_GITHUB, CiNormalization.ghCheckRunsVerdict(checks));
        }
        if (workflow == null || workflow.isEmpty()) {
            return verdictJson(PROVIDER_GITHUB, CiNormalization.VERDICT_NONE);
        }
        String raw = call("github_list_workflow_runs", args("workspace", ws, "repository", repo, "workflowId", workflow, "perPage", 30));
        if (isErr(raw)) return raw;
        JSONArray runs = decodeObject(raw).optJSONArray("workflow_runs");
        for (int i = 0; runs != null && i < runs.length(); i++) {
            JSONObject r = runs.optJSONObject(i);
            if (r != null && sha.equals(r.optString("head_sha", null))) {
                return verdictJson(PROVIDER_GITHUB, CiNormalization.ghRunVerdict(r.optString("status", null), r.optString("conclusion", null)));
            }
        }
        return verdictJson(PROVIDER_GITHUB, CiNormalization.VERDICT_NONE);
    }

    private String glVerdict(String ws, String repo, String runId, String sha, String pr) throws Exception {
        if (runId != null && !runId.isEmpty()) {
            String raw = call("gitlab_get_pipeline_jobs", args("workspace", ws, "repository", repo, "pipelineId", runId));
            JSONObject b = decodeObject(raw);
            if (b.has("error")) return runMismatch(raw, PROVIDER_GITLAB, runId);
            return verdictJson(PROVIDER_GITLAB, CiNormalization.gitlabStatusListVerdict(b.optJSONArray("jobs")));
        }
        if (pr != null && !pr.isEmpty()) {
            String raw = call("gitlab_get_mr_pipelines", args("workspace", ws, "repository", repo, "pullRequestId", pr));
            if (isErr(raw)) return raw;
            return verdictJson(PROVIDER_GITLAB, CiNormalization.gitlabStatusListVerdict(decodeArray(raw)));
        }
        if (sha != null && !sha.isEmpty()) {
            String raw = call("gitlab_get_commit_statuses", args("workspace", ws, "repository", repo, "commitSha", sha));
            if (isErr(raw)) return raw;
            return verdictJson(PROVIDER_GITLAB, CiNormalization.gitlabStatusListVerdict(decodeArray(raw)));
        }
        return err("ci_get_verdict requires runId, pr, or sha (+ optional workflow) for the head probe");
    }

    /**
     * Best-effort run handle after a GitHub dispatch (the API returns no id): the newest
     * workflow_dispatch run of the workflow on the dispatched ref created at/after the dispatch moment.
     * No provably-fresh run means the honest null handle — a plausible-but-wrong handle would surface a
     * stale verdict as authoritative (fa #762); the race-safe path is the sha probe.
     */
    private Object ghRunIdLookup(String ws, String repo, String workflow, String ref, Instant dispatchedAt) {
        Instant cutoff = dispatchedAt.minusSeconds(RUN_LOOKUP_SKEW_SECONDS);
        try {
            String raw = call("github_list_workflow_runs", args("workspace", ws, "repository", repo, "workflowId", workflow, "perPage", 5));
            JSONArray runs = decodeObject(raw).optJSONArray("workflow_runs");
            for (int i = 0; runs != null && i < runs.length(); i++) {
                JSONObject r = runs.optJSONObject(i);
                if (r == null || !ref.equals(r.optString("head_branch", null)) || !"workflow_dispatch".equals(r.optString("event", null))) continue;
                try {
                    if (!Instant.parse(r.optString("created_at", "")).isBefore(cutoff)) return r.opt("id");
                } catch (Exception ignored) {
                    // unparsable created_at: not provably fresh
                }
            }
        } catch (Exception ignored) {
            // best-effort by contract: never fail an accepted trigger
        }
        return null;
    }

    // ───────────────────────────── shared plumbing ─────────────────────────────

    @FunctionalInterface
    private interface Route {
        String run() throws Exception;
    }

    private String route(String family, String provider, Route github, Route gitlab) {
        if (provider == null) {
            return err(family + "_* is not configured: set " + ("scm".equals(family) ? "DEFAULT_SCM=github|gitlab" : "DEFAULT_CI=actions|gitlab-ci"));
        }
        try {
            return PROVIDER_GITLAB.equals(provider) ? gitlab.run() : github.run();
        } catch (Exception e) {
            return err(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Invokes a concrete tool and returns its result as a string (JSON when the tool returns a model). */
    private String call(String tool, Map<String, Object> args) throws Exception {
        Object result = invoker.invoke(tool, args);
        if (result == null) return "{}";
        if (result instanceof String) return (String) result;
        return JSONUtils.serializeResult(result);
    }

    /** Builds an argument map from key/value pairs, dropping null values. */
    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Integer toInt(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Integer.valueOf(s.trim().replaceAll("^.*#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JSONObject decodeObject(String raw) {
        try {
            return new JSONObject(raw == null ? "{}" : raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONArray decodeArray(String raw) {
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** The one error convention: a decoded {@code {"error": ...}} object, never a substring sniff. */
    static boolean isErr(String raw) {
        if (raw == null || !raw.trim().startsWith("{")) return false;
        JSONObject o = decodeObject(raw);
        return o.has("error") && !o.isNull("error");
    }

    static String err(String message) {
        return new JSONObject().put("error", message).toString();
    }

    private static String gap(String tool) {
        return err(tool + " is not available on the GitLab route (no concrete GitLab tool for it yet) — see epam/dm.ai#630 tiering notes");
    }

    private static String verdictJson(String provider, String verdict) {
        return new JSONObject().put("provider", provider).put("verdict", verdict).toString();
    }

    private static String runMismatch(String raw, String provider, String runId) {
        JSONObject b = decodeObject(raw);
        return b.has("error") ? err(b.get("error") + " (runId " + runId + " not found on " + provider + " — was it created by a different provider?)") : raw;
    }

    private static String triggerResult(String provider, Object runId, String raw) {
        JSONObject decoded = decodeObject(raw);
        Object message = decoded.has("message") ? decoded.get("message") : unwrapQuoted(raw);
        return new JSONObject().put("provider", provider).put("runId", runId == null ? JSONObject.NULL : runId).put("message", message).toString();
    }

    private static String unwrapQuoted(String raw) {
        String t = raw == null ? "" : raw.trim();
        return t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"") ? t.substring(1, t.length() - 1) : t;
    }

    /** Flat string map: object JSON string; non-string values stringified. Null marks unparsable JSON; "" = none. */
    private static String stringifiedInputs(String inputs) {
        if (inputs == null || inputs.trim().isEmpty()) return "";
        try {
            JSONObject o = new JSONObject(inputs.trim());
            JSONObject flat = new JSONObject();
            for (String k : o.keySet()) {
                Object v = o.get(k);
                flat.put(k, v instanceof String ? v : String.valueOf(v));
            }
            return flat.toString();
        } catch (JSONException e) {
            return null;
        }
    }
}
