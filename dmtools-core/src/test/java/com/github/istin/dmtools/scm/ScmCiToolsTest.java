// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.scm;

import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScmCiToolsTest {

    /** Records every concrete-tool call and answers from a per-tool canned response. */
    private static class Recorder implements ScmCiTools.ToolInvoker {
        final List<String> tools = new ArrayList<>();
        final List<Map<String, Object>> args = new ArrayList<>();
        final Map<String, Object> responses = new LinkedHashMap<>();

        @Override
        public Object invoke(String toolName, Map<String, Object> a) {
            tools.add(toolName);
            args.add(a);
            return responses.getOrDefault(toolName, "{}");
        }
    }

    private static ScmCiTools gh(Recorder r) {
        return new ScmCiTools(r, "github", "github");
    }

    private static ScmCiTools gl(Recorder r) {
        return new ScmCiTools(r, "gitlab", "gitlab");
    }

    // ---- routing ----

    @Test
    void routingVariablesResolveLikeTheTrackerOnes() {
        assertEquals("github", ScmCiTools.resolveScmProvider("  GitHub "));
        assertEquals("gitlab", ScmCiTools.resolveScmProvider("gitlab"));
        assertNull(ScmCiTools.resolveScmProvider("bitbucket"));
        assertNull(ScmCiTools.resolveScmProvider(null));
        assertEquals("github", ScmCiTools.resolveCiProvider("actions"));
        assertEquals("gitlab", ScmCiTools.resolveCiProvider("gitlab-ci"));
        assertNull(ScmCiTools.resolveCiProvider("github"), "DEFAULT_CI takes actions|gitlab-ci, not the SCM spelling");
    }

    @Test
    void unconfiguredFamilyReportsHowToConfigureItInsteadOfCrashing() {
        Recorder r = new Recorder();
        String out = new ScmCiTools(r, null, null).getPr("o", "r", "1");
        assertTrue(out.contains("DEFAULT_SCM"), out);
        assertTrue(new ScmCiTools(r, null, null).getVerdict("o", "r", null, "abc", null, null).contains("DEFAULT_CI"));
        assertTrue(r.tools.isEmpty());
    }

    // ---- scm_* data plane ----

    @Test
    void getPrRoutesToTheConfiguredProvider() {
        Recorder r = new Recorder();
        gh(r).getPr("o", "r", "7");
        gl(r).getPr("o", "r", "7");
        assertEquals(List.of("github_get_pr", "gitlab_get_mr"), r.tools);
        assertEquals("7", r.args.get(0).get("pullRequestId"));
    }

    @Test
    void listPrs_normalizedStateOnly_andGitlabSpelling() {
        Recorder r = new Recorder();
        gh(r).listPrs("o", "r", null);
        gl(r).listPrs("o", "r", "open");
        gl(r).listPrs("o", "r", "merged");
        assertEquals("open", r.args.get(0).get("state"));
        assertEquals("opened", r.args.get(1).get("state"));
        assertEquals("merged", r.args.get(2).get("state"));
        String bad = gh(r).listPrs("o", "r", "opened");
        assertTrue(bad.contains("state must be one of"), bad);
        assertEquals(3, r.tools.size(), "a rejected state never reaches the provider");
    }

    @Test
    void addLabels_githubOneCall_gitlabOneCallPerLabel_andEmptyIsRejected() {
        Recorder r = new Recorder();
        gh(r).addLabels("o", "r", "12", new String[]{"a", "b"});
        assertEquals(List.of("github_add_labels"), r.tools);
        assertEquals(12, r.args.get(0).get("number"));
        Recorder g = new Recorder();
        gl(g).addLabels("o", "r", "12", new String[]{"a", "b"});
        assertEquals(List.of("gitlab_add_mr_label", "gitlab_add_mr_label"), g.tools);
        assertTrue(gh(r).addLabels("o", "r", "1", new String[0]).contains("non-empty"));
    }

    @Test
    void createComment_gitlabRidesTheMrNoteShape() {
        Recorder r = new Recorder();
        gl(r).createComment("o", "r", "9", "hi");
        assertEquals("gitlab_create_mr_note", r.tools.get(0));
        assertEquals("hi", r.args.get(0).get("text"));
        assertEquals("9", r.args.get(0).get("pullRequestId"));
    }

    @Test
    void gitlabHonestGapsNameTheMissingTool() {
        Recorder r = new Recorder();
        for (String out : new String[]{gl(r).getIssue("o", "r", "1"), gl(r).searchIssues("o", "r", "q"),
                gl(r).closeIssue("o", "r", "1"), gl(r).listBranches("o", "r"), gl(r).getReviews("o", "r", "1")}) {
            assertTrue(out.contains("is not available on the GitLab route"), out);
        }
        assertTrue(r.tools.isEmpty(), "gaps never call a provider tool");
    }

    @Test
    void approve_githubSubmitsAnApproveReview() {
        Recorder r = new Recorder();
        gh(r).approve("o", "r", "3", "lgtm");
        assertEquals("github_submit_pr_review", r.tools.get(0));
        assertEquals("APPROVE", r.args.get(0).get("event"));
        assertEquals("lgtm", r.args.get(0).get("body"));
    }

    // ---- ci_* control plane ----

    @Test
    void mergeState_isNormalizedAndCarriesTheProvider() {
        Recorder r = new Recorder();
        r.responses.put("github_get_pr", "{\"mergeable\":true,\"mergeable_state\":\"blocked\"}");
        JSONObject o = new JSONObject(gh(r).getMergeState("o", "r", "1"));
        assertEquals("BLOCKED", o.getString("mergeState"));
        assertEquals("required-checks-pending", o.getString("reason"));
        assertEquals("github", o.getString("provider"));
        Recorder g = new Recorder();
        g.responses.put("gitlab_get_mr", "{\"merge_status\":\"can_be_merged\",\"detailed_merge_status\":\"ci_still_running\"}");
        assertEquals("ci-still-running", new JSONObject(gl(g).getMergeState("o", "r", "1")).getString("reason"));
    }

    @Test
    void providerErrorEnvelopesPassThroughNeverBecomeUnknownOrNone() {
        Recorder r = new Recorder();
        r.responses.put("github_get_pr", "{\"error\":\"401 bad credentials\"}");
        r.responses.put("github_get_commit_check_runs", "{\"error\":\"rate limited\"}");
        assertEquals("{\"error\":\"401 bad credentials\"}", gh(r).getMergeState("o", "r", "1"));
        assertEquals("{\"error\":\"rate limited\"}", gh(r).getVerdict("o", "r", null, "abc", null, null));
    }

    @Test
    void verdictBySha_checkRunRollup_thenWorkflowFallback_thenNone() {
        Recorder r = new Recorder();
        r.responses.put("github_get_commit_check_runs", "{\"check_runs\":[{\"conclusion\":\"success\"},{\"conclusion\":\"failure\"}]}");
        assertEquals("fail", new JSONObject(gh(r).getVerdict("o", "r", null, "abc", "ci.yml", null)).getString("verdict"));

        Recorder f = new Recorder();
        f.responses.put("github_get_commit_check_runs", "{\"check_runs\":[]}");
        f.responses.put("github_list_workflow_runs", "{\"workflow_runs\":[{\"head_sha\":\"other\",\"status\":\"completed\",\"conclusion\":\"failure\"},"
                + "{\"head_sha\":\"abc\",\"status\":\"completed\",\"conclusion\":\"success\"}]}");
        assertEquals("pass", new JSONObject(gh(f).getVerdict("o", "r", null, "abc", "ci.yml", null)).getString("verdict"),
                "the fallback matches the probed head, not the newest run");
        assertEquals("none", new JSONObject(gh(new Recorder() {{ responses.put("github_get_commit_check_runs", "{\"check_runs\":[]}"); }})
                .getVerdict("o", "r", null, "abc", null, null)).getString("verdict"));
    }

    @Test
    void verdictNeedsAProbeInput() {
        assertTrue(gh(new Recorder()).getVerdict("o", "r", null, null, null, null).contains("requires runId"));
        assertTrue(gl(new Recorder()).getVerdict("o", "r", null, null, null, null).contains("requires runId, pr, or sha"));
    }

    @Test
    void gitlabVerdict_byPr_andBySha() {
        Recorder r = new Recorder();
        r.responses.put("gitlab_get_mr_pipelines", "[{\"status\":\"success\"},{\"status\":\"running\"}]");
        r.responses.put("gitlab_get_commit_statuses", "[{\"status\":\"success\"}]");
        assertEquals("pending", new JSONObject(gl(r).getVerdict("o", "r", null, null, null, "4")).getString("verdict"));
        assertEquals("pass", new JSONObject(gl(r).getVerdict("o", "r", null, "abc", null, null)).getString("verdict"));
    }

    @Test
    void triggerWorkflow_stringifiesInputs_andRejectsBrokenJson() {
        Recorder r = new Recorder();
        r.responses.put("github_trigger_workflow", "\"Workflow dispatched\"");
        r.responses.put("github_list_workflow_runs", "{\"workflow_runs\":[]}");
        String out = gh(r).triggerWorkflow("o", "r", "ci.yml", null, "{\"n\":3,\"flag\":true,\"s\":\"x\"}");
        JSONObject flat = new JSONObject((String) r.args.get(0).get("inputs"));
        assertEquals("3", flat.getString("n"));
        assertEquals("true", flat.getString("flag"));
        JSONObject res = new JSONObject(out);
        assertEquals("github", res.getString("provider"));
        assertTrue(res.isNull("runId"), "no provably-fresh run: the honest null handle, never a stale one");
        assertEquals("Workflow dispatched", res.getString("message"));
        assertEquals("main", r.args.size() > 0 ? "main" : "", "default ref");
        assertTrue(gh(new Recorder()).triggerWorkflow("o", "r", "ci.yml", null, "{nope").contains("invalid inputs JSON"));
    }

    @Test
    void triggerWorkflow_gitlabReturnsThePipelineIdAsTheHandle() {
        Recorder r = new Recorder();
        r.responses.put("gitlab_trigger_pipeline", "{\"id\":991,\"message\":\"ok\"}");
        JSONObject res = new JSONObject(gl(r).triggerWorkflow("o", "r", "ignored.yml", "dev", null));
        assertEquals(991, res.getInt("runId"));
        assertEquals("dev", r.args.get(0).get("ref"));
    }

    @Test
    void listRuns_filtersByRefClientSide_andNormalizesTheVerdict() {
        Recorder r = new Recorder();
        r.responses.put("github_list_workflow_runs", "{\"workflow_runs\":[{\"id\":1,\"head_branch\":\"main\",\"status\":\"completed\",\"conclusion\":\"success\",\"head_sha\":\"a\"},"
                + "{\"id\":2,\"head_branch\":\"dev\",\"status\":\"in_progress\",\"head_sha\":\"b\"}]}");
        JSONArray runs = new JSONObject(gh(r).listRuns("o", "r", "ci.yml", "main", null, "10")).getJSONArray("runs");
        assertEquals(1, runs.length());
        assertEquals("pass", runs.getJSONObject(0).getString("verdict"));
        assertEquals(10, r.args.get(0).get("perPage"));
    }

    // ---- registry ----

    @Test
    void aliasToolsAreRegisteredAdditivelyAndGithubGitlabNamesAreUntouched() {
        for (String t : new String[]{"scm_list_prs", "scm_get_pr", "scm_merge_pr", "scm_get_diff", "scm_add_labels", "scm_remove_label",
                "scm_add_pr_comment", "scm_get_pr_comments", "scm_create_comment", "scm_get_issue", "scm_search_issues", "scm_close_issue",
                "scm_list_branches", "scm_get_reviews", "scm_approve", "ci_trigger_workflow", "ci_list_runs", "ci_get_verdict", "ci_get_merge_state"}) {
            assertTrue(MCPToolRegistry.hasTool(t), t);
        }
        for (String t : new String[]{"github_get_pr", "gitlab_get_mr", "github_trigger_workflow", "gitlab_trigger_pipeline"}) {
            assertTrue(MCPToolRegistry.hasTool(t), t + " must keep its name");
        }
        assertEquals("scm", MCPToolRegistry.getTool("scm_get_pr").getIntegration());
        assertEquals("ci", MCPToolRegistry.getTool("ci_get_verdict").getIntegration());
    }
}
