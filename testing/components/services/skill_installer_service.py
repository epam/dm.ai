from __future__ import annotations

import shlex
import textwrap
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class InstallerSkillConfig:
    skills: tuple[str, ...]
    integrations: tuple[str, ...]
    raw: str


@dataclass(frozen=True)
class SkillSelectionAuditResult:
    initial_command_result: CommandResult
    updated_command_result: CommandResult
    workspace_root: str
    install_dir: str
    installer_path: str
    installer_env_path: str
    retained_skill: str
    removed_skill: str
    initial_config_exists: bool
    initial_config_raw: str | None
    initial_config: InstallerSkillConfig | None
    initial_parse_error: str | None
    updated_config_exists: bool
    updated_config_raw: str | None
    updated_config: InstallerSkillConfig | None
    updated_parse_error: str | None


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
    INSTALLER_RELATIVE_PATH = "install.sh"
    BASE_INTEGRATIONS = ("ai", "cli", "file", "kb", "mermaid")
    SKILL_INTEGRATIONS = {
        "jira": ("jira",),
        "confluence": ("confluence",),
        "github": ("github",),
        "gitlab": ("gitlab",),
        "figma": ("figma",),
        "teams": ("teams", "teams_auth"),
        "sharepoint": ("sharepoint", "teams_auth"),
        "ado": ("ado",),
        "testrail": ("testrail",),
        "xray": ("jira_xray",),
        "dmtools": (),
    }

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
            install_dir = sandbox.home / ".dmtools"
            installer_env_path = install_dir / "bin" / "dmtools-installer.env"

            initial_selection = ",".join((retained_skill, removed_skill))
            initial_command_result = sandbox.run(
                self._build_installer_command(
                    installer_path=installer_path,
                    selected_skills=initial_selection,
                    install_dir=install_dir,
                    installer_env_path=installer_env_path,
                )
            )
            (
                initial_config_exists,
                initial_config_raw,
                initial_config,
                initial_parse_error,
            ) = self._read_installer_skill_config(installer_env_path)

            updated_command_result = sandbox.run(
                self._build_installer_command(
                    installer_path=installer_path,
                    selected_skills=retained_skill,
                    install_dir=install_dir,
                    installer_env_path=installer_env_path,
                )
            )
            (
                updated_config_exists,
                updated_config_raw,
                updated_config,
                updated_parse_error,
            ) = self._read_installer_skill_config(installer_env_path)

            return SkillSelectionAuditResult(
                initial_command_result=initial_command_result,
                updated_command_result=updated_command_result,
                workspace_root=sandbox.workspace.as_posix(),
                install_dir=install_dir.as_posix(),
                installer_path=installer_path.as_posix(),
                installer_env_path=installer_env_path.as_posix(),
                retained_skill=retained_skill,
                removed_skill=removed_skill,
                initial_config_exists=initial_config_exists,
                initial_config_raw=initial_config_raw,
                initial_config=initial_config,
                initial_parse_error=initial_parse_error,
                updated_config_exists=updated_config_exists,
                updated_config_raw=updated_config_raw,
                updated_config=updated_config,
                updated_parse_error=updated_parse_error,
            )
        finally:
            sandbox.cleanup()

    def validate_selective_uninstall(
        self,
        result: SkillSelectionAuditResult,
    ) -> list[SkillSelectionAuditFailure]:
        failures: list[SkillSelectionAuditFailure] = []
        initial_skills = (result.retained_skill, result.removed_skill)
        retained_skills = (result.retained_skill,)

        if result.initial_command_result.returncode != 0:
            failures.append(
                SkillSelectionAuditFailure(
                    step=1,
                    summary="Bootstrapping the installer with both skills should succeed.",
                    details=(
                        f"Command exited with {result.initial_command_result.returncode}.\n"
                        f"Command: {result.initial_command_result.command}"
                    ),
                )
            )
        else:
            failures.extend(
                self._validate_config_state(
                    step=1,
                    expected_skills=initial_skills,
                    expected_integrations=self.expected_integrations(initial_skills),
                    config_exists=result.initial_config_exists,
                    config=result.initial_config,
                    parse_error=result.initial_parse_error,
                    config_path=result.installer_env_path,
                )
            )

        if result.updated_command_result.returncode != 0:
            failures.append(
                SkillSelectionAuditFailure(
                    step=2,
                    summary="Re-running the installer with only the retained skill should succeed.",
                    details=(
                        f"Command exited with {result.updated_command_result.returncode}.\n"
                        f"Command: {result.updated_command_result.command}"
                    ),
                )
            )
        else:
            failures.extend(
                self._validate_config_state(
                    step=3,
                    expected_skills=retained_skills,
                    expected_integrations=self.expected_integrations(retained_skills),
                    config_exists=result.updated_config_exists,
                    config=result.updated_config,
                    parse_error=result.updated_parse_error,
                    config_path=result.installer_env_path,
                )
            )

        return failures

    def format_failures(
        self,
        result: SkillSelectionAuditResult,
        failures: list[SkillSelectionAuditFailure],
    ) -> str:
        lines = [
            "Selective installer skill reconfiguration verification failed.",
            "",
            "Steps to reproduce:",
            (
                "1. Run the root installer with both retained and removable skills selected "
                f"({result.retained_skill!r}, {result.removed_skill!r})."
            ),
            (
                "2. Re-run the root installer with only the retained skill selected "
                f"({result.retained_skill!r})."
            ),
            (
                "3. Inspect dmtools-installer.env and confirm it now retains only the Jira "
                "selection state and integrations required by the remaining skill."
            ),
            "",
            "Observed state:",
            f"- Installer: {result.installer_path}",
            f"- Workspace: {result.workspace_root}",
            f"- Install dir: {result.install_dir}",
            f"- Installer env: {result.installer_env_path}",
            (
                f"- Initial config exists={result.initial_config_exists} "
                f"parse_error={result.initial_parse_error or '<none>'}"
            ),
            (
                f"- Updated config exists={result.updated_config_exists} "
                f"parse_error={result.updated_parse_error or '<none>'}"
            ),
            "",
            "Initial installer env:",
            result.initial_config_raw.rstrip() if result.initial_config_raw is not None else "<missing>",
            "",
            "Updated installer env:",
            result.updated_config_raw.rstrip() if result.updated_config_raw is not None else "<missing>",
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
                "Initial stdout:",
                result.initial_command_result.stdout.rstrip() or "<empty>",
                "",
                "Initial stderr:",
                result.initial_command_result.stderr.rstrip() or "<empty>",
                "",
                "Updated stdout:",
                result.updated_command_result.stdout.rstrip() or "<empty>",
                "",
                "Updated stderr:",
                result.updated_command_result.stderr.rstrip() or "<empty>",
            ]
        )
        return "\n".join(lines)

    @classmethod
    def expected_integrations(cls, skills: tuple[str, ...]) -> tuple[str, ...]:
        ordered = list(cls.BASE_INTEGRATIONS)
        for skill in skills:
            for integration in cls.SKILL_INTEGRATIONS[skill]:
                if integration not in ordered:
                    ordered.append(integration)
        return tuple(ordered)

    @staticmethod
    def _create_sandbox(repository_root: Path) -> Sandbox:
        return RepoSandbox(
            repository_root,
            base_dir=repository_root / ".repo-sandboxes",
        )

    @staticmethod
    def _read_installer_skill_config(
        installer_env_path: Path,
    ) -> tuple[bool, str | None, InstallerSkillConfig | None, str | None]:
        if not installer_env_path.is_file():
            return False, None, None, None

        raw = installer_env_path.read_text(encoding="utf-8")
        config, parse_error = SkillInstallerService._parse_installer_skill_config(raw)
        return True, raw, config, parse_error

    @staticmethod
    def _parse_installer_skill_config(
        raw: str,
    ) -> tuple[InstallerSkillConfig | None, str | None]:
        values: dict[str, tuple[str, ...]] = {}

        for line in raw.splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if "=" not in stripped:
                return None, f"Unsupported line format: {stripped!r}."
            key, raw_value = stripped.split("=", 1)
            key = key.strip()
            value = raw_value.strip()
            if value[:1] == value[-1:] and value[:1] in {'"', "'"}:
                value = value[1:-1]
            values[key] = tuple(token for token in value.split(",") if token)

        missing_keys = [
            key
            for key in ("DMTOOLS_SKILLS", "DMTOOLS_INTEGRATIONS")
            if key not in values
        ]
        if missing_keys:
            return None, f"Missing expected keys: {', '.join(missing_keys)}."

        return (
            InstallerSkillConfig(
                skills=values["DMTOOLS_SKILLS"],
                integrations=values["DMTOOLS_INTEGRATIONS"],
                raw=raw,
            ),
            None,
        )

    @staticmethod
    def _build_installer_command(
        *,
        installer_path: Path,
        selected_skills: str,
        install_dir: Path,
        installer_env_path: Path,
    ) -> str:
        return textwrap.dedent(
            f"""
            set -e
            export DMTOOLS_INSTALLER_TEST_MODE=true
            export DMTOOLS_INSTALL_DIR={shlex.quote(install_dir.as_posix())}
            export DMTOOLS_BIN_DIR={shlex.quote((install_dir / "bin").as_posix())}
            export DMTOOLS_INSTALLER_ENV_PATH={shlex.quote(installer_env_path.as_posix())}

            source {shlex.quote(installer_path.as_posix())}

            check_java() {{
                info "stubbed check_java"
            }}

            get_latest_version() {{
                echo "v0.0.0-test"
            }}

            download_dmtools() {{
                local version="$1"
                info "stubbed download_dmtools $version"
                mkdir -p "$(dirname "$JAR_PATH")"
                printf 'stub jar for %s\\n' "$version" > "$JAR_PATH"
            }}

            update_shell_config() {{
                info "stubbed update_shell_config"
            }}

            verify_installation() {{
                info "stubbed verify_installation"
            }}

            print_instructions() {{
                info "stubbed print_instructions"
            }}

            main --skills {shlex.quote(selected_skills)}
            """
        ).strip()

    @staticmethod
    def _validate_config_state(
        *,
        step: int,
        expected_skills: tuple[str, ...],
        expected_integrations: tuple[str, ...],
        config_exists: bool,
        config: InstallerSkillConfig | None,
        parse_error: str | None,
        config_path: str,
    ) -> list[SkillSelectionAuditFailure]:
        failures: list[SkillSelectionAuditFailure] = []

        if not config_exists:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary="The installer-managed skill config file must be created.",
                    details=f"{config_path} was not found.",
                )
            )
            return failures

        if parse_error is not None:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary="The installer-managed skill config file must be parseable.",
                    details=f"{config_path} could not be parsed: {parse_error}",
                )
            )
            return failures

        assert config is not None

        if config.skills != expected_skills:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary="The installer-managed skill list must match the selected skills exactly.",
                    details=(
                        f"Expected skills {expected_skills}, observed {config.skills} in {config_path}."
                    ),
                )
            )

        if config.integrations != expected_integrations:
            failures.append(
                SkillSelectionAuditFailure(
                    step=step,
                    summary=(
                        "The installer-managed integration list must match the selected skills exactly."
                    ),
                    details=(
                        f"Expected integrations {expected_integrations}, observed "
                        f"{config.integrations} in {config_path}."
                    ),
                )
            )

        return failures
