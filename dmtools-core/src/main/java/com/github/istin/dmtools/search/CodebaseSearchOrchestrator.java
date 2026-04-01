// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.search;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class CodebaseSearchOrchestrator extends AbstractSearchOrchestrator {

    private final SourceCode sourceCode;

    public CodebaseSearchOrchestrator(SourceCode sourceCode) throws IOException {
        super(); // Use parameterless constructor since SourceCode is not a TrackerClient
        this.sourceCode = sourceCode;
    }

    protected void setupDependencyInjection() {
    }

    @Override
    protected String getFullItemContent(Object item, Object platformContext) throws Exception {
        IFile file = (IFile) item;
        StringBuilder stringBuffer = new StringBuilder();
        String fileContent = sourceCode.getFileContent(file.getSelfLink());
        stringBuffer.append(file).append("\n");
        stringBuffer.append(fileContent).append("\n");
        return stringBuffer.toString();
    }

    @Override
    protected String getItemResourceKey(Object item) {
        return ((IFile) item).getSelfLink();
    }

    @Override
    protected List<Object> preprocessing(SearchStats searchStats, String fullTask, Object platformContext) throws Exception {
        List<IFile> result = sourceCode.getListOfFiles(
                sourceCode.getDefaultWorkspace(),
                sourceCode.getDefaultRepository(),
                sourceCode.getDefaultBranch()
        );
        return result.stream().filter(file -> !file.isDir()).collect(Collectors.toList());
    }

    @Override
    public List<IFile> searchItemsWithKeywords(String keyword, Object platformContext, int itemsLimit) throws Exception {
        try {
            return sourceCode.searchFiles(
                    sourceCode.getDefaultWorkspace(),
                    sourceCode.getDefaultRepository(),
                    keyword,
                    itemsLimit
            );
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public Object createInitialPlatformContext() {
        return sourceCode;
    }

    @Override
    protected Object getItemByKey(Object key, List<?> items) {
        for (Object o : items) {
            IFile file = (IFile) o;
            if (file.getPath().equalsIgnoreCase((String) key)) {
                return file;
            }
        }
        return null;
    }

    @Override
    protected String getKeyFieldValue() {
        return "path";
    }

    @Override
    protected String getSourceType() {
        return "files";
    }
}