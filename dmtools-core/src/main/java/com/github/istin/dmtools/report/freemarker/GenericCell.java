// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.apache.commons.lang3.StringEscapeUtils;

import java.text.DecimalFormat;

public class GenericCell {

    public static final String META_WIKI_GREEN = "class=\"highlight-green\"";
    public static final String META_WIKI_RED = "class=\"highlight-red\"";
    public static final String META_WIKI_YELLOW = "class=\"highlight-yellow\"";

    private int duration = 1;

    private String text = "";

    private String meta = "";

    public GenericCell() {
    }

    public GenericCell(String text) {
        this.text = text;
    }

    public GenericCell escape() {
        text = "&nbsp;"+StringEscapeUtils.escapeXml(text.replaceAll("&nbsp;","")).replaceAll("\n", "<br/>")+"&nbsp;";
        return this;
    }

    public int getDuration() {
        return duration;
    }

    public GenericCell setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public String getText() {
        if (text == null) {
            return "";
        }
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMeta() {
        return meta;
    }

    public GenericCell setMeta(String meta) {
        this.meta = meta;
        return this;
    }

    public static String roundOff(double val) {
        DecimalFormat f = new DecimalFormat("##");
        return f.format(val);
    }
}