// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.code;

import com.github.istin.dmtools.atlassian.bitbucket.BasicBitbucket;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.github.BasicGithub;
import com.github.istin.dmtools.gitlab.BasicGitLab;
import com.github.istin.dmtools.networking.AbstractRestClient;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public interface SourceCode {

    List<IPullRequest> pullRequests(String workspace, String repository, String state, boolean checkAllRequests, Calendar startDate) throws IOException;

    IPullRequest pullRequest(String workspace, String repository, String pullRequestId) throws IOException;

    List<IComment> pullRequestComments(String workspace, String repository, String pullRequestId) throws IOException;

    List<IActivity> pullRequestActivities(String workspace, String repository, String pullRequestId) throws IOException;

    List<ITask> pullRequestTasks(String workspace, String repository, String pullRequestId) throws IOException;

    String addTask(Integer commentId, String text) throws IOException;

    String createPullRequestCommentAndTaskIfNotExists(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException;

    String createPullRequestCommentAndTask(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException;

    String addPullRequestComment(String workspace, String repository, String pullRequestId, String text) throws IOException;

    JSONModel commitComments(String workspace, String repository, String commitId) throws IOException;

    String renamePullRequest(String workspace, String repository, IPullRequest pullRequest, String newTitle) throws IOException;

    List<ITag> getTags(String workspace, String repository) throws IOException;

    List<ITag> getBranches(String workspace, String repository) throws IOException;

    void addPullRequestLabel(String workspace, String repository, String pullRequestId, String label) throws IOException;

    void removePullRequestLabel(String workspace, String repository, String pullRequestId, String label) throws IOException;

    List<IRepository> getRepositories(String namespace) throws IOException;

    List<ICommit> getCommitsBetween(String workspace, String repository, String from, String to) throws IOException;

    List<ICommit> getCommitsFromBranch(String workspace, String repository, String branchName, String startDate, String endDate) throws IOException;

    void performCommitsFromBranch(String workspace, String repository, String branchName, AbstractRestClient.Performer<ICommit> performer) throws Exception;

    IDiffStats getCommitDiffStat(String workspace, String repository, String commitId) throws IOException;

    IDiffStats getPullRequestDiff(String workspace, String repository, String pullRequestID) throws IOException;

    IBody getCommitDiff(String workspace, String repository, String commitId) throws IOException;

    IBody getDiff(String workspace, String repository, String pullRequestId) throws IOException;

    List<IFile> getListOfFiles(String workspace, String repository, String branchName)  throws IOException;

    String getFileContent(String selfLink) throws IOException;

    String getDefaultRepository();

    String getDefaultBranch();

    String getDefaultWorkspace();

    boolean isConfigured();

    String getBasePath();

    String getPullRequestUrl(String workspace, String repository, String id);

    List<IFile> searchFiles(String workspace, String repository, String query, int filesLimit) throws IOException, InterruptedException;

    SourceCodeConfig getDefaultConfig();

    String callHookAndWaitResponse(String hookUrl, String request) throws Exception;

    class Impl {

        public static List<SourceCode> getConfiguredSourceCodes(JSONArray sources) throws IOException {
            List<SourceCode> sourceCodes = new ArrayList<>();
            if (sources == null) {
                addGitHubSource(sourceCodes);
                addBitbucketSource(sourceCodes);
                addGitlabSource(sourceCodes);
            } else {
                for (int i = 0; i < sources.length(); i++) {
                    String sourceCode = sources.getString(i);
                    if (sourceCode.equalsIgnoreCase("github")) {
                        addGitHubSource(sourceCodes);
                    } else if (sourceCode.equalsIgnoreCase("bitbucket")) {
                        addBitbucketSource(sourceCodes);
                    } else if (sourceCode.equalsIgnoreCase("gitlab")) {
                        addGitlabSource(sourceCodes);
                    }
                }
            }

            return sourceCodes;
        }

        private static void addGitHubSource(List<SourceCode> sourceCodes) throws IOException {
            SourceCode githubSourceCode = BasicGithub.getInstance();
            if (githubSourceCode.isConfigured()) {
                sourceCodes.add(githubSourceCode);
            }
        }

    }

    static void addBitbucketSource(List<SourceCode> sourceCodes) throws IOException {
        SourceCode bitbucketSourceCode = BasicBitbucket.getInstance();
        if (bitbucketSourceCode.isConfigured()) {
            sourceCodes.add(bitbucketSourceCode);
        }
    }

    static void addGitlabSource(List<SourceCode> sourceCodes) throws IOException {
        SourceCode gitlabSourceCode = BasicGitLab.getInstance();
        if (gitlabSourceCode.isConfigured()) {
            if (gitlabSourceCode.getDefaultRepository() == null) {
                List<IRepository> repositories = gitlabSourceCode.getRepositories(gitlabSourceCode.getDefaultWorkspace());
                for (IRepository repository : repositories) {
                    sourceCodes.add(new BasicGitLab() {
                        @Override
                        public String getDefaultBranch() {
                            return "main";
                        }

                        @Override
                        public String getDefaultRepository() {
                            return repository.getName();
                        }
                    });
                }
            } else {
                sourceCodes.add(gitlabSourceCode);
            }
        }
    }
}