package com.github.istin.dmtools.sm;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScrumMasterDaily extends AbstractJob<ScrumMasterDailyParams, String> {
    private static final Logger logger = LogManager.getLogger(ScrumMasterDaily.class);
    @Override
    public String runJob(ScrumMasterDailyParams scrumMasterDailyParams) throws Exception {
        BasicConfluence confluence = BasicConfluence.getInstance();
        TrackerClient<? extends ITicket> trackerClient = BasicJiraClient.getInstance();
        Calendar yesterday = Calendar.getInstance();

        // Subtract one day to get yesterday
        yesterday.add(Calendar.DATE, -1);

        // Set the time to 00:00:00
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);

        List<Change> listOfChanges = new ArrayList<>();

        String searchQuery = "(" + scrumMasterDailyParams.getJql() + ") and (updated >= '-2d' or created >= '-2d')";
        logger.info("JQL: " + searchQuery);
        trackerClient.searchAndPerform(ticket -> {
            Calendar ticketCreatedCalendar = DateUtils.calendar(ticket.getCreated());
            if (ticketCreatedCalendar.compareTo(yesterday) >= 0) {
                Change change = new Change();
                change.setWhen(ticketCreatedCalendar);
                change.setWho(ticket.getCreator());
                change.setTicket(ticket);
                change.setHistoryItem(new IHistoryItem.NewTicketCreation());
                listOfChanges.add(change);
            }
            IChangelog changeLog = trackerClient.getChangeLog(ticket.getTicketKey(), ticket);
            List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
            boolean isFirstChange = true;
            for (IHistory history : histories) {
                IUser author = history.getAuthor();
                String authorName = author == null ? "" : author.getFullName();
                Calendar created = history.getCreated();
                if (history.getCreated().compareTo(yesterday) >= 0 && !authorName.contains("Automation")) {
                    if (isFirstChange) {
                        isFirstChange = false;
                        logger.info("Changes in ticket {}: {}", ticket.getKey(), ticket.getTicketTitle());
                    }
                    logger.info("By Author: {}", authorName);
                    logger.info("When: {}", DateUtils.formatToRallyDate(created));
                    for (IHistoryItem item : history.getHistoryItems()) {
                        String field = item.getField();
                        String fromString = item.getFromAsString();
                        String toString = item.getToAsString();

                        logger.info("Field: {}", field);
                        logger.info("From: {}", fromString == null ? "" : fromString);
                        logger.info("To: {}", toString == null ? "" : toString);
                        logger.info("\n");

                        Change change = new Change();
                        change.setWhen(created);
                        change.setTicket(ticket);
                        change.setWho(author);
                        change.setHistoryItem(item);
                        listOfChanges.add(change);
                    }
                }
            }
            if (!isFirstChange) {
                logger.info("-----");
            }
            return false;
        }, searchQuery, trackerClient.getDefaultQueryFields());

        logger.log(Level.INFO, listOfChanges.size());

        BasicDialAI ai = new BasicDialAI(null);
        PromptManager promptManager = new PromptManager();
        JAssistant jAssistant = new JAssistant(trackerClient, null, ai, promptManager);

        // Group by IUser.getName()
        Map<String, List<Change>> groupedByUserName = listOfChanges.stream()
                .collect(Collectors.groupingBy(change -> change.getWho().getFullName()));

        // Print the results
        StringBuilder finalPageDescription = new StringBuilder();
        groupedByUserName.forEach((name, changeList) -> {
            logger.log(Level.INFO, "User: " + name);
            try {
                finalPageDescription.append(jAssistant.makeDailyScrumReportOfUserWork(name, changeList)).append("\n");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        logger.log(Level.INFO, finalPageDescription);
        Content content = confluence.findContent(scrumMasterDailyParams.getConfluencePage());
        confluence.updatePage(content, BasicConfluence.macroCloudHTML(finalPageDescription.toString()));
        return finalPageDescription.toString();
    }

    @Override
    public AI getAi() {
        return null;
    }
}
