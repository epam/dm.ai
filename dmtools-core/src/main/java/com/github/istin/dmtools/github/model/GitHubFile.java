// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.utils.HtmlCleaner;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class GitHubFile extends JSONModel implements IFile, ToText {

    public static final String TEXT_MATCHES = "text_matches";

    public GitHubFile() {

    }

    public GitHubFile(String json) throws JSONException {
        super(json);
    }

    public GitHubFile(JSONObject json) {
        super(json);
    }

    @Override
    public String getPath() {
        return getString("path");
    }

    @Override
    public String getType() {
        //tree
        //blob
        return getString("type");
    }

    @Override
    public boolean isDir() {
        return getType().equals("tree");
    }

    @Override
    public String getSelfLink() {
        return getString("url");
    }

    private String fileContent;

    @Override
    public String getFileContent() {
        return fileContent;
    }

    @Override
    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    @Override
    public List<ITextMatch> getTextMatches() {
        return getModels(GitHubTextMatch.class, TEXT_MATCHES);
    }

    @Override
    public String toText() throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("path", getPath());
            //json.put("type", getType());
            //json.put("url", getSelfLink());

            // Add fileContent if it exists
            if (fileContent != null) {
                json.put("fileContent", fileContent);
            }

            // Process and filter text matches
            List<ITextMatch> textMatches = getTextMatches();
            JSONArray filteredTextMatches = new JSONArray();
            for (ITextMatch textMatch : textMatches) {
                JSONObject textMatchJson = new JSONObject();
                textMatchJson.put("fragment", HtmlCleaner.cleanSvgFragment(HtmlCleaner.filterBase64InText(textMatch.getFragment())));

                // Process matches within the text match
                JSONArray filteredMatches = new JSONArray();
                List<IMatch> matches = textMatch.getMatches();
                for (IMatch match : matches) {
                    filteredMatches.put(HtmlCleaner.cleanSvgFragment(HtmlCleaner.filterBase64InText(match.getText())));
                }
                textMatchJson.put("matches", filteredMatches);

                filteredTextMatches.put(textMatchJson);
            }
            if (!filteredTextMatches.isEmpty()) {
                json.put(TEXT_MATCHES, filteredTextMatches);
            }
            return json.toString();
        } catch (JSONException e) {
            return super.toString();
        }
    }


}