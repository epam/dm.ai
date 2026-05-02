from pathlib import Path

from testing.components.services.per_skill_catalog_service import (
    PerSkillCatalogService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_914_per_skill_catalog_lists_all_canonical_skill_rows() -> None:
    service = PerSkillCatalogService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)


def test_dmc_914_service_accepts_complete_catalog_fixture(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    catalog_path = repository_root / "dmtools-ai-docs/per-skill-packages/index.md"
    catalog_path.parent.mkdir(parents=True)

    rows = [
        "| Skill | Slash command | Java package | Artifact alias |",
        "| --- | --- | --- | --- |",
        *[
            (
                f"| `{skill.skill_name}` | `{skill.slash_command}` | "
                f"`{skill.java_package}` | `{skill.artifact_alias}` |"
            )
            for skill in PerSkillCatalogService.EXPECTED_SKILLS
        ],
    ]
    catalog_path.write_text("\n".join(rows) + "\n", encoding="utf-8")

    service = PerSkillCatalogService(repository_root)

    assert service.validate() == []


def test_dmc_914_service_reports_missing_artifact_alias(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    catalog_path = repository_root / "dmtools-ai-docs/per-skill-packages/index.md"
    catalog_path.parent.mkdir(parents=True)

    rows: list[str] = [
        "| Skill | Slash command | Java package | Artifact alias |",
        "| --- | --- | --- | --- |",
    ]
    for skill in PerSkillCatalogService.EXPECTED_SKILLS:
        artifact_alias = "" if skill.skill_name == "dmtools-report" else skill.artifact_alias
        rows.append(
            f"| `{skill.skill_name}` | `{skill.slash_command}` | "
            f"`{skill.java_package}` | `{artifact_alias}` |"
        )
    catalog_path.write_text("\n".join(rows) + "\n", encoding="utf-8")

    service = PerSkillCatalogService(repository_root)

    failures = service.validate()

    assert [failure.step for failure in failures] == [4]
    assert "dmtools-report" in failures[0].actual
    assert "com.github.istin:dmtools-report" in failures[0].actual
