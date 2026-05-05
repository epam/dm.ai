from __future__ import annotations

import os

from testing.core.interfaces.github_actions_release_client import GitHubActionsReleaseClient
from testing.frameworks.api.rest.github_actions_release_client import (
    GitHubActionsReleaseRestClient,
)


def create_github_actions_release_client(
    *,
    owner: str,
    repo: str,
    token: str | None = None,
) -> GitHubActionsReleaseClient:
    github_token = token if token is not None else os.environ.get("GH_TOKEN") or os.environ.get(
        "GITHUB_TOKEN"
    )
    return GitHubActionsReleaseRestClient(
        owner=owner,
        repo=repo,
        token=github_token,
    )
