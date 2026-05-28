from __future__ import annotations

from typing import Protocol

from testing.core.models.shadow_jar_build_audit import ShadowJarBuildAudit


class ShadowJarBuildAuditService(Protocol):
    def audit(self) -> ShadowJarBuildAudit:
        raise NotImplementedError

    def format_failure(self, audit: ShadowJarBuildAudit) -> str:
        raise NotImplementedError
