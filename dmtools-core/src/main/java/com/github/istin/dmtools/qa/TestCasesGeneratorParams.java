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
    public static final String IS_GENERATE_NEW = "isGenerateNew";
    public static final String EXAMPLES = "examples";
    public static final String TEST_CASE_LINK_RELATIONSHIP = "testCaseLinkRelationship";
    public static final String INCLUDE_OTHER_TICKET_REFERENCES = "includeOtherTicketReferences";
    public static final String TEST_CASE_LINK_RELATIONSHIP_FOR_NEW = "testCaseLinkRelationshipForNew";
    public static final String TEST_CASE_LINK_RELATIONSHIP_FOR_EXISTING = "testCaseLinkRelationshipForExisting";
    public static final String TEST_CASES_CUSTOM_FIELDS = "testCasesCustomFields";
    public static final String CUSTOM_FIELDS_RULES = "customFieldsRules";
    public static final String PREPROCESS_JS_ACTION = "preprocessJSAction";
    public static final String MODEL_TEST_CASES_CREATION = "modelTestCasesCreation";
    public static final String MODEL_TEST_CASES_RELATION = "modelTestCasesRelation";
    public static final String MODEL_TEST_CASE_RELATION = "modelTestCaseRelation";
    public static final String MODEL_TEST_CASES_DEDUPLICATION = "modelTestCaseDeduplication";

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
    @SerializedName(INCLUDE_OTHER_TICKET_REFERENCES)
    private boolean includeOtherTicketReferences = true;
    @SerializedName(IS_FIND_RELATED)
    private boolean isFindRelated = true;
    @SerializedName(IS_LINK_RELATED)
    private boolean isLinkRelated = true;
    @SerializedName(IS_GENERATE_NEW)
    private boolean isGenerateNew = true;
    @SerializedName(IS_OVERRIDE_PROMPT_EXAMPLES)
    private boolean isOverridePromptExamples = false;
    @SerializedName(TEST_CASE_LINK_RELATIONSHIP)
    private String testCaseLinkRelationship = Relationship.IS_TESTED_BY;
    @SerializedName(TEST_CASE_LINK_RELATIONSHIP_FOR_NEW)
    private String testCaseLinkRelationshipForNew;
    @SerializedName(TEST_CASE_LINK_RELATIONSHIP_FOR_EXISTING)
    private String testCaseLinkRelationshipForExisting;
    @SerializedName(TEST_CASES_CUSTOM_FIELDS)
    private String[] testCasesCustomFields;
    @SerializedName(CUSTOM_FIELDS_RULES)
    private String customFieldsRules;
    @SerializedName(PREPROCESS_JS_ACTION)
    private String preprocessJSAction;

    @SerializedName(MODEL_TEST_CASES_CREATION)
    private String modelTestCasesCreation;
    @SerializedName(MODEL_TEST_CASES_RELATION)
    private String modelTestCasesRelation;
    @SerializedName(MODEL_TEST_CASE_RELATION)
    private String modelTestCaseRelation;
    @SerializedName(MODEL_TEST_CASES_DEDUPLICATION)
    private String modelTestCaseDeduplication;
}