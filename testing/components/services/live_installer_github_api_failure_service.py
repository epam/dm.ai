from __future__ import annotations

import os
import tempfile
import textwrap
from dataclasses import dataclass
from pathlib import Path
from urllib.request import Request, urlopen

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class LiveInstallerGitHubApiFailureObservation:
    release_installer_url: str
    resolved_installer_url: str
    execution: ProcessExecutionResult


class LiveInstallerGitHubApiFailureService:
    USER_AGENT = "dm-ai-testing-dmc-1028"

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        release_installer_url: str,
        repo: str,
        api_failure_exit_code: int,
        api_failure_message: str,
        git_failure_exit_code: int,
        git_failure_message: str,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.release_installer_url = release_installer_url
        self.repo = repo
        self.api_failure_exit_code = api_failure_exit_code
        self.api_failure_message = api_failure_message
        self.git_failure_exit_code = git_failure_exit_code
        self.git_failure_message = git_failure_message

    def simulate_github_api_failure(self) -> LiveInstallerGitHubApiFailureObservation:
        with tempfile.TemporaryDirectory(prefix="dmc-1028-") as temp_dir:
            temp_path = Path(temp_dir)
            installer_script_path, resolved_installer_url = self._download_release_installer(temp_path)
            bin_dir = temp_path / "bin"
            bin_dir.mkdir(parents=True, exist_ok=True)
            self._write_executable(bin_dir / "curl", self._curl_stub_script())
            self._write_executable(bin_dir / "git", self._git_stub_script())

            command = textwrap.dedent(
                f"""
                set -e
                source "{installer_script_path}"
                check_java() {{ :; }}
                download_dmtools() {{ :; }}
                update_shell_config() {{ :; }}
                verify_installation() {{ :; }}
                print_instructions() {{ :; }}
                main
                """
            ).strip()
            execution = self.runner.run(
                ["bash", "-lc", command],
                cwd=self.repository_root,
                env={"DMTOOLS_INSTALLER_TEST_MODE": "true", "PATH": f"{bin_dir}:{os.environ['PATH']}"},
            )
            return LiveInstallerGitHubApiFailureObservation(
                release_installer_url=self.release_installer_url,
                resolved_installer_url=resolved_installer_url,
                execution=execution,
            )

    def normalized_stdout(self, execution: ProcessExecutionResult) -> str:
        return self._strip_ansi(execution.stdout)

    def normalized_stderr(self, execution: ProcessExecutionResult) -> str:
        return self._strip_ansi(execution.stderr)

    def normalized_combined_output(self, execution: ProcessExecutionResult) -> str:
        return self._strip_ansi(execution.combined_output)

    def _download_release_installer(self, temp_path: Path) -> tuple[Path, str]:
        request = Request(self.release_installer_url, headers={"User-Agent": self.USER_AGENT})
        with urlopen(request, timeout=30) as response:
            installer_script = response.read().decode("utf-8")
            resolved_installer_url = response.geturl()

        installer_script_path = temp_path / "install.sh"
        installer_script_path.write_text(installer_script, encoding="utf-8")
        installer_script_path.chmod(0o755)
        return installer_script_path, resolved_installer_url

    def _curl_stub_script(self) -> str:
        return textwrap.dedent(
            f"""\
            #!/bin/bash
            url="${{@: -1}}"
            if [[ "$url" == "https://api.github.com/repos/{self.repo}/releases"* ]]; then
                echo {self.api_failure_message!r} >&2
                exit {self.api_failure_exit_code}
            fi
            echo "unexpected curl invocation: $*" >&2
            exit 99
            """
        )

    def _git_stub_script(self) -> str:
        return textwrap.dedent(
            f"""\
            #!/bin/bash
            if [ "$1" = "ls-remote" ] && [ "$2" = "--tags" ] && [ "$3" = "--refs" ]; then
                echo {self.git_failure_message!r} >&2
                exit {self.git_failure_exit_code}
            fi
            echo "unexpected git invocation: $*" >&2
            exit 99
            """
        )

    @staticmethod
    def _write_executable(path: Path, content: str) -> None:
        path.write_text(content, encoding="utf-8")
        path.chmod(0o755)

    @staticmethod
    def _strip_ansi(text: str) -> str:
        result: list[str] = []
        index = 0
        while index < len(text):
            if text[index] == "\x1b" and index + 1 < len(text) and text[index + 1] == "[":
                index += 2
                while index < len(text) and text[index] not in "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~":
                    index += 1
                if index < len(text):
                    index += 1
                continue
            result.append(text[index])
            index += 1
        return "".join(result)
