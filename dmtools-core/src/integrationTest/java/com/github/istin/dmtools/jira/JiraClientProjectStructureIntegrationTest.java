package com.github.istin.dmtools.jira;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.model.IssueTypeScheme;
import com.github.istin.dmtools.atlassian.jira.model.Project;
import com.github.istin.dmtools.atlassian.jira.model.WorkflowScheme;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for Jira project structure operations:
 * - getProjectDetails
 * - getProjectIssueTypeScheme   (requires Jira admin — skipped gracefully when unavailable)
 * - getProjectWorkflowScheme    (requires Jira admin — skipped gracefully when unavailable)
 * - copyProjectStructure        (requires Jira admin — skipped gracefully when unavailable)
 *
 * Requires Jira credentials via env / dmtools.env (JIRA_BASE_PATH, JIRA_LOGIN_PASS_TOKEN).
 * Source project: system property {@code jira.source.project} (default: MYTUBE)
 * Target project: system property {@code jira.test.project}   (default: TP)
 *
 * Note on Jira Admin APIs:
 * /rest/api/3/issuetypescheme/project and /rest/api/3/workflowscheme/project both
 * require the "Administer Jira" global permission. Tests that call these endpoints
 * use {@code assumeTrue} so they are skipped (not failed) when running under a
 * non-admin account.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JiraClientProjectStructureIntegrationTest {

    private static final Logger logger = LogManager.getLogger(JiraClientProjectStructureIntegrationTest.class);

    private static BasicJiraClient jiraClient;
    private static String sourceProjectKey;
    private static String targetProjectKey;

    /** True when read-only admin APIs (scheme read) are accessible. */
    private static boolean adminReadAvailable = false;

    /** True when write admin APIs (scheme assign, project create) are accessible. */
    private static boolean adminWriteAvailable = false;

    private static final String CLONE_KEY = "ITCLONE";

    @BeforeAll
    static void setUp() throws IOException {
        jiraClient = new BasicJiraClient();
        jiraClient.setLogEnabled(true);
        jiraClient.setClearCache(true);
        jiraClient.setCacheGetRequestsEnabled(false);

        sourceProjectKey = System.getProperty("jira.source.project", "MYTUBE");
        targetProjectKey = System.getProperty("jira.test.project", "TP");

        logger.info("BasicJiraClient initialized. Source: {}, Target: {}", sourceProjectKey, targetProjectKey);
        logger.info("Jira base path: {}", BasicJiraClient.BASE_PATH);

        // Probe read-level admin API (scheme read)
        try {
            jiraClient.getProjectIssueTypeScheme(sourceProjectKey);
            adminReadAvailable = true;
            logger.info("Admin read API (scheme read) is accessible");
        } catch (Exception e) {
            logger.warn("Admin read API unavailable ({}); admin-read tests will be skipped", e.getMessage());
        }

        // Probe write-level admin API (project creation)
        try {
            String cloneResult = jiraClient.cloneProject(sourceProjectKey, CLONE_KEY + "PRE", "IT Pre-test Clone");
            if (cloneResult.contains("\"success\":true")) {
                adminWriteAvailable = true;
                logger.info("Admin write API (project creation) is accessible");
                // Clean up pre-test clone
                jiraClient.deleteProject(CLONE_KEY + "PRE", "true");
            }
        } catch (Exception e) {
            logger.warn("Admin write API unavailable ({}); admin-write tests will be skipped", e.getMessage());
        }
    }

    // ─── Order 1 – basic sanity ───────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Sanity: client and project keys are configured")
    void testSetup() {
        assertNotNull(jiraClient);
        assertNotNull(sourceProjectKey);
        assertNotNull(targetProjectKey);
        assertFalse(sourceProjectKey.isBlank());
        assertFalse(targetProjectKey.isBlank());
        assertNotNull(BasicJiraClient.BASE_PATH);
        assertFalse(BasicJiraClient.BASE_PATH.isBlank());
        logger.info("Sanity check passed – source={}, target={}", sourceProjectKey, targetProjectKey);
    }

    // ─── Order 2-3 – getProjectDetails (no admin needed) ─────────────────────

    @Test
    @Order(2)
    @DisplayName("jira_get_project_details – source project")
    void testGetProjectDetails_source() throws IOException {
        Project project = jiraClient.getProjectDetails(sourceProjectKey);

        assertNotNull(project, "Project should not be null");
        assertNotNull(project.getId(), "Project numeric ID should be present");
        assertFalse(project.getId().isBlank(), "Project ID should not be blank");
        assertEquals(sourceProjectKey, project.getKey(), "Project key should match");
        assertNotNull(project.getName(), "Project name should be present");

        logger.info("Source project: id={}, key={}, name={}", project.getId(), project.getKey(), project.getName());
    }

    @Test
    @Order(3)
    @DisplayName("jira_get_project_details – target project")
    void testGetProjectDetails_target() throws IOException {
        Project project = jiraClient.getProjectDetails(targetProjectKey);

        assertNotNull(project);
        assertNotNull(project.getId());
        assertFalse(project.getId().isBlank());
        assertEquals(targetProjectKey, project.getKey());

        logger.info("Target project: id={}, key={}, name={}", project.getId(), project.getKey(), project.getName());
    }

    // ─── Order 4-5 – getProjectIssueTypeScheme (admin) ───────────────────────

    @Test
    @Order(4)
    @DisplayName("jira_get_project_issue_type_scheme – source project [admin]")
    void testGetProjectIssueTypeScheme_source() throws IOException {
        assumeTrue(adminReadAvailable, "Skipped: Jira admin read permission required for issue type scheme API");

        IssueTypeScheme scheme = jiraClient.getProjectIssueTypeScheme(sourceProjectKey);

        assertNotNull(scheme, "Issue type scheme should not be null");
        assertNotNull(scheme.getId(), "Scheme ID should be present");
        assertFalse(scheme.getId().isBlank(), "Scheme ID should not be blank");
        assertNotNull(scheme.getName(), "Scheme name should be present");

        logger.info("Source issue type scheme: id={}, name={}, issueTypeIds={}",
                scheme.getId(), scheme.getName(), scheme.getIssueTypeIds());
    }

    @Test
    @Order(5)
    @DisplayName("jira_get_project_issue_type_scheme – target project [admin]")
    void testGetProjectIssueTypeScheme_target() throws IOException {
        assumeTrue(adminReadAvailable, "Skipped: Jira admin read permission required for issue type scheme API");

        IssueTypeScheme scheme = jiraClient.getProjectIssueTypeScheme(targetProjectKey);

        assertNotNull(scheme);
        assertNotNull(scheme.getId());
        assertFalse(scheme.getId().isBlank());

        logger.info("Target issue type scheme (before copy): id={}, name={}", scheme.getId(), scheme.getName());
    }

    // ─── Order 6-7 – getProjectWorkflowScheme (admin) ────────────────────────

    @Test
    @Order(6)
    @DisplayName("jira_get_project_workflow_scheme – source project [admin]")
    void testGetProjectWorkflowScheme_source() throws IOException {
        assumeTrue(adminReadAvailable, "Skipped: Jira admin read permission required for workflow scheme API");

        WorkflowScheme scheme = jiraClient.getProjectWorkflowScheme(sourceProjectKey);

        assertNotNull(scheme, "Workflow scheme should not be null");
        assertNotNull(scheme.getId(), "Scheme ID should be present");
        assertFalse(scheme.getId().isBlank(), "Scheme ID should not be blank");
        assertNotNull(scheme.getName(), "Scheme name should be present");

        logger.info("Source workflow scheme: id={}, name={}, defaultWorkflow={}, mappings={}",
                scheme.getId(), scheme.getName(), scheme.getDefaultWorkflow(), scheme.getIssueTypeMappings());
    }

    @Test
    @Order(7)
    @DisplayName("jira_get_project_workflow_scheme – target project [admin]")
    void testGetProjectWorkflowScheme_target() throws IOException {
        assumeTrue(adminReadAvailable, "Skipped: Jira admin read permission required for workflow scheme API");

        WorkflowScheme scheme = jiraClient.getProjectWorkflowScheme(targetProjectKey);

        assertNotNull(scheme);
        assertNotNull(scheme.getId());
        assertFalse(scheme.getId().isBlank());

        logger.info("Target workflow scheme (before copy): id={}, name={}", scheme.getId(), scheme.getName());
    }

    // ─── Order 8 – copyProjectStructure (admin) ───────────────────────────────

    @Test
    @Order(8)
    @DisplayName("jira_copy_project_structure – copies schemes from source to target [admin]")
    void testCopyProjectStructure() throws IOException {
        assumeTrue(adminWriteAvailable, "Skipped: Jira admin write permission required for copyProjectStructure");

        IssueTypeScheme sourceIssueScheme = jiraClient.getProjectIssueTypeScheme(sourceProjectKey);
        WorkflowScheme sourceWorkflowScheme = jiraClient.getProjectWorkflowScheme(sourceProjectKey);

        logger.info("Source issue type scheme to copy: id={}, name={}", sourceIssueScheme.getId(), sourceIssueScheme.getName());
        logger.info("Source workflow scheme to copy:   id={}, name={}", sourceWorkflowScheme.getId(), sourceWorkflowScheme.getName());

        String resultJson = jiraClient.copyProjectStructure(sourceProjectKey, targetProjectKey);

        assertNotNull(resultJson, "Result JSON should not be null");
        assertFalse(resultJson.isBlank(), "Result JSON should not be blank");

        JSONObject result = new JSONObject(resultJson);
        logger.info("copyProjectStructure result: {}", result.toString(2));

        assertEquals(sourceProjectKey, result.getString("sourceProject"));
        assertEquals(targetProjectKey, result.getString("targetProject"));
        assertEquals(sourceIssueScheme.getId(), result.getString("issueTypeSchemeId"));
        assertEquals(sourceIssueScheme.getName(), result.getString("issueTypeSchemeName"));
        assertEquals(sourceWorkflowScheme.getId(), result.getString("workflowSchemeId"));
        assertEquals(sourceWorkflowScheme.getName(), result.getString("workflowSchemeName"));
    }

    // ─── Order 9-10 – verify copy was applied (admin) ─────────────────────────

    @Test
    @Order(9)
    @DisplayName("Verify: target project now has the same issue type scheme as source [admin]")
    void testVerify_issueTypeSchemeApplied() throws IOException {
        assumeTrue(adminWriteAvailable, "Skipped: Jira admin write permission required to verify scheme assignment");

        IssueTypeScheme sourceScheme = jiraClient.getProjectIssueTypeScheme(sourceProjectKey);
        IssueTypeScheme targetScheme = jiraClient.getProjectIssueTypeScheme(targetProjectKey);

        logger.info("Source issue type scheme: id={}, name={}", sourceScheme.getId(), sourceScheme.getName());
        logger.info("Target issue type scheme: id={}, name={}", targetScheme.getId(), targetScheme.getName());

        assertEquals(sourceScheme.getId(), targetScheme.getId(),
                "Target project should now use the same issue type scheme as source");
        assertEquals(sourceScheme.getName(), targetScheme.getName());
    }

    @Test
    @Order(10)
    @DisplayName("Verify: target project now has the same workflow scheme as source [admin]")
    void testVerify_workflowSchemeApplied() throws IOException {
        assumeTrue(adminWriteAvailable, "Skipped: Jira admin write permission required to verify scheme assignment");

        WorkflowScheme sourceScheme = jiraClient.getProjectWorkflowScheme(sourceProjectKey);
        WorkflowScheme targetScheme = jiraClient.getProjectWorkflowScheme(targetProjectKey);

        logger.info("Source workflow scheme: id={}, name={}", sourceScheme.getId(), sourceScheme.getName());
        logger.info("Target workflow scheme: id={}, name={}", targetScheme.getId(), targetScheme.getName());

        assertEquals(sourceScheme.getId(), targetScheme.getId(),
                "Target project should now use the same workflow scheme as source");
        assertEquals(sourceScheme.getName(), targetScheme.getName());
    }

    // ─── Order 11 – issue types sanity (no admin needed) ─────────────────────

    @Test
    @Order(11)
    @DisplayName("jira_get_issue_types – both projects return non-empty lists")
    void testGetIssueTypes_bothProjects() throws IOException {
        List<IssueType> sourceTypes = jiraClient.getIssueTypes(sourceProjectKey);
        List<IssueType> targetTypes = jiraClient.getIssueTypes(targetProjectKey);

        assertNotNull(sourceTypes);
        assertNotNull(targetTypes);
        assertFalse(sourceTypes.isEmpty(), "Source project should have issue types");
        assertFalse(targetTypes.isEmpty(), "Target project should have issue types");

        logger.info("Source issue types: {}", sourceTypes.stream().map(IssueType::getName).toList());
        logger.info("Target issue types: {}", targetTypes.stream().map(IssueType::getName).toList());

        // When admin scheme copy succeeded, source types must be a subset of target types.
        // (Plugins such as Xray may add extra types to target – that is acceptable.)
        if (adminWriteAvailable) {
            List<String> sourceNames = sourceTypes.stream().map(IssueType::getName).toList();
            List<String> targetNames = targetTypes.stream().map(IssueType::getName).toList();
            assertTrue(targetNames.containsAll(sourceNames),
                    "After structure copy, target should contain at least all source issue type names. " +
                    "Source: " + sourceNames + " | Target: " + targetNames);
        }
    }

    // ─── Orders 12-14 – clone / delete / restore (requires admin write) ─────

    @Test
    @Order(12)
    @DisplayName("jira_clone_project – creates a fresh next-gen project mirroring source [admin-write]")
    void testCloneProject_createsProject() throws IOException {
        assumeTrue(adminWriteAvailable, "Skipped: Jira admin write permission required for project creation");

        String resultJson = jiraClient.cloneProject(sourceProjectKey, CLONE_KEY, "IT Clone Project");

        assertNotNull(resultJson);
        JSONObject result = new JSONObject(resultJson);
        logger.info("cloneProject result: {}", result.toString(2));

        assertTrue(result.getBoolean("success"), "Clone should succeed");
        assertEquals(CLONE_KEY, result.getString("projectKey"));
        assertFalse(result.getJSONArray("autoCreatedIssueTypes").isEmpty(),
                "Auto-created issue types should not be empty");

        logger.info("Auto-created issue types: {}", result.getJSONArray("autoCreatedIssueTypes"));
        if (result.has("missingIssueTypes")) {
            logger.info("Missing issue types (require manual steps): {}",
                    result.getJSONArray("missingIssueTypes"));
        }
    }

    @Test
    @Order(13)
    @DisplayName("jira_delete_project – moves cloned project to trash [admin-write]")
    void testDeleteProject_movesToTrash() throws IOException {
        assumeTrue(adminWriteAvailable, "Skipped: Jira admin write permission required for project deletion");

        String resultJson = jiraClient.deleteProject(CLONE_KEY, "true");

        assertNotNull(resultJson);
        JSONObject result = new JSONObject(resultJson);
        logger.info("deleteProject result: {}", result.toString(2));

        assertTrue(result.getBoolean("success"), "Delete should succeed");
        assertEquals(CLONE_KEY, result.getString("projectKey"));
        assertTrue(result.getString("message").contains("trash"),
                "Message should mention trash");
    }

    @Test
    @Order(14)
    @DisplayName("jira_restore_project – restores cloned project from trash [admin-write]")
    void testRestoreProject_restoresFromTrash() throws IOException {
        assumeTrue(adminWriteAvailable, "Skipped: Jira admin write permission required for project restore");

        String resultJson = jiraClient.restoreProject(CLONE_KEY);

        assertNotNull(resultJson);
        JSONObject result = new JSONObject(resultJson);
        logger.info("restoreProject result: {}", result.toString(2));

        assertTrue(result.getBoolean("success"), "Restore should succeed");
        assertEquals(CLONE_KEY, result.getString("projectKey"));

        // Clean up — move back to trash so we don't leave test projects around
        jiraClient.deleteProject(CLONE_KEY, "true");
        logger.info("Test cleanup: {} moved to trash", CLONE_KEY);
    }

    @Test
    @Order(15)
    @DisplayName("jira_sync_project_workflow – syncs MYTUBE workflow statuses into TP [admin-write]")
    void testSyncProjectWorkflow() throws IOException {
        assumeTrue(adminWriteAvailable, "Skipped: Jira admin write permission required for workflow sync");

        String resultJson = jiraClient.syncProjectWorkflow(sourceProjectKey, targetProjectKey);

        assertNotNull(resultJson);
        JSONObject result = new JSONObject(resultJson);
        logger.info("syncProjectWorkflow result: {}", result.toString(2));

        assertEquals("success", result.getString("result"), "Sync should succeed");
        assertEquals(sourceProjectKey, result.getString("sourceProject"));
        assertEquals(targetProjectKey, result.getString("targetProject"));
        assertTrue(result.getInt("statusesSynced") > 0, "Should sync at least 1 status");
        assertNotNull(result.getString("workflowId"), "Should return workflow ID");
        logger.info("Synced {} statuses, {} removed, workflowId={}",
                result.getInt("statusesSynced"),
                result.getInt("removedStatuses"),
                result.getString("workflowId"));
    }
}
