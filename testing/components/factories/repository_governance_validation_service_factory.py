from __future__ import annotations

from pathlib import Path

from testing.components.services.repository_governance_validation_service import (
    RepositoryGovernanceValidationService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_repository_governance_validation_service(
    repository_root: Path,
    *,
    topic_limit: int = 20,
) -> RepositoryGovernanceValidationService:
    return RepositoryGovernanceValidationService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        topic_limit=topic_limit,
    )
