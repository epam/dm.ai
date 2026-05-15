from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.report_generator_rate_limit_audit_service_factory import (  # noqa: E402
    create_report_generator_rate_limit_audit_service,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def build_service():
    return create_report_generator_rate_limit_audit_service(
        repository_root=REPOSITORY_ROOT,
        expected_fallback_delay_ms=int(str(CONFIG["expected_fallback_delay_ms"])),
    )


def test_dmc_1031_report_generator_uses_fallback_delay_when_rate_limit_reset_header_is_missing() -> None:
    service = build_service()
    audit = service.audit()
    execution = audit.missing_header_probe_execution

    assert execution.returncode == 0, (
        "Expected the ReportGenerator missing-header probe to pass, "
        f"but it exited with {execution.returncode}.\n{execution.combined_output}"
    )
    combined_output = execution.combined_output

    assert f"observedDelays=[{CONFIG['expected_fallback_delay_ms']}]" in combined_output
    assert "sourceNames=[commits]" in combined_output
    assert "metricNames=[CommitsMetricSource]" in combined_output
    assert str(CONFIG["expected_warning_log"]) in combined_output
    assert str(CONFIG["expected_retry_log"]) in combined_output
