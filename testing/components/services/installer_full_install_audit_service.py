from __future__ import annotations

import re
import textwrap
from typing import Mapping

from testing.core.interfaces.installer_script import InstallerScript
from testing.core.models.installer_full_install_observation import (
    InstallerFullInstallObservation,
)


class InstallerFullInstallAuditService:
    ALL_SKILLS = (
        "dmtools,jira,confluence,github,gitlab,figma,teams,"
        "sharepoint,ado,testrail,xray"
    )
    ALL_INTEGRATIONS = (
        "ai,cli,file,kb,mermaid,jira,confluence,github,gitlab,figma,"
        "teams,teams_auth,sharepoint,ado,testrail,jira_xray"
    )

    _ANSI_ESCAPE_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
    _PROBE_PREFIX = "__DMTOOLS_PROBE__:"
    _INSTALLER_ENV_LINE_KEY = "installer_env_line"

    def __init__(self, installer_script: InstallerScript) -> None:
        self.installer_script = installer_script

    def observe(
        self,
        extra_env: Mapping[str, str | None] | None = None,
    ) -> InstallerFullInstallObservation:
        execution = self.installer_script.run_main(
            extra_env=extra_env,
            post_script=self._probe_script(),
        )

        probe_values: dict[str, str] = {}
        installer_env: dict[str, str] = {}
        visible_stdout_lines: list[str] = []

        for line in self._strip_ansi(execution.stdout).splitlines():
            if not line.startswith(self._PROBE_PREFIX):
                visible_stdout_lines.append(line)
                continue

            key, _, value = line[len(self._PROBE_PREFIX) :].partition("=")
            if key == self._INSTALLER_ENV_LINE_KEY:
                env_key, _, env_value = value.partition("=")
                if env_key:
                    installer_env[env_key] = env_value.strip().strip('"')
                continue

            probe_values[key] = value

        return InstallerFullInstallObservation(
            execution=execution,
            stdout="\n".join(visible_stdout_lines).strip(),
            stderr=self._strip_ansi(execution.stderr).strip(),
            install_root_exists=probe_values.get("install_root_exists") == "true",
            bin_dir_exists=probe_values.get("bin_dir_exists") == "true",
            jar_exists=probe_values.get("jar_exists") == "true",
            script_exists=probe_values.get("script_exists") == "true",
            script_is_executable=probe_values.get("script_is_executable") == "true",
            installer_env=installer_env,
        )

    @classmethod
    def _probe_script(cls) -> str:
        return textwrap.dedent(
            f"""
            if [ -d "$INSTALL_DIR" ]; then
                printf '{cls._PROBE_PREFIX}install_root_exists=true\\n'
            else
                printf '{cls._PROBE_PREFIX}install_root_exists=false\\n'
            fi
            if [ -d "$BIN_DIR" ]; then
                printf '{cls._PROBE_PREFIX}bin_dir_exists=true\\n'
            else
                printf '{cls._PROBE_PREFIX}bin_dir_exists=false\\n'
            fi
            if [ -s "$JAR_PATH" ]; then
                printf '{cls._PROBE_PREFIX}jar_exists=true\\n'
            else
                printf '{cls._PROBE_PREFIX}jar_exists=false\\n'
            fi
            if [ -f "$SCRIPT_PATH" ]; then
                printf '{cls._PROBE_PREFIX}script_exists=true\\n'
            else
                printf '{cls._PROBE_PREFIX}script_exists=false\\n'
            fi
            if [ -x "$SCRIPT_PATH" ]; then
                printf '{cls._PROBE_PREFIX}script_is_executable=true\\n'
            else
                printf '{cls._PROBE_PREFIX}script_is_executable=false\\n'
            fi
            if [ -f "$INSTALLER_ENV_PATH" ]; then
                while IFS= read -r line || [ -n "$line" ]; do
                    printf '{cls._PROBE_PREFIX}{cls._INSTALLER_ENV_LINE_KEY}=%s\\n' "$line"
                done < "$INSTALLER_ENV_PATH"
            fi
            """
        ).strip()

    @classmethod
    def _strip_ansi(cls, value: str) -> str:
        return cls._ANSI_ESCAPE_PATTERN.sub("", value)
