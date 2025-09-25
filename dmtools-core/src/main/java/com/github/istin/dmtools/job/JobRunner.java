package com.github.istin.dmtools.job;


import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.model.Metadata;
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
import com.github.istin.dmtools.report.productivity.BAProductivityReport;
import com.github.istin.dmtools.report.productivity.DevProductivityReport;
import com.github.istin.dmtools.report.productivity.QAProductivityReport;
import com.github.istin.dmtools.sa.SolutionArchitectureCreator;
import com.github.istin.dmtools.sm.ScrumMasterDaily;
import com.github.istin.dmtools.sync.SourceCodeCommitTrackerSyncJob;
import com.github.istin.dmtools.sync.SourceCodeTrackerSyncJob;
import com.github.istin.dmtools.mcp.cli.McpCliHandler;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import com.github.istin.dmtools.teammate.Teammate;
import com.github.istin.dmtools.js.JSRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
            case "solutionarchitecturecreator": return new SolutionArchitectureCreator();
            case "diagramscreator", "diagramcreator": return new DiagramsCreator();
            case "codegenerator": return new CodeGenerator();
            case "devproductivityreport": return new DevProductivityReport();
            case "baproductivityreport": return new BAProductivityReport();
            case "businessanalyticdorgeneration": return new BusinessAnalyticDORGeneration();
            case "qaproductivityreport": return new QAProductivityReport();
            case "scrummasterdaily": return new ScrumMasterDaily();
            case "expert": return new Expert();
            case "teammate": return new Teammate();
            case "sourcecodetrackersyncjob": return new SourceCodeTrackerSyncJob();
            case "sourcecodecommittrackersyncjob": return new SourceCodeCommitTrackerSyncJob();
            case "userstorygenerator": return new UserStoryGenerator();
            case "unittestsgenerator": return new UnitTestsGenerator();
            case "commitstriage": return new CommitsTriage();
            case "jsrunner": return new JSRunner();
            default: return null;
        }
    }

    /**
     * Static job instances used only for job listing and name lookup.
     * These should NOT be used for execution to avoid race conditions.
     */
    protected static List<Job> JOBS = Arrays.asList(
            new PreSaleSupport(),
            new DocumentationGenerator(),
            new RequirementsCollector(),
            new JEstimator(),
            new TestCasesGenerator(),
            new SolutionArchitectureCreator(),
            new DiagramsCreator(),
            new CodeGenerator(),
            new DevProductivityReport(),
            new BAProductivityReport(),
            new BusinessAnalyticDORGeneration(),
            new QAProductivityReport(),
            new ScrumMasterDaily(),
            new Expert(),
            new Teammate(),
            new SourceCodeTrackerSyncJob(),
            new SourceCodeCommitTrackerSyncJob(),
            new UserStoryGenerator(),
            new UnitTestsGenerator(),
            new CommitsTriage(),
            new JSRunner()
    );

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
                    System.out.println(result);
                    return;
                }
                System.exit(1);
            }
        }
        
        if (args.length == 0) {
            System.err.println("Error: No arguments provided.");
            printHelp();
            System.exit(1);
        }
        
        // Existing base64-only parameter handling for backward compatibility
        JobParams jobParams = new JobParams(new String(decodeBase64(args[0])));
        Object result = new JobRunner().run(jobParams);
        if (result == null) {
            System.err.println("Execution result of '" + jobParams.getName() + "' is null.");
        } else {
            System.out.println(result);
            return;
        }
        System.exit(1);
    }

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
        }
        
        throw new IllegalArgumentException("Unknown job name: " + jobParams.getName());
    }

    private static void printHelp() {
        System.out.println("DMTools - Development Management Toolkit");
        System.out.println();
        System.out.println("Usage: java -jar dmtools.jar [OPTIONS] [BASE64_ENCODED_JOB_PARAMS]");
        System.out.println("       java -jar dmtools.jar run <json-file-path> [encoded-config]");
        System.out.println("       java -jar dmtools.jar mcp <command> [args...]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version, -v     Show version information");
        System.out.println("  --help, -h        Show this help message");
        System.out.println("  --list-jobs       List all available jobs");
        System.out.println();
        System.out.println("Run Commands:");
        System.out.println("  run <json-file>           Execute job with JSON config file only");
        System.out.println("  run <json-file> <encoded> Execute job with file + encoded overrides");
        System.out.println("                            (supports base64 and URL encoding auto-detection)");
        System.out.println();
        System.out.println("MCP Commands:");
        System.out.println("  mcp list                    List available MCP tools");
        System.out.println("  mcp <tool_name> [args...]   Execute MCP tool");
        System.out.println("  mcp <tool_name> --data '{\"json\": \"data\"}'  Execute with JSON data");
        System.out.println();
        System.out.println("For legacy job execution, provide Base64-encoded JSON parameters.");
        String baseUrl = System.getProperty("app.base-url", "http://localhost:8080");
        System.out.println("Use the web interface at " + baseUrl + " for easier job configuration.");
    }

    private static void listJobs() {
        System.out.println("Available Jobs:");
        System.out.println("===============");
        for (Job job : JOBS) {
            System.out.println("- " + job.getName());
        }
        System.out.println();
        System.out.println("Total: " + JOBS.size() + " jobs available");
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
