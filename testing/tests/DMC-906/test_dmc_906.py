from pathlib import Path

from testing.components.services.upgrade_guidance_service import UpgradeGuidanceService


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
VISIBLE_GUIDANCE_PATHS = {
    "README.md",
    "dmtools-ai-docs/references/installation/README.md",
}


def test_dmc_906_visible_upgrade_sections_include_complete_migration_safeguards() -> None:
    service = UpgradeGuidanceService(REPOSITORY_ROOT)

    audits_by_path = {
        audit.path.relative_to(REPOSITORY_ROOT).as_posix(): audit
        for audit in service.audit_upgrade_guidance_sections()
    }

    missing_sections = sorted(VISIBLE_GUIDANCE_PATHS.difference(audits_by_path))
    assert not missing_sections, (
        "Expected visible upgrade guidance sections for "
        + ", ".join(sorted(VISIBLE_GUIDANCE_PATHS))
        + f"; missing {', '.join(missing_sections)}"
    )

    incomplete_sections = {
        path: audits_by_path[path].missing_requirements
        for path in sorted(VISIBLE_GUIDANCE_PATHS)
        if audits_by_path[path].missing_requirements
    }
    assert not incomplete_sections, (
        "Visible upgrade guidance sections are missing required migration safeguards: "
        + ", ".join(
            f"{path} -> {', '.join(requirements)}"
            for path, requirements in incomplete_sections.items()
        )
    )
