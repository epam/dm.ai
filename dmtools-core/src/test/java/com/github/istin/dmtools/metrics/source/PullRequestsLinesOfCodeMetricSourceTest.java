// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.networking.AbstractRestClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PullRequestsLinesOfCodeMetricSourceTest {

    private PullRequestsLinesOfCodeMetricSource metricSource;
    private SourceCode sourceCodeMock;
    private IEmployees employeesMock;

    @Before
    public void setUp() {
        sourceCodeMock = mock(SourceCode.class);
        employeesMock = mock(IEmployees.class);
        metricSource = new PullRequestsLinesOfCodeMetricSource("workspace", "repo", sourceCodeMock, "branchName", employeesMock);
    }


    @Test
    public void testIsValidFileCounted() {
        assertEquals(false, PullRequestsLinesOfCodeMetricSource.isValidFileCounted("file.g.dart"));
        assertEquals(false, PullRequestsLinesOfCodeMetricSource.isValidFileCounted("file.freezed.dart"));
        assertEquals(false, PullRequestsLinesOfCodeMetricSource.isValidFileCounted("file.config.dart"));
        assertEquals(false, PullRequestsLinesOfCodeMetricSource.isValidFileCounted("packages/some_services/lib/swagger_generated_code/swagger.chopper.dart"));
        assertEquals(false, PullRequestsLinesOfCodeMetricSource.isValidFileCounted("packages/some_services/lib/swagger_generated_code/swagger.dart"));
        assertEquals(true, PullRequestsLinesOfCodeMetricSource.isValidFileCounted("validFile.java"));
    }
}