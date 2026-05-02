from __future__ import annotations

from pathlib import Path

from testing.components.services.legacy_removal_audit_service import (
    LegacyRemovalAuditService,
)
from testing.core.utils.repo_sandbox import CommandResult
from testing.core.utils.ticket_config_loader import load_ticket_config


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
AUDITED_FILES = tuple(str(path) for path in CONFIG.get("audited_files", []))


def create_service(*, repository_root: Path = REPOSITORY_ROOT) -> LegacyRemovalAuditService:
    return LegacyRemovalAuditService(
        repository_root=repository_root,
        removed_paths=AUDITED_FILES,
        build_command=str(CONFIG["build_command"]),
    )


def test_dmc_909_code_generator_source_files_are_removed_and_dmtools_core_builds() -> None:
    service = create_service()
    audit = service.run_audit(timeout=3600)

    assert audit.build_result.returncode == 0, service.format_failures(audit)
    assert not audit.present_files, service.format_failures(audit)


class FakeSandbox:
    def __init__(self, workspace: Path, result: CommandResult) -> None:
        self.workspace = workspace
        self._result = result
        self.commands: list[tuple[str, int]] = []
        self.cleaned_up = False

    def cleanup(self) -> None:
        self.cleaned_up = True

    def run(self, command: str, timeout: int = 1800) -> CommandResult:
        self.commands.append((command, timeout))
        return self._result


def test_dmc_909_service_uses_injected_sandbox_and_reports_present_files_before_build_failure_details(
    tmp_path: Path,
) -> None:
    sandbox_workspace = tmp_path / "workspace"
    for relative_path in AUDITED_FILES:
        file_path = sandbox_workspace / str(relative_path)
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text("// legacy shim still present\n", encoding="utf-8")

    sandbox = FakeSandbox(
        sandbox_workspace,
        CommandResult(
            command=str(CONFIG["build_command"]),
            returncode=1,
            stdout="BUILD FAILED",
            stderr="Dependency on removed implementation remains.",
        ),
    )

    service = LegacyRemovalAuditService(
        repository_root=tmp_path,
        removed_paths=AUDITED_FILES,
        build_command=str(CONFIG["build_command"]),
        sandbox_factory=lambda _: sandbox,
    )

    audit = service.run_audit()
    failure_message = service.format_failures(audit)

    assert sandbox.commands == [(str(CONFIG["build_command"]), 1800)]
    assert sandbox.cleaned_up is True
    assert audit.present_files == AUDITED_FILES
    assert "Step 1: expected" in failure_message
    assert (
        f"Step {len(AUDITED_FILES) + 1}: run `./gradlew --no-daemon :dmtools-core:build` "
        "-> exit code 1 (FAIL)"
    ) in failure_message
    assert "Observed legacy implementation files still present" in failure_message
    assert "Dependency on removed implementation remains." in failure_message
