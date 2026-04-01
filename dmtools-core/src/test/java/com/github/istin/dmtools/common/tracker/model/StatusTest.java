// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.tracker.model;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StatusTest {

    private Status status;

    @Before
    public void setUp() {
        status = new Status();
    }

    @Test
    public void testCreateFromName() {
        String name = "In Development";
        Status status = Status.createFromName(name);
        assertEquals(name, status.getName());
    }

    @Test
    public void testGetName() {
        status.set("name", "In Review");
        assertEquals("In Review", status.getName());
    }

    @Test
    public void testCreatePostObject() {
        status.set("id", "12345");
        JSONObject jsonObject = status.createPostObject();
        assertEquals("12345", jsonObject.getString("id"));
    }

    @Test
    public void testIsResolved() {
        status.set("name", Status.DONE);
        assertTrue(status.isResolved());
    }

    @Test
    public void testIsInDev() {
        status.set("name", Status.IN_DEVELOPMENT);
        assertTrue(status.isInDev());
    }

    @Test
    public void testIsRejected() {
        status.set("name", Status.REJECTED);
        assertTrue(status.isRejected());
    }

    @Test
    public void testIsImplementationRejected() {
        status.set("name", Status.IMPLEMENTATION_REJECTED);
        assertTrue(status.isImplementationRejected());
    }

    @Test
    public void testIsNotInScope() {
        status.set("name", "not in scope");
        assertTrue(status.isNotInScope());
    }

    @Test
    public void testIsTestingBlocked() {
        status.set("name", "Testing Blocked");
        assertTrue(status.isTestingBlocked());
    }

    @Test
    public void testIsDevelopmentBlocked() {
        status.set("name", "Development Blocked");
        assertTrue(status.isDevelopmentBlocked());
    }

    @Test
    public void testIsBlocked() {
        status.set("name", "Blocked");
        assertTrue(status.isBlocked());
    }

    @Test
    public void testIsQABlocked() {
        status.set("name", "QA Blocked");
        assertTrue(status.isQABlocked());
    }

    @Test
    public void testIsBABlocked() {
        status.set("name", "BA Blocked");
        assertTrue(status.isBABlocked());
    }

    @Test
    public void testIsInBusinessAnalysis() {
        status.set("name", Status.IN_BUSINESS_ANALYSIS);
        assertTrue(status.isInBusinessAnalysis());
    }

    @Test
    public void testIsInDiscovery() {
        status.set("name", Status.IN_ASSESSMENT);
        assertTrue(status.isInDiscovery());
    }

    @Test
    public void testIsReadyForAssessment() {
        status.set("name", Status.READY_FOR_ASSESSMENT);
        assertTrue(status.isReadyForAssessment());
    }

    @Test
    public void testIsInAssessment() {
        status.set("name", Status.IN_ASSESSMENT);
        assertTrue(status.isInAssessment());
    }

    @Test
    public void testIsReadyForDevelopment() {
        status.set("name", Status.READY_FOR_DEVELOPMENT);
        assertTrue(status.isReadyForDevelopment());
    }

    @Test
    public void testIsDesignAdjustments() {
        status.set("name", Status.DESIGN_ADJUSTMENTS);
        assertTrue(status.isDesignAdjustments());
    }

    @Test
    public void testIsInDevelopment() {
        status.set("name", Status.IN_DEVELOPMENT);
        assertTrue(status.isInDevelopment());
    }

    @Test
    public void testIsInGrooming() {
        status.set("name", Status.IN_GROOMING);
        assertTrue(status.isInGrooming());
    }

    @Test
    public void testIsInReview() {
        status.set("name", Status.IN_REVIEW);
        assertTrue(status.isInReview());
    }

    @Test
    public void testIsReview() {
        status.set("name", Status.REVIEW);
        assertTrue(status.isReview());
    }

    @Test
    public void testIsCodeReview() {
        status.set("name", Status.CODE_REVIEW);
        assertTrue(status.isCodeReview());
    }

    @Test
    public void testIsReadyForReview() {
        status.set("name", Status.READY_FOR_REVIEW);
        assertTrue(status.isReadyForReview());
    }

    @Test
    public void testIsMerged() {
        status.set("name", Status.MERGED);
        assertTrue(status.isMerged());
    }

    @Test
    public void testIsNew() {
        status.set("name", Status.NEW);
        assertTrue(status.isNew());
    }

    @Test
    public void testIsReopened() {
        status.set("name", Status.REOPENED);
        assertTrue(status.isReopened());
    }

    @Test
    public void testIsDraft() {
        status.set("name", Status.DRAFT);
        assertTrue(status.isDraft());
    }

    @Test
    public void testIsReadyForTesting() {
        status.set("name", Status.READY_FOR_TESTING);
        assertTrue(status.isReadyForTesting());
    }

    @Test
    public void testIsDone() {
        status.set("name", Status.DONE);
        assertTrue(status.isDone());
    }

    @Test
    public void testIsCompleted() {
        status.set("name", Status.COMPLETED);
        assertTrue(status.isCompleted());
    }

    @Test
    public void testIsReadyForTest() {
        status.set("name", Status.READY_FOR_TEST);
        assertTrue(status.isReadyForTest());
    }

    @Test
    public void testIsReadyForRetest() {
        status.set("name", Status.READY_FOR_RETEST);
        assertTrue(status.isReadyForRetest());
    }

    @Test
    public void testIsDevelopmentCompleted() {
        status.set("name", Status.DEVELOPMENT_COMPLETED);
        assertTrue(status.isDevelopmentCompleted());
    }

    @Test
    public void testIsBacklog() {
        status.set("name", Status.BACKLOG);
        assertTrue(status.isBacklog());
    }

    @Test
    public void testIsReadyForQA() {
        status.set("name", Status.IN_QA);
        assertTrue(status.isReadyForQA());
    }

    @Test
    public void testIsDevComplete() {
        status.set("name", Status.IN_VALIDATION);
        assertTrue(status.isDevComplete());
    }

    @Test
    public void testIsOpen() {
        status.set("name", "Open");
        assertTrue(status.isOpen());
    }

    @Test
    public void testIsInIntegration() {
        status.set("name", Status.IN_INTEGRATION);
        assertTrue(status.isInIntegration());
    }

    @Test
    public void testIsImplementationAccepted() {
        status.set("name", Status.IMPLEMENTATION_ACCEPTED);
        assertTrue(status.isImplementationAccepted());
    }

    @Test
    public void testIsReadyForAcceptance() {
        status.set("name", Status.READY_FOR_ACCEPTANCE);
        assertTrue(status.isReadyForAcceptance());
    }

    @Test
    public void testIsInAcceptance() {
        status.set("name", Status.IN_ACCEPTANCE);
        assertTrue(status.isInAcceptance());
    }

    @Test
    public void testIsAccepted() {
        status.set("name", Status.ACCEPTED);
        assertTrue(status.isAccepted());
    }

    @Test
    public void testIsRelease() {
        status.set("name", Status.RELEASE);
        assertTrue(status.isRelease());
    }

    @Test
    public void testIsIntegrationDone() {
        status.set("name", Status.INTEGRATION_DONE);
        assertTrue(status.isIntegrationDone());
    }

    @Test
    public void testIsCancelled() {
        status.set("name", Status.CANCELLED);
        assertTrue(status.isCancelled());
    }

    @Test
    public void testIsInTesting() {
        status.set("name", Status.IN_TESTING);
        assertTrue(status.isInTesting());
    }

    @Test
    public void testIsTesting() {
        status.set("name", Status.TESTING);
        assertTrue(status.isTesting());
    }

    @Test
    public void testIsInProgress() {
        status.set("name", Status.IN_PROGRESS);
        assertTrue(status.isInProgress());
    }

    @Test
    public void testIsOnHold() {
        status.set("name", Status.ON_HOLD);
        assertTrue(status.isOnHold());
    }
}