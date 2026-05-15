from __future__ import annotations

import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
INSTALL_ENTRYPOINTS = (REPOSITORY_ROOT / "install.sh", REPOSITORY_ROOT / "install")
WORKFLOW_PATH = REPOSITORY_ROOT / ".github" / "workflows" / "windows-git-bash-installer-check.yml"


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


class TestDmc1025(unittest.TestCase):
    def test_installer_falls_back_to_git_tags_when_github_api_lookup_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            bin_dir = Path(temp_dir) / "bin"
            bin_dir.mkdir(parents=True)

            _write_executable(
                bin_dir / "curl",
                """
                #!/bin/bash
                echo "curl: (22) The requested URL returned error: 404" >&2
                exit 22
                """,
            )
            _write_executable(
                bin_dir / "git",
                """
                #!/bin/bash
                if [ "$1" = "ls-remote" ] && [ "$2" = "--tags" ] && [ "$3" = "--refs" ]; then
                    cat <<'EOF'
                deadbeef	refs/tags/v1.7.183
                deadbeef	refs/tags/v1.7.184-beta.55.1
                deadbeef	refs/tags/v2026.0507.195911-standalone
                deadbeef	refs/tags/skill-v1.0.19
                deadbeef	refs/tags/v1.7.184
                EOF
                    exit 0
                fi
                echo "unexpected git invocation: $*" >&2
                exit 1
                """,
            )

            for installer_script in INSTALL_ENTRYPOINTS:
                with self.subTest(installer_script=installer_script.name):
                    result = run_installer_functions(
                        """
                        version=$(get_latest_version)
                        printf 'version=%s\\n' "$version"
                        """,
                        installer_script=installer_script,
                        extra_env={"PATH": f"{bin_dir}:{os.environ['PATH']}"},
                    )

                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertIn("version=v1.7.184", result.stdout)
                    self.assertIn("Falling back to git tag lookup", result.stderr)

    def test_windows_git_bash_workflow_validates_documented_latest_installer_path(self) -> None:
        workflow_text = WORKFLOW_PATH.read_text(encoding="utf-8")

        self.assertIn("workflow_dispatch:", workflow_text)
        self.assertIn("runs-on: windows-latest", workflow_text)
        self.assertIn("shell: bash", workflow_text)
        self.assertIn("releases/latest/download/install.sh | bash", workflow_text)
        self.assertIn("DMTOOLS_INSTALL_DIR", workflow_text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
