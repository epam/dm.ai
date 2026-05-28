from __future__ import annotations

from pathlib import Path

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.shadow_jar_build_audit import ShadowJarBuildAudit


class ShadowJarBuildAuditService:
    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        gradle_task: str = ":dmtools-core:shadowJar",
        expected_directory: str = "dmtools-core/build/libs",
        fallback_directory: str = "build/libs",
        artifact_pattern: str = "dmtools-v*-all.jar",
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.gradle_task = gradle_task
        self.expected_directory = repository_root / expected_directory
        self.fallback_directory = repository_root / fallback_directory
        self.artifact_pattern = artifact_pattern

    def audit(self) -> ShadowJarBuildAudit:
        gradle_command = (
            str(self.repository_root / "gradlew"),
            "--no-daemon",
            self.gradle_task,
        )
        execution = self.runner.run(
            gradle_command,
            cwd=self.repository_root,
        )
        return ShadowJarBuildAudit(
            gradle_command=gradle_command,
            execution=execution,
            expected_directory=self.expected_directory,
            fallback_directory=self.fallback_directory,
            artifact_pattern=self.artifact_pattern,
            expected_artifacts=self._find_artifacts(self.expected_directory),
            fallback_artifacts=self._find_artifacts(self.fallback_directory),
        )

    def format_failure(self, audit: ShadowJarBuildAudit) -> str:
        return (
            "The Gradle shadow JAR build did not produce the requested compatibility artifact in the "
            "ticket's expected directory.\n"
            f"Command: {' '.join(audit.gradle_command)}\n"
            f"Build return code: {audit.execution.returncode}\n"
            f"Expected directory: {audit.expected_directory}\n"
            f"Expected directory exists: {audit.expected_directory_exists}\n"
            f"Expected artifact pattern: {audit.artifact_pattern}\n"
            f"Observed expected-directory artifacts: {self._format_paths(audit.expected_artifacts)}\n"
            f"Observed fallback directory: {audit.fallback_directory}\n"
            f"Observed fallback-directory artifacts: {self._format_paths(audit.fallback_artifacts)}\n"
            f"stdout:\n{audit.execution.stdout}\n\nstderr:\n{audit.execution.stderr}"
        )

    def _find_artifacts(self, directory: Path) -> tuple[Path, ...]:
        if not directory.exists():
            return ()
        return tuple(
            sorted(
                directory.glob(self.artifact_pattern),
                key=lambda candidate: candidate.name,
            )
        )

    def _format_paths(self, paths: tuple[Path, ...]) -> str:
        if not paths:
            return "<none>"
        return ", ".join(path.as_posix() for path in paths)
