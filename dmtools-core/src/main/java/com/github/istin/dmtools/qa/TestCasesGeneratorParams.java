package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.atlassian.jira.model.Relationship;
import com.github.istin.dmtools.job.Params;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper=false)
@NoArgsConstructor
@AllArgsConstructor
public class TestCasesGeneratorParams extends Params {

    public static final String EXISTING_TEST_CASES_JQL = "existingTestCasesJql";
    public static final String TEST_CASES_PRIORITIES = "testCasesPriorities";
    public static final String RELATED_TEST_CASES_RULES = "relatedTestCasesRules";
    public static final String TEST_CASE_ISSUE_TYPE = "testCaseIssueType";
    public static final String IS_CONVERT_TO_JIRA_MARKDOWN = "isConvertToJiraMarkdown";
    public static final String IS_OVERRIDE_PROMPT_EXAMPLES = "isOverridePromptExamples";
    public static final String IS_FIND_RELATED = "isFindRelated";
    public static final String IS_LINK_RELATED = "isLinkRelated";
    public static final String EXAMPLES = "examples";
    public static final String TEST_CASE_LINK_RELATIONSHIP = "testCaseLinkRelationship";

    @SerializedName(EXISTING_TEST_CASES_JQL)
    private String existingTestCasesJql;
    @SerializedName(TEST_CASES_PRIORITIES)
    private String testCasesPriorities;
    @SerializedName(RELATED_TEST_CASES_RULES)
    private String relatedTestCasesRules;
    @SerializedName(EXAMPLES)
    private String examples;
    @SerializedName(TEST_CASE_ISSUE_TYPE)
    private String testCaseIssueType = "Test Case";
    @SerializedName(IS_CONVERT_TO_JIRA_MARKDOWN)
    private boolean isConvertToJiraMarkdown = true;
    @SerializedName(IS_FIND_RELATED)
    private boolean isFindRelated = true;
    @SerializedName(IS_LINK_RELATED)
    private boolean isLinkRelated = true;
    @SerializedName(IS_OVERRIDE_PROMPT_EXAMPLES)
    private boolean isOverridePromptExamples = false;
    @SerializedName(TEST_CASE_LINK_RELATIONSHIP)
    private String testCaseLinkRelationship = Relationship.IS_TESTED_BY;

}