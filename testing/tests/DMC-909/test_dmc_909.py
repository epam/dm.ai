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
CODE_GENERATOR_PATH = AUDITED_FILES[0]
CODE_GENERATOR_PARAMS_PATH = AUDITED_FILES[1]
CODE_GENERATOR_TEST_PATH = AUDITED_FILES[2]
CODE_GENERATOR_PARAMS_TEST_PATH = AUDITED_FILES[3]

CODE_GENERATOR_COMPATIBILITY_MARKERS = (
    "compatibility shim",
    "No code will be generated",
    "getCompatibilityResponse",
    "getDeprecationMessage",
    "REMOVAL_VERSION",
)
CODE_GENERATOR_LEGACY_MARKERS = (
    "JAssistant",
    "BasicConfluence",
    "ConversationObserver",
    "GenericReport",
    "generateCode(",
)
CODE_GENERATOR_TEST_MARKERS = (
    "getCompatibilityResponse",
    "getDeprecationMessage",
    'assertEquals("CodeGenerator", resultItems.get(0).getKey())',
)
CODE_GENERATOR_PARAMS_MARKERS = (
    "extends BaseJobParams",
    "CONFLUENCE_ROOT_PAGE",
    "EACH_PAGE_PREFIX",
    "SOURCES",
    "ROLE",
)
CODE_GENERATOR_PARAMS_TEST_MARKERS = (
    "testConstructorWithJSONString",
    "testConstructorWithJSONObject",
    "testGetConfluenceRootPage",
    "testGetRole",
)


def create_service(*, repository_root: Path = REPOSITORY_ROOT) -> LegacyRemovalAuditService:
    audited_files = list(AUDITED_FILES)
    return LegacyRemovalAuditService(
        repository_root=repository_root,
        removed_paths=audited_files,
        build_command=str(CONFIG["build_command"]),
    )


def read_repository_text(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def assert_contains_markers(
    failures: list[str],
    *,
    relative_path: str,
    source_text: str,
    expected_markers: tuple[str, ...],
) -> None:
    missing_markers = [marker for marker in expected_markers if marker not in source_text]
    if missing_markers:
        failures.append(
            f"{relative_path} is missing expected compatibility markers: {', '.join(missing_markers)}."
        )


def assert_absent_markers(
    failures: list[str],
    *,
    relative_path: str,
    source_text: str,
    forbidden_markers: tuple[str, ...],
) -> None:
    present_markers = [marker for marker in forbidden_markers if marker in source_text]
    if present_markers:
        failures.append(
            f"{relative_path} still contains legacy implementation markers: {', '.join(present_markers)}."
        )


def test_dmc_909_code_generator_is_a_compatibility_shim_and_builds() -> None:
    service = create_service()
    audit = service.run_audit(timeout=3600)
    failures: list[str] = []

    if audit.present_files != AUDITED_FILES:
        failures.append(
            "Expected compatibility shim files to be present in the sandboxed workspace.\n"
            f"Expected: {AUDITED_FILES}\n"
            f"Actual:   {audit.present_files}"
        )

    code_generator_source = read_repository_text(CODE_GENERATOR_PATH)
    assert_contains_markers(
        failures,
        relative_path=CODE_GENERATOR_PATH,
        source_text=code_generator_source,
        expected_markers=CODE_GENERATOR_COMPATIBILITY_MARKERS,
    )
    assert_absent_markers(
        failures,
        relative_path=CODE_GENERATOR_PATH,
        source_text=code_generator_source,
        forbidden_markers=CODE_GENERATOR_LEGACY_MARKERS,
    )

    assert_contains_markers(
        failures,
        relative_path=CODE_GENERATOR_PARAMS_PATH,
        source_text=read_repository_text(CODE_GENERATOR_PARAMS_PATH),
        expected_markers=CODE_GENERATOR_PARAMS_MARKERS,
    )
    assert_contains_markers(
        failures,
        relative_path=CODE_GENERATOR_TEST_PATH,
        source_text=read_repository_text(CODE_GENERATOR_TEST_PATH),
        expected_markers=CODE_GENERATOR_TEST_MARKERS,
    )
    assert_contains_markers(
        failures,
        relative_path=CODE_GENERATOR_PARAMS_TEST_PATH,
        source_text=read_repository_text(CODE_GENERATOR_PARAMS_TEST_PATH),
        expected_markers=CODE_GENERATOR_PARAMS_TEST_MARKERS,
    )

    if audit.build_result.returncode != 0:
        failures.append(
            "The dmtools-core build failed in the RepoSandbox workspace.\n"
            f"{audit.build_result.combined_output}"
        )

    assert not failures, "\n\n".join(failures)


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
    assert "Step 5: run `./gradlew --no-daemon :dmtools-core:build` -> exit code 1 (FAIL)" in failure_message
    assert "Observed legacy implementation files still present" in failure_message
    assert "Dependency on removed implementation remains." in failure_message
