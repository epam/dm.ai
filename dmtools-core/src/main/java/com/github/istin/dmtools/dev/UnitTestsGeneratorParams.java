// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.job.BaseJobParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class UnitTestsGeneratorParams extends BaseJobParams {

    public static final String SRC_FOLDER = "srcFolder";
    public static final String ROOT_TESTS_FOLDER = "rootTestsFolder";
    public static final String FILE_EXTENSIONS = "fileExtensions";
    public static final String TEST_TEMPLATE = "testTemplate";
    public static final String TEST_FILE_NAME_POSTFIX = "testFileNamePostfix";
    public static final String PACKAGE_FILTER = "packageFilter";
    public static final String EXCLUDE_CLASSES = "excludeClasses";
    public static final String EXCLUDE_PATTERN = "excludePattern";
    public static final String VALIDATION_COMMAND = "validationCommand";
    public static final String ROLE = "role";
    public static final String RULES = "rules";

    public UnitTestsGeneratorParams() {

    }

    public UnitTestsGeneratorParams(String json) throws JSONException {
        super(json);
    }

    public UnitTestsGeneratorParams(JSONObject json) {
        super(json);
    }

    public String getSrcFolder() {
        return getString(SRC_FOLDER);
    }

    public String getRootTestsFolder() {
        return getString(ROOT_TESTS_FOLDER);
    }

    public String[] getFileExtensions() {
        return getStringArray(FILE_EXTENSIONS);
    }

    public String getTestTemplate() {
        return getString(TEST_TEMPLATE);
    }

    public String getPackageFilter() {
        return getString(PACKAGE_FILTER);
    }

    public String getRole() {
        return getString(ROLE);
    }

    public String getExcludePattern() {
        return getString(EXCLUDE_PATTERN);
    }

    public String getTestFileNamePostfix() {
        return getString(TEST_FILE_NAME_POSTFIX);
    }

    public String getRules() {
        return getString(RULES);
    }

    public String getValidationCommand() {
        return getString(VALIDATION_COMMAND);
    }

    public String[] getExcludeClasses() {
        return getStringArray(EXCLUDE_CLASSES);
    }

    public UnitTestsGeneratorParams setSrcFolder(String src) {
        set(SRC_FOLDER, src);
        return this;
    }

    public UnitTestsGeneratorParams setPackageFilter(String packageFilter) {
        set(PACKAGE_FILTER, packageFilter);
        return this;
    }

    public UnitTestsGeneratorParams setRootTestsFolder(String rootTests) {
        set(ROOT_TESTS_FOLDER, rootTests);
        return this;
    }

    public UnitTestsGeneratorParams setFileExtensions(String... extensions) {
        set(FILE_EXTENSIONS, new JSONArray(Arrays.asList(extensions)));
        return this;
    }

    public UnitTestsGeneratorParams setExcludeClasses(String... classes) {
        set(EXCLUDE_CLASSES, new JSONArray(Arrays.asList(classes)));
        return this;
    }

    public UnitTestsGeneratorParams setTestTemplate(String template) {
        set(TEST_TEMPLATE, template);
        return this;
    }

    public UnitTestsGeneratorParams setRole(String role) {
        set(ROLE, role);
        return this;
    }

    public UnitTestsGeneratorParams setTestFileNamePostfix(String testFileNamePostfix) {
        set(TEST_FILE_NAME_POSTFIX, testFileNamePostfix);
        return this;
    }

    public UnitTestsGeneratorParams setExcludePattern(String excludePattern) {
        set(EXCLUDE_PATTERN, excludePattern);
        return this;
    }

    public UnitTestsGeneratorParams setRules(String rules) {
        set(RULES, rules);
        return this;
    }

    public UnitTestsGeneratorParams setValidationCommand(String validationCommand) {
        set(VALIDATION_COMMAND, validationCommand);
        return this;
    }
}