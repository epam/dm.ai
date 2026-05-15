from __future__ import annotations

import os
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.workflow_dispatch_validation_service_factory import (  # noqa: E402
    create_workflow_dispatch_validation_service,
)
from testing.core.interfaces.workflow_dispatch_validation_service import (  # noqa: E402
    WorkflowDispatchValidationService,
)
from testing.core.models.workflow_dispatch_validation import (  # noqa: E402
    WorkflowDispatchValidationAudit,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
WORKFLOW_PATH = REPOSITORY_ROOT / ".github" / "workflows" / str(CONFIG["workflow_file"])


def _normalized_config_list(key: str) -> tuple[str, ...]:
    values = CONFIG.get(key, [])
    if not isinstance(values, list):
        raise TypeError(f"Expected {key!r} to be a list in {TEST_DIRECTORY / 'config.yaml'}.")
    return tuple(str(value) for value in values)


def build_service() -> WorkflowDispatchValidationService:
    return create_workflow_dispatch_validation_service(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_ref=str(CONFIG["workflow_ref"]),
        workflow_name=str(CONFIG["workflow_name"]),
        workflow_job_name=str(CONFIG["workflow_job_name"]),
        dispatch_timeout_seconds=int(str(CONFIG["dispatch_timeout_seconds"])),
        completion_timeout_seconds=int(str(CONFIG["completion_timeout_seconds"])),
        poll_interval_seconds=int(str(CONFIG["poll_interval_seconds"])),
        required_step_names=_normalized_config_list("required_step_names"),
        required_log_fragments=_normalized_config_list("required_log_fragments"),
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )


def _assert_successful_audit(audit: WorkflowDispatchValidationAudit) -> None:
    assert audit.workflow_run is not None
    assert audit.workflow_job is not None


def test_dmc_1029_manual_windows_validation_workflow_passes_in_ci_environment() -> None:
    workflow_text = WORKFLOW_PATH.read_text(encoding="utf-8")

    assert "name: Windows Git Bash Installer Check" in workflow_text
    assert "workflow_dispatch:" in workflow_text
    assert "runs-on: windows-latest" in workflow_text
    assert "shell: bash" in workflow_text
    assert "releases/latest/download/install.sh | bash" in workflow_text
    assert 'test -f "$DMTOOLS_INSTALL_DIR/dmtools.jar"' in workflow_text
    assert '"$DMTOOLS_BIN_DIR/dmtools" --help >/dev/null' in workflow_text

    service = build_service()
    audit = service.audit()

    assert not audit.failures, service.format_failures(audit.failures)
    _assert_successful_audit(audit)

    assert audit.workflow_run.event == "workflow_dispatch"
    assert audit.workflow_run.head_branch == str(CONFIG["workflow_ref"])
    assert audit.workflow_run.conclusion == "success"
    assert audit.workflow_job.name == str(CONFIG["workflow_job_name"])
    assert audit.workflow_job.conclusion == "success"

    required_steps = _normalized_config_list("required_step_names")
    observed_steps = audit.workflow_job.step_conclusions
    assert tuple(observed_steps.keys())[: len(required_steps)] or observed_steps
    for step_name in required_steps:
        assert observed_steps.get(step_name) == "success"

    print(f"Workflow run URL: {audit.workflow_run.html_url}")
    print(f"Workflow job URL: {audit.workflow_job.html_url}")
    print(f"Observed steps: {observed_steps}")
