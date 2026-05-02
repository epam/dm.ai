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


def create_service(*, repository_root: Path = REPOSITORY_ROOT) -> LegacyRemovalAuditService:
    removed_files = [str(path) for path in CONFIG.get("removed_files", [])]
    return LegacyRemovalAuditService(
        repository_root=repository_root,
        removed_paths=removed_files,
        build_command=str(CONFIG["build_command"]),
    )


def test_dmc_909_legacy_code_generator_implementation_files_are_removed() -> None:
    service = create_service()

    audit = service.run_audit(timeout=3600)

    assert not audit.present_files and audit.build_result.returncode == 0, (
        service.format_failures(audit)
    )


def test_dmc_909_service_reports_present_files_before_build_failure_details(tmp_path: Path) -> None:
    for relative_path in CONFIG["removed_files"]:
        file_path = tmp_path / str(relative_path)
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text("// legacy shim still present\n", encoding="utf-8")

    def fake_build_executor(_: Path, command: str, timeout: int) -> CommandResult:
        assert timeout == 1800
        return CommandResult(
            command=command,
            returncode=1,
            stdout="BUILD FAILED",
            stderr="Dependency on removed implementation remains.",
        )

    service = LegacyRemovalAuditService(
        repository_root=tmp_path,
        removed_paths=[str(path) for path in CONFIG["removed_files"]],
        build_command=str(CONFIG["build_command"]),
        build_executor=fake_build_executor,
    )

    audit = service.run_audit()
    failure_message = service.format_failures(audit)

    assert audit.present_files == tuple(str(path) for path in CONFIG["removed_files"])
    assert "Step 1: expected" in failure_message
    assert "Step 5: run `./gradlew --no-daemon :dmtools-core:build` -> exit code 1 (FAIL)" in failure_message
    assert "Observed legacy implementation files still present" in failure_message
    assert "Dependency on removed implementation remains." in failure_message
