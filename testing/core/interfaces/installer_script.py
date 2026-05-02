from __future__ import annotations

from typing import Mapping, Protocol, Sequence

from testing.core.models.process_execution_result import ProcessExecutionResult


class InstallerScript(Protocol):
    def run_main(
        self,
        args: Sequence[str] = (),
        extra_env: Mapping[str, str | None] | None = None,
        post_script: str = "",
    ) -> ProcessExecutionResult:
        raise NotImplementedError
