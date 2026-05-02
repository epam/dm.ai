from __future__ import annotations

import json
import shlex
import textwrap
from dataclasses import dataclass
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class SkillSelectionAuditResult:
    command_result: CommandResult
    workspace_root: str
    installer_path: str
    skills_directory: str
    retained_skill: str
    removed_skill: str
    retained_directory: str
    removed_directory: str
    retained_exists: bool
    removed_exists: bool
    retained_files: tuple[str, ...]
    removed_files: tuple[str, ...]
    metadata_path: str
    metadata_exists: bool
    metadata_raw: str | None
    metadata_parse_error: str | None

    def metadata_mentions(self, skill_key: str) -> bool:
        if self.metadata_raw is None:
            return False
        return skill_key.lower() in self.metadata_raw.lower()


@dataclass(frozen=True)
class SkillSelectionAuditFailure:
    step: int
    summary: str
    details: str


class SkillInstallerService:
    INSTALLER_RELATIVE_PATH = "dmtools-ai-docs/install.sh"
    METADATA_FILENAME = "installed-skills.json"

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root

    def run_selective_skill_uninstall(
        self,
        *,
        retained_skill: str = "jira",
        removed_skill: str = "github",
    ) -> SkillSelectionAuditResult:
        sandbox = RepoSandbox(self.repository_root)
        try:
            skills_dir = sandbox.path(".claude/skills")
            skills_dir.mkdir(parents=True, exist_ok=True)

            self._seed_installed_skill(skills_dir, retained_skill)
            self._seed_installed_skill(skills_dir, removed_skill)
            metadata_path = skills_dir / self.METADATA_FILENAME
            metadata_path.write_text(
                json.dumps(
                    {
                        "installed_skills": [retained_skill, removed_skill],
                        "active_commands": [
                            self.skill_command_name(retained_skill),
                            self.skill_command_name(removed_skill),
                        ],
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )

            asset_dir = sandbox.root / "skill-assets"
            asset_dir.mkdir(parents=True, exist_ok=True)
            retained_asset = self._write_skill_archive(asset_dir, retained_skill)
            removed_asset = self._write_skill_archive(asset_dir, removed_skill)

            fake_bin = sandbox.root / "fake-bin"
            fake_bin.mkdir(parents=True, exist_ok=True)
            self._write_fake_curl(
                fake_bin / "curl",
                {
                    retained_asset.name: retained_asset,
                    removed_asset.name: removed_asset,
                },
            )

            installer_path = sandbox.path(self.INSTALLER_RELATIVE_PATH)
            command = " && ".join(
                [
                    f"export PATH={shlex.quote(str(fake_bin))}:$PATH",
                    f"cd {shlex.quote(str(sandbox.workspace))}",
                    f"DMTOOLS_SKILLS={shlex.quote(retained_skill)} bash {shlex.quote(str(installer_path))}",
                ]
            )
            command_result = sandbox.run(command)

            retained_directory = skills_dir / self.skill_install_name(retained_skill)
            removed_directory = skills_dir / self.skill_install_name(removed_skill)
            metadata_exists = metadata_path.exists()
            metadata_raw = metadata_path.read_text(encoding="utf-8") if metadata_exists else None
            metadata_parse_error = None
            if metadata_raw is not None:
                try:
                    json.loads(metadata_raw)
                except json.JSONDecodeError as error:
                    metadata_parse_error = str(error)

            return SkillSelectionAuditResult(
                command_result=command_result,
                workspace_root=sandbox.workspace.as_posix(),
                installer_path=installer_path.as_posix(),
                skills_directory=skills_dir.as_posix(),
                retained_skill=retained_skill,
                removed_skill=removed_skill,
                retained_directory=retained_directory.as_posix(),
                removed_directory=removed_directory.as_posix(),
                retained_exists=retained_directory.is_dir(),
                removed_exists=removed_directory.is_dir(),
                retained_files=self._collect_relative_files(retained_directory),
                removed_files=self._collect_relative_files(removed_directory),
                metadata_path=metadata_path.as_posix(),
                metadata_exists=metadata_exists,
                metadata_raw=metadata_raw,
                metadata_parse_error=metadata_parse_error,
            )
        finally:
            sandbox.cleanup()

    def validate_selective_uninstall(
        self,
        result: SkillSelectionAuditResult,
    ) -> list[SkillSelectionAuditFailure]:
        failures: list[SkillSelectionAuditFailure] = []

        if result.command_result.returncode != 0:
            failures.append(
                SkillSelectionAuditFailure(
                    step=1,
                    summary="Running the installer with only the retained skill should succeed.",
                    details=(
                        f"Command exited with {result.command_result.returncode}.\n"
                        f"Command: {result.command_result.command}"
                    ),
                )
            )

        artifact_failures: list[str] = []
        if not result.retained_exists:
            artifact_failures.append(
                f"{result.retained_directory} is missing after the reinstall."
            )
        if result.removed_exists:
            artifact_failures.append(
                f"{result.removed_directory} still exists with files: "
                f"{', '.join(result.removed_files) if result.removed_files else '<empty directory>'}."
            )
        if artifact_failures:
            failures.append(
                SkillSelectionAuditFailure(
                    step=2,
                    summary=(
                        "Retained skill artifacts must stay installed while deselected skill "
                        "artifacts are removed."
                    ),
                    details=" ".join(artifact_failures),
                )
            )

        metadata_failures: list[str] = []
        if not result.metadata_exists:
            metadata_failures.append(f"{result.metadata_path} was not found.")
        elif result.metadata_parse_error:
            metadata_failures.append(
                f"{result.metadata_path} is not valid JSON: {result.metadata_parse_error}."
            )
        else:
            if not result.metadata_mentions(result.retained_skill):
                metadata_failures.append(
                    f"{result.metadata_path} does not mention retained skill "
                    f"{result.retained_skill!r}."
                )
            if result.metadata_mentions(result.removed_skill):
                metadata_failures.append(
                    f"{result.metadata_path} still mentions removed skill "
                    f"{result.removed_skill!r}."
                )
        if metadata_failures:
            failures.append(
                SkillSelectionAuditFailure(
                    step=3,
                    summary="installed-skills.json must reflect only the currently active skill set.",
                    details=" ".join(metadata_failures),
                )
            )

        return failures

    def format_failures(
        self,
        result: SkillSelectionAuditResult,
        failures: list[SkillSelectionAuditFailure],
    ) -> str:
        step_status = {
            1: (
                "✅"
                if result.command_result.returncode == 0
                else f"❌ return code {result.command_result.returncode}"
            ),
            2: (
                "✅"
                if result.retained_exists and not result.removed_exists
                else "❌ artifact state mismatch"
            ),
            3: (
                "✅"
                if result.metadata_exists
                and result.metadata_parse_error is None
                and result.metadata_mentions(result.retained_skill)
                and not result.metadata_mentions(result.removed_skill)
                else "❌ metadata mismatch"
            ),
        }

        lines = [
            "Selective skill uninstall verification failed.",
            "",
            "Steps to reproduce:",
            (
                "1. Run the skill installer with "
                f"DMTOOLS_SKILLS={result.retained_skill!r} only. {step_status[1]}"
            ),
            (
                "2. Verify the retained skill artifacts are still present and the "
                f"deselected {result.removed_skill!r} artifacts are gone. {step_status[2]}"
            ),
            (
                "3. Inspect installed-skills.json and confirm only the retained skill "
                f"is active. {step_status[3]}"
            ),
            "",
            "Observed state:",
            f"- Installer: {result.installer_path}",
            f"- Workspace: {result.workspace_root}",
            f"- Skills directory: {result.skills_directory}",
            (
                f"- Retained directory ({result.retained_skill}): {result.retained_directory} | "
                f"exists={result.retained_exists} | files="
                f"{', '.join(result.retained_files) if result.retained_files else '<none>'}"
            ),
            (
                f"- Removed directory ({result.removed_skill}): {result.removed_directory} | "
                f"exists={result.removed_exists} | files="
                f"{', '.join(result.removed_files) if result.removed_files else '<none>'}"
            ),
            (
                f"- Metadata: {result.metadata_path} | exists={result.metadata_exists} | "
                f"parse_error={result.metadata_parse_error or '<none>'}"
            ),
            (
                "- Metadata content:\n"
                + (result.metadata_raw.rstrip() if result.metadata_raw is not None else "<missing>")
            ),
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
                "stdout:",
                result.command_result.stdout.rstrip() or "<empty>",
                "",
                "stderr:",
                result.command_result.stderr.rstrip() or "<empty>",
            ]
        )
        return "\n".join(lines)

    @staticmethod
    def skill_install_name(skill_key: str) -> str:
        return {
            "dmtools": "dmtools",
            "jira": "dmtools-jira",
            "github": "dmtools-github",
            "ado": "dmtools-ado",
            "testrail": "dmtools-testrail",
        }[skill_key]

    @staticmethod
    def skill_command_name(skill_key: str) -> str:
        return {
            "dmtools": "/dmtools",
            "jira": "/dmtools-jira",
            "github": "/dmtools-github",
            "ado": "/dmtools-ado",
            "testrail": "/dmtools-testrail",
        }[skill_key]

    def _seed_installed_skill(self, skills_dir: Path, skill_key: str) -> None:
        skill_dir = skills_dir / self.skill_install_name(skill_key)
        skill_dir.mkdir(parents=True, exist_ok=True)
        (skill_dir / "SKILL.md").write_text(
            textwrap.dedent(
                f"""
                ---
                name: {self.skill_install_name(skill_key)}
                ---

                # {self.skill_install_name(skill_key)}

                Invoke {self.skill_command_name(skill_key)} for {skill_key} workflows.
                """
            ).strip()
            + "\n",
            encoding="utf-8",
        )
        (skill_dir / "artifact.txt").write_text(
            f"{skill_key} artifact remains visible to the user.\n",
            encoding="utf-8",
        )

    def _write_skill_archive(self, asset_dir: Path, skill_key: str) -> Path:
        asset_name = f"{self.skill_install_name(skill_key)}-skill.zip"
        asset_path = asset_dir / asset_name
        with ZipFile(asset_path, "w", compression=ZIP_DEFLATED) as archive:
            archive.writestr(
                "SKILL.md",
                textwrap.dedent(
                    f"""
                    ---
                    name: {self.skill_install_name(skill_key)}
                    ---

                    # {self.skill_install_name(skill_key)}

                    Use {self.skill_command_name(skill_key)} to access the {skill_key} skill.
                    """
                ).strip()
                + "\n",
            )
            archive.writestr(
                "artifact.txt",
                f"{skill_key} package downloaded by the installer.\n",
            )
        return asset_path

    def _write_fake_curl(self, script_path: Path, assets: dict[str, Path]) -> None:
        cases = "\n".join(
            [
                f'    *"/{asset_name}") cp {shlex.quote(asset_path.as_posix())} "$output_path" ;;'
                for asset_name, asset_path in sorted(assets.items())
            ]
        )
        script_path.write_text(
            textwrap.dedent(
                f"""
                #!/usr/bin/env bash
                set -euo pipefail

                output_path=""
                url=""

                while [ "$#" -gt 0 ]; do
                    case "$1" in
                        -o)
                            output_path="$2"
                            shift 2
                            ;;
                        -f|-L|-s|--fail|--location|--silent)
                            shift
                            ;;
                        *)
                            url="$1"
                            shift
                            ;;
                    esac
                done

                if [ -z "$output_path" ] || [ -z "$url" ]; then
                    echo "fake curl expected both a URL and -o destination" >&2
                    exit 2
                fi

                case "$url" in
                {cases}
                    *)
                        echo "No fake asset configured for $url" >&2
                        exit 1
                        ;;
                esac
                """
            ).strip()
            + "\n",
            encoding="utf-8",
        )
        script_path.chmod(0o755)

    @staticmethod
    def _collect_relative_files(directory: Path) -> tuple[str, ...]:
        if not directory.is_dir():
            return ()
        return tuple(
            sorted(path.relative_to(directory).as_posix() for path in directory.rglob("*"))
        )
