// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableType;
import com.github.istin.dmtools.report.projectstatus.presentation.MarkdownTableGenerator;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

public class TableFactory {
    private final TableGenerator baseTableGenerator;
    private final TicketStatisticsCalculator statisticsCalculator;
    private final TicketSorter ticketSorter;
    private final ReportConfiguration config;

    public TableFactory(ReportConfiguration config) {
        this.config = config;
        this.baseTableGenerator = new MarkdownTableGenerator();
        this.statisticsCalculator = new TicketStatisticsCalculator(config);
        this.ticketSorter = new TicketSorter(config);
    }

    public TableGenerator createGenerator(TableType type) {
        switch (type) {
            case SUMMARY:
                return new SummaryTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter);
            case BUG_OVERVIEW:
            case BUGS_TABLE:
                return new BugTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter, false);
            case STORY_POINTS_DISTRIBUTION:
                return new StoryPointsTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter);
            case ROLE_DISTRIBUTION:
            case ROLE_SPECIFIC:
                return new RoleDistributionTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter, config);
            case TASKS_AND_STORIES:
                return new TasksAndStoriesTableGenerator(baseTableGenerator);
            case TIMELINE:
                return new TimelineTableGenerator(baseTableGenerator, ticketSorter);
            default:
                throw new IllegalArgumentException("Unknown table type: " + type);
        }
    }

    public TimelineTableGenerator createTimelineTableGenerator() {
        return new TimelineTableGenerator(baseTableGenerator, ticketSorter);
    }

    public SummaryTableGenerator createSummaryTableGenerator() {
        return new SummaryTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter);
    }

    public BugTableGenerator createBugTableGenerator(boolean countStoryPoints) {
        return new BugTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter, countStoryPoints);
    }

    public StoryPointsTableGenerator createStoryPointsTableGenerator() {
        return new StoryPointsTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter);
    }

    public RoleDistributionTableGenerator createRoleDistributionTableGenerator() {
        return new RoleDistributionTableGenerator(baseTableGenerator, statisticsCalculator, ticketSorter, config);
    }

    public TasksAndStoriesTableGenerator createTasksAndStoriesTableGenerator() {
        return new TasksAndStoriesTableGenerator(baseTableGenerator);
    }

    public LabelAnalysisGenerator createLabelAnalysisGenerator() {
        return new LabelAnalysisGenerator(baseTableGenerator, createTimelineTableGenerator());
    }
}