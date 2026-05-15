from __future__ import annotations

import os
import subprocess
import tempfile
import textwrap
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
INSTALL_ENTRYPOINTS = (REPOSITORY_ROOT / "install.sh", REPOSITORY_ROOT / "install")
EXPECTED_VERSION = "v1.7.184"
EXPECTED_RELEASES_URL = (
    "https://api.github.com/repos/epam/dm.ai/releases?per_page=100&page=1"
)


def run_installer_functions(
    commands: str,
    *,
    installer_script: Path,
    extra_env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["DMTOOLS_INSTALLER_TEST_MODE"] = "true"
    if extra_env:
        env.update(extra_env)

    script = f"""
set -e
source "{installer_script}"
{commands}
"""
    return subprocess.run(
        ["bash", "-lc", script],
        cwd=REPOSITORY_ROOT,
        env=env,
        capture_output=True,
        text=True,
    )


def _write_executable(path: Path, content: str) -> None:
    path.write_text(textwrap.dedent(content).lstrip(), encoding="utf-8")
    path.chmod(0o755)


def test_dmc_1027_installer_filters_non_cli_releases_from_github_api() -> None:
    for installer_script in INSTALL_ENTRYPOINTS:
        with tempfile.TemporaryDirectory() as temp_dir:
            bin_dir = Path(temp_dir) / "bin"
            curl_calls_log = Path(temp_dir) / "curl-calls.log"
            bin_dir.mkdir(parents=True)

            _write_executable(
                bin_dir / "curl",
                f"""
                #!/bin/bash
                set -e
                printf '%s\\n' "$*" >> "{curl_calls_log}"
                url="${{@: -1}}"
                if [ "$url" = "{EXPECTED_RELEASES_URL}" ]; then
                    cat <<'EOF'
                [
                  {{ "tag_name": "v2026.0507.195911-standalone" }},
                  {{ "tag_name": "skill-v1.0.19" }},
                  {{ "tag_name": "v1.7.184-beta.55.1" }},
                  {{ "tag_name": "{EXPECTED_VERSION}" }},
                  {{ "tag_name": "v1.7.183" }}
                ]
                EOF
                else
                    echo "unexpected curl invocation: $*" >&2
                    exit 1
                fi
                """,
            )
            _write_executable(
                bin_dir / "git",
                """
                #!/bin/bash
                echo "git fallback should not be used when the releases API already contains a stable CLI tag" >&2
                exit 1
                """,
            )

            result = run_installer_functions(
                """
                version=$(get_latest_version)
                printf 'version=%s\\n' "$version"
                """,
                installer_script=installer_script,
                extra_env={"PATH": f"{bin_dir}:{os.environ['PATH']}"},
            )

            assert result.returncode == 0, result.stderr
            assert f"version={EXPECTED_VERSION}" in result.stdout
            assert f"Found latest CLI release: {EXPECTED_VERSION}" in result.stderr
            assert "trying fallback" not in result.stderr

            curl_calls = curl_calls_log.read_text(encoding="utf-8").splitlines()
            assert curl_calls == [
                f"-s --connect-timeout 10 --max-time 30 --fail {EXPECTED_RELEASES_URL}"
            ]
