from __future__ import annotations

from collections.abc import Callable, Iterable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class RemovalTargetAudit:
    relative_path: str
    exists: bool

    @property
    def status(self) -> str:
        return "present" if self.exists else "absent"


@dataclass(frozen=True)
class LegacyRemovalAudit:
    file_audits: tuple[RemovalTargetAudit, ...]
    build_result: CommandResult

    @property
    def present_files(self) -> tuple[str, ...]:
        return tuple(audit.relative_path for audit in self.file_audits if audit.exists)


class Sandbox(Protocol):
    workspace: Path

    def cleanup(self) -> None: ...

    def run(self, command: str, timeout: int = 1800) -> CommandResult: ...


class LegacyRemovalAuditService:
    def __init__(
        self,
        repository_root: Path,
        removed_paths: Iterable[str],
        build_command: str,
        sandbox_factory: Callable[[Path], Sandbox] | None = None,
    ) -> None:
        self.repository_root = repository_root
        self.removed_paths = tuple(removed_paths)
        self.build_command = build_command
        self._sandbox_factory = sandbox_factory or self._create_sandbox

    def run_audit(self, timeout: int = 1800) -> LegacyRemovalAudit:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            file_audits = tuple(self._audit_paths(sandbox.workspace))
            build_result = sandbox.run(self.build_command, timeout=timeout)
            return LegacyRemovalAudit(
                file_audits=file_audits,
                build_result=build_result,
            )
        finally:
            sandbox.cleanup()

    def observe_repository_state(self) -> tuple[RemovalTargetAudit, ...]:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            return tuple(self._audit_paths(sandbox.workspace))
        finally:
            sandbox.cleanup()

    def format_failures(self, audit: LegacyRemovalAudit) -> str:
        lines = ["Legacy CodeGenerator cleanup verification failed."]
        for index, file_audit in enumerate(audit.file_audits, start=1):
            expected = "absent"
            actual = file_audit.status
            status = "PASS" if not file_audit.exists else "FAIL"
            lines.append(
                f"Step {index}: expected {file_audit.relative_path} to be {expected}, observed {actual} -> {status}"
            )

        build_step = len(audit.file_audits) + 1
        build_status = "PASS" if audit.build_result.returncode == 0 else "FAIL"
        lines.append(
            f"Step {build_step}: run `{audit.build_result.command}` -> exit code "
            f"{audit.build_result.returncode} ({build_status})"
        )

        if audit.present_files:
            lines.append(
                "Observed legacy implementation files still present: "
                + ", ".join(audit.present_files)
            )

        combined_output = audit.build_result.combined_output
        if combined_output:
            lines.append("Build output:")
            lines.append(combined_output)

        return "\n".join(lines)

    def _audit_paths(self, root: Path) -> list[RemovalTargetAudit]:
        return [
            RemovalTargetAudit(
                relative_path=relative_path,
                exists=(root / relative_path).exists(),
            )
            for relative_path in self.removed_paths
        ]

    @staticmethod
    def _create_sandbox(repository_root: Path) -> Sandbox:
        return RepoSandbox(
            repository_root,
            initialize_git_repo=True,
            base_dir=repository_root / ".repo-sandboxes",
        )
