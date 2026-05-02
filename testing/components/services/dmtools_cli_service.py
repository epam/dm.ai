from __future__ import annotations

import json
import re
import tempfile
from pathlib import Path
from typing import Any

from testing.core.interfaces.dmtools_cli import DmtoolsCli
from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


class DmtoolsCliService(DmtoolsCli):
    COMPATIBILITY_RESPONSE = (
        "CodeGenerator compatibility shim executed successfully. "
        "No action was taken and no code artifacts were produced."
    )
    _NETWORK_ACTIVITY_PATTERN = re.compile(r"(connect|sendto|sendmsg)\(")

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.gradlew_path = repository_root / "gradlew"
        self.shadow_jar_directory = repository_root / "build" / "libs"

    @property
    def compatibility_response(self) -> str:
        return self.COMPATIBILITY_RESPONSE

    def build_shadow_jar(self) -> Path:
        result = self.runner.run(
            [str(self.gradlew_path), "--no-daemon", ":dmtools-core:shadowJar"],
            cwd=self.repository_root,
        )
        if result.returncode != 0:
            raise AssertionError(
                "Failed to build the DMTools fat JAR.\n"
                f"stdout:\n{result.stdout}\n\nstderr:\n{result.stderr}"
            )
        return self.find_shadow_jar()

    def find_shadow_jar(self) -> Path:
        jars = sorted(
            self.shadow_jar_directory.glob("*-all.jar"),
            key=lambda candidate: candidate.stat().st_mtime,
            reverse=True,
        )
        if not jars:
            raise FileNotFoundError(
                f"No fat JAR was found under {self.shadow_jar_directory.as_posix()}."
            )
        return jars[0]

    def run_job(
        self,
        job_name: str,
        params: dict[str, Any] | None = None,
    ) -> ProcessExecutionResult:
        jar_path = self.find_shadow_jar() if any(self.shadow_jar_directory.glob("*-all.jar")) else self.build_shadow_jar()
        payload = {
            "name": job_name,
            "params": params or {},
        }

        with tempfile.TemporaryDirectory(prefix="dmtools-job-") as temp_dir:
            config_path = Path(temp_dir) / f"{job_name.lower()}.json"
            config_path.write_text(json.dumps(payload), encoding="utf-8")
            return self.runner.run(
                [
                    "java",
                    "-Dlog4j2.configurationFile=classpath:log4j2-cli.xml",
                    "-Dlog4j.configuration=log4j2-cli.xml",
                    "-Dlog4j2.disable.jmx=true",
                    "-Djava.net.preferIPv4Stack=true",
                    "-Djava.rmi.server.hostname=127.0.0.1",
                    "--add-opens",
                    "java.base/java.lang=ALL-UNNAMED",
                    "-XX:-PrintWarnings",
                    "-Dpolyglot.engine.WarnInterpreterOnly=false",
                    "-cp",
                    str(jar_path),
                    "com.github.istin.dmtools.job.JobRunner",
                    "run",
                    str(config_path),
                ],
                cwd=self.repository_root,
                trace_network=True,
            )

    def parse_result(self, execution: ProcessExecutionResult) -> list[dict[str, Any]]:
        return json.loads(execution.stdout)

    def outbound_network_lines(
        self,
        execution: ProcessExecutionResult,
    ) -> list[str]:
        return [
            line
            for line in execution.trace_lines
            if ("AF_INET" in line or "AF_INET6" in line)
            and self._NETWORK_ACTIVITY_PATTERN.search(line)
        ]
