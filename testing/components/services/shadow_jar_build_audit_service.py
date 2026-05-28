from __future__ import annotations

from pathlib import Path
from typing import Mapping

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
        expected_before = self._snapshot_artifacts(self.expected_directory)
        fallback_before = self._snapshot_artifacts(self.fallback_directory)
        gradle_command = (
            str(self.repository_root / "gradlew"),
            "--no-daemon",
            self.gradle_task,
        )
        execution = self.runner.run(
            gradle_command,
            cwd=self.repository_root,
        )
        expected_after = self._snapshot_artifacts(self.expected_directory)
        fallback_after = self._snapshot_artifacts(self.fallback_directory)
        return ShadowJarBuildAudit(
            gradle_command=gradle_command,
            execution=execution,
            expected_directory=self.expected_directory,
            fallback_directory=self.fallback_directory,
            artifact_pattern=self.artifact_pattern,
            expected_artifacts_before_build=self._paths_from_snapshot(expected_before),
            expected_artifacts=self._paths_from_snapshot(expected_after),
            expected_artifacts_from_current_build=self._find_changed_artifacts(
                before=expected_before,
                after=expected_after,
            ),
            fallback_artifacts_before_build=self._paths_from_snapshot(fallback_before),
            fallback_artifacts=self._paths_from_snapshot(fallback_after),
            fallback_artifacts_from_current_build=self._find_changed_artifacts(
                before=fallback_before,
                after=fallback_after,
            ),
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
            "Observed expected-directory artifacts before build: "
            f"{self._format_paths(audit.expected_artifacts_before_build)}\n"
            f"Observed expected-directory artifacts after build: {self._format_paths(audit.expected_artifacts)}\n"
            "Observed expected-directory artifacts created or updated by this build: "
            f"{self._format_paths(audit.expected_artifacts_from_current_build)}\n"
            f"Observed fallback directory: {audit.fallback_directory}\n"
            "Observed fallback-directory artifacts before build: "
            f"{self._format_paths(audit.fallback_artifacts_before_build)}\n"
            f"Observed fallback-directory artifacts after build: {self._format_paths(audit.fallback_artifacts)}\n"
            "Observed fallback-directory artifacts created or updated by this build: "
            f"{self._format_paths(audit.fallback_artifacts_from_current_build)}\n"
            f"stdout:\n{audit.execution.stdout}\n\nstderr:\n{audit.execution.stderr}"
        )

    def _snapshot_artifacts(self, directory: Path) -> dict[Path, tuple[int, int, int]]:
        if not directory.exists():
            return {}
        snapshots: dict[Path, tuple[int, int, int]] = {}
        for candidate in sorted(directory.glob(self.artifact_pattern), key=lambda item: item.name):
            stat_result = candidate.stat()
            snapshots[candidate] = (
                stat_result.st_size,
                stat_result.st_mtime_ns,
                stat_result.st_ino,
            )
        return snapshots

    def _paths_from_snapshot(self, snapshot: Mapping[Path, tuple[int, int, int]]) -> tuple[Path, ...]:
        return tuple(snapshot.keys())

    def _find_changed_artifacts(
        self,
        *,
        before: Mapping[Path, tuple[int, int, int]],
        after: Mapping[Path, tuple[int, int, int]],
    ) -> tuple[Path, ...]:
        changed_paths = [
            path
            for path, fingerprint in after.items()
            if before.get(path) != fingerprint
        ]
        return tuple(sorted(changed_paths, key=lambda candidate: candidate.name))

    def _format_paths(self, paths: tuple[Path, ...]) -> str:
        if not paths:
            return "<none>"
        return ", ".join(path.as_posix() for path in paths)
