from __future__ import annotations

import re
import tempfile
import textwrap
from pathlib import Path
from typing import Mapping, Sequence

from testing.core.interfaces.installer_script import InstallerScript
from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


class InstallerScriptService(InstallerScript):
    AVAILABLE_SKILLS_CSV = (
        "dmtools,jira,confluence,github,gitlab,figma,teams,"
        "sharepoint,ado,testrail,xray"
    )
    SIDE_EFFECT_MARKER = "UNEXPECTED "
    _ANSI_ESCAPE_PATTERN = re.compile(r"\x1B\[[0-?]*[ -/]*[@-~]")

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
    ) -> None:
        self.repository_root = repository_root
        self.install_script_path = repository_root / "install.sh"
        self.runner = runner

    def run_main(
        self,
        args: Sequence[str] = (),
        extra_env: Mapping[str, str] | None = None,
        post_script: str = "",
    ) -> ProcessExecutionResult:
        with tempfile.TemporaryDirectory(prefix="dmtools-installer-") as temp_dir:
            install_dir = Path(temp_dir) / "home" / ".dmtools"
            bin_dir = install_dir / "bin"
            installer_env_path = bin_dir / "dmtools-installer.env"

            env = {
                "DMTOOLS_INSTALLER_TEST_MODE": "true",
                "DMTOOLS_INSTALL_DIR": str(install_dir),
                "DMTOOLS_BIN_DIR": str(bin_dir),
                "DMTOOLS_INSTALLER_ENV_PATH": str(installer_env_path),
            }
            if extra_env:
                env.update(extra_env)

            command = textwrap.dedent(
                f"""
                set -e
                source "{self.install_script_path}"

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

                main "$@"
                {post_script}
                """
            ).strip()

            return self.runner.run(
                ["bash", "-lc", command, "bash", *args],
                cwd=self.repository_root,
                env=env,
            )

    @property
    def available_skills_csv(self) -> str:
        return self.AVAILABLE_SKILLS_CSV

    @property
    def side_effect_marker(self) -> str:
        return self.SIDE_EFFECT_MARKER

    def run_main_with_env_skills(self, skills_csv: str) -> ProcessExecutionResult:
        command = textwrap.dedent(
            f"""\
            set -e
            source "{self.install_script_path}"
            check_java() {{ :; }}
            detect_version() {{ printf 'v0.0.0'; }}
            get_latest_version() {{ printf 'v0.0.0'; }}
            create_install_dir() {{ printf '{self.SIDE_EFFECT_MARKER}create_install_dir\\n'; }}
            write_installer_skill_config() {{ printf '{self.SIDE_EFFECT_MARKER}write_installer_skill_config\\n'; }}
            download_dmtools() {{ printf '{self.SIDE_EFFECT_MARKER}download_dmtools\\n'; }}
            update_shell_config() {{ printf '{self.SIDE_EFFECT_MARKER}update_shell_config\\n'; }}
            verify_installation() {{ printf '{self.SIDE_EFFECT_MARKER}verify_installation\\n'; }}
            print_instructions() {{ printf '{self.SIDE_EFFECT_MARKER}print_instructions\\n'; }}
            main
            """
        )
        return self.runner.run(
            ["bash", "-lc", command],
            cwd=self.repository_root,
            env={
                "DMTOOLS_INSTALLER_TEST_MODE": "true",
                "DMTOOLS_SKILLS": skills_csv,
            },
        )

    @classmethod
    def strip_ansi(cls, text: str) -> str:
        return cls._ANSI_ESCAPE_PATTERN.sub("", text)

    def normalized_stdout(self, execution: ProcessExecutionResult) -> str:
        return self.strip_ansi(execution.stdout)

    def normalized_stderr(self, execution: ProcessExecutionResult) -> str:
        return self.strip_ansi(execution.stderr)

    def normalized_combined_output(self, execution: ProcessExecutionResult) -> str:
        return self.strip_ansi(execution.combined_output)
