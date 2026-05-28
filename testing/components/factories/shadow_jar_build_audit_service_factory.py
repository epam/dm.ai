from __future__ import annotations

from pathlib import Path

from testing.components.services.shadow_jar_build_audit_service import (
    ShadowJarBuildAuditService as ShadowJarBuildAuditServiceImpl,
)
from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.interfaces.shadow_jar_build_audit_service import (
    ShadowJarBuildAuditService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_shadow_jar_build_audit_service(
    repository_root: Path,
    runner: ProcessRunner | None = None,
    *,
    gradle_task: str = ":dmtools-core:shadowJar",
    expected_directory: str = "dmtools-core/build/libs",
    fallback_directory: str = "build/libs",
    artifact_pattern: str = "dmtools-v*-all.jar",
) -> ShadowJarBuildAuditService:
    return ShadowJarBuildAuditServiceImpl(
        repository_root=repository_root,
        runner=runner or SubprocessProcessRunner(),
        gradle_task=gradle_task,
        expected_directory=expected_directory,
        fallback_directory=fallback_directory,
        artifact_pattern=artifact_pattern,
    )
