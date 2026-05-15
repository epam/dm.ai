from __future__ import annotations

import textwrap
from dataclasses import dataclass
from pathlib import Path

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class ReportGeneratorRateLimitAudit:
    missing_header_probe_execution: ProcessExecutionResult


class ReportGeneratorRateLimitAuditService:
    _CLASSPATH_MARKER = "DMTOOLS_TEST_CLASSPATH::"
    _PROBE_CLASS_NAME = "ReportGeneratorMissingHeaderProbe"

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        expected_fallback_delay_ms: int,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.expected_fallback_delay_ms = expected_fallback_delay_ms
        self.gradlew_path = repository_root / "gradlew"

    def audit(self) -> ReportGeneratorRateLimitAudit:
        return ReportGeneratorRateLimitAudit(
            missing_header_probe_execution=self._run_missing_header_probe(),
        )

    def _run_missing_header_probe(self) -> ProcessExecutionResult:
        script = "\n".join(
            [
                "set -euo pipefail",
                "",
                'temp_dir="$(mktemp -d)"',
                "cleanup() {",
                '    rm -rf "$temp_dir"',
                "}",
                "trap cleanup EXIT",
                "",
                'init_script="$temp_dir/print-test-classpath.init.gradle"',
                'log_config="$temp_dir/log4j2-test.xml"',
                f'probe_source="$temp_dir/{self._PROBE_CLASS_NAME}.java"',
                "",
                "cat <<'GRADLE' > \"$init_script\"",
                "allprojects { project ->",
                "    afterEvaluate {",
                "        if (project.path == ':dmtools-core') {",
                "            tasks.register('printTestRuntimeClasspath') {",
                "                doLast {",
                f'                    println("{self._CLASSPATH_MARKER}" + project.sourceSets.test.runtimeClasspath.asPath)',
                "                }",
                "            }",
                "        }",
                "    }",
                "}",
                "GRADLE",
                "",
                f'classpath_line="$("{self.gradlew_path}" --no-daemon -q -I "$init_script" :dmtools-core:testClasses :dmtools-core:printTestRuntimeClasspath | grep \'{self._CLASSPATH_MARKER}\' | tail -n 1)"',
                f'classpath="${{classpath_line#{self._CLASSPATH_MARKER}}}"',
                "",
                "cat <<'LOG4J' > \"$log_config\"",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Configuration status=\"WARN\">",
                "  <Appenders>",
                "    <Console name=\"Console\" target=\"SYSTEM_OUT\">",
                "      <PatternLayout pattern=\"%msg%n\"/>",
                "    </Console>",
                "  </Appenders>",
                "  <Loggers>",
                "    <Root level=\"info\">",
                "      <AppenderRef ref=\"Console\"/>",
                "    </Root>",
                "  </Loggers>",
                "</Configuration>",
                "LOG4J",
                "",
                "cat <<'JAVA' > \"$probe_source\"",
                self._missing_header_probe_source(),
                "JAVA",
                "",
                'javac -cp "$classpath" -d "$temp_dir" "$probe_source"',
                "java \\",
                '  -Dlog4j2.configurationFile="$log_config" \\',
                "  -Dlog4j.configuration=log4j2-cli.xml \\",
                "  -Dlog4j2.disable.jmx=true \\",
                "  --add-opens java.base/java.lang=ALL-UNNAMED \\",
                '  -cp "$temp_dir:$classpath" \\',
                f"  {self._PROBE_CLASS_NAME}",
            ]
        )
        return self.runner.run(
            ["bash", "-lc", script],
            cwd=self.repository_root,
        )

    def _missing_header_probe_source(self) -> str:
        return textwrap.dedent(
            f"""
            import com.github.istin.dmtools.common.code.SourceCode;
            import com.github.istin.dmtools.common.model.ICommit;
            import com.github.istin.dmtools.common.model.IUser;
            import com.github.istin.dmtools.common.networking.RestClient;
            import com.github.istin.dmtools.reporting.ReportGenerator;
            import com.github.istin.dmtools.reporting.datasource.DataSourceFactory;
            import com.github.istin.dmtools.reporting.metrics.MetricFactory;
            import com.github.istin.dmtools.reporting.model.DataSourceConfig;
            import com.github.istin.dmtools.reporting.model.MetricConfig;
            import com.github.istin.dmtools.reporting.model.ReportConfig;
            import com.github.istin.dmtools.reporting.model.TimeGroupingConfig;
            import okhttp3.Response;

            import java.lang.reflect.Method;
            import java.util.ArrayList;
            import java.util.Calendar;
            import java.util.Collections;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            import static org.mockito.Mockito.*;

            public class {self._PROBE_CLASS_NAME} {{
                private static final long EXPECTED_FALLBACK_DELAY_MS = {self.expected_fallback_delay_ms}L;

                public static void main(String[] args) throws Exception {{
                    SourceCode sourceCode = mock(SourceCode.class);
                    ICommit commit = mock(ICommit.class);
                    IUser author = mock(IUser.class);
                    Calendar commitDate = Calendar.getInstance();
                    commitDate.set(2025, Calendar.JANUARY, 15, 10, 0, 0);
                    commitDate.set(Calendar.MILLISECOND, 0);

                    when(author.getFullName()).thenReturn("Author");
                    when(commit.getAuthor()).thenReturn(author);
                    when(commit.getHash()).thenReturn("abc123");
                    when(commit.getCommitterDate()).thenReturn(commitDate);
                    when(commit.getMessage()).thenReturn("Commit message");
                    when(commit.getUrl()).thenReturn("https://github.test/commit/abc123");

                    Response rateLimitResponse = mock(Response.class);
                    when(rateLimitResponse.header("Retry-After")).thenReturn(null);
                    when(rateLimitResponse.header("X-RateLimit-Reset")).thenReturn(null);

                    when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
                    when(sourceCode.getDefaultRepository()).thenReturn("repo");
                    when(sourceCode.getDefaultBranch()).thenReturn("main");
                    when(sourceCode.getCommitsFromBranch("workspace", "repo", "main", "2025-01-01", null))
                        .thenThrow(new RestClient.RateLimitException("rate limit", "rate limit", rateLimitResponse, 429))
                        .thenReturn(List.of(commit));

                    TestableReportGenerator generator = new TestableReportGenerator(sourceCode);
                    Map<?, ?> results =
                        invokeCollectDataFromAllSources(generator, sourceCode, createCommitsReportConfig());
                    Map<?, ?> commitsResults = (Map<?, ?>) results.get("commits");

                    if (!results.containsKey("commits")) {{
                        throw new AssertionError("Expected the commits data source to be collected after retry.");
                    }}
                    if (commitsResults == null || !commitsResults.containsKey("CommitsMetricSource")) {{
                        throw new AssertionError("Expected CommitsMetricSource results after retry.");
                    }}
                    if (!List.of(EXPECTED_FALLBACK_DELAY_MS).equals(generator.getObservedDelays())) {{
                        throw new AssertionError("Expected a single fallback delay of " + EXPECTED_FALLBACK_DELAY_MS + " ms but observed " + generator.getObservedDelays());
                    }}

                    verify(sourceCode, times(2)).getCommitsFromBranch(
                        eq("workspace"),
                        eq("repo"),
                        eq("main"),
                        eq("2025-01-01"),
                        isNull()
                    );

                    System.out.println("observedDelays=" + generator.getObservedDelays());
                    System.out.println("sourceNames=" + results.keySet());
                    System.out.println("metricNames=" + commitsResults.keySet());
                }}

                private static ReportConfig createCommitsReportConfig() {{
                    MetricConfig commitsMetric = new MetricConfig();
                    commitsMetric.setName("CommitsMetricSource");
                    commitsMetric.setParams(new HashMap<>());

                    DataSourceConfig dataSourceConfig = new DataSourceConfig();
                    dataSourceConfig.setName("commits");
                    dataSourceConfig.setParams(new HashMap<>(Map.of(
                        "workspace", "workspace",
                        "repository", "repo",
                        "branch", "main"
                    )));
                    dataSourceConfig.setMetrics(List.of(commitsMetric));

                    ReportConfig config = new ReportConfig();
                    config.setStartDate("2025-01-01");
                    config.setDataSources(List.of(dataSourceConfig));
                    config.setTimeGroupings(Collections.singletonList(new TimeGroupingConfig()));
                    return config;
                }}

                @SuppressWarnings("unchecked")
                private static Map<?, ?> invokeCollectDataFromAllSources(
                    ReportGenerator generator,
                    SourceCode sourceCode,
                    ReportConfig config
                ) throws Exception {{
                    Method method = ReportGenerator.class.getDeclaredMethod(
                        "collectDataFromAllSources",
                        ReportConfig.class,
                        com.github.istin.dmtools.common.tracker.TrackerClient.class,
                        SourceCode.class,
                        DataSourceFactory.class,
                        MetricFactory.class
                    );
                    method.setAccessible(true);
                    return (Map<?, ?>) method.invoke(
                        generator,
                        config,
                        null,
                        sourceCode,
                        new DataSourceFactory(),
                        new MetricFactory(null, sourceCode, null, null, config.getStartDate())
                    );
                }}

                private static class TestableReportGenerator extends ReportGenerator {{
                    private final List<Long> observedDelays = new ArrayList<>();

                    private TestableReportGenerator(SourceCode sourceCode) {{
                        super(null, sourceCode);
                    }}

                    @Override
                    protected void sleepBeforeRetry(long delayMs) {{
                        observedDelays.add(delayMs);
                    }}

                    private List<Long> getObservedDelays() {{
                        return observedDelays;
                    }}
                }}
            }}
            """
        ).strip()
