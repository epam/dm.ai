from pathlib import Path

import pytest

from testing.components.services.per_skill_catalog_service import (
    PerSkillCatalogService,
)
from testing.components.services.per_skill_page_audit_service import (
    PerSkillPageAuditService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def write_page_fixture(repository_root: Path, skill_name: str, package_name: str) -> Path:
    installation_guide = (
        repository_root / "dmtools-ai-docs" / "references" / "installation" / "README.md"
    )
    installation_guide.parent.mkdir(parents=True, exist_ok=True)
    installation_guide.write_text("# Installation\n", encoding="utf-8")

    per_skill_root = repository_root / "dmtools-ai-docs" / "per-skill-packages"
    per_skill_root.mkdir(parents=True, exist_ok=True)
    (per_skill_root / "index.md").write_text("# Per-skill Index\n", encoding="utf-8")

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
                "- [Per-skill index](index.md)",
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


def write_all_page_fixtures(
    repository_root: Path,
    excluded_skills: set[str] | None = None,
) -> None:
    excluded_skills = excluded_skills or set()
    for expectation in PerSkillCatalogService.EXPECTED_SKILLS:
        if expectation.skill_name in excluded_skills:
            continue
        write_page_fixture(
            repository_root,
            expectation.skill_name,
            expectation.java_package,
        )


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


def test_child_pages_excludes_per_skill_index_aliases(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    per_skill_root = repository_root / "dmtools-ai-docs" / "per-skill-packages"
    per_skill_root.mkdir(parents=True)
    (per_skill_root / "index.md").write_text("# Canonical index\n", encoding="utf-8")
    (per_skill_root / "README.md").write_text("# Legacy alias\n", encoding="utf-8")
    skill_page = per_skill_root / "dmtools-github.md"
    skill_page.write_text("# dmtools-github\n", encoding="utf-8")

    service = PerSkillPageAuditService(repository_root)

    assert service.child_pages() == [skill_page]


def test_repository_exposes_child_pages_for_each_canonical_skill() -> None:
    service = PerSkillPageAuditService(REPOSITORY_ROOT)

    expected_pages = sorted(
        REPOSITORY_ROOT / "dmtools-ai-docs" / "per-skill-packages" / f"{skill.skill_name}.md"
        for skill in PerSkillCatalogService.EXPECTED_SKILLS
    )

    assert service.child_pages() == expected_pages


def test_audit_reports_missing_expected_child_page(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    write_all_page_fixtures(repository_root, excluded_skills={"dmtools-github"})

    service = PerSkillPageAuditService(repository_root)

    assert service.audit() == [
        "Expected canonical skill child page "
        "dmtools-ai-docs/per-skill-packages/dmtools-github.md to exist, but it is missing."
    ]
