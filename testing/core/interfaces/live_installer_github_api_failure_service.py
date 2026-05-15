from __future__ import annotations

from typing import Any
from typing import Protocol
from testing.core.models.process_execution_result import ProcessExecutionResult


class LiveInstallerGitHubApiFailureService(Protocol):
    def simulate_github_api_failure(self) -> Any:
        raise NotImplementedError

    def normalized_stdout(self, execution: ProcessExecutionResult) -> str:
        raise NotImplementedError

    def normalized_stderr(self, execution: ProcessExecutionResult) -> str:
        raise NotImplementedError

    def normalized_combined_output(self, execution: ProcessExecutionResult) -> str:
        raise NotImplementedError
