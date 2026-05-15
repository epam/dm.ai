from __future__ import annotations

import sys
from pathlib import Path

import pytest


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.installer_version_resolution_service_factory import (  # noqa: E402
    create_installer_version_resolution_service,
)


EXPECTED_VERSION = "v1.7.184"
EXPECTED_RELEASES_URL = "https://api.github.com/repos/epam/dm.ai/releases?per_page=100&page=1"
RELEASES_PAGE = f"""[
  {{ "tag_name": "v2026.0507.195911-standalone" }},
  {{ "tag_name": "skill-v1.0.19" }},
  {{ "tag_name": "v1.7.184-beta.55.1" }},
  {{ "tag_name": "{EXPECTED_VERSION}" }},
  {{ "tag_name": "v1.7.183" }}
]"""


@pytest.mark.parametrize(
    "installer_script_relative_path",
    ["install.sh", "install"],
    ids=["install.sh", "install"],
)
def test_dmc_1027_installer_filters_non_cli_releases_from_github_api(
    installer_script_relative_path: str,
) -> None:
    service = create_installer_version_resolution_service(
        REPOSITORY_ROOT,
        install_script_relative_path=installer_script_relative_path,
    )

    observation = service.observe_latest_release_resolution(release_pages=[RELEASES_PAGE])

    assert observation.execution.returncode == 0, (
        "The installer flow should complete successfully when the paginated GitHub "
        "releases API already includes a stable CLI release.\n"
        f"stdout:\n{observation.stdout}\n\nstderr:\n{observation.stderr}"
    )
    assert "Installing DMTools CLI..." in observation.stdout, (
        "The real installer path should emit the standard installation banner.\n"
        f"stdout:\n{observation.stdout}"
    )
    assert f"Latest version: {EXPECTED_VERSION}" in observation.stdout, (
        "The installer logs should report the resolved stable CLI version.\n"
        f"stdout:\n{observation.stdout}"
    )
    assert f"stubbed download_dmtools {EXPECTED_VERSION}" in observation.stdout, (
        "The installer should pass the resolved version through the normal download step.\n"
        f"stdout:\n{observation.stdout}"
    )
    assert f"Found latest CLI release: {EXPECTED_VERSION}" in observation.stderr, (
        "The installer should report the selected stable CLI release in its progress logs.\n"
        f"stderr:\n{observation.stderr}"
    )
    assert "Paginated search failed" not in observation.stderr, (
        "The installer should not fall back when the paginated releases API already "
        "contains a matching stable CLI release.\n"
        f"stderr:\n{observation.stderr}"
    )
    assert "Falling back to git tag lookup" not in observation.stderr, (
        "Git tag fallback must stay unused when the paginated releases API already "
        "returned a stable CLI release.\n"
        f"stderr:\n{observation.stderr}"
    )
    assert observation.curl_calls == (
        f"-s --connect-timeout 10 --max-time 30 --fail {EXPECTED_RELEASES_URL}",
    ), (
        "The installer should stop after the first paginated releases request once it "
        "finds the newest stable CLI release.\n"
        f"curl calls:\n{observation.curl_calls}"
    )
