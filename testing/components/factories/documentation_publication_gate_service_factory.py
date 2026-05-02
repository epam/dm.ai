from __future__ import annotations

import os
from pathlib import Path

from testing.components.services.documentation_publication_gate_service import (
    DocumentationPublicationGateService,
)
from testing.frameworks.api.rest.github_publication_gate_client import (
    GitHubPublicationGateRestClient,
)


def create_documentation_publication_gate_service(
    repository_root: Path,
    ticket_key: str,
    *,
    owner: str = "epam",
    repo: str = "dm.ai",
    token: str | None = None,
    target_pull_request_number: int | None = None,
    technical_writer_logins: list[str] | tuple[str, ...] | set[str] | None = None,
) -> DocumentationPublicationGateService:
    github_token = token if token is not None else os.environ.get("GH_TOKEN") or os.environ.get(
        "GITHUB_TOKEN"
    )
    return DocumentationPublicationGateService(
        repository_root=repository_root,
        ticket_key=ticket_key,
        github_client=GitHubPublicationGateRestClient(
            owner=owner,
            repo=repo,
            token=github_token,
        ),
        target_pull_request_number=target_pull_request_number,
        technical_writer_logins=technical_writer_logins,
    )
