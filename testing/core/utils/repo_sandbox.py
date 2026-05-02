from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class CommandResult:
    command: str
    returncode: int
    stdout: str
    stderr: str

    @property
    def combined_output(self) -> str:
        output_parts = [self.stdout.rstrip(), self.stderr.rstrip()]
        return "\n".join(part for part in output_parts if part).strip()


class RepoSandbox:
    def __init__(
        self,
        repository_root: Path,
        *,
        initialize_git_repo: bool = False,
        base_dir: Path | None = None,
    ) -> None:
        self.repository_root = repository_root
        self._initialize_git_repo = initialize_git_repo
        if base_dir is not None:
            base_dir.mkdir(parents=True, exist_ok=True)
        self._temp_dir = tempfile.TemporaryDirectory(
            prefix="dmc-897-",
            dir=str(base_dir) if base_dir is not None else None,
        )
        self.root = Path(self._temp_dir.name)
        self.workspace = self.root / "workspace"
        self.home = self.root / "home"
        self.workspace.mkdir(parents=True, exist_ok=True)
        self.home.mkdir(parents=True, exist_ok=True)
        self._copy_repository()
        if self._initialize_git_repo:
            self._bootstrap_git_repository()

    def cleanup(self) -> None:
        self._temp_dir.cleanup()

    def path(self, relative_path: str) -> Path:
        return self.workspace / relative_path

    def read_text(self, relative_path: str) -> str:
        return self.path(relative_path).read_text(encoding="utf-8")

    def write_text(self, relative_path: str, content: str) -> None:
        self.path(relative_path).write_text(content, encoding="utf-8")

    def run(self, command: str, timeout: int = 1800) -> CommandResult:
        env = os.environ.copy()
        env["HOME"] = str(self.home)
        env.setdefault("GRADLE_USER_HOME", str(self.home / ".gradle"))
        env.setdefault("XDG_CACHE_HOME", str(self.home / ".cache"))
        env.setdefault("PYTHONUNBUFFERED", "1")

        completed = subprocess.run(
            ["bash", "-lc", command],
            cwd=self.workspace,
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

    def _copy_repository(self) -> None:
        ignored_names = {
            ".git",
            ".gradle",
            ".idea",
            ".pytest_cache",
            ".repo-sandboxes",
            ".venv",
            "__pycache__",
            "build",
            "node_modules",
            "outputs",
            "test-results",
        }

        def ignore(_: str, names: list[str]) -> set[str]:
            return {name for name in names if name in ignored_names}

        shutil.copytree(
            self.repository_root,
            self.workspace,
            dirs_exist_ok=True,
            ignore=ignore,
            symlinks=False,
        )

    def _bootstrap_git_repository(self) -> None:
        if (self.workspace / ".git").exists():
            return

        branch_name = self._read_git_value(
            ["git", "branch", "--show-current"],
            cwd=self.repository_root,
        ) or "main"
        remote_url = self._read_git_value(
            ["git", "remote", "get-url", "origin"],
            cwd=self.repository_root,
        )

        commands = [
            ["git", "init", f"--initial-branch={branch_name}"],
            ["git", "config", "user.name", "RepoSandbox"],
            ["git", "config", "user.email", "repo-sandbox@example.invalid"],
        ]
        if remote_url:
            commands.append(["git", "remote", "add", "origin", remote_url])
        commands.extend(
            [
                ["git", "add", "."],
                ["git", "commit", "--no-gpg-sign", "-m", "Sandbox snapshot"],
            ]
        )

        for command in commands:
            completed = subprocess.run(
                command,
                cwd=self.workspace,
                capture_output=True,
                text=True,
                check=False,
            )
            if completed.returncode != 0:
                raise RuntimeError(
                    "Failed to bootstrap git metadata in RepoSandbox with command "
                    f"{' '.join(command)}:\n{completed.stdout}{completed.stderr}"
                )

    @staticmethod
    def _read_git_value(command: list[str], cwd: Path) -> str:
        completed = subprocess.run(
            command,
            cwd=cwd,
            capture_output=True,
            text=True,
            check=False,
        )
        if completed.returncode != 0:
            return ""
        return completed.stdout.strip()
