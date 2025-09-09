package com.github.istin.dmtools.sync;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.report.model.KeyTime;
import io.github.furstenheim.CopyDown;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public class SourceCodeTrackerSyncJob extends AbstractJob<SourceCodeTrackerSyncParams, ResultItem> {

    private static final Logger logger = LogManager.getLogger(SourceCodeTrackerSyncJob.class);

    @Override
    public ResultItem runJob(SourceCodeTrackerSyncParams sourceCodeTrackerSyncParams) throws Exception {
        List<SourceCode> sources = SourceCode.Impl.getConfiguredSourceCodes(null);
        for (SourceCode sourceCode : sources) {
            String defaultWorkspace = sourceCode.getDefaultWorkspace();
            String defaultRepository = sourceCode.getDefaultRepository();

            checkAndSyncPullRequests(
                    sourceCode,
                    defaultWorkspace,
                    defaultRepository,
                    sourceCodeTrackerSyncParams.getPullRequestState(),
                    new IssuesIDsParser(sourceCodeTrackerSyncParams.getIssueIdCodes()),
                    BasicJiraClient.getInstance(),
                    s -> {
                        ITicket.TicketPriority ticketPriority = ITicket.TicketPriority.byName(s);
                        if (ITicket.TicketPriority.isHighAttention(ticketPriority)) {
                            return sourceCodeTrackerSyncParams.getPriorityHighAttentionIcon();
                        } else if (ITicket.TicketPriority.isNormal(ticketPriority)) {
                            return sourceCodeTrackerSyncParams.getPriorityNormalIcon();
                        } else if (ITicket.TicketPriority.isLow(ticketPriority)) {
                            return sourceCodeTrackerSyncParams.getPriorityLowIcon();
                        } else {
                            return sourceCodeTrackerSyncParams.getPriorityDefaultIcon();
                        }
                    },
                    s -> {
                        if (IssueType.isBug(s)) {
                            return sourceCodeTrackerSyncParams.getBugIcon();
                        } else {
                            return sourceCodeTrackerSyncParams.getStoryIcon();
                        }
                    },
                    new SourceCodeTrackerSyncJob.StatusSyncDelegate() {

                        @Override
                        public void onMerged(IPullRequest pullRequest, ITicket ticket, TrackerClient tracker) throws IOException {
                            Status statusModel = ticket.getStatusModel();
                            String key = ticket.getTicketKey();
                            JSONModel onPullRequestMergedStatusesMapping = sourceCodeTrackerSyncParams.getOnPullRequestMergedStatusesMapping();
                            String[] statusesMapping = onPullRequestMergedStatusesMapping.getStringArray(statusModel.getName().toLowerCase());
                            String defaultStatus = sourceCodeTrackerSyncParams.getOnPullRequestMergedDefaultStatus();
                            moveToStatus(tracker, statusesMapping, key, defaultStatus);
                        }

                        @Override
                        public void onCreated(IPullRequest pullRequest, ITicket ticket, TrackerClient tracker) throws IOException {
                            Status statusModel = ticket.getStatusModel();
                            String key = ticket.getTicketKey();
                            JSONModel onPullRequestCreatedStatusesMapping = sourceCodeTrackerSyncParams.getOnPullRequestCreatedStatusesMapping();
                            String[] statusesMapping = onPullRequestCreatedStatusesMapping.getStringArray(statusModel.getName().toLowerCase());
                            String defaultStatus = sourceCodeTrackerSyncParams.getOnPullRequestCreatedDefaultStatus();
                            moveToStatus(tracker, statusesMapping, key, defaultStatus);
                        }
                    },
                    sourceCodeTrackerSyncParams.isCheckAllPullRequests(),
                    sourceCodeTrackerSyncParams.getAddPullRequestLabelsAsIssueType(), sourceCodeTrackerSyncParams.getInProgressReopenedStatuses()
            );
        }
        return new ResultItem("SourceCodeTrackerSync", "success");
    }

    @Override
    public AI getAi() {
        return null;
    }

    protected static void moveToStatus(TrackerClient tracker, String[] statusesMapping, String key, String defaultStatus) throws IOException {
        if (statusesMapping != null) {
            for (String status : statusesMapping) {
                tracker.moveToStatus(key, status);
            }
        } else {
            if (defaultStatus != null) {
                tracker.moveToStatus(key, defaultStatus);
            }
        }
    }

    public static interface StatusSyncDelegate {

        void onMerged(IPullRequest pullRequest, ITicket ticket, TrackerClient tracker) throws IOException;

        void onCreated(IPullRequest pullRequest, ITicket ticket, TrackerClient tracker) throws IOException;

    }

    public static void checkAndSyncPullRequests(SourceCode sourceCode, String workspace, String repository, String pullRequestState, IssuesIDsParser issuesIDsParser, TrackerClient tracker, Function<String, String> priorityToIcon, Function<String, String> issueTypeToIcon, StatusSyncDelegate statusSyncDelegate, boolean checkAllPullRequests, boolean addPullRequestLabels, String... inProgressReopenedStatuses) throws IOException {
        List<IPullRequest> bitbucketPullRequests = sourceCode.pullRequests(workspace, repository, pullRequestState, checkAllPullRequests, null);
        for (IPullRequest pullRequest : bitbucketPullRequests) {
            List<String> keys = issuesIDsParser.parseIssues(pullRequest.getTitle(), pullRequest.getSourceBranchName(), pullRequest.getDescription());
            boolean wasRenamed = false;
            for (String key : keys) {
                logger.info(key);
                wasRenamed = syncTicket(sourceCode, workspace, repository, pullRequestState, tracker, priorityToIcon, issueTypeToIcon, statusSyncDelegate, pullRequest, key, wasRenamed, addPullRequestLabels, inProgressReopenedStatuses);
            }
        }
    }

    public static boolean syncTicket(SourceCode sourceCode, String workspace, String repository, String pullRequestState, TrackerClient tracker, Function<String, String> priorityToIcon, Function<String, String> issueTypeToIcon, StatusSyncDelegate statusSyncDelegate, IPullRequest pullRequest, String key, boolean wasRenamed, boolean addPullRequestLabels, String... inProgressReopenedStatuses) throws IOException {
        ITicket ticket = null;
        try {
            ticket = tracker.performTicket(key, tracker.getDefaultQueryFields());
        } catch (AtlassianRestClient.RestClientException ignored) {
            ignored.printStackTrace();
        }
        if (ticket == null) {
            return false;
        }


        if (pullRequestState.equals(IPullRequest.PullRequestState.STATE_MERGED)) {
            List<KeyTime> reopened = ChangelogAssessment.findDatesWhenTicketWasInStatus(tracker, key, ticket, inProgressReopenedStatuses);
            boolean wasReopened = false;
            if (!reopened.isEmpty()) {
                for (KeyTime keyTime : reopened) {
                    if (keyTime.getWhen().compareTo(IPullRequest.Utils.getCreatedDateAsCalendar(pullRequest)) > 0) {
                        wasReopened = true;
                    }
                }
            }
            if (wasReopened) {
                return false;
            }

            if (statusSyncDelegate != null) {
                statusSyncDelegate.onMerged(pullRequest, ticket, tracker);
            }
        } else {
            if (!wasRenamed) {
                wasRenamed = true;
                renamePullRequest(workspace, repository, sourceCode, pullRequest, ticket, priorityToIcon, issueTypeToIcon, addPullRequestLabels);
            }

            if (statusSyncDelegate != null) {
                statusSyncDelegate.onCreated(pullRequest, ticket, tracker);
            }


            addSourceCodeCommentIfNotExists(sourceCode, pullRequest, key, ticket, workspace, repository);
        }
        addTrackerCommentIfNotExists(sourceCode, workspace, repository, tracker, pullRequest, key);
        return wasRenamed;
    }

    protected static void renamePullRequest(String workspace, String repo, SourceCode sourceCode, IPullRequest pullRequest, ITicket ticket, Function<String, String> priorityToIcon, Function<String, String> issueTypeToIcon, boolean addPullRequestLabels) throws IOException {
        String summary = ticket.getTicketTitle();
        String ticketIssueType = ticket.getIssueType();
        String ticketPriority = ticket.getPriority();
        if (addPullRequestLabels) {
            sourceCode.addPullRequestLabel(workspace, repo, String.valueOf(pullRequest.getId()), ticketIssueType);
        }
        String issueType = issueTypeToIcon == null ? ticketIssueType : issueTypeToIcon.apply(ticketIssueType);
        String priority = priorityToIcon == null ? ticketPriority : priorityToIcon.apply(ticketPriority);
        sourceCode.renamePullRequest(workspace, repo, pullRequest,  priority + " " + issueType + " " + ticket.getKey() + " " + summary);
    }

    protected static void addTrackerCommentIfNotExists(SourceCode sourceCode, String workspace, String repository, TrackerClient tracker, IPullRequest pullRequest, String key) throws IOException {
        String url = sourceCode.getPullRequestUrl(workspace, repository, String.valueOf(pullRequest.getId()));
        String author = pullRequest.getAuthor().getFullName();
        if (tracker.getTextType() == TrackerClient.TextType.HTML) {
            String comment = "Merge request by <b>" + author + "</b> <br/> <a href=\"" + url + "\">" + url + "</a>";
            tracker.postCommentIfNotExists(key, comment);
        } else {
            String comment = "Merge request by <b>" + author + "</b> <br/>" + url;
            CopyDown converter = new CopyDown();
            tracker.postCommentIfNotExists(key, converter.convert(comment).replaceAll("\\*\\*", "*"));
        }
    }

    public static void addSourceCodeCommentIfNotExists(SourceCode sourceCode, IPullRequest pullRequest, String key, ITicket ticket, String workspace, String repository) throws IOException {
        String message = buildMessage(key, ticket);
        String pullRequestId = pullRequest.getId().toString();
        List<IComment> comments = sourceCode.pullRequestComments(workspace, repository, pullRequestId);
        if (IComment.Impl.checkCommentStartedWith(comments, message) == null) {
            sourceCode.addPullRequestComment(workspace, repository, pullRequestId, message);
        }
    }

    public static @NotNull String buildMessage(String key, ITicket ticket) throws IOException {
        return "[" + key + "](" + ticket.getTicketLink() + ")" + " " + ticket.getTicketTitle().replaceAll("\"", "") + " ";
    }

}
