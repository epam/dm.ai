// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.tracker.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Status extends JSONModel {
    public static final String READY_FOR_DEVELOPMENT = "Ready For Development";
    public static final String IN_DEVELOPMENT = "In Development";
    public static final String IN_REFINEMENT = "In refinement";
    public static final String IN_REVIEW = "In Review";
    public static final String IN_GROOMING = "In Grooming";
    public static final String MERGED = "Merged";
    public static final String READY_FOR_TESTING = "Ready for Testing";
    public static final String BUG_TO_FIX = "Bug to fix";
    public static final String IN_SYSTEM_TEST = "In System Test";
    public static final String RESOLVED = "Resolved";
    public static final String CLOSED = "Closed";
    public static final String IN_QA = "In QA";
    public static final String DRAFT = "Draft";
    public static final String WAITING_FOR_DESIGN = "Waiting For Design";
    public static final String IN_BUSINESS_ANALYSIS = "In Business Analysis";
    public static final String BA_BLOCKED = "BA Blocked";
    public static final String DONE = "Done";
    public static final String COMPLETED = "Completed";
    public static final String REJECTED = "Rejected";
    public static final String DEFINED = "Defined";
    public static final String READY_FOR_RETEST = "Ready for retest";
    public static final String READY_FOR_TEST = "Ready for Test";
    public static final String READY_FOR_INTEGRATION = "Ready For Integration";
    public static final String READY_FOR_ACCEPTANCE = "Ready for Acceptance";
    public static final String MEASUREMENTS_PLANNING = "Measurements Planning";
    public static final String IN_INTEGRATION = "In Integration";
    public static final String IN_TESTING = "In Testing";
    public static final String ON_HOLD = "On hold";
    public static final String INTEGRATION_DONE = "Integration Done";
    public static final String READY_FOR_RELEASE = "READY FOR RELEASE";
    public static final String IMPLEMENTATION_ACCEPTED = "Implementation accepted";
    public static final String IMPLEMENTATION_REJECTED = "Implementation Rejected";
    public static final String PRODUCT_ACCEPTANCE_BLOCKED = "Product Acceptance Blocked";
    public static final String INTEGRATION_BLOCKED = "Integration Blocked";
    public static final String BLOCKED = "blocked";
    public static final String IN_VALIDATION = "In Validation";
    public static final String BUSINESS_VALIDATION = "Business Validation";

    public static final String BUSINESS_ANALYSIS = "Business Analysis";
    public static final String WAITING_FOR_VISUAL_DESIGN = "Waiting For Visual Design";
    public static final String WAITING_FOR_VISUAL_DESIGN_APPROVE = "Waiting For Visual Design Approve";
    public static final String WAITING_FOR_PO_APPROVAL = "Waiting For PO Approval";
    public static final String DESIGN_ADJUSTMENTS = "Design adjustments";
    public static final String APPROVED = "Approved";
    //Workflow value
    public static final String DEVELOPMENT_COMPLETE = "Development Complete";
    //Status value
    public static final String DEVELOPMENT_COMPLETED = "Development Completed";
    public static final String QA_BLOCKED = "QA Blocked";
    public static final String TESTING_BLOCKED = "Testing Blocked";
    public static final String NEW = "new";
    public static final String READY_FOR_ASSESSMENT = "ready for assessment";
    public static final String IN_ASSESSMENT = "In Assessment";
    public static final String REOPENED = "Reopened";
    public static final String DEVELOPMENT_BLOCKED = "Development Blocked";
    public static final String BACKLOG = "Backlog";
    public static final String IN_DESIGN = "In Design";
    public static final String CODE_REVIEW = "Code review";
    public static final String CANCELLED = "Cancelled";
    public static final String IN_ACCEPTANCE = "In Acceptance";
    public static final String ACCEPTED = "Accepted";
    public static final String RELEASE = "Release";
    public static final String REVIEW = "Review";
    public static final String TESTING = "Testing";
    public static final String IN_PROGRESS = "In Progress";
    public static final String READY_FOR_REVIEW = "Ready for review";

    private static final String JSON_KEY_NAME = "name";
    private static final String JSON_KEY_NAME_UPPER_FIRST_CHAR = "Name";
    private static final String JSON_KEY_ID = "id";

    public Status() {
    }

    public Status(String json) throws JSONException {
        super(json);
    }

    public static Status createFromName(String name) {
        Status status = new Status();
        status.set(JSON_KEY_NAME, name);
        return status;
    }
    public Status(JSONObject json) {
        super(json);
    }

    public String getName() {
        String name = getString(JSON_KEY_NAME);
        if (name == null) {
            name = getString(JSON_KEY_NAME_UPPER_FIRST_CHAR);
        }
        return name;
    }

    public JSONObject createPostObject() {
        return new JSONObject().put(JSON_KEY_ID, getString(JSON_KEY_ID));
    }

    public boolean isResolved() {
        String name = getName();
        return name.equals(DONE) || name.equals(REJECTED) || name.equals(RESOLVED) || name.equals(CLOSED)
                || name.equals(DEFINED) || name.equals(READY_FOR_RETEST) || name.equals(READY_FOR_INTEGRATION)
                || name.equals(IN_INTEGRATION)
                || name.equals(DESIGN_ADJUSTMENTS)
                || name.equals(IN_QA)
                || name.equals(IN_TESTING)
                || name.equals(READY_FOR_TEST)
                || name.equals(ON_HOLD)
                || name.equals(READY_FOR_TESTING)
                || name.equals(INTEGRATION_DONE) || name.equals(READY_FOR_RELEASE)
                || name.equals(IMPLEMENTATION_ACCEPTED)
                || name.equals(READY_FOR_ACCEPTANCE)
                || name.equals(PRODUCT_ACCEPTANCE_BLOCKED)
                || name.equals(INTEGRATION_BLOCKED)
                || name.equals(BUSINESS_VALIDATION)
                || name.equals(IN_ACCEPTANCE)
                || name.equals(APPROVED)
                || name.equals(ACCEPTED)
                || name.equals(COMPLETED);
    }

    public boolean isInDev() {
        return !isDevComplete() && (getName().equals(IN_DEVELOPMENT) || getName().equals(IN_REVIEW));
    }

    public boolean isRejected() {
        return getName().equalsIgnoreCase(REJECTED);
    }

    public boolean isImplementationRejected() {
        return getName().equalsIgnoreCase(IMPLEMENTATION_REJECTED);
    }

    public boolean isNotInScope() {
        return getName().equalsIgnoreCase("not in scope");
    }

    public boolean isTestingBlocked() {
        return getName().toLowerCase().contains("testing blocked");
    }

    public boolean isDevelopmentBlocked() {
        return getName().toLowerCase().contains("development blocked");
    }

    public boolean isBlocked() {
        return getName().toLowerCase().contains(BLOCKED);
    }

    public boolean isQABlocked() {
        return getName().equalsIgnoreCase("QA Blocked");
    }

    public boolean isBABlocked() {
        return getName().equalsIgnoreCase("BA Blocked");
    }

    public boolean isInBusinessAnalysis() {
        return getName().equalsIgnoreCase(DRAFT) || getName().equalsIgnoreCase(IN_BUSINESS_ANALYSIS);
    }

    public boolean isInDiscovery() {
        return getName().equalsIgnoreCase(IN_ASSESSMENT) || getName().equalsIgnoreCase(DRAFT) || getName().equalsIgnoreCase(WAITING_FOR_DESIGN)
                || getName().equalsIgnoreCase(IN_BUSINESS_ANALYSIS);
    }

    public boolean isReadyForAssessment() {
        return getName().equalsIgnoreCase(READY_FOR_ASSESSMENT);
    }

    public boolean isInAssessment() {
        return getName().equals(IN_ASSESSMENT);
    }

    public boolean isReadyForDevelopment() {
        return getName().equals(READY_FOR_DEVELOPMENT);
    }

    public boolean isDesignAdjustments() {
        return getName().equalsIgnoreCase(DESIGN_ADJUSTMENTS);
    }

    public boolean isInDevelopment() {
        return getName().equals(IN_DEVELOPMENT);
    }

    public boolean isInGrooming() {
        return getName().equals(IN_GROOMING);
    }

    public boolean isInReview() {
        return getName().equals(IN_REVIEW);
    }

    public boolean isReview() {
        return getName().equals(REVIEW);
    }

    public boolean isCodeReview() {
        return getName().equals(CODE_REVIEW);
    }

    public boolean isReadyForReview() {
        return getName().equals(READY_FOR_REVIEW);
    }

    public boolean isMerged() {
        return getName().equals(MERGED);
    }

    public boolean isNew() {
        return getName().equalsIgnoreCase(NEW);
    }

    public boolean isReopened() {
        return getName().equals(REOPENED);
    }

    public boolean isDraft() {
        return getName().equals(DRAFT);
    }

    public boolean isReadyForTesting() {
        return getName().equalsIgnoreCase(READY_FOR_TESTING);
    }

    public boolean isDone() {
        return getName().equalsIgnoreCase(DONE);
    }

    public boolean isCompleted() {
        return getName().equalsIgnoreCase(COMPLETED);
    }


    public boolean isReadyForTest() {
        return getName().equalsIgnoreCase(READY_FOR_TEST);
    }

    public boolean isReadyForRetest() {
        return getName().equalsIgnoreCase(READY_FOR_RETEST);
    }

    public boolean isDevelopmentCompleted() {
        return getName().equals(DEVELOPMENT_COMPLETED);
    }

    public boolean isBacklog() {
        return getName().equals(BACKLOG);
    }

    public boolean isReadyForQA() {
        if (isResolved()) {
            return true;
        }
        String name = getName();
        if (name.equals(IN_QA) || name.equals(TESTING) || name.equals(READY_FOR_TESTING) || name.equals(QA_BLOCKED)
                || name.equals(READY_FOR_RETEST) || name.equals(IN_SYSTEM_TEST)) {
            return true;
        }
        return false;
    }

    public boolean isDevComplete() {
        if (isReadyForQA()) {
            return true;
        }
        String name = getName();
        return name.equals(IN_VALIDATION) || name.equals(MERGED) || name.equals(BUG_TO_FIX) || isDevelopmentCompleted() || name.equals(QA_BLOCKED) || name.equals(TESTING_BLOCKED);
    }

    public boolean isOpen() {
        String name = getName();
        return name.equals("Open");
    }

    public boolean isInIntegration() {
        String name = getName();
        return name.equalsIgnoreCase(IN_INTEGRATION);
    }

    public boolean isImplementationAccepted() {
        String name = getName();
        return name.equalsIgnoreCase(IMPLEMENTATION_ACCEPTED);
    }

    public boolean isReadyForAcceptance() {
        String name = getName();
        return name.equalsIgnoreCase(READY_FOR_ACCEPTANCE);
    }

    public boolean isInAcceptance() {
        String name = getName();
        return name.equalsIgnoreCase(IN_ACCEPTANCE);
    }

    public boolean isAccepted() {
        String name = getName();
        return name.equalsIgnoreCase(ACCEPTED);
    }

    public boolean isRelease() {
        String name = getName();
        return name.equalsIgnoreCase(RELEASE);
    }

    public boolean isIntegrationDone() {
        String name = getName();
        return name.equalsIgnoreCase(INTEGRATION_DONE);
    }

    public boolean isCancelled() {
        String name = getName();
        return name.equalsIgnoreCase(CANCELLED);
    }

    public boolean isInTesting() {
        String name = getName();
        return name.equalsIgnoreCase(IN_TESTING);
    }

    public boolean isTesting() {
        String name = getName();
        return name.equalsIgnoreCase(TESTING);
    }

    public boolean isInProgress() {
        String name = getName();
        return name.equalsIgnoreCase(IN_PROGRESS);
    }

    public boolean isOnHold() {
        String name = getName();
        return name.equalsIgnoreCase(ON_HOLD);
    }
}