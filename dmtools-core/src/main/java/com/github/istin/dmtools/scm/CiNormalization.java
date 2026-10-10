// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.scm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CI-verdict and merge-state normalization for the vendor-neutral {@code scm_*} / {@code ci_*} alias
 * layer (epam/dm.ai#630, mirror of dmtools-dart gh-339 {@code ci_normalization.dart}).
 *
 * <p>The alias layer returns ONLY the pinned enums — callers never see raw provider values:
 * <ul>
 *   <li>verdict: {@code pass | fail | pending | none}; anything unmappable is {@code pending}
 *       (never a crash, never a silent pass);</li>
 *   <li>merge state: {@code CLEAN | BEHIND | DIRTY | BLOCKED | UNKNOWN}; checks-settling transients
 *       are {@code BLOCKED} + a reason.</li>
 * </ul>
 * Rollup across several checks of one head: any fail wins, otherwise any pending, otherwise (at least
 * one pass) pass, no evidence at all is none.
 */
public final class CiNormalization {

    public static final String STATE_CLEAN = "CLEAN";
    public static final String STATE_BEHIND = "BEHIND";
    public static final String STATE_DIRTY = "DIRTY";
    public static final String STATE_BLOCKED = "BLOCKED";
    public static final String STATE_UNKNOWN = "UNKNOWN";

    public static final String VERDICT_PASS = "pass";
    public static final String VERDICT_FAIL = "fail";
    public static final String VERDICT_PENDING = "pending";
    public static final String VERDICT_NONE = "none";

    private static final Set<String> GH_FAIL = new HashSet<>(Arrays.asList("failure", "timed_out"));
    private static final Set<String> GL_FAIL = new HashSet<>(Arrays.asList("failed", "fail"));
    private static final Set<String> IN_FLIGHT = new HashSet<>(Arrays.asList("queued", "in_progress", "waiting", "pending"));

    private static final Map<String, String> GH_SIMPLE = Map.of(
            "clean", STATE_CLEAN, "dirty", STATE_DIRTY, "behind", STATE_BEHIND, "unknown", STATE_UNKNOWN);
    private static final Map<String, String> GH_BLOCKED = Map.of(
            "blocked", "required-checks-pending", "has_hooks", "has-hooks", "draft", "draft", "unstable", "unstable");
    private static final Map<String, String> GL_BLOCKED = Map.of(
            "blocked", "branch-protection", "ci_still_running", "ci-still-running", "ci_must_pass", "ci-must-pass",
            "discussions_not_resolved", "discussions-not-resolved", "draft_status", "draft", "pinned_thread", "pinned-thread");
    private static final Map<String, String> GL_DETAILED = Map.of(
            "checking", STATE_UNKNOWN, "has_conflicts", STATE_DIRTY, "broken_status", STATE_DIRTY);
    private static final Map<String, String> GL_MERGE_STATUS = Map.of(
            "can_be_merged", STATE_CLEAN, "cannot_be_merged", STATE_DIRTY, "cannot_be_merged_rechecking", STATE_DIRTY);

    private CiNormalization() {
    }

    private static String norm(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** One GitHub check-run / Actions-run conclusion to a verdict (in-flight and unknown = pending). */
    public static String ghConclusionVerdict(String raw) {
        String c = norm(raw);
        if (GH_FAIL.contains(c)) return VERDICT_FAIL;
        if ("success".equals(c)) return VERDICT_PASS;
        return VERDICT_PENDING;
    }

    /** A check-run with no conclusion: in-flight = pending, anything else = no evidence (null). */
    private static String statusVerdict(String status) {
        String s = norm(status);
        return (s.isEmpty() || IN_FLIGHT.contains(s)) ? VERDICT_PENDING : null;
    }

    /** Rollup of a decoded {@code check_runs} array to one verdict; empty = none. */
    public static String ghCheckRunsVerdict(JSONArray runs) {
        java.util.List<String> verdicts = new java.util.ArrayList<>();
        for (int i = 0; i < (runs == null ? 0 : runs.length()); i++) {
            JSONObject run = runs.optJSONObject(i);
            String conclusion = run == null || run.isNull("conclusion") ? null : run.optString("conclusion", null);
            String status = run == null ? null : run.optString("status", null);
            verdicts.add(conclusion == null ? statusVerdict(status) : ghConclusionVerdict(conclusion));
        }
        return rollup(verdicts);
    }

    /** One Actions run: a non-completed run has no verdict yet. */
    public static String ghRunVerdict(String status, String conclusion) {
        return "completed".equals(norm(status)) ? ghConclusionVerdict(conclusion) : VERDICT_PENDING;
    }

    /** One GitLab pipeline/job/commit status to a verdict. */
    public static String gitlabStatusVerdict(String raw) {
        String s = norm(raw);
        if (GL_FAIL.contains(s)) return VERDICT_FAIL;
        if ("success".equals(s)) return VERDICT_PASS;
        return VERDICT_PENDING;
    }

    /** Rollup of a GitLab status list ({@code [{status: ...}]}); empty = none. */
    public static String gitlabStatusListVerdict(JSONArray items) {
        java.util.List<String> verdicts = new java.util.ArrayList<>();
        for (int i = 0; i < (items == null ? 0 : items.length()); i++) {
            JSONObject item = items.optJSONObject(i);
            verdicts.add(gitlabStatusVerdict(item == null ? null : item.optString("status", null)));
        }
        return rollup(verdicts);
    }

    private static String rollup(Iterable<String> verdicts) {
        boolean sawPass = false;
        boolean sawPending = false;
        for (String v : verdicts) {
            if (VERDICT_FAIL.equals(v)) return VERDICT_FAIL;
            if (VERDICT_PENDING.equals(v)) sawPending = true;
            else if (VERDICT_PASS.equals(v)) sawPass = true;
        }
        if (sawPending) return VERDICT_PENDING;
        return sawPass ? VERDICT_PASS : VERDICT_NONE;
    }

    /** Normalized merge state + optional machine reason (BLOCKED transients). */
    public static JSONObject mergeState(String state, String reason) {
        JSONObject o = new JSONObject().put("mergeState", state);
        if (reason != null) o.put("reason", reason);
        return o;
    }

    /**
     * GitHub merge state from a PR body ({@code mergeable}, REST {@code mergeable_state}, GraphQL
     * {@code mergeStateStatus}). {@code mergeable == false} is the deterministic DIRTY override;
     * {@code blocked} stays BLOCKED (masking it as CLEAN deadlocked armed fresh PRs, dart #195);
     * {@code unknown} (mergeability still computing) is UNKNOWN, never an optimistic CLEAN.
     */
    public static JSONObject ghMergeState(Boolean mergeable, String mergeableState, String mergeStateStatus) {
        if (Boolean.FALSE.equals(mergeable)) return mergeState(STATE_DIRTY, null);
        String raw = mergeStateStatus != null ? mergeStateStatus
                : (mergeableState != null && !mergeableState.isEmpty() ? mergeableState.toUpperCase(Locale.ROOT) : "");
        String token = norm(raw);
        String simple = GH_SIMPLE.get(token);
        if (simple != null) return mergeState(simple, null);
        String blocked = GH_BLOCKED.get(token);
        if (blocked != null) return mergeState(STATE_BLOCKED, blocked);
        return mergeState(Boolean.TRUE.equals(mergeable) ? STATE_CLEAN : STATE_UNKNOWN, null);
    }

    /** GitLab merge state from an MR body ({@code merge_status}, {@code detailed_merge_status}, {@code has_conflicts}). */
    public static JSONObject gitlabMergeState(String mergeStatus, String detailedMergeStatus, Boolean hasConflicts) {
        if ("not_open".equals(norm(mergeStatus))) return mergeState(STATE_UNKNOWN, null);
        if (Boolean.TRUE.equals(hasConflicts)) return mergeState(STATE_DIRTY, null);
        String detailed = norm(detailedMergeStatus);
        String blocked = GL_BLOCKED.get(detailed);
        if (blocked != null) return mergeState(STATE_BLOCKED, blocked);
        String state = GL_DETAILED.get(detailed);
        if (state == null) state = GL_MERGE_STATUS.get(norm(mergeStatus));
        return mergeState(state != null ? state : STATE_UNKNOWN, null);
    }
}
