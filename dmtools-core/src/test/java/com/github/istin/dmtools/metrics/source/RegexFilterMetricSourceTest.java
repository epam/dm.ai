// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for titleRegex (PR sources) and branchNameRegex (commit sources).
 */
public class RegexFilterMetricSourceTest {

    private SourceCode sourceCode;
    private IEmployees employees;

    @Before
    public void setUp() {
        sourceCode = mock(SourceCode.class);
        employees = mock(IEmployees.class);
        when(employees.transformName(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(employees.contains(anyString())).thenReturn(true);
        when(employees.isBot(anyString())).thenReturn(false);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private IPullRequest mockPR(String title, String author) throws Exception {
        IPullRequest pr = mock(IPullRequest.class);
        IUser user = mock(IUser.class);
        when(user.getFullName()).thenReturn(author);
        when(pr.getAuthor()).thenReturn(user);
        when(pr.getTitle()).thenReturn(title);
        when(pr.getId()).thenReturn(Math.abs(title != null ? title.hashCode() : 0));
        when(pr.getClosedDate()).thenReturn(System.currentTimeMillis());
        return pr;
    }

    private ICommit mockCommit(String hash, String author) {
        ICommit commit = mock(ICommit.class);
        IUser user = mock(IUser.class);
        when(user.getFullName()).thenReturn(author);
        when(commit.getAuthor()).thenReturn(user);
        when(commit.getHash()).thenReturn(hash);
        when(commit.getCommitterDate()).thenReturn(Calendar.getInstance());
        return commit;
    }

    private ITag mockBranch(String name) {
        ITag branch = mock(ITag.class);
        when(branch.getName()).thenReturn(name);
        return branch;
    }

    // ── PR titleRegex tests ──────────────────────────────────────────────────

    @Test
    public void testPullRequestsMetricSource_noRegex_returnsAll() throws Exception {
        IPullRequest pr1 = mockPR("feat(auth): add login", "Alice");
        IPullRequest pr2 = mockPR("fix: typo", "Bob");
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Arrays.asList(pr1, pr2));

        PullRequestsMetricSource source = new PullRequestsMetricSource("ws", "repo", sourceCode, employees, null);
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(2, result.size());
    }

    @Test
    public void testPullRequestsMetricSource_titleRegex_filtersMatching() throws Exception {
        IPullRequest pr1 = mockPR("feat(auth): add login", "Alice");
        IPullRequest pr2 = mockPR("fix: typo", "Bob");
        IPullRequest pr3 = mockPR("feat(ui): new dashboard", "Carol");
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Arrays.asList(pr1, pr2, pr3));

        PullRequestsMetricSource source = new PullRequestsMetricSource("ws", "repo", sourceCode, employees, null, "^feat\\(");
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(kt -> kt.getSummary() != null && kt.getSummary().contains("typo")));
    }

    @Test
    public void testPullRequestsMetricSource_nullTitle_doesNotThrow() throws Exception {
        IPullRequest pr = mockPR(null, "Alice");
        when(pr.getTitle()).thenReturn(null);
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Collections.singletonList(pr));

