from pathlib import Path

from testing.components.services.upgrade_guidance_service import UpgradeGuidanceService


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
VISIBLE_GUIDANCE_PATHS = {
    "README.md",
    "dmtools-ai-docs/references/installation/README.md",
}


def test_dmc_902_upgrade_guidance_is_complete() -> None:
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

    incomplete_sections = [
        audits_by_path[path]
        for path in sorted(VISIBLE_GUIDANCE_PATHS)
        if audits_by_path[path].missing_requirements
    ]
    assert not incomplete_sections, service.format_missing_requirements(incomplete_sections)
