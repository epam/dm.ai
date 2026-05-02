from __future__ import annotations

from pathlib import Path

from testing.components.services.release_placeholder_audit_service import (
    ReleasePlaceholderAuditService,
)
from testing.core.interfaces.release_placeholder_audit import ReleasePlaceholderAudit


def create_release_placeholder_audit(
    repository_root: Path,
    doc_relative_path: str | None = None,
) -> ReleasePlaceholderAudit:
    return ReleasePlaceholderAuditService(
        repository_root=repository_root,
        doc_relative_path=doc_relative_path,
    )
