from __future__ import annotations

from pathlib import Path

from testing.components.services.shadow_jar_build_audit_service import (
    ShadowJarBuildAuditService,
)
from testing.core.models.process_execution_result import ProcessExecutionResult


class FakeProcessRunner:
    def __init__(
        self,
        *,
        returncode: int = 0,
        stdout: str = "build ok",
        stderr: str = "",
    ) -> None:
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        self.calls: list[tuple[tuple[str, ...], Path]] = []

    def run(
        self,
        args: tuple[str, ...] | list[str],
        cwd: Path,
        env: dict[str, str | None] | None = None,
        trace_network: bool = False,
    ) -> ProcessExecutionResult:
        del env, trace_network
        call_args = tuple(args)
        self.calls.append((call_args, cwd))
        return ProcessExecutionResult(
            args=call_args,
            cwd=cwd,
            returncode=self.returncode,
            stdout=self.stdout,
            stderr=self.stderr,
        )


def test_audit_reports_expected_directory_artifact_when_present(tmp_path: Path) -> None:
    expected_directory = tmp_path / "dmtools-core" / "build" / "libs"
    expected_directory.mkdir(parents=True)
    artifact_path = expected_directory / "dmtools-v1.0.0-all.jar"
    artifact_path.write_text("jar", encoding="utf-8")

    runner = FakeProcessRunner()
    service = ShadowJarBuildAuditService(
        repository_root=tmp_path,
        runner=runner,
    )

    audit = service.audit()

    assert runner.calls == [((str(tmp_path / "gradlew"), "--no-daemon", ":dmtools-core:shadowJar"), tmp_path)]
    assert audit.expected_directory_exists
    assert audit.expected_artifact_present
    assert audit.expected_artifacts == (artifact_path,)
    assert not audit.fallback_artifact_present


def test_audit_captures_fallback_artifact_when_ticket_expected_directory_is_missing(tmp_path: Path) -> None:
    fallback_directory = tmp_path / "build" / "libs"
    fallback_directory.mkdir(parents=True)
    fallback_artifact = fallback_directory / "dmtools-v1.0.0-all.jar"
    fallback_artifact.write_text("jar", encoding="utf-8")

    service = ShadowJarBuildAuditService(
        repository_root=tmp_path,
        runner=FakeProcessRunner(),
    )

    audit = service.audit()

    assert not audit.expected_directory_exists
    assert not audit.expected_artifact_present
    assert audit.fallback_directory_exists
    assert audit.fallback_artifact_present
    assert audit.fallback_artifacts == (fallback_artifact,)
    failure_message = service.format_failure(audit)
    assert "Expected directory exists: False" in failure_message
    assert fallback_artifact.as_posix() in failure_message
