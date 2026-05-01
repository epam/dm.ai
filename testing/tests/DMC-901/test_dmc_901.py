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

    artifact_version_mismatches = service.artifact_reference_version_mismatches()
    assert not artifact_version_mismatches, "\n".join(
        (
            "Found installation artifact URLs where the release path version does not",
            "match the dmtools artifact filename version:",
            *[f"- {mismatch}" for mismatch in artifact_version_mismatches],
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
