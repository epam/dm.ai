from pathlib import Path

import pytest

from testing.components.services.per_skill_catalog_service import (
    PerSkillCatalogService,
)
from testing.components.services.per_skill_page_audit_service import (
    PerSkillPageAuditService,
)


def write_page_fixture(repository_root: Path, skill_name: str, package_name: str) -> Path:
    installation_guide = (
        repository_root / "dmtools-ai-docs" / "references" / "installation" / "README.md"
    )
    installation_guide.parent.mkdir(parents=True, exist_ok=True)
    installation_guide.write_text("# Installation\n", encoding="utf-8")

    per_skill_root = repository_root / "dmtools-ai-docs" / "per-skill-packages"
    per_skill_root.mkdir(parents=True, exist_ok=True)
    (per_skill_root / "README.md").write_text("# Per-skill Index\n", encoding="utf-8")

    page_path = per_skill_root / f"{skill_name}.md"
    page_path.write_text(
        "\n".join(
            [
                f"# {skill_name}",
                "",
                "## Overview",
                "",
                "Overview text.",
                "",
                "## Package / Artifact",
                "",
                f"`{package_name}`",
                "",
                "## Installer / CLI example",
                "",
                f"`/{skill_name}`",
                "",
                "## Endpoints / Config keys",
                "",
                "Endpoint details.",
                "",
                "## Minimal usage example",
                "",
                "Usage details.",
                "",
                "## Compatibility / Supported versions",
                "",
                "Compatibility details.",
                "",
                "## Security & Permissions",
                "",
                "Security details.",
                "",
                "## Linkbacks",
                "",
                "- [Installation guide](../references/installation/README.md)",
                "- [Per-skill index](README.md)",
                "",
                "## Maintainer / Contact",
                "",
                "Maintainer details.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return page_path


@pytest.mark.parametrize(
    ("skill_name", "package_name"),
    [
        ("dmtools-jira", "com.github.istin.dmtools.atlassian.jira"),
        ("dmtools-confluence", "com.github.istin.dmtools.atlassian.confluence"),
        ("dmtools-ado", "com.github.istin.dmtools.microsoft.ado"),
    ],
)
def test_audit_page_uses_canonical_skill_mapping(
    tmp_path: Path,
    skill_name: str,
    package_name: str,
) -> None:
    repository_root = tmp_path / "repo"
    page_path = write_page_fixture(repository_root, skill_name, package_name)

    service = PerSkillPageAuditService(repository_root)

    assert service.audit_page(page_path) == []


def test_audit_page_reports_unknown_skill_name(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    page_path = write_page_fixture(
        repository_root,
        "dmtools-custom",
        "com.github.istin.dmtools.custom",
    )

    service = PerSkillPageAuditService(repository_root)

    assert service.audit_page(page_path) == [
        "dmtools-ai-docs/per-skill-packages/dmtools-custom.md is not listed in the canonical "
        "per-skill catalogue mapping."
    ]


def test_expected_skill_comes_from_shared_catalog_mapping(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    page_path = repository_root / "dmtools-ai-docs" / "per-skill-packages" / "dmtools-jira.md"

    service = PerSkillPageAuditService(repository_root)

    assert service.expected_skill(page_path) == next(
        expectation
        for expectation in PerSkillCatalogService.EXPECTED_SKILLS
        if expectation.skill_name == "dmtools-jira"
    )
