// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.job.ResultItem;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CodeGeneratorTest {

    @Test
    public void testRunJobReturnsCompatibilityResponseAndLogsDeprecationWarning() {
        Logger logger = mock(Logger.class);
        CodeGeneratorCompatibilityJob codeGenerator = new CodeGeneratorCompatibilityJob(logger);

        List<ResultItem> resultItems = codeGenerator.runJob(null);

        assertEquals(1, resultItems.size());
        assertEquals("CodeGenerator", resultItems.get(0).getKey());
        assertEquals(CodeGeneratorCompatibilityJob.getCompatibilityResponse(), resultItems.get(0).getResult());
        assertNull(codeGenerator.getAi());
        assertTrue(CodeGeneratorCompatibilityJob.getDeprecationMessage().contains("deprecated"));
        assertTrue(CodeGeneratorCompatibilityJob.getDeprecationMessage().contains(CodeGeneratorCompatibilityJob.REMOVAL_VERSION));
        verify(logger).warn(CodeGeneratorCompatibilityJob.getDeprecationMessage());
    }
}
