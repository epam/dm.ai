// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.broadcom.rally.model.RallyFields;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.report.model.KeyTime;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class JQLNumberCell extends GenericCell {

    private List<Key> items = new ArrayList<>();

    private String hrefClass;

    private boolean isTextUpdateNeeded = false;

    public boolean isWeightPrint() {
        return isWeightPrint;
    }

    public JQLNumberCell setWeightPrint(boolean weightPrint) {
        isWeightPrint = weightPrint;
        isTextUpdateNeeded = true;
        return this;
    }

    private boolean isWeightPrint = true;

    public boolean isCountPrint() {
        return isCountPrint;
    }

    public JQLNumberCell setCountPrint(boolean countPrint) {
        isCountPrint = countPrint;
        isTextUpdateNeeded = true;
        return this;
    }

    private boolean isCountPrint = true;

    private String basePath;

    public JQLNumberCell(String basePath, Collection<? extends Key> keys) {
        super(generateText(basePath, keys, true, true));
        items.addAll(keys);
        this.basePath = basePath;
    }

    public JQLNumberCell(String basePath) {
        super("0");
        this.basePath = basePath;
    }

    public List<Key> getItems() {
        return items;
    }

    public void addItems(Collection<? extends Key> keys) {
        items.addAll(keys);
        for (Key newK : keys) {
            boolean isExists = false;
            for (Key currentK : items) {
                if (currentK.getKey().equals(newK.getKey())) {
                    isExists = true;
                    break;
                }
            }
            if (!isExists) {
                items.add(newK);
            }
        }
        isTextUpdateNeeded = true;
    }

    private void update(String basePath) {
        setText(generateText(basePath, items, isCountPrint, isWeightPrint));
        isTextUpdateNeeded = false;
    }

    public void add(Key key) {
        int index = -1;

        for (int i = 0; i < items.size(); i++) {
            Key k = items.get(i);
            if (k.getKey().equals(key.getKey())) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            items.set(index, key);
        } else {
            items.add(key);
        }
        isTextUpdateNeeded = true;
    }

    public void removeKeyItem(KeyTime key) {
        Iterator<Key> iter = items.iterator();
        while(iter.hasNext()){
            KeyTime oldKey = (KeyTime) iter.next();
            if (oldKey.getKey().equals(key.getKey()) && oldKey.getWhen().getTimeInMillis() < key.getWhen().getTimeInMillis()) {
                iter.remove();
            }
        }
        isTextUpdateNeeded = true;
    }

    @Override
    public String getText() {
        if (isTextUpdateNeeded) {
            update(basePath);
        }
        return super.getText();
    }

    private static String generateText(String basePath, Collection<? extends Key> keys, boolean isCountPrint, boolean isWeightPrint) {
        if (keys.isEmpty()) {
            return "0";
        }
        return basePath.contains("rally") ? buildRallyKeysURL(basePath, keys, isCountPrint, isWeightPrint) : buildJiraJQLURL(basePath, keys, isCountPrint, isWeightPrint);
    }

    private static String buildRallyKeysURL(String basePath, Collection<? extends Key> keys, boolean isCountPrint, boolean isWeightPrint) {
        StringBuilder url = new StringBuilder(basePath + "/#/search?keywords=");
        double weightSum = 0;
        StringBuilder query = new StringBuilder("");
        boolean isFirst = true;
        for (Key key : keys) {
            String keyValue = key.getKey();
            //ignore not jira keys
            if (isFirst) {
                isFirst = false;
            } else {
                query.append(" OR ");
            }
            query.append(RallyFields.FORMATTED_ID + ":" + keyValue);
            double weight = key.getWeight();
            if (weight == 0) {
                //case when story/task is created but no story points set.
                weight = 1;
            }
            weightSum = weightSum + weight;
        }

        return "<a href=\"" + url.append(query) + "\">" +
                (isCountPrint ? keys.size() : "") +
                (isWeightPrint ? (isCountPrint ? "(" + roundOff(weightSum) + ")" : roundOff(weightSum)) : "") + "</a>";
    }

    @NotNull
    public static String buildJiraJQLURL(String basePath, Collection<? extends Key> keys, boolean isCountPrint, boolean isWeightPrint) {
        StringBuilder url = new StringBuilder(basePath + "/issues/?jql=");
        double weightSum = 0;
        StringBuilder jql = new StringBuilder("key in (");
        boolean isFirst = true;
        for (Key key : keys) {
            String keyValue = key.getKey();
            //ignore not jira keys
            if (keyValue.contains("-")) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    jql.append(",");
                }
                jql.append(keyValue);
            }
            double weight = key.getWeight();
            if (weight == 0) {
                //case when story/task is created but no story points set.
                weight = 1;
            }
            weightSum = weightSum + weight;
        }
        jql.append(")");

        try {
            return "<a href=\"" + url.append(URLEncoder.encode(jql.toString(), "UTF-8")) + "\">" +
                    (isCountPrint ? keys.size() : "") +
                    (isWeightPrint ? (isCountPrint ? "(" + roundOff(weightSum) + ")" : roundOff(weightSum)) : "") + "</a>";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
