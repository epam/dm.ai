// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.scm;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static com.github.istin.dmtools.scm.CiNormalization.*;
import static org.junit.jupiter.api.Assertions.*;

class CiNormalizationTest {

    private static JSONArray arr(String json) {
        return new JSONArray(json);
    }

    @Test
    void githubConclusions() {
        assertEquals(VERDICT_PASS, ghConclusionVerdict("success"));
        assertEquals(VERDICT_FAIL, ghConclusionVerdict("failure"));
        assertEquals(VERDICT_FAIL, ghConclusionVerdict("TIMED_OUT"));
        assertEquals(VERDICT_PENDING, ghConclusionVerdict("cancelled"));
        assertEquals(VERDICT_PENDING, ghConclusionVerdict("skipped"));
        assertEquals(VERDICT_PENDING, ghConclusionVerdict(null));
    }

    @Test
    void githubRunVerdictNeedsCompletedStatus() {
        assertEquals(VERDICT_PENDING, ghRunVerdict("in_progress", "success"));
        assertEquals(VERDICT_PASS, ghRunVerdict("completed", "success"));
        assertEquals(VERDICT_FAIL, ghRunVerdict("completed", "failure"));
    }

    @Test
    void checkRunRollup_failWins_thenPending_thenPass_emptyIsNone() {
        assertEquals(VERDICT_FAIL, ghCheckRunsVerdict(arr("[{\"conclusion\":\"success\"},{\"conclusion\":\"failure\"},{\"status\":\"queued\"}]")));
        assertEquals(VERDICT_PENDING, ghCheckRunsVerdict(arr("[{\"conclusion\":\"success\"},{\"status\":\"in_progress\"}]")));
        assertEquals(VERDICT_PASS, ghCheckRunsVerdict(arr("[{\"conclusion\":\"success\"},{\"conclusion\":\"success\"}]")));
        assertEquals(VERDICT_NONE, ghCheckRunsVerdict(arr("[]")));
        assertEquals(VERDICT_NONE, ghCheckRunsVerdict(null));
    }

    @Test
    void gitlabStatusesAndRollup() {
        assertEquals(VERDICT_PASS, gitlabStatusVerdict("success"));
        assertEquals(VERDICT_FAIL, gitlabStatusVerdict("failed"));
        assertEquals(VERDICT_FAIL, gitlabStatusVerdict("fail"));
        assertEquals(VERDICT_PENDING, gitlabStatusVerdict("running"));
        assertEquals(VERDICT_FAIL, gitlabStatusListVerdict(arr("[{\"status\":\"success\"},{\"status\":\"failed\"}]")));
        assertEquals(VERDICT_NONE, gitlabStatusListVerdict(arr("[]")));
    }

    @Test
    void githubMergeStateMapping() {
        assertEquals("DIRTY", ghMergeState(false, "clean", null).getString("mergeState"));
        assertEquals("CLEAN", ghMergeState(true, "clean", null).getString("mergeState"));
        assertEquals("BEHIND", ghMergeState(null, null, "BEHIND").getString("mergeState"));
        JSONObject blocked = ghMergeState(true, null, "BLOCKED");
        assertEquals("BLOCKED", blocked.getString("mergeState"));
        assertEquals("required-checks-pending", blocked.getString("reason"));
        assertEquals("UNKNOWN", ghMergeState(true, "unknown", null).getString("mergeState"), "computing mergeability is never an optimistic CLEAN");
        assertEquals("UNKNOWN", ghMergeState(null, null, null).getString("mergeState"));
        assertEquals("CLEAN", ghMergeState(true, "weird", null).getString("mergeState"));
    }

    @Test
    void gitlabMergeStateMapping() {
        assertEquals("UNKNOWN", gitlabMergeState("not_open", null, null).getString("mergeState"));
        assertEquals("DIRTY", gitlabMergeState("can_be_merged", null, true).getString("mergeState"));
        JSONObject running = gitlabMergeState("can_be_merged", "ci_still_running", false);
        assertEquals("BLOCKED", running.getString("mergeState"));
        assertEquals("ci-still-running", running.getString("reason"));
        assertEquals("CLEAN", gitlabMergeState("can_be_merged", null, false).getString("mergeState"));
        assertEquals("UNKNOWN", gitlabMergeState(null, "checking", null).getString("mergeState"));
        assertEquals("UNKNOWN", gitlabMergeState(null, null, null).getString("mergeState"));
    }
}
