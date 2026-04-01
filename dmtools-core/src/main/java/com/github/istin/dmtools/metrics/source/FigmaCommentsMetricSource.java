// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.List;

import static com.github.istin.dmtools.common.utils.DateUtils.calendar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FigmaCommentsMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(FigmaCommentsMetricSource.class);

    private List<String> peopleToFilterOut;

    private IEmployees employees;
    private FigmaClient figmaClient;
    private final String[] files;

    public FigmaCommentsMetricSource(IEmployees employees, List<String> peopleToFilterOut, FigmaClient figmaClient, String... files) {
        super(employees);
        this.peopleToFilterOut = peopleToFilterOut;
        this.employees = employees;
        this.figmaClient = figmaClient;
        this.files = files;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        for (String fileKey : files) {
            List<IComment> comments = figmaClient.getComments(fileKey);
            logger.info(comments.size());
            for (IComment comment : comments) {
                String name = employees.transformName(comment.getAuthor().getFullName());
                if (employees != null) {
                    if (!employees.contains(name)) {
                        continue;
                    }
                }
                KeyTime keyTime = new KeyTime(comment.getId(), calendar(comment.getCreated()), isPersonalized ? name : metricName);
                data.add(keyTime);
            }

        }
        return data;
    }

}
