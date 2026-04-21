// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class RelationshipTest {

    private IssueLink issueLink;

    @Before
    public void setUp() {
        issueLink = mock(IssueLink.class);
    }

    @Test
    public void testIsFixedInOrImplementedIn_FixedIn() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.FIXED_IN);
        assertTrue(Relationship.isFixedInOrImplementedIn(issueLink));
    }

    @Test
    public void testIsFixedInOrImplementedIn_ImplementedIn() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.IS_IMPLEMENTED_IN);
        assertTrue(Relationship.isFixedInOrImplementedIn(issueLink));
    }

    @Test
    public void testIsFixedInOrImplementedIn_CausedByWithInwardIssue() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.IS_CAUSED_BY);
        Mockito.when(issueLink.getInwardIssue()).thenReturn(mock(Ticket.class));
        assertTrue(Relationship.isFixedInOrImplementedIn(issueLink));
    }

    @Test
    public void testIsFixedInOrImplementedIn_CausedByWithOutwardIssue() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.IS_CAUSED_BY);
        Mockito.when(issueLink.getOutwardIssue()).thenReturn(mock(Ticket.class));
        assertTrue(Relationship.isFixedInOrImplementedIn(issueLink));
    }

    @Test
    public void testIsImplements_Implements() {
        Mockito.when(issueLink.getOutwardType()).thenReturn("implements");
        Mockito.when(issueLink.getOutwardIssue()).thenReturn(mock(Ticket.class));
        assertTrue(Relationship.isImplements(issueLink));
    }

    @Test
    public void testIsImplements_Fixes() {
        Mockito.when(issueLink.getOutwardType()).thenReturn("fixes");
        Mockito.when(issueLink.getOutwardIssue()).thenReturn(mock(Ticket.class));
        assertTrue(Relationship.isImplements(issueLink));
    }

    @Test
    public void testIsImplements_Causes() {
        Mockito.when(issueLink.getOutwardType()).thenReturn("causes");
        Mockito.when(issueLink.getOutwardIssue()).thenReturn(mock(Ticket.class));
        assertTrue(Relationship.isImplements(issueLink));
    }

    @Test
    public void testIsBlockedBy() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.IS_BLOCKED_BY);
        assertTrue(Relationship.isBlockedBy(issueLink));
    }

    @Test
    public void testRelatesTo() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.RELATE);
        assertTrue(Relationship.relatesTo(issueLink));
    }

    @Test
    public void testIsBlockerForCurrentIssue_BrokenBy() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.IS_BROKEN_BY);
        assertTrue(Relationship.isBlockerForCurrentIssue(issueLink));
    }

    @Test
    public void testIsBlockerForCurrentIssue_BlockedBy() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.IS_BLOCKED_BY);
        assertTrue(Relationship.isBlockerForCurrentIssue(issueLink));
    }

    @Test
    public void testIsLinkedTo_LinkedTo() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.LINKED_TO);
        assertTrue(Relationship.isLinkedTo(issueLink));
    }

    @Test
    public void testIsLinkedTo_LinkedWith() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.LINKED_WITH);
        assertTrue(Relationship.isLinkedTo(issueLink));
    }

    @Test
    public void testIsFixedInOrImplementedIn_NotFixedOrImplemented() {
        Mockito.when(issueLink.getInwardType()).thenReturn("other");
        assertFalse(Relationship.isFixedInOrImplementedIn(issueLink));
    }

    @Test
    public void testIsImplements_NotImplements() {
        Mockito.when(issueLink.getOutwardType()).thenReturn("other");
        assertFalse(Relationship.isImplements(issueLink));
    }

    @Test
    public void testIsBlockedBy_NotBlockedBy() {
        Mockito.when(issueLink.getInwardType()).thenReturn("other");
        assertFalse(Relationship.isBlockedBy(issueLink));
    }

    @Test
    public void testRelatesTo_NotRelates() {
        Mockito.when(issueLink.getInwardType()).thenReturn("other");
        assertFalse(Relationship.relatesTo(issueLink));
    }

    @Test
    public void testIsBlockerForCurrentIssue_NotBlocker() {
        Mockito.when(issueLink.getInwardType()).thenReturn("other");
        assertFalse(Relationship.isBlockerForCurrentIssue(issueLink));
    }

    @Test
    public void testIsLinkedTo_NotLinked() {
        Mockito.when(issueLink.getInwardType()).thenReturn("other");
        assertFalse(Relationship.isLinkedTo(issueLink));
    }

    @Test
    public void testIsClonedBy_InwardIsClonedBy() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.IS_CLONED_BY);
        Mockito.when(issueLink.getOutwardType()).thenReturn("clones");
        assertTrue(Relationship.isClonedBy(issueLink));
    }

    @Test
    public void testIsClonedBy_OutwardClones() {
        Mockito.when(issueLink.getInwardType()).thenReturn("is cloned by");
        Mockito.when(issueLink.getOutwardType()).thenReturn(Relationship.CLONES);
        assertTrue(Relationship.isClonedBy(issueLink));
    }

    @Test
    public void testIsClonedBy_CaseInsensitive() {
        Mockito.when(issueLink.getInwardType()).thenReturn("IS CLONED BY");
        Mockito.when(issueLink.getOutwardType()).thenReturn("CLONES");
        assertTrue(Relationship.isClonedBy(issueLink));
    }

    @Test
    public void testIsClonedBy_NotClone() {
        Mockito.when(issueLink.getInwardType()).thenReturn(Relationship.RELATES_TO);
        Mockito.when(issueLink.getOutwardType()).thenReturn(Relationship.RELATES_TO);
        assertFalse(Relationship.isClonedBy(issueLink));
    }
}