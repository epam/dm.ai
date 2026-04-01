// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.IFile;
import com.github.istin.dmtools.common.model.ITextMatch;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class File extends JSONModel implements IFile {

    public File() {

    }

    public File(String json) throws JSONException {
        super(json);
    }

    public File(JSONObject json) {
        super(json);
    }

    @Override
    public String getPath() {
        return getString("path");
    }

    @Override
    public String getType() {
        //commit_directory
        //commit_file
        return getString("type");
    }

    @Override
    public boolean isDir() {
        return getType().equals("commit_directory");
    }

    @Override
    public String getSelfLink() {
        return getJSONObject("links").getJSONObject("self").getString("href");
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
        return List.of();
    }
}