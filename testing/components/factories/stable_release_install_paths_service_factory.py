from __future__ import annotations

import os
from pathlib import Path

from testing.components.services.stable_release_install_paths_service import (
    StableReleaseInstallPathsService,
)
from testing.frameworks.api.rest.github_publication_gate_client import (
    GitHubPublicationGateRestClient,
)


def create_stable_release_install_paths_service(
    repository_root: Path,
    *,
    owner: str = "epam",
    repo: str = "dm.ai",
    workflow_file: str = "release.yml",
    workflow_job_name: str = "create-unified-release",
    release_limit: int = 10,
    run_limit: int = 20,
    max_run_match_seconds: int = StableReleaseInstallPathsService.DEFAULT_MAX_RUN_MATCH_SECONDS,
    token: str | None = None,
) -> StableReleaseInstallPathsService:
    github_token = token if token is not None else os.environ.get("GH_TOKEN") or os.environ.get(
        "GITHUB_TOKEN"
    )
    return StableReleaseInstallPathsService(
        repository_root=repository_root,
        github_client=GitHubPublicationGateRestClient(
            owner=owner,
            repo=repo,
            token=github_token,
        ),
        owner=owner,
        repo=repo,
        workflow_file=workflow_file,
        workflow_job_name=workflow_job_name,
        release_limit=release_limit,
        run_limit=run_limit,
        max_run_match_seconds=max_run_match_seconds,
    )
