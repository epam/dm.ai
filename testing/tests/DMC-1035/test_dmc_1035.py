from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
WRAPPER_SOURCE = REPOSITORY_ROOT / "dmtools.sh"


def _write_executable(path: Path, content: str) -> None:
    path.write_text(textwrap.dedent(content).lstrip(), encoding="utf-8")
    path.chmod(0o755)


class TestDmc1035(unittest.TestCase):
    def test_installed_wrapper_uses_custom_install_dir_for_jar_discovery(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            install_dir = temp_root / "custom-install"
            bin_dir = install_dir / "bin"
            wrapper_path = bin_dir / "dmtools"
            fake_java_dir = temp_root / "fake-java"
            home_dir = temp_root / "home"

            bin_dir.mkdir(parents=True)
            fake_java_dir.mkdir()
            home_dir.mkdir()

            shutil.copy2(WRAPPER_SOURCE, wrapper_path)
            wrapper_path.chmod(0o755)
            (install_dir / "dmtools.jar").write_text("stub jar", encoding="utf-8")

            _write_executable(
                fake_java_dir / "java",
                """
                #!/bin/bash
                exit 0
                """,
            )

            result = subprocess.run(
                ["bash", str(wrapper_path), "--help"],
                cwd=REPOSITORY_ROOT,
                env={
                    **os.environ,
                    "HOME": str(home_dir),
                    "DMTOOLS_INSTALL_DIR": str(install_dir),
                    "DMTOOLS_BIN_DIR": str(bin_dir),
                    "PATH": f"{fake_java_dir}:{os.environ['PATH']}",
                },
                capture_output=True,
                text=True,
            )

            self.assertEqual(
                0,
                result.returncode,
                "The installed wrapper should find dmtools.jar in the custom installer directory "
                "used by the latest-release workflow.\n"
                f"stdout:\n{result.stdout}\n\nstderr:\n{result.stderr}",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
