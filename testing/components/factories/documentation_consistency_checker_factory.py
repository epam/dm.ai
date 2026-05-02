from __future__ import annotations

from pathlib import Path

from testing.components.services.documentation_consistency_service import (
    DocumentationConsistencyService,
)
from testing.core.interfaces.documentation_consistency_checker import (
    DocumentationConsistencyChecker,
)


def create_documentation_consistency_checker(
    repository_root: Path,
) -> DocumentationConsistencyChecker:
    return DocumentationConsistencyService(repository_root)

