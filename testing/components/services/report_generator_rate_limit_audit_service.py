from __future__ import annotations

import textwrap
from pathlib import Path

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.interfaces.report_generator_rate_limit_audit_service import (
    ReportGeneratorRateLimitAuditService as ReportGeneratorRateLimitAuditServiceContract,
)
from testing.core.models.process_execution_result import ProcessExecutionResult
from testing.core.models.report_generator_rate_limit_audit import (
    ReportGeneratorRateLimitAudit,
)


class ReportGeneratorRateLimitAuditService(ReportGeneratorRateLimitAuditServiceContract):
    _CLASSPATH_MARKER = "DMTOOLS_TEST_CLASSPATH::"
    _MISSING_HEADER_PROBE_CLASS_NAME = "ReportGeneratorMissingHeaderProbe"
    _INVALID_RESET_PROBE_CLASS_NAME = "ReportGeneratorInvalidResetHeaderProbe"

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        gradle_task: str | None = None,
        target_test: str | None = None,
        report_generator_path: str | None = None,
        expected_rate_limit_status: int | None = None,
        expected_invalid_reset_header_name: str | None = None,
        expected_invalid_reset_header_value: str | None = None,
        expected_invalid_reset_warning: str | None = None,
        expected_fallback_warning: str | None = None,
        expected_retry_log: str | None = None,
        expected_fallback_delay_ms: int | None = None,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.gradle_task = gradle_task
        self.target_test = target_test
        self.report_generator_path = (
            repository_root / report_generator_path if report_generator_path is not None else None
        )
        self.expected_rate_limit_status = expected_rate_limit_status
        self.expected_invalid_reset_header_name = expected_invalid_reset_header_name
        self.expected_invalid_reset_header_value = expected_invalid_reset_header_value
        self.expected_invalid_reset_warning = expected_invalid_reset_warning
        self.expected_fallback_warning = expected_fallback_warning
        self.expected_retry_log = expected_retry_log
        self.expected_fallback_delay_ms = expected_fallback_delay_ms
        self.gradlew_path = repository_root / "gradlew"

    def audit(self) -> ReportGeneratorRateLimitAudit:
        return ReportGeneratorRateLimitAudit(
            missing_header_probe_execution=self._run_probe(
                probe_class_name=self._MISSING_HEADER_PROBE_CLASS_NAME,
                rate_limit_status=429,
                retry_after_value=None,
                reset_header_value=None,
            ),
        )

    def run_audit(self) -> ReportGeneratorRateLimitAudit:
        self._require_dmc_1034_configuration()

        report_generator_text = self._read_report_generator_text()
        targeted_test_execution = self.runner.run(
            [
                str(self.gradlew_path),
                "--no-daemon",
                self.gradle_task,
                "--tests",
                self.target_test,
            ],
            cwd=self.repository_root,
        )
        invalid_reset_probe_execution = self._run_probe(
            probe_class_name=self._INVALID_RESET_PROBE_CLASS_NAME,
            rate_limit_status=self.expected_rate_limit_status,
            retry_after_value=None,
            reset_header_value=self.expected_invalid_reset_header_value,
        )

        return ReportGeneratorRateLimitAudit(
            execution=targeted_test_execution,
            invalid_reset_probe_execution=invalid_reset_probe_execution,
            report_generator_path=self.report_generator_path,
            invalid_reset_warning_present="Could not parse X-RateLimit-Reset header"
            in report_generator_text,
            fallback_warning_present=(
                "Rate limit metadata unavailable or invalid. Falling back to"
                in report_generator_text
            ),
        )

    def _run_probe(
        self,
        *,
        probe_class_name: str,
        rate_limit_status: int,
        retry_after_value: str | None,
        reset_header_value: str | None,
    ) -> ProcessExecutionResult:
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
                f'probe_source="$temp_dir/{probe_class_name}.java"',
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
                self._probe_source(
                    probe_class_name=probe_class_name,
                    rate_limit_status=rate_limit_status,
                    retry_after_value=retry_after_value,
                    reset_header_value=reset_header_value,
                ),
                "JAVA",
                "",
                'javac -cp "$classpath" -d "$temp_dir" "$probe_source"',
                "java \\",
                '  -Dlog4j2.configurationFile="$log_config" \\',
                "  -Dlog4j.configuration=log4j2-cli.xml \\",
                "  -Dlog4j2.disable.jmx=true \\",
                "  --add-opens java.base/java.lang=ALL-UNNAMED \\",
                '  -cp "$temp_dir:$classpath" \\',
                f"  {probe_class_name}",
            ]
        )
        return self.runner.run(["bash", "-lc", script], cwd=self.repository_root)

    def _probe_source(
        self,
        *,
        probe_class_name: str,
        rate_limit_status: int,
        retry_after_value: str | None,
        reset_header_value: str | None,
    ) -> str:
        retry_after_java = "null" if retry_after_value is None else f'"{retry_after_value}"'
        reset_header_java = "null" if reset_header_value is None else f'"{reset_header_value}"'

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

            public class {probe_class_name} {{
                private static final long EXPECTED_FALLBACK_DELAY_MS = {self.expected_fallback_delay_ms}L;
                private static final int EXPECTED_RATE_LIMIT_STATUS = {rate_limit_status};

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
                    when(rateLimitResponse.header("Retry-After")).thenReturn({retry_after_java});
                    when(rateLimitResponse.header("{self.expected_invalid_reset_header_name or 'X-RateLimit-Reset'}"))
                        .thenReturn({reset_header_java});

                    when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
                    when(sourceCode.getDefaultRepository()).thenReturn("repo");
                    when(sourceCode.getDefaultBranch()).thenReturn("main");
                    when(sourceCode.getCommitsFromBranch("workspace", "repo", "main", "2025-01-01", null))
                        .thenThrow(new RestClient.RateLimitException("rate limit", "rate limit", rateLimitResponse, EXPECTED_RATE_LIMIT_STATUS))
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
                    System.out.println("rateLimitStatus=" + EXPECTED_RATE_LIMIT_STATUS);
                    System.out.println("retryAfterHeader=" + {retry_after_java});
                    System.out.println("invalidResetHeader=" + {reset_header_java});
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

    def _read_report_generator_text(self) -> str:
        if self.report_generator_path is None:
            return ""
        return self.report_generator_path.read_text(encoding="utf-8")

    def _require_dmc_1034_configuration(self) -> None:
        required_values = {
            "gradle_task": self.gradle_task,
            "target_test": self.target_test,
            "report_generator_path": self.report_generator_path,
            "expected_rate_limit_status": self.expected_rate_limit_status,
            "expected_invalid_reset_header_name": self.expected_invalid_reset_header_name,
            "expected_invalid_reset_header_value": self.expected_invalid_reset_header_value,
            "expected_invalid_reset_warning": self.expected_invalid_reset_warning,
            "expected_fallback_warning": self.expected_fallback_warning,
            "expected_retry_log": self.expected_retry_log,
            "expected_fallback_delay_ms": self.expected_fallback_delay_ms,
        }
        missing = [name for name, value in required_values.items() if value is None]
        if missing:
            raise ValueError(
                "Missing DMC-1034 audit configuration: " + ", ".join(sorted(missing))
            )

    def format_failures(self, audit: ReportGeneratorRateLimitAudit) -> str:
        failures: list[str] = []

        if not audit.invalid_reset_warning_present and audit.report_generator_path is not None:
            failures.append(
                "ReportGenerator.java no longer contains the malformed X-RateLimit-Reset "
                "warning path required for graceful fallback handling."
            )
        if not audit.fallback_warning_present and audit.report_generator_path is not None:
            failures.append(
                "ReportGenerator.java no longer contains the safe fallback wait warning "
                "used when rate-limit metadata is unavailable or invalid."
            )
        if audit.execution is None:
            failures.append("The targeted ReportGenerator regression was not executed.")
        elif audit.execution.returncode != 0:
            failures.append(
                "The targeted ReportGenerator regression failed.\n"
                f"Command: {' '.join(audit.execution.args)}\n"
                f"stdout:\n{audit.execution.stdout}\n\nstderr:\n{audit.execution.stderr}"
            )

        probe_execution = audit.invalid_reset_probe_execution
        if probe_execution is None:
            failures.append("The DMC-1034 invalid reset header probe was not executed.")
        else:
            combined_output = probe_execution.combined_output
            if probe_execution.returncode != 0:
                failures.append(
                    "The DMC-1034 invalid reset header probe failed.\n"
                    f"Command: {' '.join(probe_execution.args)}\n"
                    f"stdout:\n{probe_execution.stdout}\n\nstderr:\n{probe_execution.stderr}"
                )
            if f"rateLimitStatus={self.expected_rate_limit_status}" not in combined_output:
                failures.append(
                    "The DMC-1034 probe did not confirm the required GitHub 403 rate-limit status."
                )
            if (
                f"invalidResetHeader={self.expected_invalid_reset_header_value}"
                not in combined_output
            ):
                failures.append(
                    "The DMC-1034 probe did not confirm the malformed X-RateLimit-Reset header value."
                )
            if f"observedDelays=[{self.expected_fallback_delay_ms}]" not in combined_output:
                failures.append(
                    "The DMC-1034 probe did not observe the expected safe fallback delay."
                )
            if self.expected_invalid_reset_warning not in combined_output:
                failures.append(
                    "The DMC-1034 probe did not emit the malformed X-RateLimit-Reset warning."
                )
            if self.expected_fallback_warning not in combined_output:
                failures.append(
                    "The DMC-1034 probe did not emit the safe fallback wait warning."
                )
            if self.expected_retry_log not in combined_output:
                failures.append("The DMC-1034 probe did not emit the expected retry log.")

        return "\n\n".join(failures)
