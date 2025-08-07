package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.file.FileContentListener;
import com.github.istin.dmtools.file.SourceCodeReader;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class UnitTestsGenerator extends AbstractJob<UnitTestsGeneratorParams, ResultItem> {

    @Override
    public ResultItem runJob(UnitTestsGeneratorParams params) throws Exception {
        List<String> extensionsList = Arrays.asList(params.getFileExtensions());
        String[] excludeClasses = params.getExcludeClasses();
        SourceCodeReader sourceCodeReader = new SourceCodeReader(extensionsList, Paths.get(params.getSrcFolder()));
        ConversationObserver conversationObserver = new ConversationObserver();
        BasicDialAI ai = new BasicDialAI(conversationObserver);
        PromptManager promptManager = new PromptManager();
        JAssistant jAssistant = new JAssistant(null, null, ai, promptManager);
        sourceCodeReader.readSourceFiles(Paths.get(params.getSrcFolder()), new FileContentListener() {
            @Override
            public void onFileRead(String folderPath, String packageName, String fileName, String fileContent) throws Exception {
                try {
                    String packageFilter = params.getPackageFilter();
                    if (packageFilter == null || packageName.startsWith(packageFilter)) {
                        String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));
                        if (shouldExclude(baseFileName, excludeClasses) || shouldExcludeByPattern(baseFileName, params.getExcludePattern())) {
                            return;
                        }
                        System.out.println(folderPath + " " + fileName);

                        String testFileName = baseFileName + params.getTestFileNamePostfix();
                        String rootTestsFolder = params.getRootTestsFolder();
                        Path testFilePath;
                        if (rootTestsFolder == null) {
                            //assuming if target dir is not set use same dir as file hosted
                            testFilePath = Paths.get(params.getSrcFolder(), folderPath, testFileName);
                        } else {
                            testFilePath = Paths.get(rootTestsFolder, packageName.replace('.', '/'), testFileName);
                        }

                        if (!Files.exists(testFilePath)) {
                            createTestFileSkeleton(fileContent, testFilePath, baseFileName, packageName, params, jAssistant);
                        }
                    }
                } catch (Exception e) {
                    // Handle exceptions appropriately
                }
            }
        });
        return new ResultItem("unitests", "success");
    }

    @Override
    public AI getAi() {
        return null;
    }

    private boolean shouldExcludeByPattern(String baseFileName, String excludePattern) {
        if (excludePattern == null) {
            return false;
        }
        try {
            Pattern pattern = Pattern.compile(excludePattern);
            return pattern.matcher(baseFileName).matches();
        } catch (PatternSyntaxException e) {
            // Handle invalid regex pattern syntax here (optional)
            System.err.println("Invalid regex pattern: " + excludePattern);
            return false;
        }
    }

    public static boolean shouldExclude(String baseFileName, String[] excludeClasses) {
        if (excludeClasses == null || baseFileName == null) {
            return false;
        }

        // Convert the baseFileName to lower case for case-insensitive comparison
        String lowerCaseBaseFileName = baseFileName.toLowerCase();

        // Use a for-each loop to check each class name in the excludeClasses array
        for (String excludeClass : excludeClasses) {
            if (lowerCaseBaseFileName.equals(excludeClass.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    protected void createTestFileSkeleton(String fileContent, Path testFilePath, String className, String packageName, UnitTestsGeneratorParams params, JAssistant jAssistant) throws Exception {
        Files.createDirectories(testFilePath.getParent());
        String testClassContent = generateTestClassSkeleton(fileContent, className, packageName, params, jAssistant);
        if (testClassContent != null) {
            Files.writeString(testFilePath, testClassContent);
            //TODO need to think how to check the test
        }
    }

    protected String generateTestClassSkeleton(String fileContent, String className, String packageName, UnitTestsGeneratorParams params, JAssistant jAssistant) throws Exception {
        String testTemplate = params.getTestTemplate();
        if (testTemplate == null || testTemplate.isEmpty()) {
            // Default template if none provided
            return String.format("import org.junit.Test;\n\n" +
                    "public class %sTest {\n\n" +
                    "    @Test\n" +
                    "    public void test() {\n" +
                    "        // TODO: Implement test logic for %s\n" +
                    "    }\n" +
                    "}\n", className, className);
        } else {
            // Use provided template, replace placeholders
            return jAssistant.generateUnitTest(fileContent, className, packageName,
                    testTemplate.replace("${PACKAGE_NAME}", packageName)
                    .replace("${CLASS_NAME}", className)
                    .replace("${TEST_NAME}", className + "Test"), params);
        }
    }

    public static void main2(String[] args) throws Exception {
        UnitTestsGeneratorParams params = new UnitTestsGeneratorParams();
        params.setSrcFolder("src/main/java");
        params.setRootTestsFolder("src/test/java");
        params.setFileExtensions(".java");
        params.setPackageFilter("com.github.istin.dmtools");
        params.setExcludeClasses("UnitTestsGenerator");
        params.setTestFileNamePostfix("Test.java");
        params.setTestTemplate(
                "package ${PACKAGE_NAME};\n\n" +
                        "import org.junit.Test;\n\n" +
                        "public class ${TEST_NAME} {\n\n" +
                        "    @Test\n" +
                        "    public void test() {\n" +
                        "        // TODO: Implement test logic for ${CLASS_NAME}\n" +
                        "    }\n" +
                        "}"
        );
        params.setTestTemplate(
                "package ${PACKAGE_NAME};\n\n"
        );
        params.setRole("Java Developer");
        params.setRules(readRulesFromFile("jai_config/unit_tests_generation_rules.md"));

        UnitTestsGenerator generator = new UnitTestsGenerator();
        generator.runJob(params);
    }

    public static String readRulesFromFile(String filePath) throws IOException {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            System.err.println("Error reading rules file: " + e.getMessage());
            return ""; // Return an empty string if the file cannot be read
        }
    }
}
