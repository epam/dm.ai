import sys
import textwrap
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.release_placeholder_audit_factory import (  # noqa: E402
    create_release_placeholder_audit,
)
from testing.core.interfaces.release_placeholder_audit import ReleasePlaceholderAudit  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def build_service(repository_root: Path) -> ReleasePlaceholderAudit:
    return create_release_placeholder_audit(
        repository_root=repository_root,
        doc_relative_path=str(CONFIG["doc_path"]),
    )


def test_dmc_917_installation_docs_use_release_placeholders_and_latest_guidance() -> None:
    service = build_service(REPOSITORY_ROOT)

    findings = service.audit()

    assert not findings, service.format_findings(findings)


def test_dmc_917_service_accepts_placeholder_based_reusable_snippets_with_latest_note(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    installation_path = repository_root / str(CONFIG["doc_path"])
    installation_path.parent.mkdir(parents=True)
    installation_path.write_text(
        textwrap.dedent(
            """
            # DMtools Installation Guide

            ```bash
            curl -fsSL https://github.com/epam/dm.ai/releases/download/${DMTOOLS_RELEASE_TAG}/install.sh | bash
            curl -fsSL https://github.com/epam/dm.ai/releases/download/v${DMTOOLS_VERSION}/install.sh | bash -s -- v${DMTOOLS_VERSION}
            wget -O ~/.dmtools/dmtools.jar "https://github.com/epam/dm.ai/releases/download/v${DMTOOLS_VERSION}/dmtools-v${DMTOOLS_VERSION}-all.jar"
            curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
            ```

            The `latest` release alias is mutable, and pinned release-tags are the authoritative
            reference for reusable automation snippets and installation examples.
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    service = build_service(repository_root)

    assert service.audit() == []


def test_dmc_917_service_accepts_equivalent_latest_guidance_wording(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    installation_path = repository_root / str(CONFIG["doc_path"])
    installation_path.parent.mkdir(parents=True)
    installation_path.write_text(
        textwrap.dedent(
            """
            # DMtools Installation Guide

            ```bash
            curl -fsSL https://github.com/epam/dm.ai/releases/download/${DMTOOLS_RELEASE_TAG}/install.sh | bash
            curl -fsSL https://github.com/epam/dm.ai/releases/download/v${DMTOOLS_VERSION}/install.sh | bash -s -- v${DMTOOLS_VERSION}
            curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
            ```

            The `latest` alias can change over time. For repeatable automation, pin to a specific
            release tag as the source of truth.
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    service = build_service(repository_root)

    assert service.audit() == []


def test_dmc_917_service_requires_mutable_latest_note_when_latest_alias_is_used(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    installation_path = repository_root / str(CONFIG["doc_path"])
    installation_path.parent.mkdir(parents=True)
    installation_path.write_text(
        textwrap.dedent(
            """
            # DMtools Installation Guide

            ```bash
            curl -fsSL https://github.com/epam/dm.ai/releases/download/${DMTOOLS_RELEASE_TAG}/install.sh | bash
            curl -fsSL https://github.com/epam/dm.ai/releases/download/v${DMTOOLS_VERSION}/install.sh | bash -s -- v${DMTOOLS_VERSION}
            curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
            ```

            Use the latest alias for the fastest path.
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    service = build_service(repository_root)

    findings = service.audit()

    assert len(findings) == 1
    assert "mutable" in findings[0]
    assert "authoritative reference" in findings[0]
