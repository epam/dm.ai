from __future__ import annotations

from pathlib import Path
from typing import Callable

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
        on_run: Callable[[Path], None] | None = None,
    ) -> None:
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        self.on_run = on_run
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
        if self.on_run is not None:
            self.on_run(cwd)
        return ProcessExecutionResult(
            args=call_args,
            cwd=cwd,
            returncode=self.returncode,
            stdout=self.stdout,
            stderr=self.stderr,
        )


def test_audit_reports_expected_directory_artifact_when_present(tmp_path: Path) -> None:
    artifact_path = tmp_path / "dmtools-core" / "build" / "libs" / "dmtools-v1.0.0-all.jar"

    runner = FakeProcessRunner(
        on_run=lambda _: _write_artifact(artifact_path),
    )
    service = ShadowJarBuildAuditService(
        repository_root=tmp_path,
        runner=runner,
    )

    audit = service.audit()

    assert runner.calls == [((str(tmp_path / "gradlew"), "--no-daemon", ":dmtools-core:shadowJar"), tmp_path)]
    assert audit.expected_directory_exists
    assert audit.expected_artifact_present
    assert audit.expected_artifacts_before_build == ()
    assert audit.expected_artifacts == (artifact_path,)
    assert audit.expected_artifacts_from_current_build == (artifact_path,)
    assert not audit.fallback_artifact_present


def test_audit_captures_fallback_artifact_when_ticket_expected_directory_is_missing(tmp_path: Path) -> None:
    fallback_artifact = tmp_path / "build" / "libs" / "dmtools-v1.0.0-all.jar"

    service = ShadowJarBuildAuditService(
        repository_root=tmp_path,
        runner=FakeProcessRunner(
            on_run=lambda _: _write_artifact(fallback_artifact),
        ),
    )

    audit = service.audit()

    assert not audit.expected_directory_exists
    assert not audit.expected_artifact_present
    assert audit.fallback_directory_exists
    assert audit.fallback_artifact_present
    assert audit.fallback_artifacts_before_build == ()
    assert audit.fallback_artifacts == (fallback_artifact,)
    assert audit.fallback_artifacts_from_current_build == (fallback_artifact,)
    failure_message = service.format_failure(audit)
    assert "Expected directory exists: False" in failure_message
    assert fallback_artifact.as_posix() in failure_message


def test_audit_does_not_treat_stale_expected_artifact_as_current_build_output(tmp_path: Path) -> None:
    expected_artifact = tmp_path / "dmtools-core" / "build" / "libs" / "dmtools-v1.0.0-all.jar"
    _write_artifact(expected_artifact)

    service = ShadowJarBuildAuditService(
        repository_root=tmp_path,
        runner=FakeProcessRunner(),
    )

    audit = service.audit()

    assert audit.expected_directory_exists
    assert audit.expected_artifact_present
    assert audit.expected_artifacts_before_build == (expected_artifact,)
    assert audit.expected_artifacts == (expected_artifact,)
    assert not audit.expected_artifact_built_this_run
    failure_message = service.format_failure(audit)
    assert "Observed expected-directory artifacts before build" in failure_message
    assert "Observed expected-directory artifacts created or updated by this build: <none>" in failure_message


def _write_artifact(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("jar", encoding="utf-8")
