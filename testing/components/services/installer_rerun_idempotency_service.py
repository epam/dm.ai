from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class InstallerArtifactSnapshot:
    relative_path: str
    mtime_ns: int
    size: int


@dataclass(frozen=True)
class InstallerRunSnapshot:
    command: CommandResult
    artifacts: dict[str, InstallerArtifactSnapshot]


@dataclass(frozen=True)
class InstallerRerunObservation:
    initial_skills_csv: str
    follow_up_skills_csv: str
    first_run: InstallerRunSnapshot
    second_run: InstallerRunSnapshot

    def changed_artifacts(
        self,
        artifact_names: tuple[str, ...] | None = None,
    ) -> list[str]:
        changed: list[str] = []
        selected_artifact_names = artifact_names or tuple(self.first_run.artifacts)
        for relative_path in selected_artifact_names:
            first_snapshot = self.first_run.artifacts[relative_path]
            second_snapshot = self.second_run.artifacts[relative_path]
            if (
                first_snapshot.mtime_ns != second_snapshot.mtime_ns
                or first_snapshot.size != second_snapshot.size
            ):
                changed.append(
                    f"{relative_path}: first mtime_ns={first_snapshot.mtime_ns}, "
                    f"second mtime_ns={second_snapshot.mtime_ns}, "
                    f"first size={first_snapshot.size}, second size={second_snapshot.size}"
                )
        return changed


class Sandbox(Protocol):
    home: Path

    def cleanup(self) -> None: ...

    def run(self, command: str, timeout: int = 1800) -> CommandResult: ...


class InstallerRerunIdempotencyService:
    def __init__(
        self,
        repository_root: Path,
        *,
        skills_csv: str | None = None,
        initial_skills_csv: str = "jira,github",
        follow_up_skills_csv: str | None = None,
        sandbox_factory: Callable[[Path], Sandbox] = RepoSandbox,
    ) -> None:
        self._repository_root = repository_root
        selected_initial_skills = skills_csv or initial_skills_csv
        self._initial_skills_csv = selected_initial_skills
        self._follow_up_skills_csv = follow_up_skills_csv or selected_initial_skills
        self._sandbox_factory = sandbox_factory

    def exercise(self) -> InstallerRerunObservation:
        sandbox = self._sandbox_factory(self._repository_root)
        try:
            first_run = self._run_installer(sandbox, self._initial_skills_csv)
            second_run = self._run_installer(sandbox, self._follow_up_skills_csv)
            return InstallerRerunObservation(
                initial_skills_csv=self._initial_skills_csv,
                follow_up_skills_csv=self._follow_up_skills_csv,
                first_run=first_run,
                second_run=second_run,
            )
        finally:
            sandbox.cleanup()

    def _run_installer(
        self,
        sandbox: Sandbox,
        skills_csv: str,
    ) -> InstallerRunSnapshot:
        install_dir = sandbox.home / ".dmtools"
        bin_dir = install_dir / "bin"
        installer_env_path = bin_dir / "dmtools-installer.env"

        command = "\n".join(
            [
                "set -e",
                'export SHELL="/bin/bash"',
                'export DMTOOLS_INSTALL_DIR="$HOME/.dmtools"',
                'export DMTOOLS_BIN_DIR="$HOME/.dmtools/bin"',
                'export DMTOOLS_INSTALLER_ENV_PATH="$HOME/.dmtools/bin/dmtools-installer.env"',
                f'bash ./install.sh --skills "{skills_csv}"',
            ]
        )

        result = sandbox.run(command, timeout=1800)

        artifacts = self._snapshot_artifacts(
            install_dir=install_dir,
            bin_dir=bin_dir,
            installer_env_path=installer_env_path,
            command_result=result,
        )

        return InstallerRunSnapshot(command=result, artifacts=artifacts)

    @staticmethod
    def _snapshot_artifacts(
        *,
        install_dir: Path,
        bin_dir: Path,
        installer_env_path: Path,
        command_result: CommandResult,
    ) -> dict[str, InstallerArtifactSnapshot]:
        artifact_paths = (
            install_dir / "dmtools.jar",
            bin_dir / "dmtools",
            installer_env_path,
        )
        snapshots: dict[str, InstallerArtifactSnapshot] = {}
        for artifact_path in artifact_paths:
            if not artifact_path.exists():
                raise AssertionError(
                    "Installer run did not produce the expected artifact "
                    f"{artifact_path}.\nCommand output:\n{command_result.combined_output}"
                )
            stat_result = artifact_path.stat()
            snapshots[artifact_path.name] = InstallerArtifactSnapshot(
                relative_path=artifact_path.name,
                mtime_ns=stat_result.st_mtime_ns,
                size=stat_result.st_size,
            )
        return snapshots
