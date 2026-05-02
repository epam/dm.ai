from __future__ import annotations

from typing import Any, Protocol

from testing.core.models.process_execution_result import ProcessExecutionResult


class DmtoolsCli(Protocol):
    @property
    def compatibility_response(self) -> str:
        raise NotImplementedError

    def run_job(
        self,
        job_name: str,
        params: dict[str, Any] | None = None,
    ) -> ProcessExecutionResult:
        raise NotImplementedError

    def parse_result(
        self,
        execution: ProcessExecutionResult,
    ) -> list[dict[str, Any]]:
        raise NotImplementedError

    def outbound_network_lines(
        self,
        execution: ProcessExecutionResult,
    ) -> list[str]:
        raise NotImplementedError
