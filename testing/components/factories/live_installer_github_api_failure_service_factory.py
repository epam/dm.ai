from __future__ import annotations

from pathlib import Path

from testing.components.services.live_installer_github_api_failure_service import (
    LiveInstallerGitHubApiFailureService,
)
from testing.core.interfaces.live_installer_github_api_failure_service import (
    LiveInstallerGitHubApiFailureService as LiveInstallerGitHubApiFailureServiceProtocol,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_live_installer_github_api_failure_service(
    repository_root: Path,
    *,
    release_installer_url: str,
    repo: str,
    api_failure_exit_code: int,
    api_failure_message: str,
    git_failure_exit_code: int,
    git_failure_message: str,
) -> LiveInstallerGitHubApiFailureServiceProtocol:
    return LiveInstallerGitHubApiFailureService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        release_installer_url=release_installer_url,
        repo=repo,
        api_failure_exit_code=api_failure_exit_code,
        api_failure_message=api_failure_message,
        git_failure_exit_code=git_failure_exit_code,
        git_failure_message=git_failure_message,
    )
