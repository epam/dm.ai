// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import com.github.istin.dmtools.common.utils.HtmlCleaner;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public interface IComment extends IBody {
    IUser getAuthor();
    String getId();
    Date getCreated();
    class Impl {
        public static String checkCommentStartedWith(List<? extends IBody> comments, String commentPrefix) throws IOException {
            String cleanedPrefix = HtmlCleaner.cleanAllHtmlTags("", commentPrefix.replaceAll("\\\\\"", ""));
            for (IBody body : comments) {
                String cleanedComment = HtmlCleaner.cleanAllHtmlTags("", body.getBody().replaceAll("\\\\\"", ""));
                if (body.getBody().startsWith(commentPrefix) || body.getBody().startsWith("<p>"+commentPrefix) || cleanedComment.startsWith(cleanedPrefix)) {
                    return body.getBody();
                }
            }
            return null;
        }
    }
}
