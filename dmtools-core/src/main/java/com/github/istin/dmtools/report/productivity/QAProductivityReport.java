package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.WeeksReleaseGenerator;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.rules.*;
import com.github.istin.dmtools.report.ProductivityTools;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class QAProductivityReport extends AbstractJob<QAProductivityReportParams, ResultItem> {

    @Override
    public ResultItem runJob(QAProductivityReportParams qaProductivityReportParams) throws Exception {
        WeeksReleaseGenerator releaseGenerator = new WeeksReleaseGenerator(qaProductivityReportParams.getStartDate());
        String formula = qaProductivityReportParams.getFormula();
        Employees testers = Employees.getTesters(qaProductivityReportParams.getEmployees());
        String response = FileUtils.readFileToString(ProductivityTools.generate(BasicJiraClient.getInstance(), releaseGenerator, qaProductivityReportParams.getReportName() + (qaProductivityReportParams.isWeight() ? "_sp" : ""), formula, qaProductivityReportParams.getInputJQL(), generateListOfMetrics(qaProductivityReportParams), Release.Style.BY_SPRINTS, testers, qaProductivityReportParams.getIgnoreTicketPrefixes()));
        Set<String> unknownNames = testers.getUnknownNames();
        System.out.println("Unknown Names");
        System.out.println(unknownNames.size());
        System.out.println(unknownNames);
        return new ResultItem("qaProductivityReport", response);
    }

    @Override
    public AI getAi() {
        return null;
    }

    protected List<Metric> generateListOfMetrics(QAProductivityReportParams qaProductivityReportParams) throws IOException {
        List<Metric> listOfCustomMetrics = new ArrayList<>();
        createdBugs(qaProductivityReportParams, listOfCustomMetrics);
        numberOfAttachments(qaProductivityReportParams, listOfCustomMetrics);
        numberOfComponents(qaProductivityReportParams, listOfCustomMetrics);
        movedToDone(qaProductivityReportParams, listOfCustomMetrics);
        movedToReopened(qaProductivityReportParams, listOfCustomMetrics);
        createdTests(qaProductivityReportParams, listOfCustomMetrics);
        fieldsChanged(qaProductivityReportParams, listOfCustomMetrics);
        ticketLinksChanged(qaProductivityReportParams, listOfCustomMetrics);
        commentsWritten(qaProductivityReportParams, listOfCustomMetrics);
        ProductivityUtils.vacationDays(listOfCustomMetrics, Employees.getTesters(qaProductivityReportParams.getEmployees()));
//        numberOfRejectedBugs(qaProductivityReportParams, listOfCustomMetrics);
        return listOfCustomMetrics;
    }

    //

    protected void ticketLinksChanged(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Test Ticket Linked", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"link"}, false, false) {
            @Override
            public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
                if (IssueType.isTestCase(ticket.getIssueType())) {
                    return super.check(trackerClient, ticket);
                } else {
                    return Collections.emptyList();
                }
            }
        }));
    }

    protected void fieldsChanged(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Ticket Fields Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

    protected void commentsWritten(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Ticket Comments Written", qaProductivityReportParams.isWeight(), new CommentsWrittenRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), qaProductivityReportParams.getCommentsRegex())));
    }

    protected void createdBugs(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Created Bugs", qaProductivityReportParams.isWeight(), new BugsCreatorsRule(qaProductivityReportParams.getBugsProjectCode(), Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

    protected void numberOfAttachments(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Number of Attachments", qaProductivityReportParams.isWeight(), new TicketAttachmentRule(null, Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

    protected void numberOfComponents(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Number of Components", qaProductivityReportParams.isWeight(), new ComponentsRule(qaProductivityReportParams.getBugsProjectCode(), Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

    protected void movedToDone(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        Boolean weight = qaProductivityReportParams.isWeight();
        listOfCustomMetrics.add(new Metric("Stories Moved To Done", weight, new TicketMovedToStatusRule(qaProductivityReportParams.getStatusesDone(), null, false) {
            @Override
            public List<KeyTime> check(TrackerClient jiraClient, ITicket ticket) throws Exception {
                if (ProductivityUtils.isStory(qaProductivityReportParams, ticket)) {
                    List<KeyTime> keyTimes = super.check(jiraClient, ticket);
                    if (keyTimes != null && !keyTimes.isEmpty()) {
                        keyTimes = keyTimes.subList(0, 1);
                        KeyTime keyTime = keyTimes.get(0);
                        keyTime.setWho(findResponsibleQA(qaProductivityReportParams, jiraClient, ticket, qaProductivityReportParams.getStatusesInTesting(), qaProductivityReportParams.getStatusesDone()));
                    }
                    return keyTimes;
                } else {
                    return null;
                }
            }

        }));
    }

    public String findResponsibleQA(QAProductivityReportParams qaProductivityReportParams, TrackerClient trackerClient, ITicket ticket, String[] inProgressStatuses, String[] doneStatuses) throws IOException {
        Employees testers = Employees.getTesters(qaProductivityReportParams.getEmployees());
        String whoIsResponsible = ChangelogAssessment.findWhoIsResponsible(trackerClient, testers, ticket, inProgressStatuses);
        if (whoIsResponsible.equalsIgnoreCase(Employees.UNKNOWN)) {
            whoIsResponsible = ChangelogAssessment.findWhoIsResponsible(trackerClient, testers, ticket, doneStatuses);
        }
        return whoIsResponsible;
    }

    protected void movedToReopened(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {

        listOfCustomMetrics.add(new Metric("Items Moved to Reopened", qaProductivityReportParams.isWeight(), new TicketMovedToStatusRule(qaProductivityReportParams.getStatusesInTesting(), false) {
            @Override
            public List<KeyTime> check(TrackerClient jiraClient, ITicket ticket) throws Exception {
                if (ProductivityUtils.isIgnoreTask(qaProductivityReportParams.getIgnoreTicketPrefixes(), ticket)) return null;
                List<KeyTime> keyTimes = super.check(jiraClient, ticket);
                if (keyTimes != null && !keyTimes.isEmpty()) {
                    boolean isFirstTimeRight = !ChangelogAssessment.isFirstTimeRight(jiraClient, ticket.getTicketKey(), ticket, qaProductivityReportParams.getStatusesInDevelopment(), qaProductivityReportParams.getStatusesInTesting());
                    if (isFirstTimeRight) {
                        keyTimes = keyTimes.subList(0, 1);
                        KeyTime keyTime = keyTimes.get(0);
                        keyTime.setWho(findResponsibleQA(qaProductivityReportParams, jiraClient, ticket, qaProductivityReportParams.getStatusesInTesting(), qaProductivityReportParams.getStatusesDone()));
                        return keyTimes;
                    } else {
                        return null;
                    }
                }
                return keyTimes;
            }
        }));
    }

    protected void createdTests(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Created Tests", qaProductivityReportParams.isWeight(), new TestCasesCreatorsRule(qaProductivityReportParams.getTestCasesProjectCode(), Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

//    protected void numberOfRejectedBugs(QAProductivityReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
//        listOfCustomMetrics.add(new Metric("Number of Rejected Bugs", qaProductivityReportParams.isWeight(), new QAReportRules.NumberOfRejectedBugsRule()));
//    }
}