from __future__ import annotations

import json
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.shadow_jar_build_audit_service_factory import (  # noqa: E402
    create_shadow_jar_build_audit_service,
)
from testing.core.interfaces.shadow_jar_build_audit_service import (  # noqa: E402
    ShadowJarBuildAuditService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def build_service() -> ShadowJarBuildAuditService:
    return create_shadow_jar_build_audit_service(
        repository_root=REPOSITORY_ROOT,
        gradle_task=str(CONFIG["gradle_task"]),
        expected_directory=str(CONFIG["expected_directory"]),
        fallback_directory=str(CONFIG["fallback_directory"]),
        artifact_pattern=str(CONFIG["artifact_pattern"]),
    )


def test_dmc_1040_shadowjar_build_generates_expected_dmtools_core_artifact() -> None:
    service = build_service()
    audit = service.audit()

    print(
        "DMC1040_OBSERVATION="
        + json.dumps(
            {
                "returncode": audit.execution.returncode,
                "expected_directory": audit.expected_directory.as_posix(),
                "expected_directory_exists": audit.expected_directory_exists,
                "expected_artifacts": [path.name for path in audit.expected_artifacts],
                "fallback_directory": audit.fallback_directory.as_posix(),
                "fallback_artifacts": [path.name for path in audit.fallback_artifacts],
            },
            sort_keys=True,
        )
    )

    assert audit.execution.returncode == 0, service.format_failure(audit)
    assert audit.expected_directory_exists, service.format_failure(audit)
    assert audit.expected_artifact_present, service.format_failure(audit)
