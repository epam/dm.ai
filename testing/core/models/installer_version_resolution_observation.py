from __future__ import annotations

from dataclasses import dataclass

from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class InstallerVersionResolutionObservation:
    execution: ProcessExecutionResult
    stdout: str
    stderr: str
    curl_calls: tuple[str, ...]

