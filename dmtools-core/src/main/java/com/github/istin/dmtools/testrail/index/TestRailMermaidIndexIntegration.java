// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.testrail.index;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.index.mermaid.MermaidIndexIntegration;
import com.github.istin.dmtools.testrail.model.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * TestRail implementation of {@link MermaidIndexIntegration}.
 * <p>
 * Iterates over TestRail test cases using the underlying {@link TrackerClient}
 * and feeds each case into the Mermaid indexing pipeline. The first include
 * pattern is treated as the TestRail API query (e.g. {@code project_id=5&suite_id=3}).
 */
public class TestRailMermaidIndexIntegration implements MermaidIndexIntegration {

    private static final Logger logger = LogManager.getLogger(TestRailMermaidIndexIntegration.class);

    private final TrackerClient<? extends ITicket> trackerClient;

    public TestRailMermaidIndexIntegration(TrackerClient<? extends ITicket> trackerClient) {
        if (trackerClient == null) {
            throw new IllegalArgumentException("TrackerClient is required");
        }
        this.trackerClient = trackerClient;
    }

    @Override
    public void getContentForIndex(List<String> includePatterns, List<String> excludePatterns, ContentProcessor processor) {
        if (includePatterns == null || includePatterns.isEmpty()) {
            logger.warn("No include patterns provided, no TestRail content will be retrieved");
            return;
        }

        String query = includePatterns.get(0);
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Empty TestRail query pattern, skipping");
            return;
        }

        logger.info("Processing TestRail cases with query: {}", query);

        try {
            @SuppressWarnings("unchecked")
            TrackerClient<TestCase> client = (TrackerClient<TestCase>) trackerClient;
            client.searchAndPerform(testCase -> {
                processTestCase(testCase, processor);
                return false; // continue processing all cases
            }, query, trackerClient.getDefaultQueryFields());
        } catch (Exception e) {
            logger.error("Error retrieving content from TestRail", e);
            throw new RuntimeException("Failed to retrieve content from TestRail", e);
        }
    }

    private void processTestCase(TestCase testCase, ContentProcessor processor) {
        if (testCase == null) {
            return;
        }

        String caseKey = testCase.getTicketKey();
        if (caseKey == null || caseKey.isEmpty()) {
            logger.warn("Skipping TestRail case without key");
            return;
        }

        String title;
        try {
            title = testCase.getTicketTitle();
        } catch (Exception e) {
            title = caseKey;
        }

        String content;
        try {
            content = testCase.toText();
        } catch (Exception e) {
            logger.warn("Failed to convert {} to text, falling back to description", caseKey, e);
            content = testCase.getTicketDescription();
        }

        if (content == null || content.trim().isEmpty()) {
            logger.debug("No content for TestRail case {}, skipping", caseKey);
            return;
        }

        List<String> metadata = new ArrayList<>();
        metadata.add("testCaseKey:" + caseKey);
        try {
            String issueType = testCase.getIssueType();
            if (issueType != null) {
                metadata.add("issueType:" + issueType);
            }
        } catch (Exception e) {
            logger.debug("Failed to get issue type for {}: {}", caseKey, e.getMessage());
        }

        Long updatedMillis = testCase.getUpdatedAsMillis();
        Date lastModified = updatedMillis != null && updatedMillis > 0
                ? new Date(updatedMillis)
                : new Date();

        // TestRail attachments are not supported yet; pass empty list
        List<File> attachments = Collections.emptyList();

        processor.process(caseKey, title, content, metadata, attachments, lastModified);
    }
}
