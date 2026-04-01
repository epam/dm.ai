// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AbstractJobTest {

    private static class TestJob extends AbstractJob<String, String> {
        @Override
        public String runJob(String s) throws Exception {
            return "success";
        }

        @Override
        public AI getAi() {
            return null;
        }
        // No additional implementation needed for testing
    }

    @Test
    public void testGetName() {
        TestJob job = new TestJob();
        String expectedName = "TestJob";
        assertEquals(expectedName, job.getName());
    }

    @Test
    public void testGetParamsClass() {
        TestJob job = new TestJob();
        assertEquals(String.class, job.getParamsClass());
    }

    @Test
    public void testGetTemplateParameterClass() {
        AbstractJob<String, String> job = new TestJob();
        assertEquals(String.class, job.getParamsClass());
    }

    @Test
    public void testGetTemplateParameterClassThrowsException() {
        class InvalidJob extends AbstractJob {
            @Override
            public Object runJob(Object o) throws Exception {
                return "success";
            }

            @Override
            public AI getAi() {
                return null;
            }
            // No type parameter specified
        }
        InvalidJob job = new InvalidJob();
        assertThrows(IllegalArgumentException.class, job::getParamsClass);
    }

    // -------------------------------------------------------------------------
    // shouldPostComments tests
    // Regression: ciRunUrl was gated on outputType != none, ignoring alwaysPostComments.
    // -------------------------------------------------------------------------

    private TrackerParams paramsWithOutputType(TrackerParams.OutputType outputType) {
        TrackerParams p = new TrackerParams();
        p.setOutputType(outputType);
        return p;
    }

    @Test
    public void testShouldPostComments_outputTypeComment_returnsTrue() {
        TestJob job = new TestJob();
        TrackerParams params = paramsWithOutputType(TrackerParams.OutputType.comment);
        assertTrue(job.shouldPostComments(params));
    }

    @Test
    public void testShouldPostComments_outputTypeNone_returnsFalse() {
        TestJob job = new TestJob();
        TrackerParams params = paramsWithOutputType(TrackerParams.OutputType.none);
        assertFalse(job.shouldPostComments(params));
    }

    @Test
    public void testShouldPostComments_outputTypeNone_alwaysPostCommentsTrue_returnsTrue() {
        // Regression test: ciRunUrl must be posted even when outputType=none,
        // as long as alwaysPostComments=true.
        TestJob job = new TestJob();
        TrackerParams params = paramsWithOutputType(TrackerParams.OutputType.none);
        params.setAlwaysPostComments(true);
        assertTrue("alwaysPostComments=true must override outputType=none",
                job.shouldPostComments(params));
    }

    @Test
    public void testShouldPostComments_outputTypeComment_alwaysPostCommentsTrue_returnsTrue() {
        TestJob job = new TestJob();
        TrackerParams params = paramsWithOutputType(TrackerParams.OutputType.comment);
        params.setAlwaysPostComments(true);
        assertTrue(job.shouldPostComments(params));
    }

    @Test
    public void testShouldPostComments_outputTypeNone_alwaysPostCommentsFalse_returnsFalse() {
        TestJob job = new TestJob();
        TrackerParams params = paramsWithOutputType(TrackerParams.OutputType.none);
        params.setAlwaysPostComments(false);
        assertFalse(job.shouldPostComments(params));
    }
}