        PullRequestsMetricSource source = new PullRequestsMetricSource("ws", "repo", sourceCode, employees, null, "feat");
        // Null title treated as empty string — should not match "feat"
        List<KeyTime> result = source.performSourceCollection(false, "metric");
        assertEquals(0, result.size());
    }

    @Test
    public void testPullRequestsMergedBy_titleRegex_filtersMatching() throws Exception {
        IPullRequest pr1 = mockPR("feat: big feature", "Alice");
        IPullRequest pr2 = mockPR("chore: cleanup", "Bob");
        IUser alice = pr1.getAuthor();
        IUser bob = pr2.getAuthor();
        when(pr1.getMergedBy()).thenReturn(alice);
        when(pr2.getMergedBy()).thenReturn(bob);
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Arrays.asList(pr1, pr2));

        PullRequestsMergedByMetricSource source = new PullRequestsMergedByMetricSource(
                "ws", "repo", sourceCode, employees, null, "^feat");
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getSummary().startsWith("feat"));
    }

    @Test
    public void testPullRequestsDeclined_titleRegex_filtersMatching() throws Exception {
        IPullRequest pr1 = mockPR("TICKET-123: implement feature", "Alice");
        IPullRequest pr2 = mockPR("chore: minor fix", "Bob");
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Arrays.asList(pr1, pr2));

        PullRequestsDeclinedMetricSource source = new PullRequestsDeclinedMetricSource(
                "ws", "repo", sourceCode, employees, null, "TICKET-\\d+");
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(1, result.size());
    }

    @Test
    public void testPullRequestsApprovals_noRegex_allPRsProcessed() throws Exception {
        IPullRequest pr1 = mockPR("feature A", "Alice");
        IPullRequest pr2 = mockPR("feature B", "Bob");
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Arrays.asList(pr1, pr2));
        when(sourceCode.pullRequestActivities(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        PullRequestsApprovalsMetricSource source = new PullRequestsApprovalsMetricSource(
                "ws", "repo", sourceCode, employees, null);
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(0, result.size()); // no activities → no approvals
        // But both PRs were iterated; verify activities fetched for both
        verify(sourceCode, times(2)).pullRequestActivities(anyString(), anyString(), anyString());
    }

    @Test
    public void testPullRequestsApprovals_withTitleRegex_onlyMatchingPRsFetched() throws Exception {
        IPullRequest pr1 = mockPR("release/2.0: deploy", "Alice");
        IPullRequest pr2 = mockPR("chore: lint", "Bob");
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Arrays.asList(pr1, pr2));
        when(sourceCode.pullRequestActivities(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        PullRequestsApprovalsMetricSource source = new PullRequestsApprovalsMetricSource(
                "ws", "repo", sourceCode, employees, null, "^release/");
        source.performSourceCollection(false, "metric");

        // Only pr1 matches — activities should only be fetched for it
        verify(sourceCode, times(1)).pullRequestActivities(anyString(), anyString(), anyString());
    }

    // ── Commits branchNameRegex tests ─────────────────────────────────────────

    @Test
    public void testSourceCodeCommitsMetricSource_noBranchRegex_usesBranch() throws Exception {
        ICommit c1 = mockCommit("abc", "Alice");
        when(sourceCode.getCommitsFromBranch("ws", "repo", "main", null, null))
                .thenReturn(Collections.singletonList(c1));

        SourceCodeCommitsMetricSource source = new SourceCodeCommitsMetricSource(
                "ws", "repo", "main", null, sourceCode, employees);
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(1, result.size());
        verify(sourceCode, never()).getBranches(anyString(), anyString());
    }

    @Test
    public void testSourceCodeCommitsMetricSource_branchNameRegex_fetchesFromMatchingBranches() throws Exception {
        ITag branchFeatureA = mockBranch("feature/auth");
        ITag branchFeatureB = mockBranch("feature/ui");
        ITag branchMain = mockBranch("main");
        when(sourceCode.getBranches("ws", "repo"))
                .thenReturn(Arrays.asList(branchFeatureA, branchFeatureB, branchMain));
        ICommit c1 = mockCommit("hash1", "Alice");
        ICommit c2 = mockCommit("hash2", "Bob");
        when(sourceCode.getCommitsFromBranch("ws", "repo", "feature/auth", null, null))
                .thenReturn(Collections.singletonList(c1));
        when(sourceCode.getCommitsFromBranch("ws", "repo", "feature/ui", null, null))
                .thenReturn(Collections.singletonList(c2));

        SourceCodeCommitsMetricSource source = new SourceCodeCommitsMetricSource(
                "ws", "repo", null, null, sourceCode, employees, "^feature/");
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(2, result.size());
        verify(sourceCode, never()).getCommitsFromBranch("ws", "repo", "main", null, null);
    }

    @Test
    public void testSourceCodeCommitsMetricSource_branchNameRegex_deduplicatesCommits() throws Exception {
        ITag b1 = mockBranch("release/1.0");
        ITag b2 = mockBranch("release/2.0");
        when(sourceCode.getBranches("ws", "repo")).thenReturn(Arrays.asList(b1, b2));
        ICommit shared = mockCommit("shared-hash", "Alice");
        ICommit unique = mockCommit("unique-hash", "Bob");
        // Both branches share one commit
        when(sourceCode.getCommitsFromBranch("ws", "repo", "release/1.0", null, null))
                .thenReturn(Arrays.asList(shared, unique));
        when(sourceCode.getCommitsFromBranch("ws", "repo", "release/2.0", null, null))
                .thenReturn(Collections.singletonList(shared));

        SourceCodeCommitsMetricSource source = new SourceCodeCommitsMetricSource(
                "ws", "repo", null, null, sourceCode, employees, "^release/");
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        // shared-hash counted only once
        assertEquals(2, result.size());
    }

    @Test
    public void testSourceCodeCommitsMetricSource_branchNameRegex_noMatchReturnsEmpty() throws Exception {
        ITag main = mockBranch("main");
        ITag dev = mockBranch("develop");
        when(sourceCode.getBranches("ws", "repo")).thenReturn(Arrays.asList(main, dev));

        SourceCodeCommitsMetricSource source = new SourceCodeCommitsMetricSource(
                "ws", "repo", null, null, sourceCode, employees, "^hotfix/");
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(0, result.size());
        verify(sourceCode, never()).getCommitsFromBranch(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    public void testPullRequestsLOCMetricSource_branchNameRegex_aggregatesFromMatchingBranches() throws Exception {
        ITag b1 = mockBranch("feature/a");
        ITag b2 = mockBranch("main");
        when(sourceCode.getBranches("ws", "repo")).thenReturn(Arrays.asList(b1, b2));
        ICommit c = mockCommit("abc123", "Alice");
        when(sourceCode.getCommitsFromBranch("ws", "repo", "feature/a", null, null))
                .thenReturn(Collections.singletonList(c));
        when(sourceCode.getCommitDiffStat(anyString(), anyString(), anyString())).thenReturn(null);

        PullRequestsLOCMetricSource source = new PullRequestsLOCMetricSource(
                "ws", "repo", null, null, sourceCode, employees, "^feature/");
        List<KeyTime> result = source.performSourceCollection(false, "metric");

        assertEquals(1, result.size());
        verify(sourceCode, never()).getCommitsFromBranch("ws", "repo", "main", null, null);
    }

    // ── Shared PR cache tests ─────────────────────────────────────────────────

    @Test
    public void testSharedPrList_pullRequestsFetchedOnlyOnce_acrossMultipleSources() throws Exception {
        IPullRequest pr = mockPR("feat: shared cache test", "Alice");
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(Collections.singletonList(pr));

        java.util.concurrent.atomic.AtomicReference<List<IPullRequest>> sharedRef =
                new java.util.concurrent.atomic.AtomicReference<>(null);

        PullRequestsMetricSource s1 = new PullRequestsMetricSource(
                "ws", "repo", sourceCode, employees, null, null, sharedRef);
        PullRequestsMergedByMetricSource s2 = new PullRequestsMergedByMetricSource(
                "ws", "repo", sourceCode, employees, null, null, sharedRef);
        PullRequestsApprovalsMetricSource s3 = new PullRequestsApprovalsMetricSource(
                "ws", "repo", sourceCode, employees, null, null, sharedRef);

        when(sourceCode.pullRequestActivities(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(pr.getMergedBy()).thenReturn(null);

        s1.performSourceCollection(false, "m1");
        s2.performSourceCollection(false, "m2");
        s3.performSourceCollection(false, "m3");

        // Despite 3 sources all consuming the PR list, the API is called only once
        verify(sourceCode, times(1))
                .pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    public void testSharedPrList_declinedUsesOwnCache_separateFromMerged() throws Exception {
        IPullRequest mergedPR = mockPR("feat: merged", "Alice");
        IPullRequest declinedPR = mockPR("feat: declined", "Bob");

        when(sourceCode.pullRequests(eq("ws"), eq("repo"),
                eq(com.github.istin.dmtools.common.model.IPullRequest.PullRequestState.STATE_MERGED),
                anyBoolean(), any()))
                .thenReturn(Collections.singletonList(mergedPR));
        when(sourceCode.pullRequests(eq("ws"), eq("repo"),
                eq(com.github.istin.dmtools.common.model.IPullRequest.PullRequestState.STATE_DECLINED),
                anyBoolean(), any()))
                .thenReturn(Collections.singletonList(declinedPR));

        java.util.concurrent.atomic.AtomicReference<List<IPullRequest>> mergedRef =
                new java.util.concurrent.atomic.AtomicReference<>(null);
        java.util.concurrent.atomic.AtomicReference<List<IPullRequest>> declinedRef =
                new java.util.concurrent.atomic.AtomicReference<>(null);

        PullRequestsMetricSource merged = new PullRequestsMetricSource(
                "ws", "repo", sourceCode, employees, null, null, mergedRef);
        PullRequestsDeclinedMetricSource declined = new PullRequestsDeclinedMetricSource(
                "ws", "repo", sourceCode, employees, null, null, declinedRef);

        List<KeyTime> mergedResult = merged.performSourceCollection(false, "merged");
        List<KeyTime> declinedResult = declined.performSourceCollection(false, "declined");

        assertEquals(1, mergedResult.size());
        assertEquals(1, declinedResult.size());
        // Each state fetches exactly once
        verify(sourceCode, times(1)).pullRequests(eq("ws"), eq("repo"),
                eq(IPullRequest.PullRequestState.STATE_MERGED), anyBoolean(), any());
        verify(sourceCode, times(1)).pullRequests(eq("ws"), eq("repo"),
                eq(IPullRequest.PullRequestState.STATE_DECLINED), anyBoolean(), any());
    }
}
