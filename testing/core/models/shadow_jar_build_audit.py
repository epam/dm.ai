from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class ShadowJarBuildAudit:
    gradle_command: tuple[str, ...]
    execution: ProcessExecutionResult
    expected_directory: Path
    fallback_directory: Path
    artifact_pattern: str
    expected_artifacts_before_build: tuple[Path, ...]
    expected_artifacts: tuple[Path, ...]
    expected_artifacts_from_current_build: tuple[Path, ...]
    fallback_artifacts_before_build: tuple[Path, ...]
    fallback_artifacts: tuple[Path, ...]
    fallback_artifacts_from_current_build: tuple[Path, ...]

    @property
    def expected_directory_exists(self) -> bool:
        return self.expected_directory.exists()

    @property
    def fallback_directory_exists(self) -> bool:
        return self.fallback_directory.exists()

    @property
    def expected_artifact_present(self) -> bool:
        return bool(self.expected_artifacts)

    @property
    def expected_artifact_built_this_run(self) -> bool:
        return bool(self.expected_artifacts_from_current_build)

    @property
    def fallback_artifact_present(self) -> bool:
        return bool(self.fallback_artifacts)

    @property
    def fallback_artifact_built_this_run(self) -> bool:
        return bool(self.fallback_artifacts_from_current_build)
