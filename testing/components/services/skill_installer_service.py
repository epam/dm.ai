from __future__ import annotations

import json
import shlex
import textwrap
import zipfile
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class InstalledSkillsMetadata:
    installed_skills: tuple[str, ...]
    active_commands: tuple[str, ...]
    raw: str


@dataclass(frozen=True)
class SkillDirectoryState:
    path: str
    exists: bool
    files: tuple[str, ...]


@dataclass(frozen=True)
class SkillSelectionAuditResult:
    installer_command_result: CommandResult
    workspace_root: str
    installer_path: str
    skills_root: str
    metadata_path: str
    retained_skill: str
    removed_skill: str
    initial_retained_state: SkillDirectoryState
    initial_removed_state: SkillDirectoryState
    initial_metadata_exists: bool
    initial_metadata_raw: str | None
    initial_metadata: InstalledSkillsMetadata | None
    initial_parse_error: str | None
    final_retained_state: SkillDirectoryState
    final_removed_state: SkillDirectoryState
    final_metadata_exists: bool
    final_metadata_raw: str | None
    final_metadata: InstalledSkillsMetadata | None
    final_parse_error: str | None


@dataclass(frozen=True)
class SkillSelectionAuditFailure:
    step: int
    summary: str
    details: str


class Sandbox(Protocol):
    workspace: Path
    home: Path

    def cleanup(self) -> None: ...

    def run(self, command: str, timeout: int = 1800) -> CommandResult: ...


