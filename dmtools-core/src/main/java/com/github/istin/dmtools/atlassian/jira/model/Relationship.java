package com.github.istin.dmtools.atlassian.jira.model;

public class Relationship {
    public static final String IS_BLOCKED_BY = "is blocked by";
    public static final String TESTED_BY = "tested by";
    public static final String IS_TESTED_BY = "is tested by";
    public static final String DEFINES = "defines";
    public static final String IS_DEFINED_BY = "is defined by";
    public static final String IS_BLOCKED_BY_ = "is blocked by_";
    public static final String IS_BROKEN_BY = "is broken by";
    public static final String LINKED_TO = "linked to";
    public static final String LINKED_WITH = "linked with";
    public static final String BLOCKS = "blocks";
    public static final String BLOCKS_ = "blocks_";
    public static final String DEPENDS_UPON = "depends upon";
    public static final String DEPENDS_ON = "depends on";
    public static final String IS_DEPENDED_ON_BY = "is depended on by";
    public static final String IS_A_PREREQUISITE_OF = "is a prerequisite of";
    public static final String MENTIONED_IN = "mentioned in";
    public static final String IS_IMPLEMENTED_IN = "Is implemented in";
    public static final String IS_CAUSED_BY = "is caused by";
    public static final String FIXED_IN = "fixed in";
    public static final String IS_COVERED_BY = "is covered by";
    public static final String RELATES_TO = "relates to";
    public static final String RELATE = "relate";
    public static final String RELATES = "Relates";
    public static final String TESTS = "Tests";

    public static boolean isFixedInOrImplementedIn(IssueLink issueLink) {
        String inwardType = issueLink.getInwardType();
        return inwardType.equalsIgnoreCase(FIXED_IN) || inwardType.equalsIgnoreCase(IS_IMPLEMENTED_IN) || (issueLink.getInwardIssue() != null && inwardType.equalsIgnoreCase(IS_CAUSED_BY)) || (issueLink.getOutwardIssue() != null && inwardType.equalsIgnoreCase(IS_CAUSED_BY));
    }

    public static boolean isImplements(IssueLink issueLink) {
        String outwardType = issueLink.getOutwardType();
        return issueLink.getOutwardIssue() != null && (
                outwardType.equalsIgnoreCase("implements") ||
                outwardType.equalsIgnoreCase("fixes") ||
                outwardType.equalsIgnoreCase("causes")
        );
    }

    public static boolean isBlockedBy(IssueLink issueLink) {
        return issueLink.getInwardType().equalsIgnoreCase(IS_BLOCKED_BY);
    }

    public static boolean relatesTo(IssueLink issueLink) {
        return issueLink.getInwardType().contains(RELATE);
    }

    public static boolean isBlockerForCurrentIssue(IssueLink issueLink) {
        String inwardType = issueLink.getInwardType();
        return inwardType.equalsIgnoreCase(IS_BROKEN_BY) || inwardType.equalsIgnoreCase(IS_BLOCKED_BY);
    }

    public static boolean isLinkedTo(IssueLink issueLink) {
        String inwardType = issueLink.getInwardType();
        return inwardType.equalsIgnoreCase(Relationship.LINKED_TO) || inwardType.equalsIgnoreCase(Relationship.LINKED_WITH);
    }
}