from pathlib import Path

from testing.components.services.documentation_audit_service import DocumentationAuditService
from testing.components.services.job_registry_service import JobRegistryService
from testing.core.interfaces.documentation_audit import DocumentationAudit
from testing.core.interfaces.job_registry import JobRegistry


def create_documentation_audit(
    teammate_configs_path: Path,
    docs_root: Path,
) -> DocumentationAudit:
    return DocumentationAuditService(teammate_configs_path, docs_root)


def create_job_registry(job_runner_path: Path) -> JobRegistry:
    return JobRegistryService(job_runner_path)
