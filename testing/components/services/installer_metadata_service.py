from __future__ import annotations

import json
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class InstallerMetadataRun:
    installer_url: str
    temp_root: Path
    install_dir: Path
    bin_dir: Path
    requested_skills: tuple[str, ...]
    execution: ProcessExecutionResult

    @property
    def installed_skills_path(self) -> Path:
        return self.install_dir / "installed-skills.json"

    @property
    def endpoints_path(self) -> Path:
        return self.install_dir / "endpoints.json"

    @property
    def installer_env_path(self) -> Path:
        return self.bin_dir / "dmtools-installer.env"


class InstallerMetadataService:
    DEFAULT_INSTALLER_URL = "https://raw.githubusercontent.com/epam/dm.ai/main/install"
    _SKILL_COLLECTION_KEYS = frozenset(
        {
            "skills",
            "installed_skills",
            "installed-skills",
            "installedskills",
            "selected_skills",
            "selected-skills",
            "selectedskills",
        }
    )
    _SKILL_VALUE_KEYS = ("skill", "slug", "name", "id")

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        installer_url: str = DEFAULT_INSTALLER_URL,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.installer_url = installer_url

    def run_selective_install(self, skills: Sequence[str]) -> InstallerMetadataRun:
        normalized_skills = tuple(skill.strip().lower() for skill in skills if skill.strip())
        if not normalized_skills:
            raise ValueError("At least one skill must be provided.")

        temp_root = Path(tempfile.mkdtemp(prefix="dmtools-installer-metadata-"))
        install_dir = temp_root / "install"
        bin_dir = install_dir / "bin"
        home_dir = temp_root / "home"
        installer_path = temp_root / "install_script"

        script = "\n".join(
            [
                "set -euo pipefail",
                'mkdir -p "$HOME"',
                'curl -fsSL "$INSTALLER_URL" -o "$INSTALLER_PATH"',
                'chmod +x "$INSTALLER_PATH"',
                'bash "$INSTALLER_PATH"',
            ]
        )
        execution = self.runner.run(
            ["bash", "-c", script],
            cwd=self.repository_root,
            env={
                "HOME": str(home_dir),
                "SHELL": "/bin/bash",
                "INSTALLER_URL": self.installer_url,
                "INSTALLER_PATH": str(installer_path),
                "DMTOOLS_INSTALL_DIR": str(install_dir),
                "DMTOOLS_BIN_DIR": str(bin_dir),
                "DMTOOLS_SKILLS": ",".join(normalized_skills),
            },
        )

        return InstallerMetadataRun(
            installer_url=self.installer_url,
            temp_root=temp_root,
            install_dir=install_dir,
            bin_dir=bin_dir,
            requested_skills=normalized_skills,
            execution=execution,
        )

    def read_json(self, path: Path) -> Any:
        return json.loads(path.read_text(encoding="utf-8"))

    def payload_contains_skills(self, payload: Any, expected_skills: Sequence[str]) -> bool:
        observed_skills = self.declared_skills(payload)
        return set(skill.lower() for skill in expected_skills).issubset(observed_skills)

    def declared_skills(self, payload: Any) -> set[str]:
        if not isinstance(payload, dict):
            return set()

        observed_skills: set[str] = set()
        for key, value in payload.items():
            if self._normalize_key(key) not in self._SKILL_COLLECTION_KEYS:
                continue
            observed_skills.update(self._extract_skill_collection(value))
        return observed_skills

    def payload_contains_version(self, payload: Any) -> bool:
        for key, value in self._walk_key_values(payload):
            if "version" not in key.lower():
                continue
            if isinstance(value, str) and value.strip():
                return True
            if isinstance(value, (int, float)):
                return True
        return False

    def endpoint_paths(self, payload: Any) -> set[str]:
        discovered_paths: set[str] = set()
        for key, value in self._walk_key_values(payload):
            if isinstance(value, str) and value.startswith("/dmtools/"):
                discovered_paths.add(value)
                continue
            if key.lower() in {"path", "endpoint", "url", "route"} and isinstance(value, str):
                discovered_paths.add(value)
        return discovered_paths

    def format_execution_failure(self, run: InstallerMetadataRun) -> str:
        return (
            "The live installer execution failed before metadata validation.\n"
            f"Installer URL: {run.installer_url}\n"
            f"Requested skills: {', '.join(run.requested_skills)}\n"
            f"Temp root: {run.temp_root}\n"
            f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
        )

    def format_missing_artifact(
        self,
        run: InstallerMetadataRun,
        missing_path: Path,
        expectation: str,
    ) -> str:
        installer_env = (
            run.installer_env_path.read_text(encoding="utf-8")
            if run.installer_env_path.exists()
            else "<missing>"
        )
        return (
            f"{expectation}\n"
            f"Missing path: {missing_path}\n"
            f"Installer URL: {run.installer_url}\n"
            f"Requested skills: {', '.join(run.requested_skills)}\n"
            f"Observed files under temp root:\n{self.describe_tree(run.temp_root)}\n\n"
            f"Observed installer-managed runtime config ({run.installer_env_path}):\n"
            f"{installer_env}\n\n"
            f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
        )

    def format_unexpected_payload(
        self,
        artifact_path: Path,
        payload: Any,
        expectation: str,
    ) -> str:
        return (
            f"{expectation}\n"
            f"Artifact: {artifact_path}\n"
            f"Observed payload:\n{json.dumps(payload, indent=2, sort_keys=True)}"
        )

    def describe_tree(self, root: Path) -> str:
        if not root.exists():
            return "<missing temp root>"

        entries = [
            path.relative_to(root).as_posix() or "."
            for path in sorted(root.rglob("*"))
        ]
        if not entries:
            return "<empty>"
        return "\n".join(entries)

    def _extract_skill_collection(self, payload: Any) -> set[str]:
        if isinstance(payload, str):
            return {
                skill.strip().lower()
                for skill in payload.split(",")
                if skill.strip()
            }

        if isinstance(payload, list):
            observed_skills: set[str] = set()
            for item in payload:
                observed_skills.update(self._extract_skill_collection(item))
            return observed_skills

        if isinstance(payload, dict):
            observed_skills: set[str] = set()
            for key in self._SKILL_VALUE_KEYS:
                value = payload.get(key)
                if isinstance(value, str) and value.strip():
                    observed_skills.update(self._extract_skill_collection(value))

            for key, value in payload.items():
                if self._normalize_key(key) in self._SKILL_COLLECTION_KEYS:
                    observed_skills.update(self._extract_skill_collection(value))
            return observed_skills

        return set()

    def _normalize_key(self, value: str) -> str:
        return value.replace("-", "_").lower()

    def _walk_key_values(self, payload: Any) -> Iterable[tuple[str, Any]]:
        if isinstance(payload, dict):
            for key, value in payload.items():
                yield str(key), value
                yield from self._walk_key_values(value)
        elif isinstance(payload, list):
            for item in payload:
                yield from self._walk_key_values(item)
