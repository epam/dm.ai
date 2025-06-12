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

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class JobRunner {

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
            new SourceCodeTrackerSyncJob(),
            new SourceCodeCommitTrackerSyncJob(),
            new UserStoryGenerator(),
            new UnitTestsGenerator(),
            new CommitsTriage()
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
        }
        
        if (args.length == 0) {
            System.err.println("Error: No arguments provided.");
            printHelp();
            System.exit(1);
        }
        
        JobParams jobParams = new JobParams(new String(decodeBase64(args[0])));
        for (Job job : JOBS) {
            if (job.getName().equalsIgnoreCase(jobParams.getName())) {
                Object paramsByClass = jobParams.getParamsByClass(job.getParamsClass());
                initMetadata(job, paramsByClass);
                job.runJob(paramsByClass);
                return;
            }
        }
        System.err.println("Job '" + jobParams.getName() + "' not found.");
        System.exit(1);
    }

    private static void printHelp() {
        System.out.println("DMTools - Development Management Toolkit");
        System.out.println();
        System.out.println("Usage: java -jar dmtools.jar [OPTIONS] [BASE64_ENCODED_JOB_PARAMS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version, -v     Show version information");
        System.out.println("  --help, -h        Show this help message");
        System.out.println("  --list-jobs       List all available jobs");
        System.out.println();
        System.out.println("For job execution, provide Base64-encoded JSON parameters.");
        System.out.println("Use the web interface at http://localhost:8080 for easier job configuration.");
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
        return new java.lang.String(decodedBytes);
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
            java.util.Properties props = new java.util.Properties();
            java.io.InputStream is = JobRunner.class.getClassLoader().getResourceAsStream("version.properties");
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
