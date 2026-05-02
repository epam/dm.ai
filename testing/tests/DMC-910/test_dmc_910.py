from pathlib import Path

from testing.components.services.codegenerator_migration_guidance_service import (
    CodeGeneratorMigrationGuidanceService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_910_codegenerator_migration_guidance_is_visible_and_actionable() -> None:
    service = CodeGeneratorMigrationGuidanceService(REPOSITORY_ROOT)

    audits = service.audit()

    assert {audit.label for audit in audits} == {
        "root README migration guidance section",
        "jobs reference deprecation notice",
    }

    assert service.jobs_reference_notice_appears_near_top(), (
        "Expected the jobs reference deprecation notice to appear within the first few "
        "non-empty lines near the top of the page."
    )

    incomplete_audits = [audit for audit in audits if audit.missing_requirements]
    assert not incomplete_audits, service.format_missing_requirements(audits)


def test_dmc_910_accepts_equivalent_readme_deprecated_feature_headings(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    repository_root.mkdir()
    (repository_root / "dmtools-ai-docs/references/jobs").mkdir(parents=True)

    (repository_root / "README.md").write_text(
        "\n".join(
            [
                "# DMTools",
                "",
                "## Breaking changes",
                "",
                "- `CodeGenerator` is deprecated and kept only as a compatibility shim for one release. "
                "Invocations now log a warning and return a no-op response; migrate affected automation "
                "before `v1.8.0`.",
            ]
        ),
        encoding="utf-8",
    )
    (repository_root / "dmtools-ai-docs/references/jobs/README.md").write_text(
        "\n".join(
            [
                "# Jobs Reference",
                "",
                "> **Deprecation notice:** `CodeGenerator` is no longer a supported development workflow. "
                "The CLI entry remains as a compatibility shim for one release, logs a deprecation warning, "
                "and performs no generation. Migrate to `Teammate`-driven development flows before `v1.8.0`.",
            ]
        ),
        encoding="utf-8",
    )

    service = CodeGeneratorMigrationGuidanceService(repository_root)

    readme_audit = service.audit()[0]

    assert readme_audit.location == "Breaking changes"
    assert not readme_audit.missing_requirements
