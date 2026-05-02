from __future__ import annotations

import tempfile
import textwrap
from pathlib import Path
from typing import Mapping, Sequence

from testing.core.interfaces.installer_script import InstallerScript
from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


class InstallerScriptService(InstallerScript):
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
        extra_env: Mapping[str, str | None] | None = None,
        post_script: str = "",
    ) -> ProcessExecutionResult:
        with tempfile.TemporaryDirectory(prefix="dmtools-installer-") as temp_dir:
            install_dir = Path(temp_dir) / "home" / ".dmtools"
            bin_dir = install_dir / "bin"
            installer_env_path = bin_dir / "dmtools-installer.env"

            env: dict[str, str | None] = {
                "DMTOOLS_INSTALLER_TEST_MODE": "true",
                "DMTOOLS_INSTALL_DIR": str(install_dir),
                "DMTOOLS_BIN_DIR": str(bin_dir),
                "DMTOOLS_INSTALLER_ENV_PATH": str(installer_env_path),
                "DMTOOLS_SKILLS": None,
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
                    mkdir -p "$(dirname "$JAR_PATH")" "$BIN_DIR"
                    printf 'stub jar for %s\\n' "$version" > "$JAR_PATH"
                    cat > "$SCRIPT_PATH" <<'EOF'
#!/bin/bash
echo "dmtools stub"
EOF
                    chmod +x "$SCRIPT_PATH"
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
