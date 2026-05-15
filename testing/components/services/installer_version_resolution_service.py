from __future__ import annotations

import os
import re
import tempfile
import textwrap
from pathlib import Path
from typing import Mapping, Sequence

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.installer_version_resolution_observation import (
    InstallerVersionResolutionObservation,
)


class InstallerVersionResolutionService:
    _ANSI_ESCAPE_PATTERN = re.compile(r"\x1B\[[0-?]*[ -/]*[@-~]")
    _RELEASES_URL_TEMPLATE = "https://api.github.com/repos/epam/dm.ai/releases?per_page=100&page={page}"
    _LATEST_URL = "https://api.github.com/repos/epam/dm.ai/releases/latest"

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        install_script_relative_path: str | Path = "install.sh",
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.install_script_path = repository_root / Path(install_script_relative_path)

    def observe_latest_release_resolution(
        self,
        *,
        release_pages: Sequence[str],
        latest_release_response: str | None = None,
        extra_env: Mapping[str, str | None] | None = None,
    ) -> InstallerVersionResolutionObservation:
        if not release_pages:
            raise ValueError("At least one paginated GitHub releases response must be provided.")

        with tempfile.TemporaryDirectory(prefix="dmtools-installer-version-") as temp_dir:
            temp_root = Path(temp_dir)
            stub_bin_dir = temp_root / "stub-bin"
            install_dir = temp_root / "install"
            bin_dir = install_dir / "bin"
            installer_env_path = bin_dir / "dmtools-installer.env"
            curl_log_path = temp_root / "curl-calls.log"

            stub_bin_dir.mkdir(parents=True, exist_ok=True)
            self._write_executable(
                stub_bin_dir / "curl",
                self._curl_stub_script(
                    release_pages=release_pages,
                    curl_log_path=curl_log_path,
                    latest_release_response=latest_release_response,
                ),
            )
            self._write_executable(
                stub_bin_dir / "git",
                """
                #!/bin/bash
                echo "git fallback should not be used when the paginated releases API already contains a stable CLI tag" >&2
                exit 1
                """,
            )

            env: dict[str, str | None] = {
                "DMTOOLS_INSTALLER_TEST_MODE": "true",
                "DMTOOLS_INSTALL_DIR": str(install_dir),
                "DMTOOLS_BIN_DIR": str(bin_dir),
                "DMTOOLS_INSTALLER_ENV_PATH": str(installer_env_path),
                "DMTOOLS_SKILLS": None,
                "PATH": f"{stub_bin_dir}:{os.environ['PATH']}",
            }
            if extra_env:
                env.update(extra_env)

            execution = self.runner.run(
                ["bash", "-lc", self._installer_command()],
                cwd=self.repository_root,
                env=env,
            )

            return InstallerVersionResolutionObservation(
                execution=execution,
                stdout=self._strip_ansi(execution.stdout).strip(),
                stderr=self._strip_ansi(execution.stderr).strip(),
                curl_calls=tuple(
                    curl_log_path.read_text(encoding="utf-8").splitlines()
                    if curl_log_path.exists()
                    else ()
                ),
            )

    def _installer_command(self) -> str:
        return textwrap.dedent(
            f"""
            set -e
            source "{self.install_script_path}"

            check_java() {{
                info "stubbed check_java"
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

            main
            """
        ).strip()

    def _curl_stub_script(
        self,
        *,
        release_pages: Sequence[str],
        curl_log_path: Path,
        latest_release_response: str | None,
    ) -> str:
        cases: list[str] = []
        for page, response in enumerate(release_pages, start=1):
            url = self._RELEASES_URL_TEMPLATE.format(page=page)
            cases.append(
                f'"{url}")\n'
                f"cat <<'EOF_RELEASES_PAGE_{page}'\n"
                f"{response}\n"
                f"EOF_RELEASES_PAGE_{page}\n"
                ";;"
            )

        if latest_release_response is not None:
            cases.append(
                f'"{self._LATEST_URL}")\n'
                "cat <<'EOF_LATEST_RELEASE'\n"
                f"{latest_release_response}\n"
                "EOF_LATEST_RELEASE\n"
                ";;"
            )

        cases_block = "\n".join(cases)
        return (
            "#!/bin/bash\n"
            "set -e\n"
            f"printf '%s\\n' \"$*\" >> \"{curl_log_path}\"\n"
            "url=\"${@: -1}\"\n"
            "case \"$url\" in\n"
            f"{cases_block}\n"
            "*)\n"
            "    echo \"unexpected curl invocation: $*\" >&2\n"
            "    exit 1\n"
            "    ;;\n"
            "esac\n"
        ).strip()

    @staticmethod
    def _write_executable(path: Path, content: str) -> None:
        path.write_text(textwrap.dedent(content).lstrip(), encoding="utf-8")
        path.chmod(0o755)

    @classmethod
    def _strip_ansi(cls, text: str) -> str:
        return cls._ANSI_ESCAPE_PATTERN.sub("", text)
