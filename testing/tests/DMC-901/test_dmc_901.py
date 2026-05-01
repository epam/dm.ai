import tempfile
import textwrap
from pathlib import Path

from testing.components.services.installation_reference_service import (
    InstallationReferenceService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_901_version_pinned_installation_docs_use_epam_release_artifact_pattern() -> None:
    service = InstallationReferenceService(REPOSITORY_ROOT)

    artifact_urls = service.artifact_reference_urls()
    assert (
        service.EXPECTED_VERSION_PINNED_ARTIFACT_URL in artifact_urls
    ), (
        "The installation docs do not contain the canonical EPAM version-pinned "
        "release artifact URL for manual installs.\n"
        f"Found URLs: {artifact_urls}"
    )

    non_canonical_artifact_references = service.non_canonical_artifact_references()
    assert not non_canonical_artifact_references, (
        service.format_non_canonical_artifact_references(
            non_canonical_artifact_references
        )
    )

    installer_examples = service.versioned_installer_examples()
    assert installer_examples, (
        "No specific-version installer examples were found in README.md or "
        "dmtools-ai-docs/references/installation/README.md."
    )

    mismatched_installer_examples = service.mismatched_installer_examples(installer_examples)
    assert not mismatched_installer_examples, service.format_installer_example_mismatches(
        mismatched_installer_examples
    )


def test_dmc_901_flags_non_canonical_version_pinned_artifact_urls() -> None:
    installation_readme = textwrap.dedent(
        """
        Canonical:
        https://github.com/epam/dm.ai/releases/download/v${DMTOOLS_VERSION}/dmtools-v${DMTOOLS_VERSION}-all.jar

        Wrong repository:
        https://github.com/example/dm.ai/releases/download/v1.7.179/dmtools-v1.7.179-all.jar

        Mismatched versions:
        https://github.com/epam/dm.ai/releases/download/v1.7.179/dmtools-v1.7.178-all.jar

        Non-canonical host:
        https://downloads.example.com/releases/v1.7.179/dmtools-v1.7.179-all.jar
        """
    ).strip()

    with tempfile.TemporaryDirectory() as temp_dir:
        repository_root = Path(temp_dir)
        installation_path = (
            repository_root / "dmtools-ai-docs/references/installation/README.md"
        )
        installation_path.parent.mkdir(parents=True)
        installation_path.write_text(installation_readme, encoding="utf-8")
        (repository_root / "README.md").write_text("", encoding="utf-8")

        service = InstallationReferenceService(repository_root)
        findings = service.non_canonical_artifact_references()

    assert len(findings) == 3, findings
    assert any("example/dm.ai" in finding for finding in findings), findings
    assert any(
        "path version 1.7.179 but artifact version 1.7.178" in finding
        for finding in findings
    ), findings
    assert any(
        "non-canonical version-pinned DMTools artifact URL" in finding
        and "downloads.example.com" in finding
        for finding in findings
    ), findings
