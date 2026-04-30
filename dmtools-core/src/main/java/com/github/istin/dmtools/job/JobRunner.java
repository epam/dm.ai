// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;


import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.ba.BusinessAnalyticDORGeneration;
import com.github.istin.dmtools.ba.RequirementsCollector;
import com.github.istin.dmtools.ba.UserStoryGenerator;
import com.github.istin.dmtools.dev.CodeGenerator;
import com.github.istin.dmtools.dev.CommitsTriage;
import com.github.istin.dmtools.dev.UnitTestsGenerator;
import com.github.istin.dmtools.diagram.DiagramsCreator;
import com.github.istin.dmtools.documentation.DocumentationGenerator;
import com.github.istin.dmtools.estimations.JEstimator;
import com.github.istin.dmtools.expert.Expert;
import com.github.istin.dmtools.presale.PreSaleSupport;
import com.github.istin.dmtools.qa.TestCasesGenerator;
import com.github.istin.dmtools.qa.InstructionsGenerator;
import com.github.istin.dmtools.report.productivity.BAProductivityReport;
import com.github.istin.dmtools.report.productivity.DevProductivityReport;
import com.github.istin.dmtools.report.productivity.QAProductivityReport;
import com.github.istin.dmtools.reporting.ReportGeneratorJob;
import com.github.istin.dmtools.sa.SolutionArchitectureCreator;
import com.github.istin.dmtools.sm.ScrumMasterDaily;
import com.github.istin.dmtools.sync.SourceCodeCommitTrackerSyncJob;
import com.github.istin.dmtools.sync.SourceCodeTrackerSyncJob;
import com.github.istin.dmtools.teammate.Teammate;
import com.github.istin.dmtools.js.JSRunner;
import com.github.istin.dmtools.kb.KBProcessingJob;
import com.github.istin.dmtools.mcp.cli.McpCliHandler;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import com.github.istin.dmtools.common.utils.JSONUtils;
import com.github.istin.dmtools.teammate.Teammate;
import com.github.istin.dmtools.js.JSRunner;
import com.github.istin.dmtools.kb.KBProcessingJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class JobRunner {

    private static final Logger logger = LogManager.getLogger(JobRunner.class);

    /**
     * Job factory that creates fresh instances for each execution to avoid 
     * race conditions and ensure proper initialization per execution mode.
     */
    private static Job createJobInstance(String jobName) {
        switch (jobName.toLowerCase()) {
            case "presalesupport": return new PreSaleSupport();
            case "documentationgenerator": return new DocumentationGenerator();
            case "requirementscollector": return new RequirementsCollector();
            case "jestimator": return new JEstimator();
            case "testcasesgenerator": return new TestCasesGenerator();
            case "instructionsgenerator": return new InstructionsGenerator();
            case "solutionarchitecturecreator": return new SolutionArchitectureCreator();
            case "diagramscreator", "diagramcreator": return new DiagramsCreator();
            case "codegenerator": return new CodeGenerator();
            case "devproductivityreport": return new DevProductivityReport();
            case "baproductivityreport": return new BAProductivityReport();
            case "businessanalyticdorgeneration": return new BusinessAnalyticDORGeneration();
            case "qaproductivityreport": return new QAProductivityReport();
            case "reportgenerator":
            case "reportgeneratorjob": return new ReportGeneratorJob();
            case "reportvisualizer":
            case "reportvisualizerjob": return new com.github.istin.dmtools.reporting.ReportVisualizerJob();
            case "scrummasterdaily": return new ScrumMasterDaily();
            case "expert": return new Expert();
            case "teammate": return new Teammate();
            case "sourcecodetrackersyncjob": return new SourceCodeTrackerSyncJob();
            case "sourcecodecommittrackersyncjob": return new SourceCodeCommitTrackerSyncJob();
            case "userstorygenerator": return new UserStoryGenerator();
            case "unittestsgenerator": return new UnitTestsGenerator();
            case "commitstriage": return new CommitsTriage();
            case "jsrunner": return new JSRunner();
            case "kbprocessing":
            case "kbprocessingjob": return new KBProcessingJob();
            default: return null;
        }
    }

    /**
     * Static job instances used only for job listing and name lookup.
     * These should NOT be used for execution to avoid race conditions.
     * Lazy initialization to avoid startup overhead.
     */
    private static volatile List<Job> JOBS = null;

    /**
     * Get the list of jobs with lazy initialization.
     * This method creates job instances only when needed (e.g., for listing).
     *
     * @return List of available jobs
     */
    private static List<Job> getJobs() {
        if (JOBS == null) {
            synchronized (JobRunner.class) {
                if (JOBS == null) {
                    logger.info("🚀 [PERFORMANCE] Lazy initialization of JOBS list");
                    long startTime = System.currentTimeMillis();
                    JOBS = Arrays.asList(
                            new PreSaleSupport(),
                            new DocumentationGenerator(),
                            new RequirementsCollector(),
                            new JEstimator(),
                            new TestCasesGenerator(),
                            new InstructionsGenerator(),
                            new SolutionArchitectureCreator(),
                            new DiagramsCreator(),
                            new CodeGenerator(),
                            new DevProductivityReport(),
                            new BAProductivityReport(),
                            new BusinessAnalyticDORGeneration(),
                            new QAProductivityReport(),
                            new ReportGeneratorJob(),
                            new com.github.istin.dmtools.reporting.ReportVisualizerJob(),
                            new ScrumMasterDaily(),
                            new Expert(),
                            new Teammate(),
                            new SourceCodeTrackerSyncJob(),
                            new SourceCodeCommitTrackerSyncJob(),
                            new UserStoryGenerator(),
                            new UnitTestsGenerator(),
                            new CommitsTriage(),
                            new JSRunner(),
                            new KBProcessingJob()
                    );
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("✅ [PERFORMANCE] JOBS list initialized in {}ms ({} jobs)", duration, JOBS.size());
                }
            }
        }
        return JOBS;
    }

    public static void main(String[] args) throws Exception {
        // Handle special arguments
        if (args.length > 0) {
            String firstArg = args[0];
            if ("--version".equals(firstArg) || "-v".equals(firstArg)) {
                String version = getVersion();
                System.out.println("DMTools " + version);
                System.out.println("A comprehensive development management toolkit");
                return;
            }
            if ("--help".equals(firstArg) || "-h".equals(firstArg)) {
                printHelp();
                return;
            }
            if ("--list-jobs".equals(firstArg)) {
                listJobs();
                return;
            }
            if ("mcp".equals(firstArg)) {
                // Handle MCP CLI commands
                McpCliHandler mcpHandler = new McpCliHandler();
                String result = mcpHandler.processMcpCommand(args);
                System.out.println(result);
                return;
            }
            if ("run".equals(firstArg)) {
                // Handle new run command with file + optional encoded parameter
                RunCommandProcessor processor = new RunCommandProcessor();
                JobParams jobParams = processor.processRunCommand(args);
                Object result = new JobRunner().run(jobParams);
                if (result == null) {
                    System.err.println("Execution result of '" + jobParams.getName() + "' is null.");
                } else {
                    System.out.println(JSONUtils.serializeResult(result));
                    return;
                }
                System.exit(1);
            }
        }
        
        if (args.length == 0) {
            printHelp();
            return;
        }
        
        // Existing base64-only parameter handling for backward compatibility
        String decodedParams = new String(decodeBase64(args[0]));
        validateJobParamsJson(decodedParams);
        JobParams jobParams = new JobParams(decodedParams);
        Object result = new JobRunner().run(jobParams);
        if (result == null) {
            System.err.println("Execution result of '" + jobParams.getName() + "' is null.");
        } else {
            System.out.println(JSONUtils.serializeResult(result));
            return;
        }
        System.exit(1);
    }

    /**
     * Executes the job described by {@code jobParams}.
     *
     * <p><b>Thread-pool warning:</b> Per-job credential overrides are stored in a
     * {@link ThreadLocal} and cleared in a {@code finally} block.  This design is safe
     * only when each invocation runs on a <em>dedicated</em> thread (CLI / single-threaded
     * executor).  If this method is ever called from a shared thread pool (e.g., a servlet
     * container or {@code CompletableFuture} executor), a thread that previously ran Job A
     * (with Tenant A's credentials) could be reused for Job B before the ThreadLocal is
     * cleared — especially if an interrupt or JVM crash occurs between
     * {@code setOverrides} and {@code clearOverrides}.  In that scenario, migrate to
     * explicit credential passing through the call stack instead of ThreadLocal storage.
     */
    public Object run(JobParams jobParams) throws Exception {
        ExecutionMode mode = jobParams.getExecutionMode();
        JSONObject resolvedIntegrations = jobParams.getResolvedIntegrations();
        
        logger.info("Executing job: {} in mode: {} with {} integrations", 
                   jobParams.getName(), 
                   (mode != null ? mode : "null"),
                   (resolvedIntegrations != null ? resolvedIntegrations.length() : 0));
        
        // Create a fresh job instance to avoid race conditions and ensure proper initialization
        Job job = createJobInstance(jobParams.getName());
        
        if (job != null) {
            logger.info("Created fresh job instance: {}", job.getClass().getSimpleName());
            
            Object paramsByClass = jobParams.getParamsByClass(job.getParamsClass());
            
            // Apply per-job env variable overrides (from envVariables in JSON params)
            if (paramsByClass instanceof TrackerParams) {
                java.util.Map<String, String> envVariables = ((TrackerParams) paramsByClass).getEnvVariables();
                if (envVariables != null && !envVariables.isEmpty()) {
                    logger.info("Applying {} env variable override(s) for job: {}", envVariables.size(), jobParams.getName());
                    PropertyReader.setOverrides(envVariables);
                }
            }

            try {
                // Initialize job for the appropriate execution mode
                if (job instanceof AbstractJob) {
                    AbstractJob<?, ?> abstractJob = (AbstractJob<?, ?>) job;
                    logger.info("Calling initializeForMode with mode: {}", mode);
                    abstractJob.initializeForMode(mode, resolvedIntegrations);
                    logger.info("Job initialization completed for mode: {}", mode);
                }
                
                initMetadata(job, paramsByClass);
                
                logger.info("Starting job execution...");
                Object result = job.runJob(paramsByClass);
                logger.info("Job execution completed successfully");
                
                return result;
            } finally {
                PropertyReader.clearOverrides();
            }
        }
        
        throw new IllegalArgumentException("Unknown job name: " + jobParams.getName());
    }

    private static void printHelp() {
        System.out.println("DMTools CLI Wrapper");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  dmtools list                           # List available MCP tools");
        System.out.println("  dmtools run <json-file>                # Execute job with JSON config file");
        System.out.println("  dmtools run <json-file> <encoded>      # Execute job with file + encoded overrides");
        System.out.println("  dmtools <tool> [args...]              # Execute MCP tool with args");
        System.out.println("  dmtools <tool> --data '{\"json\"}'      # Execute with inline JSON");
        System.out.println("  dmtools <tool> --file params.json     # Execute with JSON file");
        System.out.println("  dmtools <tool> --verbose              # Execute with verbose output");
        System.out.println("  dmtools <tool> --debug                # Execute with debug output and error messages");
        System.out.println("  dmtools <tool> <<EOF                  # Execute with heredoc");
        System.out.println("  {\"json\": \"data\"}");
        System.out.println("  EOF");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  dmtools list");
        System.out.println("  dmtools run job-config.json");
        System.out.println("  dmtools run job-config.json \"eyJvdmVycmlkZSI6InZhbHVlIn0=\"  # base64 encoded");
        System.out.println("  dmtools run job-config.json \"%7B%22override%22%3A%22value%22%7D\"  # URL encoded");
        System.out.println("  dmtools jira_get_ticket DMC-479 summary,description");
        System.out.println("  dmtools jira_get_ticket --data '{\"key\": \"DMC-479\", \"fields\": [\"summary\"]}'");
        System.out.println("  dmtools jira_get_ticket <<EOF");
        System.out.println("  {");
        System.out.println("    \"key\": \"DMC-479\",");
        System.out.println("    \"fields\": [\"summary\", \"description\"]");
        System.out.println("  }");
        System.out.println("  EOF");
        System.out.println();
        System.out.println("Environment Variables:");
        System.out.println("  DMTOOLS_INTEGRATIONS    Comma-separated list of integrations (jira,confluence,figma)");
        System.out.println();
        System.out.println("Environment Files:");
        System.out.println("  The script automatically loads environment variables from the following files (in order of precedence):");
        System.out.println("  1. .env (current directory)");
        System.out.println("  2. dmtools.env (current directory)");
        System.out.println("  3. dmtools-local.env (current directory)");
        System.out.println("  4. .env (script directory)");
        System.out.println("  5. dmtools.env (script directory)");
        System.out.println("  6. dmtools-local.env (script directory)");
        System.out.println();
        System.out.println("  File format supports:");
        System.out.println("  - KEY=VALUE pairs");
        System.out.println("  - Comments (lines starting with #)");
        System.out.println("  - Quoted values: KEY=\"value with spaces\"");
        System.out.println("  - Empty lines (ignored)");
        System.out.println();
        System.out.println("  Common variables:");
        System.out.println("  - JIRA_BASE_PATH, JIRA_EMAIL, JIRA_API_TOKEN");
        System.out.println("  - CONFLUENCE_BASE_PATH, CONFLUENCE_API_TOKEN");
        System.out.println("  - FIGMA_API_KEY");
        System.out.println("  - GEMINI_API_KEY, OPEN_AI_API_KEY");
        System.out.println("  - GITHUB_TOKEN, GITLAB_TOKEN, BITBUCKET_TOKEN");
        System.out.println();
        System.out.println("Installation:");
        System.out.println("  If you don't have DMTools installed, run:");
        System.out.println("  curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash");
        System.out.println();
        System.out.println("DM.ai uses DMTools. Do you?");
    }

    private static void listJobs() {
        List<Job> jobs = getJobs();
        System.out.println("Available Jobs:");
        System.out.println("===============");
        for (Job job : jobs) {
            System.out.println("- " + job.getName());
        }
        System.out.println();
        System.out.println("Total: " + jobs.size() + " jobs available");
    }

    public static void initMetadata(Job job, Object paramsByClass) {
        if (paramsByClass instanceof Params) {
            Metadata metadata = ((Params) paramsByClass).getMetadata();
            if (metadata != null) {
                metadata.init(job);
            }
            AI ai = job.getAi();
            if (ai != null) {
                ai.setMetadata(metadata);
            }
        }
    }

    public static String decodeBase64(String input) {
        byte[] decodedBytes = Base64.getDecoder().decode(input);
        // Convert the decoded bytes to a string
        return new String(decodedBytes);
    }

    /**
     * Performs basic schema validation on decoded base64 job parameters.
     * Rejects empty payloads and invalid JSON, and provides a helpful list of
     * available job names when the {@code name} field is missing or unknown.
     *
     * @param json the decoded JSON string
     * @throws IllegalArgumentException if the payload fails validation
     */
    static void validateJobParamsJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Job parameters JSON must not be empty.");
        }
        try {
            JSONObject obj = new JSONObject(json);
            String name = obj.optString("name", "").trim();
            if (name.isEmpty()) {
                List<String> names = new java.util.ArrayList<>();
                for (Job job : getJobs()) {
                    names.add(job.getName());
                }
                throw new IllegalArgumentException(
                        "Job parameters JSON must contain a non-empty 'name' field. "
                        + "Available jobs: " + names);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("Job parameters are not valid JSON: " + e.getMessage());
        }
    }

    public static String encodeBase64(String input) {
        byte[] decodedBytes = Base64.getEncoder().encode(input.getBytes());
        // Convert the decoded bytes to a string
        return new String(decodedBytes);
    }

    private static String getVersion() {
        // Try to get version from manifest first
        try {
            Package pkg = JobRunner.class.getPackage();
            String version = pkg.getImplementationVersion();
            if (version != null && !version.isEmpty()) {
                return version;
            }
        } catch (Exception e) {
            // Ignore and try other methods
        }
        
        // Try to read from properties file
        try {
            Properties props = new Properties();
            InputStream is = JobRunner.class.getClassLoader().getResourceAsStream("version.properties");
            if (is != null) {
                props.load(is);
                String version = props.getProperty("version");
                if (version != null && !version.isEmpty()) {
                    return "v" + version;
                }
                is.close();
            }
        } catch (Exception e) {
            // Ignore and use default
        }
        
        // Default fallback
        return "v0.0.0";
    }
}
