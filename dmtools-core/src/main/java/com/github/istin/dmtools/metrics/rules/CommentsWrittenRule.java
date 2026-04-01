// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentsWrittenRule implements TrackerRule<ITicket> {

    private String customName;
    private final IEmployees employees;
    private final String commentsRegex;

    public CommentsWrittenRule(IEmployees employees, String commentsRegex) {
        this.employees = employees;
        this.commentsRegex = commentsRegex;
    }

    @Override
    public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
        List<IComment> comments = trackerClient.getComments(ticket.getTicketKey(), ticket);
        List<KeyTime> result = new ArrayList<>();
        for (IComment comment : comments) {
            IUser author = comment.getAuthor();
            if (author != null && employees.contains(author.getFullName())) {
                if (commentsRegex == null || findCommentByRegex(commentsRegex, comment.getBody())) {
                    String authorName = employees.transformName(author.getFullName());
                    KeyTime keyTime = createKetTimeBasedOnComment(ticket, comment, authorName);
                    if (keyTime != null) {
                        keyTime.setWeight(1);
                        result.add(keyTime);
                    }
                }
            }
        }
        return result;
    }

    public KeyTime createKetTimeBasedOnComment(ITicket ticket, IComment comment, String authorName) {
        return new KeyTime(ticket.getKey(), DateUtils.calendar(comment.getCreated()), authorName);
    }


    public static boolean findCommentByRegex(String commentRegex, String text) {
        Pattern pattern = Pattern.compile(commentRegex);

        // Match the pattern against the input string

        Matcher matcher = pattern.matcher(text);

        // Check if the pattern is found
        if (matcher.find()) {
            return true;

        }
        return false;
    }
}
