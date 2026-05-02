from __future__ import annotations

from typing import Mapping, Protocol, Sequence

from testing.core.models.process_execution_result import ProcessExecutionResult


class InstallerScript(Protocol):
    @property
    def available_skills_csv(self) -> str:
        raise NotImplementedError

    @property
    def side_effect_marker(self) -> str:
        raise NotImplementedError

    def run_main(
        self,
        args: Sequence[str] = (),
        extra_env: Mapping[str, str] | None = None,
        post_script: str = "",
    ) -> ProcessExecutionResult:
        raise NotImplementedError

    def run_main_with_env_skills(self, skills_csv: str) -> ProcessExecutionResult:
        raise NotImplementedError

    def normalized_stdout(self, execution: ProcessExecutionResult) -> str:
        raise NotImplementedError

    def normalized_stderr(self, execution: ProcessExecutionResult) -> str:
        raise NotImplementedError

    def normalized_combined_output(self, execution: ProcessExecutionResult) -> str:
        raise NotImplementedError
