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
class InstallerArtifactState:
    relative_path: str
    exists: bool
    mtime_ns: int | None = None
    size: int | None = None


@dataclass(frozen=True)
class InstallerManagedPaths:
    install_dir: Path
    bin_dir: Path
    installer_env_path: Path
    jar_path: Path
    script_path: Path


@dataclass(frozen=True)
class InstallerRerunObservation:
    skills_csv: str
    first_run: InstallerRunSnapshot
    second_run: InstallerRunSnapshot
    inter_run_artifacts: dict[str, InstallerArtifactState] | None = None

    def changed_artifacts(self) -> list[str]:
        changed: list[str] = []
        for relative_path, first_snapshot in self.first_run.artifacts.items():
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
        skills_csv: str = "jira,github",
        sandbox_factory: Callable[[Path], Sandbox] = RepoSandbox,
    ) -> None:
        self._repository_root = repository_root
        self._skills_csv = skills_csv
        self._sandbox_factory = sandbox_factory

    def exercise(
        self,
        before_second_run: Callable[[InstallerManagedPaths], None] | None = None,
    ) -> InstallerRerunObservation:
        sandbox = self._sandbox_factory(self._repository_root)
        try:
            managed_paths = self._managed_paths(sandbox)
            first_run = self._run_installer(sandbox, managed_paths)
            inter_run_artifacts: dict[str, InstallerArtifactState] | None = None
            if before_second_run is not None:
                before_second_run(managed_paths)
                inter_run_artifacts = self._capture_artifact_states(managed_paths)
            second_run = self._run_installer(sandbox)
            return InstallerRerunObservation(
                skills_csv=self._skills_csv,
                first_run=first_run,
                second_run=second_run,
                inter_run_artifacts=inter_run_artifacts,
            )
        finally:
            sandbox.cleanup()

    def _run_installer(
        self,
        sandbox: Sandbox,
        managed_paths: InstallerManagedPaths | None = None,
    ) -> InstallerRunSnapshot:
        paths = managed_paths or self._managed_paths(sandbox)
        command = "\n".join(
            [
                "set -e",
                'export SHELL="/bin/bash"',
                'export DMTOOLS_INSTALL_DIR="$HOME/.dmtools"',
                'export DMTOOLS_BIN_DIR="$HOME/.dmtools/bin"',
                'export DMTOOLS_INSTALLER_ENV_PATH="$HOME/.dmtools/bin/dmtools-installer.env"',
                f'bash ./install.sh --skills "{self._skills_csv}"',
            ]
        )

        result = sandbox.run(command, timeout=1800)

        artifacts = self._snapshot_artifacts(
            install_dir=paths.install_dir,
            bin_dir=paths.bin_dir,
            installer_env_path=paths.installer_env_path,
            command_result=result,
        )

        return InstallerRunSnapshot(command=result, artifacts=artifacts)

    @staticmethod
    def _managed_paths(sandbox: Sandbox) -> InstallerManagedPaths:
        install_dir = sandbox.home / ".dmtools"
        bin_dir = install_dir / "bin"
        return InstallerManagedPaths(
            install_dir=install_dir,
            bin_dir=bin_dir,
            installer_env_path=bin_dir / "dmtools-installer.env",
            jar_path=install_dir / "dmtools.jar",
            script_path=bin_dir / "dmtools",
        )

    @staticmethod
    def _capture_artifact_states(
        managed_paths: InstallerManagedPaths,
    ) -> dict[str, InstallerArtifactState]:
        artifact_paths = (
            managed_paths.jar_path,
            managed_paths.script_path,
            managed_paths.installer_env_path,
        )
        states: dict[str, InstallerArtifactState] = {}
        for artifact_path in artifact_paths:
            if not artifact_path.exists():
                states[artifact_path.name] = InstallerArtifactState(
                    relative_path=artifact_path.name,
                    exists=False,
                )
                continue

            stat_result = artifact_path.stat()
            states[artifact_path.name] = InstallerArtifactState(
                relative_path=artifact_path.name,
                exists=True,
                mtime_ns=stat_result.st_mtime_ns,
                size=stat_result.st_size,
            )
        return states

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
