from pathlib import Path

from testing.components.services.social_preview_asset_service import (
    SocialPreviewAssetService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_989_social_preview_svg_and_playbook_meet_repository_standards() -> None:
    service = SocialPreviewAssetService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)


def test_dmc_989_service_accepts_a_compliant_social_preview_fixture(tmp_path: Path) -> None:
    repository_root = _build_fixture_repository(
        tmp_path,
        svg_fill="#F8FAFC",
        include_export_sizes=True,
    )

    service = SocialPreviewAssetService(repository_root)

    failures = service.validate()
    asset_path = service.locate_social_preview_asset()

    assert failures == [], service.format_failures(failures)
    assert asset_path is not None
    observation = service.inspect_asset(asset_path)
    assert service.relative_path(observation.asset_path) == "assets/social-preview.v1.svg"
    assert observation.hero_fill_hex == "#0F172A"
    assert observation.wordmark_texts == ("DMTools",)
    assert observation.decorative_background_count == 1
    assert all(item.contrast_ratio >= 4.5 for item in observation.text_contrast_observations)


def test_dmc_989_service_reports_missing_export_guidance_and_low_contrast_text(
    tmp_path: Path,
) -> None:
    repository_root = _build_fixture_repository(
        tmp_path,
        svg_fill="#475569",
        include_export_sizes=False,
    )

    service = SocialPreviewAssetService(repository_root)

    failures = service.validate()
    failure_message = service.format_failures(failures)

    assert [failure.step for failure in failures] == [3, 4]
    assert "below the required 4.5:1 contrast ratio" in failure_message
    assert "'DMTools' (#475569)" in failure_message
    assert "1280x640 minimum and 2560x1280 recommended" in failure_message


def test_dmc_989_service_rejects_generic_svg_names_without_a_versioned_social_preview_source(
    tmp_path: Path,
) -> None:
    repository_root = _build_fixture_repository(
        tmp_path,
        include_social_preview_asset=False,
        include_export_sizes=True,
        extra_svg_files={
            "assets/social.svg": [
                '<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="640">',
                '  <rect width="1280" height="640" fill="#0F172A" />',
                '  <text x="96" y="280" fill="#F8FAFC">DMTools</text>',
                "</svg>",
            ],
            "assets/preview.svg": [
                '<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="640">',
                '  <rect width="1280" height="640" fill="#0F172A" />',
                '  <text x="96" y="280" fill="#F8FAFC">DMTools</text>',
                "</svg>",
            ],
        },
    )

    service = SocialPreviewAssetService(repository_root)

    failures = service.validate()

    assert [failure.step for failure in failures] == [1, 2, 3]
    assert service.locate_social_preview_asset() is None
    assert "versioned SVG file matching the social-preview naming contract" in failures[0].actual


def test_dmc_989_service_requires_export_sizes_inside_social_preview_guidance_section(
    tmp_path: Path,
) -> None:
    repository_root = _build_fixture_repository(
        tmp_path,
        include_export_sizes=False,
        playbook_extra_sections=[
            "### Release checklist",
            "",
            "- Export screenshots at 1280x640 minimum.",
            "- Archive masters at 2560x1280 recommended.",
        ],
    )

    service = SocialPreviewAssetService(repository_root)

    failures = service.validate()

    assert [failure.step for failure in failures] == [4]
    assert "### Social preview asset guidance" in failures[0].actual
    assert "### Release checklist" not in failures[0].actual


def _build_fixture_repository(
    tmp_path: Path,
    *,
    svg_fill: str = "#F8FAFC",
    include_export_sizes: bool,
    include_social_preview_asset: bool = True,
    social_preview_asset_path: str = "assets/social-preview.v1.svg",
    extra_svg_files: dict[str, list[str]] | None = None,
    playbook_extra_sections: list[str] | None = None,
) -> Path:
    repository_root = tmp_path / "repo"
    repository_root.mkdir()

    if include_social_preview_asset:
        social_preview_path = repository_root / social_preview_asset_path
        social_preview_path.parent.mkdir(parents=True, exist_ok=True)
        social_preview_path.write_text(
            "\n".join(
                [
                    '<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="640" viewBox="0 0 1280 640">',
                    '  <rect width="1280" height="640" fill="#0F172A" />',
                    '  <path d="M96 112 H1184 M96 176 H1184" stroke="#94A3B8" stroke-width="4" opacity="0.18" />',
                    f'  <text x="96" y="280" fill="{svg_fill}" font-size="96" font-family="Inter, sans-serif">DMTools</text>',
                    '  <text x="96" y="360" fill="#E2E8F0" font-size="40" font-family="Inter, sans-serif">Enterprise dark-factory orchestrator</text>',
                    "</svg>",
                    "",
                ]
            ),
            encoding="utf-8",
        )

    if extra_svg_files:
        for relative_path, lines in extra_svg_files.items():
            asset_path = repository_root / relative_path
            asset_path.parent.mkdir(parents=True, exist_ok=True)
            asset_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    playbook_path = (
        repository_root
        / "dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md"
    )
    playbook_path.parent.mkdir(parents=True, exist_ok=True)
    playbook_lines = [
        "# GitHub Repository Discoverability Playbook",
        "",
        "### Social preview asset guidance",
        "",
        "- Use a dark, high-contrast base with light text.",
        "- Keep the text to the product name plus one short positioning line only.",
        "- Preferred value line: `Enterprise dark-factory orchestrator`.",
        "- Use at most one restrained accent colour.",
        "- Any text rendered into the image must meet WCAG AA contrast guidance.",
    ]
    if include_export_sizes:
        playbook_lines.extend(
            [
                "- Export a PNG at 1280x640 minimum.",
                "- Export a PNG at 2560x1280 recommended.",
            ]
        )
    if playbook_extra_sections:
        playbook_lines.extend(["", *playbook_extra_sections])
    playbook_path.write_text("\n".join(playbook_lines) + "\n", encoding="utf-8")
    return repository_root
