from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Mapping, Sequence

from testing.core.models.process_execution_result import ProcessExecutionResult


class SubprocessProcessRunner:
    def run(
        self,
        args: Sequence[str],
        cwd: Path,
        env: Mapping[str, str | None] | None = None,
        trace_network: bool = False,
    ) -> ProcessExecutionResult:
        trace_path: Path | None = None
        command = list(args)
        merged_env = os.environ.copy()
        if env:
            for key, value in env.items():
                if value is None:
                    merged_env.pop(key, None)
                else:
                    merged_env[key] = value

        if trace_network:
            strace_path = shutil.which("strace")
            if not strace_path:
                raise RuntimeError("strace is required to capture outbound network activity.")
            trace_file = tempfile.NamedTemporaryFile(prefix="dmtools-network-", suffix=".log", delete=False)
            trace_file.close()
            trace_path = Path(trace_file.name)
            command = [
                strace_path,
                "-f",
                "-qq",
                "-e",
                "trace=network",
                "-o",
                str(trace_path),
                *command,
            ]

        completed = subprocess.run(
            command,
            cwd=cwd,
            env=merged_env,
            text=True,
            capture_output=True,
            check=False,
        )

        trace_lines: tuple[str, ...] = ()
        if trace_path and trace_path.exists():
            trace_lines = tuple(
                line
                for line in trace_path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            )
            trace_path.unlink()

        return ProcessExecutionResult(
            args=tuple(args),
            cwd=cwd,
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
            trace_lines=trace_lines,
        )