class SkillInstallerService:
    INSTALLER_RELATIVE_PATH = "dmtools-ai-docs/install.sh"
    SKILLS_ROOT_RELATIVE_PATH = ".claude/skills"

    def __init__(
        self,
        repository_root: Path,
        sandbox_factory: Callable[[Path], Sandbox] | None = None,
    ) -> None:
        self.repository_root = repository_root
        self._sandbox_factory = sandbox_factory or self._create_sandbox

    def run_selective_skill_uninstall(
        self,
        *,
        retained_skill: str = "jira",
        removed_skill: str = "github",
    ) -> SkillSelectionAuditResult:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            installer_path = sandbox.workspace / self.INSTALLER_RELATIVE_PATH
            skills_root = sandbox.workspace / self.SKILLS_ROOT_RELATIVE_PATH
            metadata_path = skills_root / "installed-skills.json"

            self._prepare_fake_release_environment(sandbox, retained_skill)
            self._seed_installed_skills(
                skills_root=skills_root,
                retained_skill=retained_skill,
                removed_skill=removed_skill,
            )

            (
                initial_metadata_exists,
                initial_metadata_raw,
                initial_metadata,
                initial_parse_error,
            ) = self._read_metadata(metadata_path)
            initial_retained_state = self._directory_state(
                skills_root / self.skill_install_name(retained_skill)
            )
            initial_removed_state = self._directory_state(
                skills_root / self.skill_install_name(removed_skill)
            )

            installer_command_result = sandbox.run(
                self._build_installer_command(
                    installer_path=installer_path,
                    fake_bin_dir=sandbox.workspace.parent / "fake-bin",
                    fake_release_dir=sandbox.workspace.parent / "fake-releases",
                    selected_skill=retained_skill,
                )
            )

            (
                final_metadata_exists,
                final_metadata_raw,
                final_metadata,
                final_parse_error,
            ) = self._read_metadata(metadata_path)

            return SkillSelectionAuditResult(
                installer_command_result=installer_command_result,
                workspace_root=sandbox.workspace.as_posix(),
                installer_path=installer_path.as_posix(),
                skills_root=skills_root.as_posix(),
                metadata_path=metadata_path.as_posix(),
                retained_skill=retained_skill,
                removed_skill=removed_skill,
                initial_retained_state=initial_retained_state,
                initial_removed_state=initial_removed_state,
                initial_metadata_exists=initial_metadata_exists,
                initial_metadata_raw=initial_metadata_raw,
                initial_metadata=initial_metadata,
                initial_parse_error=initial_parse_error,
                final_retained_state=self._directory_state(
                    skills_root / self.skill_install_name(retained_skill)
                ),
                final_removed_state=self._directory_state(
                    skills_root / self.skill_install_name(removed_skill)
                ),
                final_metadata_exists=final_metadata_exists,
                final_metadata_raw=final_metadata_raw,
                final_metadata=final_metadata,
                final_parse_error=final_parse_error,
            )
        finally:
            sandbox.cleanup()

    def validate_selective_uninstall(
        self,
        result: SkillSelectionAuditResult,
    ) -> list[SkillSelectionAuditFailure]:
        failures: list[SkillSelectionAuditFailure] = []

        failures.extend(
            self._validate_skill_directory(
                step=1,
                state=result.initial_retained_state,
                expected_exists=True,
                required_files=("SKILL.md",),
                expectation=(
                    "The precondition must include the retained Jira skill artifacts before the rerun."
                ),
            )
        )
        failures.extend(
            self._validate_skill_directory(
                step=1,
                state=result.initial_removed_state,
                expected_exists=True,
                required_files=("SKILL.md",),
                expectation=(
                    "The precondition must include the removable GitHub skill artifacts before the rerun."
                ),
            )
        )
        failures.extend(
            self._validate_metadata(
                step=1,
                metadata_exists=result.initial_metadata_exists,
                metadata=result.initial_metadata,
                parse_error=result.initial_parse_error,
                metadata_path=result.metadata_path,
                expected_skills=(result.retained_skill, result.removed_skill),
            )
        )

        if result.installer_command_result.returncode != 0:
            failures.append(
                SkillSelectionAuditFailure(
                    step=2,
                    summary="Re-running the skill installer with only Jira selected should succeed.",
                    details=(
                        f"Command exited with {result.installer_command_result.returncode}.\n"
                        f"Command: {result.installer_command_result.command}"
                    ),
                )
            )
            return failures

        failures.extend(
            self._validate_skill_directory(
                step=3,
                state=result.final_retained_state,
                expected_exists=True,
                required_files=("SKILL.md",),
                expectation="The Jira skill artifacts should remain installed after the rerun.",
            )
        )
        failures.extend(
            self._validate_skill_directory(
                step=4,
                state=result.final_removed_state,
                expected_exists=False,
                required_files=(),
                expectation="The GitHub skill artifacts should be removed after the rerun.",
            )
        )
        failures.extend(
            self._validate_metadata(
                step=5,
                metadata_exists=result.final_metadata_exists,
                metadata=result.final_metadata,
                parse_error=result.final_parse_error,
                metadata_path=result.metadata_path,
                expected_skills=(result.retained_skill,),
            )
        )

        return failures

    def format_failures(
        self,
        result: SkillSelectionAuditResult,
        failures: list[SkillSelectionAuditFailure],
    ) -> str:
        lines = [
            "Selective skill uninstall verification failed.",
            "",
            "Steps to reproduce:",
            (
                "1. Start from a workspace where .claude/skills contains both Jira and GitHub "
                "skill folders plus installed-skills.json metadata."
            ),
            (
                f"2. Re-run {self.INSTALLER_RELATIVE_PATH} with "
                f"DMTOOLS_SKILLS={result.retained_skill!r}."
            ),
            (
                f"3. Verify {self.skill_install_name(result.retained_skill)!r} remains, "
                f"{self.skill_install_name(result.removed_skill)!r} is removed, and "
                "installed-skills.json lists only Jira."
            ),
            "",
            "Observed state:",
            f"- Installer: {result.installer_path}",
            f"- Workspace: {result.workspace_root}",
            f"- Skills root: {result.skills_root}",
            f"- Metadata: {result.metadata_path}",
            (
                f"- Initial retained dir: {result.initial_retained_state.path} | "
                f"exists={result.initial_retained_state.exists} | "
                f"files={', '.join(result.initial_retained_state.files) or '<empty>'}"
            ),
            (
                f"- Initial removed dir: {result.initial_removed_state.path} | "
                f"exists={result.initial_removed_state.exists} | "
                f"files={', '.join(result.initial_removed_state.files) or '<empty>'}"
            ),
            (
                f"- Final retained dir: {result.final_retained_state.path} | "
                f"exists={result.final_retained_state.exists} | "
                f"files={', '.join(result.final_retained_state.files) or '<empty>'}"
            ),
            (
                f"- Final removed dir: {result.final_removed_state.path} | "
                f"exists={result.final_removed_state.exists} | "
                f"files={', '.join(result.final_removed_state.files) or '<empty>'}"
            ),
            (
                f"- Initial metadata exists={result.initial_metadata_exists} "
                f"parse_error={result.initial_parse_error or '<none>'}"
            ),
            (
                f"- Final metadata exists={result.final_metadata_exists} "
                f"parse_error={result.final_parse_error or '<none>'}"
            ),
            "",
            "Initial metadata:",
            result.initial_metadata_raw.rstrip() if result.initial_metadata_raw is not None else "<missing>",
            "",
            "Final metadata:",
            result.final_metadata_raw.rstrip() if result.final_metadata_raw is not None else "<missing>",
        ]

        if failures:
            lines.extend(
                [
                    "",
                    "Failures:",
                    *[
                        f"- Step {failure.step}: {failure.summary} {failure.details}"
                        for failure in failures
                    ],
                ]
            )

        lines.extend(
            [
                "",
                "Installer stdout:",
                result.installer_command_result.stdout.rstrip() or "<empty>",
                "",
                "Installer stderr:",
                result.installer_command_result.stderr.rstrip() or "<empty>",
            ]
        )
        return "\n".join(lines)

    @staticmethod
    def skill_install_name(skill: str) -> str:
        return "dmtools" if skill == "dmtools" else f"dmtools-{skill}"

    @staticmethod
    def skill_command_name(skill: str) -> str:
        return "/dmtools" if skill == "dmtools" else f"/dmtools-{skill}"

    @staticmethod
    def skill_asset_name(skill: str) -> str:
        return "dmtools-skill.zip" if skill == "dmtools" else f"dmtools-{skill}-skill.zip"

    @staticmethod
    def _create_sandbox(repository_root: Path) -> Sandbox:
        return RepoSandbox(
            repository_root,
            base_dir=repository_root / ".repo-sandboxes",
        )

    def _prepare_fake_release_environment(
        self,
        sandbox: Sandbox,
        retained_skill: str,
    ) -> None:
        fake_release_dir = sandbox.workspace.parent / "fake-releases"
        fake_bin_dir = sandbox.workspace.parent / "fake-bin"
        fake_release_dir.mkdir(parents=True, exist_ok=True)
        fake_bin_dir.mkdir(parents=True, exist_ok=True)

        self._write_skill_package(
            fake_release_dir / self.skill_asset_name(retained_skill),
            retained_skill,
        )
        self._write_fake_curl(fake_bin_dir / "curl")
        self._write_fake_unzip(fake_bin_dir / "unzip")

    def _seed_installed_skills(
        self,
        *,
        skills_root: Path,
        retained_skill: str,
        removed_skill: str,
    ) -> None:
        skills_root.mkdir(parents=True, exist_ok=True)
        self._seed_skill_directory(skills_root / self.skill_install_name(retained_skill), retained_skill)
        self._seed_skill_directory(skills_root / self.skill_install_name(removed_skill), removed_skill)
        metadata = {
            "installed_skills": [retained_skill, removed_skill],
            "active_commands": [
                self.skill_command_name(retained_skill),
                self.skill_command_name(removed_skill),
            ],
        }
        (skills_root / "installed-skills.json").write_text(
            json.dumps(metadata, indent=2),
            encoding="utf-8",
        )

    @staticmethod
    def _seed_skill_directory(path: Path, skill: str) -> None:
        path.mkdir(parents=True, exist_ok=True)
        path.joinpath("SKILL.md").write_text(
            f"# {skill}\n",
            encoding="utf-8",
        )
        path.joinpath("artifact.txt").write_text(
            f"{skill} artifact\n",
            encoding="utf-8",
        )

    @staticmethod
    def _write_skill_package(package_path: Path, skill: str) -> None:
        with zipfile.ZipFile(package_path, "w") as archive:
            archive.writestr("SKILL.md", f"# {skill}\n")
            archive.writestr("artifact.txt", f"{skill} artifact\n")

    @staticmethod
    def _write_fake_curl(path: Path) -> None:
        path.write_text(
            textwrap.dedent(
                """\
                #!/usr/bin/env bash
                set -euo pipefail

                output=""
                url=""
                while [ $# -gt 0 ]; do
                    case "$1" in
                        -o)
                            output="$2"
                            shift 2
                            ;;
                        -L|-f|-s|-S|--fail|--location)
                            shift
                            ;;
                        *)
                            url="$1"
                            shift
                            ;;
                    esac
                done

                if [ -z "$output" ] || [ -z "$url" ]; then
                    echo "fake curl requires -o <output> <url>" >&2
                    exit 2
                fi

                asset_name="${url##*/}"
                source_path="${SKILL_INSTALLER_FAKE_RELEASES:?}/${asset_name}"
                if [ ! -f "$source_path" ]; then
                    echo "fake curl missing asset: $asset_name" >&2
                    exit 22
                fi

                mkdir -p "$(dirname "$output")"
                cp "$source_path" "$output"
                """
            ),
            encoding="utf-8",
        )
        path.chmod(0o755)

    @staticmethod
    def _write_fake_unzip(path: Path) -> None:
        path.write_text(
            textwrap.dedent(
                """\
                #!/usr/bin/env python3
                import pathlib
                import sys
                import zipfile

                args = sys.argv[1:]
                zip_path = None
                target_dir = None
                index = 0

                while index < len(args):
                    argument = args[index]
                    if argument == "-q":
                        index += 1
                        continue
                    if argument == "-d":
                        target_dir = args[index + 1]
                        index += 2
                        continue
                    if zip_path is None:
                        zip_path = argument
                    index += 1

                if zip_path is None or target_dir is None:
                    raise SystemExit(2)

                pathlib.Path(target_dir).mkdir(parents=True, exist_ok=True)
                with zipfile.ZipFile(zip_path) as archive:
                    archive.extractall(target_dir)
                """
            ),
            encoding="utf-8",
        )
        path.chmod(0o755)

    @staticmethod
    def _directory_state(path: Path) -> SkillDirectoryState:
        files = ()
        if path.is_dir():
            files = tuple(sorted(child.name for child in path.iterdir()))
        return SkillDirectoryState(
            path=path.as_posix(),
            exists=path.is_dir(),
            files=files,
        )

    @staticmethod
    def _read_metadata(
        metadata_path: Path,
    ) -> tuple[bool, str | None, InstalledSkillsMetadata | None, str | None]:
        if not metadata_path.is_file():
            return False, None, None, None

        raw = metadata_path.read_text(encoding="utf-8")
        metadata, parse_error = SkillInstallerService._parse_metadata(raw)
        return True, raw, metadata, parse_error

    @staticmethod
    def _parse_metadata(
        raw: str,
    ) -> tuple[InstalledSkillsMetadata | None, str | None]:
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError as error:
            return None, f"Invalid JSON: {error}"

        if not isinstance(payload, dict):
            return None, "Metadata root must be a JSON object."

        installed_skills = payload.get("installed_skills")
        active_commands = payload.get("active_commands")
        if not isinstance(installed_skills, list) or not all(
            isinstance(skill, str) for skill in installed_skills
        ):
            return None, "installed_skills must be a string array."
        if not isinstance(active_commands, list) or not all(
            isinstance(command, str) for command in active_commands
        ):
            return None, "active_commands must be a string array."

        return (
            InstalledSkillsMetadata(
                installed_skills=tuple(installed_skills),
                active_commands=tuple(active_commands),
                raw=raw,
            ),
            None,
        )

    @staticmethod
    def _build_installer_command(
        *,
        installer_path: Path,
        fake_bin_dir: Path,
        fake_release_dir: Path,
        selected_skill: str,
    ) -> str:
        return textwrap.dedent(
            f"""\
            set -e
            export PATH={shlex.quote(fake_bin_dir.as_posix())}:$PATH
            export SKILL_INSTALLER_FAKE_RELEASES={shlex.quote(fake_release_dir.as_posix())}
            export DMTOOLS_SKILLS={shlex.quote(selected_skill)}
            bash {shlex.quote(installer_path.as_posix())}
            """
        ).strip()

    def _validate_metadata(
        self,
        *,
        step: int,
        metadata_exists: bool,
        metadata: InstalledSkillsMetadata | None,
        parse_error: str | None,
        metadata_path: str,
        expected_skills: tuple[str, ...],
    ) -> list[SkillSelectionAuditFailure]:
        failures: list[SkillSelectionAuditFailure] = []
        expected_commands = tuple(self.skill_command_name(skill) for skill in expected_skills)

        if not metadata_exists:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary="installed-skills.json must exist.",
                    details=f"{metadata_path} was not found.",
                )
            )
            return failures

        if parse_error is not None:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary="installed-skills.json must be valid JSON with exact fields.",
                    details=f"{metadata_path} could not be parsed: {parse_error}",
                )
            )
            return failures

        assert metadata is not None

        if metadata.installed_skills != expected_skills:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary="installed_skills must match the selected skills exactly.",
                    details=(
                        f"Expected {expected_skills}, observed {metadata.installed_skills} "
                        f"in {metadata_path}."
                    ),
                )
            )

        if metadata.active_commands != expected_commands:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary="active_commands must match the selected skills exactly.",
                    details=(
                        f"Expected {expected_commands}, observed {metadata.active_commands} "
                        f"in {metadata_path}."
                    ),
                )
            )

        return failures

    @staticmethod
    def _validate_skill_directory(
        *,
        step: int,
        state: SkillDirectoryState,
        expected_exists: bool,
        required_files: tuple[str, ...],
        expectation: str,
    ) -> list[SkillSelectionAuditFailure]:
        if state.exists != expected_exists:
            return [
                SkillSelectionAuditFailure(
                    step=step,
                    summary=expectation,
                    details=(
                        f"{state.path} exists={state.exists}; files="
                        f"{', '.join(state.files) or '<empty>'}."
                    ),
                )
            ]

        if expected_exists:
            missing_files = tuple(
                required_file
                for required_file in required_files
                if required_file not in state.files
            )
            if missing_files:
                return [
                    SkillSelectionAuditFailure(
                        step=step,
                        summary=expectation,
                        details=(
                            f"{state.path} is missing required files {missing_files}; "
                            f"observed {state.files}."
                        ),
                    )
                ]

        return []
