from __future__ import annotations

import os
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable

from testing.core.utils.repo_sandbox import CommandResult


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


class LegacyRemovalAuditService:
    def __init__(
        self,
        repository_root: Path,
        removed_paths: Iterable[str],
        build_command: str,
        build_executor: Callable[[Path, str, int], CommandResult] | None = None,
    ) -> None:
        self.repository_root = repository_root
        self.removed_paths = tuple(removed_paths)
        self.build_command = build_command
        self._build_executor = build_executor or self._run_build

    def run_audit(self, timeout: int = 1800) -> LegacyRemovalAudit:
        file_audits = tuple(self._audit_paths(self.repository_root))
        build_result = self._build_executor(self.repository_root, self.build_command, timeout)
        return LegacyRemovalAudit(
            file_audits=file_audits,
            build_result=build_result,
        )

    def observe_repository_state(self) -> tuple[RemovalTargetAudit, ...]:
        return tuple(self._audit_paths(self.repository_root))

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
    def _run_build(workspace: Path, command: str, timeout: int) -> CommandResult:
        env = os.environ.copy()
        with tempfile.TemporaryDirectory(prefix="dmc-909-") as temp_dir:
            temp_home = Path(temp_dir)
            env["HOME"] = str(temp_home)
            env.setdefault("GRADLE_USER_HOME", str(temp_home / ".gradle"))
            env.setdefault("XDG_CACHE_HOME", str(temp_home / ".cache"))
            env.setdefault("PYTHONUNBUFFERED", "1")

            completed = subprocess.run(
                ["bash", "-lc", command],
                cwd=workspace,
                env=env,
                capture_output=True,
                text=True,
                timeout=timeout,
                check=False,
            )

        return CommandResult(
            command=command,
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )
