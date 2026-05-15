from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.report_generator_rate_limit_audit_service import (  # noqa: E402
    ReportGeneratorRateLimitAuditService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def build_service() -> ReportGeneratorRateLimitAuditService:
    return ReportGeneratorRateLimitAuditService(
        repository_root=REPOSITORY_ROOT,
        runner=SubprocessProcessRunner(),
        implementation_relative_path=str(CONFIG["implementation_relative_path"]),
        regression_test_relative_path=str(CONFIG["regression_test_relative_path"]),
        java_test_class=str(CONFIG["java_test_class"]),
        java_test_method=str(CONFIG["java_test_method"]),
    )


def test_dmc_1031_report_generator_uses_fallback_delay_when_rate_limit_reset_header_is_missing() -> None:
    service = build_service()
    audit = service.audit()
    implementation_text = audit.implementation_text
    regression_test_text = audit.regression_test_text
    execution = audit.gradle_execution

    assert str(CONFIG["missing_retry_after_guard"]) in implementation_text
    assert str(CONFIG["missing_header_guard"]) in implementation_text
    assert "private static final long RATE_LIMIT_FALLBACK_DELAY_MS = 60000L;" in implementation_text
    assert str(CONFIG["expected_warning"]) in implementation_text
    assert "return RATE_LIMIT_FALLBACK_DELAY_MS;" in implementation_text

    assert (
        "void testCollectDataFromAllSources_usesFallbackDelayWhenRateLimitMetadataMissing()"
        in regression_test_text
    )
    assert 'when(rateLimitResponse.header("Retry-After")).thenReturn("invalid");' in regression_test_text
    assert 'when(rateLimitResponse.header("X-RateLimit-Reset")).thenReturn("invalid");' in regression_test_text
    assert (
        f"assertEquals(List.of({CONFIG['expected_fallback_delay_ms']}L), generator.getObservedDelays());"
        in regression_test_text
    )
    assert (
        'verify(sourceCode, times(2)).getCommitsFromBranch("workspace", "repo", "main", "2025-01-01", null);'
        in regression_test_text
    )

    assert execution.returncode == 0, (
        f"Expected Gradle test {service.java_test_selector} to pass, "
        f"but it exited with {execution.returncode}.\n{execution.combined_output}"
    )
    assert audit.report_xml_text, "Expected Gradle to write a JUnit XML report for the targeted ReportGenerator test."
    assert (
        f'name="{CONFIG["java_test_method"]}()"' in audit.report_xml_text
    ), "Expected the targeted ReportGenerator regression test to appear in the JUnit XML report."
    assert 'failures="0"' in audit.report_xml_text
    assert 'errors="0"' in audit.report_xml_text
    assert "Rate limit metadata unavailable or invalid. Falling back to 60000 ms before retry." in audit.report_xml_text
    assert "Waiting 60000 ms before retry attempt 1/5." in audit.report_xml_text
