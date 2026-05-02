from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_903_installation_cross_links_are_bidirectional() -> None:
    service = DocumentationCrossLinkService(REPOSITORY_ROOT)

    readme_links = service.readme_installation_links()
    expected_guides = {
        service.relative_path(path): path
        for path in service.detailed_guides
    }
    matched_guide_links = {
        service.relative_path(resolved_path): (link, resolved_path, fragment)
        for link in readme_links
        for resolved_path, fragment in [service.resolve_target(service.readme_path, link.target)]
        if service.relative_path(resolved_path) in expected_guides
    }

    assert set(matched_guide_links) == set(expected_guides), service.format_missing_readme_links(
        set(expected_guides),
        readme_links,
    )

    findings: list[str] = []

    for relative_path, (link, resolved_path, fragment) in matched_guide_links.items():
        if not resolved_path.exists():
            findings.append(
                service.format_invalid_target(
                    service.readme_path,
                    link,
                    resolved_path,
                    fragment,
                )
            )
            continue

        if fragment and fragment not in service.anchors_for(resolved_path):
            findings.append(
                service.format_invalid_target(
                    service.readme_path,
                    link,
                    resolved_path,
                    fragment,
                )
            )
            continue

        backlinks = service.links_pointing_to(
            resolved_path,
            service.readme_path.resolve(),
            "installation",
        )
        readme_links_in_target = [
            candidate
            for candidate in service.parse_markdown_links(resolved_path.read_text(encoding="utf-8"))
            if service.resolve_target(resolved_path, candidate.target)[0] == service.readme_path.resolve()
        ]
        if not backlinks:
            findings.append(service.format_missing_backlink(resolved_path, readme_links_in_target))

    assert not findings, "\n".join(findings)
