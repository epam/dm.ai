package com.github.istin.dmtools.estimations;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import com.github.istin.dmtools.report.ReportUtils;
import com.github.istin.dmtools.report.freemarker.*;
import freemarker.template.TemplateException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JEstimator extends AbstractJob<JEstimatorParams, ResultItem> {
    private static final Logger logger = LogManager.getLogger(JEstimator.class);

    @Override
    public AI getAi() {
        return null;
    }

    public static class AIEstimatedTicket extends ITicket.Wrapper {

        private String storyPointsField;

        public AIEstimatedTicket(String storyPointsField, ITicket ticket) {
            super(ticket);
            this.storyPointsField = storyPointsField;
        }

        private Double aiEstimation;

        public Double getAiEstimation() {
            return aiEstimation;
        }

        public void setAiEstimation(Double aiEstimation) {
            this.aiEstimation = aiEstimation;
        }

        @Override
        public double getWeight() {
            return getFields().getInt(storyPointsField);
        }

        @Override
        public String getTicketDescription() {
            String ticketDescription = super.getTicketDescription();
            if (ticketDescription == null) {
                return "";
            }
            return ticketDescription;
        }

    }


    public static void main(String[] args) throws Exception {
        runJob(args[0], args[1]);
    }

    @Override
    public ResultItem runJob(JEstimatorParams params) throws Exception {
        return runJob(params.getJQL(), params.getReportName());
    }

    private static ResultItem runJob(String jql, String reportName) throws Exception {
        TrackerClient<? extends ITicket> jira = BasicJiraClient.getInstance();
        BasicDialAI ai = new BasicDialAI();
        ConversationObserver conversationObserver = new ConversationObserver();
        ai.setConversationObserver(conversationObserver);
        JAssistant jAssistant = new JAssistant(jira, null, ai, new PromptManager(), conversationObserver);
        List<? extends ITicket> list = jira.searchAndPerform(jql, jira.getDefaultQueryFields());
        List<AIEstimatedTicket> tickets = list.stream()
                //.limit(5)
                .map((Function<ITicket, AIEstimatedTicket>) ticket -> new AIEstimatedTicket(Fields.STORY_POINTS, ticket))
                .collect(Collectors.toList());
        System.out.println(tickets.size());
        for (AIEstimatedTicket aiEstimatedTicket : tickets) {
            File file = new File("reports/JAI_ESTIMATION_" + aiEstimatedTicket.getKey() + ".html");
            if (file.exists()) {
                Double developerEstimation = parseFromFile(file);
                aiEstimatedTicket.setAiEstimation(developerEstimation);
            } else {
                try {
                    estimateTicket(aiEstimatedTicket, tickets, jAssistant, conversationObserver);
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                };
            }
        }


        GenericReport mainReport = new GenericReport();
        mainReport.setName("JAI ESTIMATIONS " + reportName);
        GenericRow row = new GenericRow(true);
        List<GenericCell> mainRowCells = row.getCells();
        mainRowCells.add(new GenericCell("Key"));
        mainRowCells.add(new GenericCell("Summary"));
        mainRowCells.add(new GenericCell("Human Estimate"));
        mainRowCells.add(new GenericCell("AI Estimate"));
        mainRowCells.add(new GenericCell("Details"));
        mainReport.getRows().add(row);
        for (AIEstimatedTicket aiEstimatedTicket : tickets) {
            row = new GenericRow(false);
            List<GenericCell> cells = row.getCells();
            cells.add(new TicketLinkCell(aiEstimatedTicket.getTicketKey(), aiEstimatedTicket.getTicketLink()));
            cells.add(new GenericCell(aiEstimatedTicket.getTicketTitle()));
            cells.add(new GenericCell(String.valueOf((int) aiEstimatedTicket.getWeight())));
            Double aiEstimation = aiEstimatedTicket.getAiEstimation();
            if (aiEstimation != null) {
                cells.add(new GenericCell(String.valueOf(aiEstimation.intValue())));
            } else {
                cells.add(new GenericCell(""));
            }
            cells.add(new LinkCell("details", "JAI_ESTIMATION_" + aiEstimatedTicket.getKey()+ ".html"));
            mainReport.getRows().add(row);
        }

        return new ResultItem(jql, publishReport(mainReport));
    }

    protected static Double parseFromFile(File file) throws IOException {
        String htmlContent = FileUtils.readFileToString(file, "UTF-8");
        Document doc = Jsoup.parse(htmlContent);

        // Select the table by its ID
        Element table = doc.select("table#Displaytable").first();

        // Select all rows in the table
        Elements rows = table.select("tr");

        // Get the last row
        Element lastRow = rows.last();

        // Select the second cell in the last row
        Element secondCell = lastRow.select("td").get(1);

        // Extract the text from the second cell
        String cellText = secondCell.text();

        // Assuming the number is always at the beginning of the cell text,
        // split the text by spaces and take the first part
        String numberAsString = cellText.split(" ")[0];

        // Parse the number
        Double number = Double.parseDouble(numberAsString);

        logger.info("Parsed number: {}", number);
        return number;
    };

    protected static String estimateTicket(AIEstimatedTicket aiEstimatedTicket, List<AIEstimatedTicket> tickets, JAssistant jAssistant, ConversationObserver conversationObserver) throws Exception {
        List<AIEstimatedTicket> listWithoutSource = tickets.stream().filter(ticket -> !ticket.getKey().equalsIgnoreCase(aiEstimatedTicket.getKey())).collect(Collectors.toList());
        Double developerEstimation = jAssistant.estimateStory("Developer", aiEstimatedTicket.getKey(), listWithoutSource, false);
        aiEstimatedTicket.setAiEstimation(developerEstimation);

        GenericReport genericReport = new GenericReport();
        genericReport.setName("JAI ESTIMATION " + aiEstimatedTicket.getKey());
        List<ConversationObserver.Message> messages = conversationObserver.getMessages();
        for (ConversationObserver.Message message : messages) {
            GenericRow row = new GenericRow(false);
            row.getCells().add(new GenericCell("<b>" + message.getAuthor() + "</b>"));
            row.getCells().add(new GenericCell(message.getText()));
            genericReport.getRows().add(row);
        }

        conversationObserver.printAndClear();
        return publishReport(genericReport);
    }

    protected static String publishReport(GenericReport genericReport) throws IOException, TemplateException {
        String name = genericReport.getName();
        return FileUtils.readFileToString(new ReportUtils().write(name, "table_report", genericReport, null));
    }
}
