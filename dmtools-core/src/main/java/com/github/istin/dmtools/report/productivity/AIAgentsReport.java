// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.AIProvider;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.WeeksReleaseGenerator;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.expert.ExpertRequest;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.rules.CommentsWrittenRule;
import com.github.istin.dmtools.metrics.rules.TestCasesCreatorsRule;
import com.github.istin.dmtools.metrics.rules.TicketAttachmentRule;
import com.github.istin.dmtools.metrics.rules.TicketFieldsChangesRule;
import com.github.istin.dmtools.report.HtmlInjection;
import com.github.istin.dmtools.report.ProductivityTools;
import com.github.istin.dmtools.report.freemarker.DevProductivityReport;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AIAgentsReport extends AbstractJob<AIAgentsReportParams, ResultItem> {

    private static final String AI_RESPONSE_PATTERN = ".*AI Response.*";
    private static final String FEATURE_REVIEW_PATTERN =
            ".*" + ExpertRequest.BA_REVIEW.getRequest() +".*";

    // Pattern for AI Response WITH Feature Review
    private static final String AI_WITH_FEATURE_REVIEW =
            "(?=" + AI_RESPONSE_PATTERN + ")" +
                    "(?=" + FEATURE_REVIEW_PATTERN + ").*";

    // Pattern for AI Response WITHOUT Feature Review
    private static final String AI_WITHOUT_FEATURE_REVIEW =
            "(?=" + AI_RESPONSE_PATTERN + ")" +
                    "(?!" + FEATURE_REVIEW_PATTERN + ").*";

    // Map of patterns to their human-readable names
    private static final Map<String, String> PATTERN_NAMES = new LinkedHashMap<>();
    static {
        PATTERN_NAMES.put(AI_WITH_FEATURE_REVIEW, "Feature Review AI Responses");
        PATTERN_NAMES.put(AI_WITHOUT_FEATURE_REVIEW, "Other AI Responses");
        PATTERN_NAMES.put(".*similar test cases are linked and new test cases are generated.*", "Test Cases Generator");
    }

    private Map<String, Set<String>> usersPerRegex = new HashMap<>();
    private Map<String, Map<String, Integer>> interactionsPerRegexPerUser = new HashMap<>();
    private Map<String, Map<String, List<Date>>> userInteractionDates = new HashMap<>();
    private Set<String> allUsers = new HashSet<>();

    private List<String> listOfRequests = new ArrayList<>();

    @Override
    public ResultItem runJob(AIAgentsReportParams aiAgentsReportParams) throws Exception {
        usersPerRegex.clear();
        interactionsPerRegexPerUser.clear();
        userInteractionDates.clear();
        allUsers.clear();
        listOfRequests.clear();

        WeeksReleaseGenerator releaseGenerator = new WeeksReleaseGenerator(aiAgentsReportParams.getStartDate());
        String formula = aiAgentsReportParams.getFormula();
        TrackerClient<? extends ITicket> jira = BasicJiraClient.getInstance();
        File generatedFile = ProductivityTools.generate(jira, releaseGenerator, aiAgentsReportParams.getReportName() + (aiAgentsReportParams.isWeight() ? "_sp" : ""), formula, aiAgentsReportParams.getInputJQL(), generateListOfMetrics(aiAgentsReportParams), Release.Style.BY_SPRINTS, Employees.getTesters(aiAgentsReportParams.getEmployees()), aiAgentsReportParams.getIgnoreTicketPrefixes(), new HtmlInjection() {
            @Override
            public String getHtmBeforeTimeline(DevProductivityReport productivityReport) {
                try {
                    productivityReport.setDarkMode(aiAgentsReportParams.isDarkMode());
                    return generateAnalyticsHtml((BasicJiraClient) jira);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        String response = FileUtils.readFileToString(generatedFile);
        return new ResultItem("aiAgentsReport", response);
    }

    private String generateAnalyticsHtml(BasicJiraClient jira) throws Exception {
        StringBuilder html = new StringBuilder();

        // Add CSS styles
        html.append("<style>")
                .append(".analytics-table { width: 100%; border-collapse: collapse; margin: 10px 0; }")
                .append(".analytics-table th { background-color: #f4f5f7; text-align: left; padding: 8px; }")
                .append(".analytics-table td { padding: 8px; border-top: 1px solid #dfe1e6; }")
                .append(".analytics-table tr:hover { background-color: #f8f9fa; }")
                .append(".analytics-section { margin: 20px 0; }")
                .append(".analytics-header { color: #172B4D; font-size: 16px; margin: 16px 0 8px 0; }")
                .append(".analytics-count { color: #6B778C; font-size: 12px; margin-left: 8px; }")
                .append(".analytics-table td.pattern-cell { background-color: #EAE6FF; }")
                .append(".analytics-table td.number-cell { text-align: right; }")
                .append(".request-section { margin-top: 10px; }")
                .append(".request-header { cursor: pointer; color: #0052CC; padding: 8px; background-color: #f4f5f7; border: 1px solid #dfe1e6; }")
                .append(".request-details { display: none; padding: 10px; background-color: #ffffff; border: 1px solid #dfe1e6; border-top: none; }")
                .append(".request-toggle { color: #0052CC; margin-right: 8px; }")
                .append(".request-item { padding: 8px 0; border-bottom: 1px solid #dfe1e6; }")
                .append(".request-item:last-child { border-bottom: none; }")
                .append("</style>");

        html.append("<script>")
                .append("function toggleRequests() {")
                .append("  var details = document.getElementById('requests-details');")
                .append("  var toggle = document.getElementById('requests-toggle');")
                .append("  if (details.style.display === 'none') {")
                .append("    details.style.display = 'block';")
                .append("    toggle.textContent = '▼';")
                .append("  } else {")
                .append("    details.style.display = 'none';")
                .append("    toggle.textContent = '▶';")
                .append("  }")
                .append("}")
                .append("</script>");

        // Pre-fetch all user names
        Map<String, String> userNames = new HashMap<>();
        for (String userId : allUsers) {
            userNames.put(userId, getByIDFullName(jira, userId));
        }

        // All Unique Users table
        html.append("<div class='analytics-section'>")
                .append("<h3 class='analytics-header'>All Unique Users")
                .append("<span class='analytics-count'>(").append(allUsers.size()).append(")</span></h3>")
                .append("<table class='analytics-table'>");
        html.append("<tr><th>Name</th></tr>");
        allUsers.stream()
                .map(userId -> userNames.get(userId))
                .sorted()
                .forEach(name -> html.append("<tr><td>").append(name).append("</td></tr>"));
        html.append("</table></div>");

        // Users per Pattern table
        html.append("<div class='analytics-section'>")
                .append("<h3 class='analytics-header'>Users per Comment Pattern</h3>")
                .append("<table class='analytics-table'>");
        html.append("<tr><th>Pattern</th><th>Users</th></tr>");

        for (Map.Entry<String, Set<String>> entry : usersPerRegex.entrySet()) {
            html.append("<tr>");
            String patternName = PATTERN_NAMES.getOrDefault(entry.getKey(), entry.getKey());
            html.append("<td class='pattern-cell'>").append(patternName).append("</td>");
            html.append("<td>");
            String users = entry.getValue().stream()
                    .map(userId -> userNames.get(userId))
                    .sorted()
                    .collect(Collectors.joining("<br>"));
            html.append(users);
            html.append("</td></tr>");
        }
        html.append("</table></div>");

        // Interactions per Pattern table
        html.append("<div class='analytics-section'>")
                .append("<h3 class='analytics-header'>Interactions per User per Pattern</h3>")
                .append("<table class='analytics-table'>");
        html.append("<tr><th>Pattern</th><th>User</th><th>Interactions</th></tr>");

        for (Map.Entry<String, Map<String, Integer>> entry : interactionsPerRegexPerUser.entrySet()) {
            String pattern = PATTERN_NAMES.getOrDefault(entry.getKey(), entry.getKey());

            List<Map.Entry<String, Integer>> sortedUsers = entry.getValue().entrySet().stream()
                    .map(userEntry -> new AbstractMap.SimpleEntry<>(
                            userNames.get(userEntry.getKey()),
                            userEntry.getValue()))
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .collect(Collectors.toList());

            if (!sortedUsers.isEmpty()) {
                html.append("<tr>");
                html.append("<td class='pattern-cell' rowspan='").append(sortedUsers.size()).append("'>")
                        .append(pattern).append("</td>");
                html.append("<td>").append(sortedUsers.get(0).getKey()).append("</td>");
                html.append("<td class='number-cell'>").append(sortedUsers.get(0).getValue()).append("</td>");
                html.append("</tr>");

                for (int i = 1; i < sortedUsers.size(); i++) {
                    html.append("<tr>");
                    html.append("<td>").append(sortedUsers.get(i).getKey()).append("</td>");
                    html.append("<td class='number-cell'>").append(sortedUsers.get(i).getValue()).append("</td>");
                    html.append("</tr>");
                }
            }
        }
        html.append("</table></div>");
        addRecentUsersSection(html, userNames);
        if (!listOfRequests.isEmpty()) {
            html.append("<p>" + AIProvider.getCustomAI().chat("Give me summary of the user requests to LLM. I'm interesting in main topics and categories where users ask help. Your response must be nice looking html without tags: html, body because it will be injected to another html page. Use priorities of your statements most often used must be top, add some indications how many times it was used. \n" + listOfRequests) +"</p>");
            html.append("<div class='request-section'>")
                    .append("<div class='request-header' onclick='toggleRequests()'>")
                    .append("<span id='requests-toggle' class='request-toggle'>▶</span>")
                    .append("Show All Requests (").append(listOfRequests.size()).append(")")
                    .append("</div>")
                    .append("<div id='requests-details' class='request-details'>");

            for (String request : listOfRequests) {
                html.append("<div class='request-item'>")
                        .append(escapeHtml(request))
                        .append("</div>");
            }

            html.append("</div></div>");
        }

        return html.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Add this method to generate recent statistics:
    private void addRecentUsersSection(StringBuilder html, Map<String, String> userNames) {
        Date twoWeeksAgo = new Date(System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000L);

        Map<String, Map<String, Integer>> recentInteractions = new HashMap<>();

        // Calculate recent interactions
        for (Map.Entry<String, Map<String, List<Date>>> patternEntry : userInteractionDates.entrySet()) {
            String pattern = patternEntry.getKey();
            Map<String, Integer> recentUserCounts = new HashMap<>();

            for (Map.Entry<String, List<Date>> userEntry : patternEntry.getValue().entrySet()) {
                String userId = userEntry.getKey();
                long recentCount = userEntry.getValue().stream()
                        .filter(date -> date.after(twoWeeksAgo))
                        .count();

                if (recentCount > 0) {
                    recentUserCounts.put(userId, (int) recentCount);
                }
            }

            if (!recentUserCounts.isEmpty()) {
                recentInteractions.put(pattern, recentUserCounts);
            }
        }

        // Generate HTML for recent interactions
        if (!recentInteractions.isEmpty()) {
            html.append("<div class='analytics-section'>")
                    .append("<h3 class='analytics-header'>Top Users (Last 2 Weeks)</h3>")
                    .append("<table class='analytics-table'>")
                    .append("<tr><th>Pattern</th><th>User</th><th>Recent Interactions</th></tr>");

            for (Map.Entry<String, Map<String, Integer>> entry : recentInteractions.entrySet()) {
                String pattern = PATTERN_NAMES.getOrDefault(entry.getKey(), entry.getKey());

                List<Map.Entry<String, Integer>> sortedUsers = entry.getValue().entrySet().stream()
                        .map(userEntry -> new AbstractMap.SimpleEntry<>(
                                userNames.get(userEntry.getKey()),
                                userEntry.getValue()))
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .collect(Collectors.toList());

                if (!sortedUsers.isEmpty()) {
                    html.append("<tr>");
                    html.append("<td class='pattern-cell' rowspan='").append(sortedUsers.size()).append("'>")
                            .append(pattern).append("</td>");
                    html.append("<td>").append(sortedUsers.get(0).getKey()).append("</td>");
                    html.append("<td class='number-cell'>").append(sortedUsers.get(0).getValue()).append("</td>");
                    html.append("</tr>");

                    for (int i = 1; i < sortedUsers.size(); i++) {
                        html.append("<tr>");
                        html.append("<td>").append(sortedUsers.get(i).getKey()).append("</td>");
                        html.append("<td class='number-cell'>").append(sortedUsers.get(i).getValue()).append("</td>");
                        html.append("</tr>");
                    }
                }
            }

            html.append("</table></div>");
        }
    }

    private static String getByIDFullName(BasicJiraClient jira, String userId) {
        try {
            return jira.performProfile(userId).getFullName();
        } catch (IOException e) {
            return userId;
        }
    }

    @Override
    public AI getAi() {
        return null;
    }

    protected List<Metric> generateListOfMetrics(AIAgentsReportParams qaProductivityReportParams) throws IOException {
        List<Metric> listOfCustomMetrics = new ArrayList<>();
//        numberOfAttachments(qaProductivityReportParams, listOfCustomMetrics);
        createdTests(qaProductivityReportParams, listOfCustomMetrics);
//        summaryChanged(qaProductivityReportParams, listOfCustomMetrics);
//        descriptionChanged(qaProductivityReportParams, listOfCustomMetrics);
//        priorityChanged(qaProductivityReportParams, listOfCustomMetrics);
//        commentsWritten(qaProductivityReportParams, listOfCustomMetrics);
        //You're experienced business analyst.
        collectCommentsMetric(qaProductivityReportParams, listOfCustomMetrics, "Use Amount Of BA Expert", AI_WITH_FEATURE_REVIEW);
        collectCommentsMetric(qaProductivityReportParams, listOfCustomMetrics, "Use Amount Of AI Expert", AI_WITHOUT_FEATURE_REVIEW);
        collectCommentsMetric(qaProductivityReportParams, listOfCustomMetrics, "Use Amount TCs", ".*similar test cases are linked and new test cases are generated.*");
        ticketLinksChanged(qaProductivityReportParams, listOfCustomMetrics);
        return listOfCustomMetrics;
    }

    private void collectCommentsMetric(AIAgentsReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics, String metricName, String commentsRegex) {
        listOfCustomMetrics.add(new Metric(metricName, qaProductivityReportParams.isWeight(), new CommentsWrittenRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), commentsRegex) {
            @Override
            public KeyTime createKetTimeBasedOnComment(ITicket ticket, IComment comment, String authorName) {
                String body = comment.getBody();
                String notifierId = JiraClient.parseNotifierId(body);
                if (notifierId != null && !notifierId.isEmpty() && !notifierId.equalsIgnoreCase("null")) {
                    // Update analytics
                    allUsers.add(notifierId);
                    usersPerRegex.computeIfAbsent(commentsRegex, k -> new HashSet<>()).add(notifierId);
                    interactionsPerRegexPerUser
                            .computeIfAbsent(commentsRegex, k -> new HashMap<>())
                            .merge(notifierId, 1, Integer::sum);

                    userInteractionDates
                            .computeIfAbsent(commentsRegex, k -> new HashMap<>())
                            .computeIfAbsent(notifierId, k -> new ArrayList<>())
                            .add(comment.getCreated());

                    if (commentsRegex.equalsIgnoreCase(AI_WITHOUT_FEATURE_REVIEW)) {
                        listOfRequests.add(extractRequest(body));
                    }
                    try {
                        String patternName = PATTERN_NAMES.getOrDefault(commentsRegex, commentsRegex);
                        return new KeyTime(patternName + "_" + ticket.getTicketKey() + " " + getByIDFullName((BasicJiraClient) BasicJiraClient.getInstance(), notifierId), DateUtils.calendar(comment.getCreated()), authorName);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return null;
            }
        }));
    }

    private static final String REQUEST_PATTERN = "there is response on your request:\\s*(.*?)\\s*AI Response is:";

    // Usage example:
    public static String extractRequest(String text) {
        Pattern pattern = Pattern.compile(REQUEST_PATTERN, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

//    protected void summaryChanged(AIAgentsReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
//        listOfCustomMetrics.add(new Metric("Ticket Summary Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"summary"}, true, false)));
//    }
//
//    protected void descriptionChanged(AIAgentsReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
//        listOfCustomMetrics.add(new Metric("Ticket Description Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"description"}, true, false)));
//    }
//
//    protected void priorityChanged(AIAgentsReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
//        listOfCustomMetrics.add(new Metric("Ticket Priority Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"priority"}, false, false)));
//    }

    protected void ticketLinksChanged(AIAgentsReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
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

    protected void numberOfAttachments(AIAgentsReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Number of Attachments", qaProductivityReportParams.isWeight(), new TicketAttachmentRule(null, Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

    protected void createdTests(AIAgentsReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Created Tests", qaProductivityReportParams.isWeight(), new TestCasesCreatorsRule(null, Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

}