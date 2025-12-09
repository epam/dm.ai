package com.github.istin.dmtools.common.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public interface ITicket extends Key, TicketLink, ToText {

    String getStatus() throws IOException;

    Status getStatusModel() throws IOException;

    String getTicketKey();

    String getIssueType() throws IOException;

    String getPriority() throws IOException;

    String getTicketTitle() throws IOException;

    String getTicketDescription();

    String getTicketDependenciesDescription();

    Date getCreated();

    JSONObject getFieldsAsJSON();

    Long getUpdatedAsMillis();

    IUser getCreator();

    Resolution getResolution();

    JSONArray getTicketLabels();

    Fields getFields();

    ReportIteration getIteration();

    List<? extends ReportIteration> getIterations();

    double getProgress() throws IOException;

    List<? extends IAttachment> getAttachments();

    TicketPriority getPriorityAsEnum();

    /**
     * Get field value as string in a tracker-agnostic way.
     * For Jira: uses Fields.getString()
     * For ADO: uses getFieldAsString()
     * 
     * @param fieldName The field name/code to retrieve
     * @return The field value as string, or null if not found
     */
    default String getFieldValueAsString(String fieldName) {
        Fields fields = getFields();
        if (fields != null) {
            return fields.getString(fieldName);
        }
        return null;
    }

    enum TicketPriority {
        High_Attention,
        Blocker,
        Critical,
        High,
        Medium,
        Major,
        Normal,
        Minor,
        Low,
        Trivial,
        NotSet;

        public static TicketPriority byName(String name) {
            if (name == null) {
                return NotSet;
            }
            try {
                return valueOf(name.replace(" ", "_"));
            } catch (Exception ignored) {
                return NotSet;
            }
        }

        public static boolean isHighAttention(TicketPriority ticketPriority) {
            return ticketPriority == High_Attention
                    || ticketPriority == Blocker
                    || ticketPriority == Critical;
        }

        public static boolean isNormal(TicketPriority ticketPriority) {
            return ticketPriority == Normal
                    || ticketPriority == Medium
                    || ticketPriority == Major
                    || ticketPriority == High;
        }

        public static boolean isLow(TicketPriority ticketPriority) {
            return ticketPriority == Minor
                    || ticketPriority == Low
                    || ticketPriority == Trivial;
        }
    }

    class Wrapper implements ITicket {

        private ITicket ticket;

        public ITicket getWrapped() {
            return ticket;
        }

        public Wrapper(ITicket ticket) {
            this.ticket = ticket;
        }

        @Override
        public String getStatus() throws IOException {
            return ticket.getStatus();
        }

        @Override
        public Status getStatusModel() throws IOException {
            return ticket.getStatusModel();
        }

        @Override
        public String getTicketKey() {
            return ticket.getTicketKey();
        }

        @Override
        public String getIssueType() throws IOException {
            return ticket.getIssueType();
        }

        @Override
        public String getTicketLink() {
            return ticket.getTicketLink();
        }

        @Override
        public String getPriority() throws IOException {
            return ticket.getPriority();
        }

        @Override
        public String getTicketTitle() throws IOException {
            return ticket.getTicketTitle();
        }

        @Override
        public String getTicketDescription() {
            return ticket.getTicketDescription();
        }

        @Override
        public String getTicketDependenciesDescription() {
            return ticket.getTicketDependenciesDescription();
        }

        @Override
        public Date getCreated() {
            return ticket.getCreated();
        }

        @Override
        public JSONObject getFieldsAsJSON() {
            return ticket.getFieldsAsJSON();
        }

        @Override
        public Long getUpdatedAsMillis() {
            return ticket.getUpdatedAsMillis();
        }

        @Override
        public IUser getCreator() {
            return ticket.getCreator();
        }

        @Override
        public Resolution getResolution() {
            return ticket.getResolution();
        }

        @Override
        public JSONArray getTicketLabels() {
            return ticket.getTicketLabels();
        }

        @Override
        public Fields getFields() {
            return ticket.getFields();
        }

        @Override
        public ReportIteration getIteration() {
            return null;
        }

        @Override
        public List<ReportIteration> getIterations() {
            return List.of();
        }

        @Override
        public double getProgress() throws IOException {
            return ticket.getProgress();
        }

        @Override
        public List<? extends IAttachment> getAttachments() {
            return ticket.getAttachments();
        }

        @Override
        public TicketPriority getPriorityAsEnum() {
            try {
                return TicketPriority.byName(getPriority());
            } catch (IOException e) {
                throw new IllegalStateException();
            }
        }

        @Override
        public String toText() throws IOException {
            return ticket.toText();
        }

        @Override
        public double getWeight() {
            return ticket.getWeight();
        }

        @Override
        public String getKey() {
            return ticket.getKey();
        }
    }

    interface ITicketProgress {

        double calc(ITicket ticket) throws IOException;

        static Integer compare(ITicket original, ITicket second) {
            try {
                double progress = original.getProgress();
                double secondaryProgress = second.getProgress();
                int compare = Double.compare(secondaryProgress, progress);
                if (compare != 0) {
                    return compare;
                }
                return null;
            } catch (IOException e) {
                return null;
            }
        };

        class Impl implements ITicketProgress {

            @Override
            public double calc(ITicket ticket) throws IOException {
                if (IssueType.isBug(ticket.getIssueType())) {
                    Status statusModel = ticket.getStatusModel();
                    if (statusModel.isDone() || statusModel.isCompleted() || statusModel.isAccepted()  || statusModel.isRelease()  || statusModel.isRejected() || statusModel.isCancelled()) {
                        return 100;
                    } else if (statusModel.isInDev() || statusModel.isInProgress()) {
                        return 50;
                    } else if (statusModel.isInReview() || statusModel.isCodeReview()  || statusModel.isReadyForReview()) {
                        return 60;
                    } else if (statusModel.isReadyForTesting() || statusModel.isReadyForTest()) {
                        return 80;
                    } else if (statusModel.isInTesting()) {
                        return 90;
                    } else if (statusModel.isReview()) {
                        return 95;
                    } else {
                        return 0;
                    }
                } else {
                    Status statusModel = ticket.getStatusModel();
                    if (statusModel == null) {
                        return 0;
                    }
                    if (statusModel.isDone() || statusModel.isRejected() || statusModel.isCancelled() || statusModel.isCompleted() || statusModel.isAccepted()  || statusModel.isRelease() ) {
                        return 100;
                    } else if (statusModel.isInAcceptance() || statusModel.isReview()) {
                        return 95;
                    } else if (statusModel.isInDev() || statusModel.isInProgress()) {
                        return 50;
                    } else if (statusModel.isInReview() || statusModel.isCodeReview() || statusModel.isReadyForReview()) {
                        return 60;
                    } else if (statusModel.isReadyForTesting() || statusModel.isReadyForTest() ) {
                        return 80;
                    } else if (statusModel.isInTesting() || statusModel.isTesting()) {
                        return 90;
                    } else {
                        return 0;
                    }
                }
            }

        }

    }

}