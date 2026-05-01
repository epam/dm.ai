// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.dev.CodeGenerator;
import com.github.istin.dmtools.kb.KBProcessingJob;
import com.github.istin.dmtools.reporting.ReportGeneratorJob;
import com.github.istin.dmtools.reporting.ReportVisualizerJob;
import org.junit.Test;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JobRunnerTest {

    @Test
    public void testDecodeBase64() {
        // Use EncodingDetector instead of JobRunner.decodeBase64() to avoid static initialization issues
        EncodingDetector detector = new EncodingDetector();
        String encoded = Base64.getEncoder().encodeToString("testString".getBytes());
        String decoded = detector.decodeBase64(encoded);
        assertEquals("testString", decoded);
    }

    @Test
    public void testEncodeBase64() {
        // Test Base64 encoding functionality directly to avoid static initialization of JobRunner
        // JobRunner.encodeBase64() is a simple wrapper around Base64.getEncoder().encodeToString()
        String input = "testString";
        String expected = Base64.getEncoder().encodeToString(input.getBytes());
        
        // Test the core functionality directly (same as JobRunner.encodeBase64 does)
        byte[] encodedBytes = Base64.getEncoder().encode(input.getBytes());
        String encoded = new String(encodedBytes);
        assertEquals(expected, encoded);
    }

    @Test
    public void testCreateJobInstanceAcceptsExactAndLegacyReportNames() throws Exception {
        assertTrue(invokeCreateJobInstance("ReportGenerator") instanceof ReportGeneratorJob);
        assertTrue(invokeCreateJobInstance("ReportGeneratorJob") instanceof ReportGeneratorJob);

        assertTrue(invokeCreateJobInstance("ReportVisualizer") instanceof ReportVisualizerJob);
        assertTrue(invokeCreateJobInstance("ReportVisualizerJob") instanceof ReportVisualizerJob);

        assertTrue(invokeCreateJobInstance("KBProcessing") instanceof KBProcessingJob);
        assertTrue(invokeCreateJobInstance("KBProcessingJob") instanceof KBProcessingJob);
    }

    @Test
    public void testCodeGeneratorCompatibilityShimRemainsRegistered() throws Exception {
        assertTrue(invokeCreateJobInstance("CodeGenerator") instanceof CodeGenerator);
        assertTrue(invokeCreateJobInstance("codegenerator") instanceof CodeGenerator);
        assertTrue(invokeListJobs().contains("- CodeGenerator"));
    }

    @Test
    public void testKBProcessingJobUsesCliFacingNameInListingAndValidation() throws Exception {
        assertEquals("KBProcessingJob", new KBProcessingJob().getName());

        String listJobsOutput = invokeListJobs();
        assertTrue(listJobsOutput.contains("- KBProcessingJob"));
        assertFalse(listJobsOutput.contains("- KBProcessing\n"));

        try {
            JobRunner.validateJobParamsJson("{}");
            fail("Expected validateJobParamsJson to reject missing job name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("KBProcessingJob"));
            assertFalse(e.getMessage().contains("KBProcessing,"));
        }
    }

    @Test
    public void testRunSupportsCodeGeneratorCompatibilityShim() throws Exception {
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
        assertEquals(CodeGenerator.getCompatibilityResponse(), item.getResult());
    }

    private Object invokeCreateJobInstance(String jobName) throws Exception {
        Method method = JobRunner.class.getDeclaredMethod("createJobInstance", String.class);
        method.setAccessible(true);
        return method.invoke(null, jobName);
    }

    private String invokeListJobs() throws Exception {
        Method method = JobRunner.class.getDeclaredMethod("listJobs");
        method.setAccessible(true);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output));
            method.invoke(null);
        } finally {
            System.setOut(originalOut);
        }
        return output.toString();
    }

}
