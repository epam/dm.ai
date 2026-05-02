from __future__ import annotations

from pathlib import Path
from typing import Mapping, Protocol, Sequence

from testing.core.models.process_execution_result import ProcessExecutionResult


class ProcessRunner(Protocol):
    def run(
        self,
        args: Sequence[str],
        cwd: Path,
        env: Mapping[str, str | None] | None = None,
        trace_network: bool = False,
    ) -> ProcessExecutionResult:
        raise NotImplementedError
