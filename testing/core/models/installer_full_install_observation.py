from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class InstallerFullInstallObservation:
    execution: ProcessExecutionResult
    stdout: str
    stderr: str
    install_root_exists: bool
    bin_dir_exists: bool
    jar_exists: bool
    script_exists: bool
    script_is_executable: bool
    installer_env: Mapping[str, str]
