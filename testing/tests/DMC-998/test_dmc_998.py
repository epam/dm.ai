from pathlib import Path

from testing.components.services.ai_teammate_bootstrap_contract_service import (
    AITeammateBootstrapContractService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_998_ai_teammate_bootstrap_uses_release_asset_installer_contract() -> None:
    service = AITeammateBootstrapContractService(REPOSITORY_ROOT)

    workflow_reference = service.workflow_install_reference()
    docs_reference = service.public_installation_reference()

    assert docs_reference.command == service.EXPECTED_PUBLIC_INSTALL_COMMAND, (
        "The public installation guide must continue to show the canonical quick-install "
        "command that users copy from the docs.\n"
        f"Observed: {service.format_reference(docs_reference)}"
    )
    assert service.is_release_asset_installer_url(workflow_reference.installer_url), (
        "The AI Teammate workflow must fetch install.sh from the GitHub Releases asset "
        "channel instead of a source-file URL.\n"
        f"Observed: {service.format_reference(workflow_reference)}"
    )
    assert service.uses_expected_repository(workflow_reference.installer_url), (
        "The AI Teammate workflow must install DMTools from the public epam/dm.ai "
        "release channel.\n"
        f"Observed: {service.format_reference(workflow_reference)}"
    )
    assert service.version_flag_matches_url(
        workflow_reference.command,
        workflow_reference.installer_url,
    ), (
        "A version-pinned workflow install command must pass the same release tag to bash "
        "that it uses in the download URL so operators observe a consistent bootstrap contract.\n"
        f"Observed: {service.format_reference(workflow_reference)}"
    )
    assert not service.contains_legacy_source_reference(workflow_reference.command), (
        "The workflow must no longer reference legacy raw source bootstrap URLs or the old "
        "IstiN repository when installing DMTools.\n"
        f"Observed: {service.format_reference(workflow_reference)}"
    )
    assert service.is_release_asset_installer_url(docs_reference.installer_url), (
        "The public installation guide must recommend the GitHub Releases asset installer URL.\n"
        f"Observed: {service.format_reference(docs_reference)}"
    )
    assert service.uses_expected_repository(docs_reference.installer_url), (
        "The public installation guide must point to the public epam/dm.ai release channel.\n"
        f"Observed: {service.format_reference(docs_reference)}"
    )
    assert not service.contains_legacy_source_reference(docs_reference.command), (
        "The public installation guide must not fall back to legacy raw source bootstrap URLs.\n"
        f"Observed: {service.format_reference(docs_reference)}"
    )

