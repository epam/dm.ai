// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.ba.BusinessAnalyticDORGeneration;
import com.github.istin.dmtools.ba.RequirementsCollector;
import com.github.istin.dmtools.ba.UserStoryGenerator;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.dev.CodeGeneratorCompatibilityJob;
import com.github.istin.dmtools.dev.UnitTestsGenerator;
import com.github.istin.dmtools.diagram.DiagramsCreator;
import com.github.istin.dmtools.documentation.DocumentationGenerator;
import com.github.istin.dmtools.expert.Expert;
import com.github.istin.dmtools.js.JSRunner;
import com.github.istin.dmtools.kb.KBProcessingJob;
import com.github.istin.dmtools.presale.PreSaleSupport;
import com.github.istin.dmtools.qa.InstructionsGenerator;
import com.github.istin.dmtools.qa.TestCasesGenerator;
import com.github.istin.dmtools.report.productivity.BAProductivityReport;
import com.github.istin.dmtools.report.productivity.DevProductivityReport;
import com.github.istin.dmtools.report.productivity.QAProductivityReport;
import com.github.istin.dmtools.reporting.ReportGeneratorJob;
import com.github.istin.dmtools.reporting.ReportVisualizerJob;
import com.github.istin.dmtools.sa.SolutionArchitectureCreator;
import com.github.istin.dmtools.sync.SourceCodeCommitTrackerSyncJob;
import com.github.istin.dmtools.sync.SourceCodeTrackerSyncJob;
import com.github.istin.dmtools.teammate.Teammate;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobRunnerCoverageTest {

    // ------------------------------------------------------------------
    // createJobInstance (private static, invoked via reflection)
    // ------------------------------------------------------------------

    @Test
    public void testCreateJobInstanceReturnsExpectedTypeForEveryRegisteredName() throws Exception {
        assertTrue(invokeCreateJobInstance("presalesupport") instanceof PreSaleSupport);
        assertTrue(invokeCreateJobInstance("documentationgenerator") instanceof DocumentationGenerator);
        assertTrue(invokeCreateJobInstance("requirementscollector") instanceof RequirementsCollector);
        assertTrue(invokeCreateJobInstance("testcasesgenerator") instanceof TestCasesGenerator);
        assertTrue(invokeCreateJobInstance("instructionsgenerator") instanceof InstructionsGenerator);
        assertTrue(invokeCreateJobInstance("solutionarchitecturecreator") instanceof SolutionArchitectureCreator);
        assertTrue(invokeCreateJobInstance("diagramscreator") instanceof DiagramsCreator);
        assertTrue(invokeCreateJobInstance("diagramcreator") instanceof DiagramsCreator);
        assertTrue(invokeCreateJobInstance("codegenerator") instanceof CodeGeneratorCompatibilityJob);
        assertTrue(invokeCreateJobInstance("devproductivityreport") instanceof DevProductivityReport);
        assertTrue(invokeCreateJobInstance("baproductivityreport") instanceof BAProductivityReport);
        assertTrue(invokeCreateJobInstance("businessanalyticdorgeneration") instanceof BusinessAnalyticDORGeneration);
        assertTrue(invokeCreateJobInstance("qaproductivityreport") instanceof QAProductivityReport);
        assertTrue(invokeCreateJobInstance("reportgenerator") instanceof ReportGeneratorJob);
        assertTrue(invokeCreateJobInstance("reportgeneratorjob") instanceof ReportGeneratorJob);
        assertTrue(invokeCreateJobInstance("reportvisualizer") instanceof ReportVisualizerJob);
        assertTrue(invokeCreateJobInstance("reportvisualizerjob") instanceof ReportVisualizerJob);
        assertTrue(invokeCreateJobInstance("expert") instanceof Expert);
        assertTrue(invokeCreateJobInstance("teammate") instanceof Teammate);
        assertTrue(invokeCreateJobInstance("sourcecodetrackersyncjob") instanceof SourceCodeTrackerSyncJob);
        assertTrue(invokeCreateJobInstance("sourcecodecommittrackersyncjob") instanceof SourceCodeCommitTrackerSyncJob);
        assertTrue(invokeCreateJobInstance("userstorygenerator") instanceof UserStoryGenerator);
        assertTrue(invokeCreateJobInstance("unittestsgenerator") instanceof UnitTestsGenerator);
        assertTrue(invokeCreateJobInstance("jsrunner") instanceof JSRunner);
        assertTrue(invokeCreateJobInstance("kbprocessing") instanceof KBProcessingJob);
        assertTrue(invokeCreateJobInstance("kbprocessingjob") instanceof KBProcessingJob);
    }

    @Test
    public void testCreateJobInstanceIsCaseInsensitiveAndReturnsNullForUnknown() throws Exception {
        assertTrue(invokeCreateJobInstance("PreSaleSupport") instanceof PreSaleSupport);
        assertTrue(invokeCreateJobInstance("EXPERT") instanceof Expert);
        assertNull(invokeCreateJobInstance("no_such_job"));
        assertNull(invokeCreateJobInstance(""));
    }

    // ------------------------------------------------------------------
    // isKnownJobName
    // ------------------------------------------------------------------

    @Test
    public void testIsKnownJobName() {
        assertFalse(JobRunner.isKnownJobName(null));
        assertFalse(JobRunner.isKnownJobName(""));
        assertFalse(JobRunner.isKnownJobName("   "));
        assertFalse(JobRunner.isKnownJobName("unknownJob"));
        assertTrue(JobRunner.isKnownJobName("expert"));
        assertTrue(JobRunner.isKnownJobName("  Expert  "));
        assertTrue(JobRunner.isKnownJobName("CodeGenerator"));
    }

    // ------------------------------------------------------------------
    // getJobs (lazy initialization)
    // ------------------------------------------------------------------

    @Test
    public void testGetJobsReturnsAllJobsAndCachesList() {
        List<Job> jobs = JobRunner.getJobs();
        assertNotNull(jobs);
        assertEquals(22, jobs.size());
        for (Job job : jobs) {
            assertNotNull(job);
            assertNotNull(job.getName());
        }
        // second call must hit the already-initialized branch and return the same list
        assertSame(jobs, JobRunner.getJobs());
    }

    // ------------------------------------------------------------------
    // decodeBase64 / encodeBase64
    // ------------------------------------------------------------------

    @Test
    public void testDecodeAndEncodeBase64RoundTrip() {
        String input = "{\"name\":\"CodeGenerator\",\"params\":{}}";
        String encoded = JobRunner.encodeBase64(input);
        assertEquals(Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8)), encoded);
        assertEquals(input, JobRunner.decodeBase64(encoded));
    }

    // ------------------------------------------------------------------
    // validateJobParamsJson
    // ------------------------------------------------------------------

    @Test
    public void testValidateJobParamsJsonRejectsNullAndEmpty() {
        assertValidationFails(null, "must not be empty");
        assertValidationFails("", "must not be empty");
        assertValidationFails("   ", "must not be empty");
    }

    @Test
    public void testValidateJobParamsJsonRejectsInvalidJson() {
        assertValidationFails("not a json", "not valid JSON");
        assertValidationFails("[1,2,3]", "not valid JSON");
    }

    @Test
    public void testValidateJobParamsJsonRejectsMissingNameWithAvailableJobsList() {
        try {
            JobRunner.validateJobParamsJson("{}");
            fail("Expected IllegalArgumentException for missing name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("non-empty 'name' field"));
            assertTrue(e.getMessage().contains("Available jobs:"));
            assertTrue(e.getMessage().contains("CodeGenerator"));
        }
    }

    @Test
    public void testValidateJobParamsJsonAcceptsAnyNonEmptyName() {
        // validation only requires a non-empty name; unknown names fail later in run()
        JobRunner.validateJobParamsJson("{\"name\":\"TotallyUnknownJob\"}");
        JobRunner.validateJobParamsJson("{\"name\":\"  expert  \"}");
    }

    // ------------------------------------------------------------------
    // initMetadata
    // ------------------------------------------------------------------

    @Test
    public void testInitMetadataInitializesMetadataAndPropagatesToAi() {
        Job job = mock(Job.class);
        AI ai = mock(AI.class);
        when(job.getAi()).thenReturn(ai);

        Params params = new Params();
        Metadata metadata = new Metadata();
        params.setMetadata(metadata);

        JobRunner.initMetadata(job, params);

        assertNotNull(metadata.getAgentId());
        verify(ai).setMetadata(metadata);
    }

    @Test
    public void testInitMetadataWithNullMetadataStillSetsAiMetadata() {
        Job job = mock(Job.class);
        AI ai = mock(AI.class);
        when(job.getAi()).thenReturn(ai);

        Params params = new Params();
        assertNull(params.getMetadata());

        JobRunner.initMetadata(job, params);

        verify(ai).setMetadata(null);
    }

    @Test
    public void testInitMetadataWithNullAiDoesNotFail() {
        Job job = mock(Job.class);
        when(job.getAi()).thenReturn(null);

        Params params = new Params();
        Metadata metadata = new Metadata();
        params.setMetadata(metadata);

        JobRunner.initMetadata(job, params);

        assertNotNull(metadata.getAgentId());
    }

    @Test
    public void testInitMetadataIgnoresNonParamsObjects() {
        Job job = mock(Job.class);

        JobRunner.initMetadata(job, new BaseJobParams());
        JobRunner.initMetadata(job, "not params");
        JobRunner.initMetadata(job, null);

        verify(job, never()).getAi();
    }

    // ------------------------------------------------------------------
    // run(JobParams)
    // ------------------------------------------------------------------

    @Test
    public void testRunThrowsForUnknownJobName() throws Exception {
        JobParams jobParams = new JobParams();
        jobParams.setName("no_such_job");
        jobParams.set(JobParams.PARAMS, new JSONObject());

        try {
            new JobRunner().run(jobParams);
            fail("Expected IllegalArgumentException for unknown job");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown job name: no_such_job"));
        }
    }

    @Test
    public void testRunExecutesCompatibilityShimJob() throws Exception {
        JobParams jobParams = new JobParams();
        jobParams.setName("CodeGenerator");
        jobParams.set(JobParams.PARAMS, new JSONObject());

        Object result = new JobRunner().run(jobParams);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> items = (List<?>) result;
        assertEquals(1, items.size());
        ResultItem item = (ResultItem) items.get(0);
        assertEquals("CodeGenerator", item.getKey());
        assertEquals(CodeGeneratorCompatibilityJob.getCompatibilityResponse(), item.getResult());
    }

    @Test
    public void testRunAppliesAndClearsEnvVariableOverridesForTrackerParams() throws Exception {
        String uniqueKey = "JOB_RUNNER_COVERAGE_TEST_VAR";
        PropertyReader propertyReader = new PropertyReader();
        assertNull(propertyReader.getValue(uniqueKey));

        JSONObject params = new JSONObject();
        params.put("envVariables", new JSONObject().put(uniqueKey, "override-value"));
        params.put("issueIgnorePrefixes", "IGN-");
        params.put("issueAllowedPrefixes", "ALLOW-");

        JobParams jobParams = new JobParams();
        jobParams.setName("CodeGenerator");
        jobParams.set(JobParams.PARAMS, params);

        Object result = new JobRunner().run(jobParams);

        assertNotNull(result);
        // overrides must be cleared in the finally block even after successful execution
        assertNull(propertyReader.getValue(uniqueKey));
        assertNull(propertyReader.getValue(PropertyReader.JIRA_ISSUE_IGNORE_PREFIXES));
        assertNull(propertyReader.getValue(PropertyReader.JIRA_ISSUE_ALLOWED_PREFIXES));
    }

    @Test
    public void testRunClearsOverridesWhenJobThrows() throws Exception {
        String uniqueKey = "JOB_RUNNER_COVERAGE_TEST_FAIL_VAR";

        JSONObject params = new JSONObject();
        params.put("envVariables", new JSONObject().put(uniqueKey, "override-value"));

        JobParams jobParams = new JobParams();
        jobParams.setName("CodeGenerator");
        jobParams.set(JobParams.PARAMS, params);
        // SERVER_MANAGED mode makes AbstractJob.initializeForMode throw
        // UnsupportedOperationException for the compatibility shim job
        jobParams.setExecutionMode(ExecutionMode.SERVER_MANAGED);

        try {
            new JobRunner().run(jobParams);
            fail("Expected job initialization to fail in SERVER_MANAGED mode");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        assertNull(new PropertyReader().getValue(uniqueKey));
    }

    @Test
    public void testRunWithOnlyPrefixOverridesAndEmptyEnvVariables() throws Exception {
        JSONObject params = new JSONObject();
        params.put("envVariables", new JSONObject());
        params.put("issueIgnorePrefixes", "IGN-");

        JobParams jobParams = new JobParams();
        jobParams.setName("CodeGenerator");
        jobParams.set(JobParams.PARAMS, params);

        Object result = new JobRunner().run(jobParams);

        assertNotNull(result);
        assertNull(new PropertyReader().getValue(PropertyReader.JIRA_ISSUE_IGNORE_PREFIXES));
    }

    @Test
    public void testRunJSRunnerWithInlineScriptAppliesTrackerParamsOverrides() throws Exception {
        String uniqueKey = "JOB_RUNNER_COVERAGE_JS_VAR";

        JSONObject params = new JSONObject();
        params.put("jsPath", "function action(params) { return {status: 'ok'}; }");
        params.put("envVariables", new JSONObject().put(uniqueKey, "js-override"));
        params.put("issueIgnorePrefixes", "IGN-");
        params.put("issueAllowedPrefixes", "ALLOW-");

        JobParams jobParams = new JobParams();
        jobParams.setName("jsrunner");
        jobParams.set(JobParams.PARAMS, params);

        Object result = new JobRunner().run(jobParams);

        assertNotNull(result);
        assertNull(new PropertyReader().getValue(uniqueKey));
        assertNull(new PropertyReader().getValue(PropertyReader.JIRA_ISSUE_IGNORE_PREFIXES));
        assertNull(new PropertyReader().getValue(PropertyReader.JIRA_ISSUE_ALLOWED_PREFIXES));
    }

    // ------------------------------------------------------------------
    // main(String[])
    // ------------------------------------------------------------------

    @Test
    public void testMainPrintsVersion() throws Exception {
        for (String flag : new String[]{"--version", "-v"}) {
            String output = captureOut(() -> JobRunner.main(new String[]{flag}));
            assertTrue(output.contains("DMTools "));
            assertTrue(output.contains("comprehensive development management toolkit"));
        }
    }

    @Test
    public void testMainPrintsHelp() throws Exception {
        for (String flag : new String[]{"--help", "-h"}) {
            String output = captureOut(() -> JobRunner.main(new String[]{flag}));
            assertTrue(output.contains("DMTools CLI Wrapper"));
            assertTrue(output.contains("Usage:"));
            assertTrue(output.contains("dmtools run <json-file>"));
        }
    }

    @Test
    public void testMainListsJobs() throws Exception {
        String output = captureOut(() -> JobRunner.main(new String[]{"--list-jobs"}));
        assertTrue(output.contains("Available Jobs:"));
        assertTrue(output.contains("- CodeGenerator"));
        assertTrue(output.contains("- KBProcessingJob"));
        assertTrue(output.contains("Total: 22 jobs available"));
    }

    @Test
    public void testMainWithNoArgsPrintsHelpWhenNoConsole() throws Exception {
        Assume.assumeTrue("Requires non-interactive environment (no console)", System.console() == null);
        String output = captureOut(() -> JobRunner.main(new String[0]));
        assertTrue(output.contains("DMTools CLI Wrapper"));
    }

    @Test
    public void testMainMcpWithoutCommandPrintsUsageError() throws Exception {
        String output = captureOut(() -> JobRunner.main(new String[]{"mcp"}));
        assertTrue(output.contains("Usage: mcp <command>"));
    }

    @Test
    public void testMainRunCommandWithKnownJobNameExecutesJob() throws Exception {
        String output = captureOut(() -> JobRunner.main(new String[]{"run", "codegenerator"}));
        assertTrue(output.contains("CodeGenerator"));
        assertTrue(output.contains(CodeGeneratorCompatibilityJob.getCompatibilityResponse()));
    }

    @Test
    public void testMainRunCommandWithoutFileFails() throws Exception {
        try {
            JobRunner.main(new String[]{"run"});
            fail("Expected IllegalArgumentException for run without arguments");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid run command arguments"));
        }
    }

    @Test
    public void testMainBase64ParamsExecutesJob() throws Exception {
        String json = "{\"name\":\"CodeGenerator\",\"params\":{}}";
        String encoded = JobRunner.encodeBase64(json);

        String output = captureOut(() -> JobRunner.main(new String[]{encoded}));

        assertTrue(output.contains("CodeGenerator"));
        assertTrue(output.contains(CodeGeneratorCompatibilityJob.getCompatibilityResponse()));
    }

    @Test
    public void testMainBase64ParamsWithUnknownJobThrows() throws Exception {
        String encoded = JobRunner.encodeBase64("{\"name\":\"no_such_job\",\"params\":{}}");

        try {
            JobRunner.main(new String[]{encoded});
            fail("Expected IllegalArgumentException for unknown job");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown job name: no_such_job"));
        }
    }

    @Test
    public void testMainBase64ParamsWithInvalidPayloadThrows() throws Exception {
        String encoded = JobRunner.encodeBase64("not a json");

        try {
            JobRunner.main(new String[]{encoded});
            fail("Expected IllegalArgumentException for invalid payload");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not valid JSON"));
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Object invokeCreateJobInstance(String jobName) throws Exception {
        Method method = JobRunner.class.getDeclaredMethod("createJobInstance", String.class);
        method.setAccessible(true);
        return method.invoke(null, jobName);
    }

    private void assertValidationFails(String json, String expectedMessagePart) {
        try {
            JobRunner.validateJobParamsJson(json);
            fail("Expected IllegalArgumentException for input: " + json);
        } catch (IllegalArgumentException e) {
            assertTrue("Message should contain '" + expectedMessagePart + "' but was: " + e.getMessage(),
                    e.getMessage().contains(expectedMessagePart));
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private String captureOut(ThrowingRunnable runnable) throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output));
            runnable.run();
        } finally {
            System.setOut(originalOut);
        }
        return output.toString();
    }
}
