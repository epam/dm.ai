from __future__ import annotations

from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _read(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def test_dmc_1018_release_guidance_matches_skill_assets_java_baseline_and_parent_docs() -> None:
    root_readme = _read("README.md")
    skill_readme = _read("dmtools-ai-docs/README.md")
    skill_manifest = _read("dmtools-ai-docs/SKILL.md")
    install_readme = _read("dmtools-ai-docs/references/installation/README.md")
    install_script = _read("install.sh")
    wrapper_script = _read("dmtools.sh")
    release_workflow = _read(".github/workflows/release.yml")

    guidance_surfaces = {
        "README.md": root_readme,
        "dmtools-ai-docs/README.md": skill_readme,
        "dmtools-ai-docs/references/installation/README.md": install_readme,
        ".github/workflows/release.yml": release_workflow,
    }

    missing_skill_installer_guidance = [
        path
        for path, text in guidance_surfaces.items()
        if "skill-install.sh" not in text
    ]
    assert not missing_skill_installer_guidance, (
        "Agent Skill guidance must point users to the published skill-install.sh asset; "
        f"missing from {', '.join(missing_skill_installer_guidance)}"
    )

    misleading_local_skill_installer_paths = [
        path
        for path, text in {
            "dmtools-ai-docs/README.md": skill_readme,
            "dmtools-ai-docs/references/installation/README.md": install_readme,
        }.items()
        if "bash install.sh" in text
    ]
    assert not misleading_local_skill_installer_paths, (
        "Agent Skill docs must not tell first-time users to run a bare install.sh script when "
        "the published installer asset is skill-install.sh; found in "
        + ", ".join(misleading_local_skill_installer_paths)
    )

    stale_java_23_surfaces = [
        path
        for path, text in {
            ".github/workflows/release.yml": release_workflow,
            "install.sh": install_script,
            "dmtools.sh": wrapper_script,
        }.items()
        if "Java 23" in text
    ]
    assert not stale_java_23_surfaces, (
        "User-facing install/runtime guidance must align to the Java 17 baseline; stale Java 23 "
        "references remain in " + ", ".join(stale_java_23_surfaces)
    )

    assert "Java 17+" in release_workflow, "release.yml must publish the Java 17+ baseline."
    assert "Java 17+" in root_readme, "README.md must expose the Java 17+ baseline."

    inheritance_link = "references/configuration/json-config-rules.md#config-inheritance-via-parent"
    discoverable_inheritance_surfaces = [
        path
        for path, text in {
            "README.md": root_readme,
            "dmtools-ai-docs/README.md": skill_readme,
            "dmtools-ai-docs/SKILL.md": skill_manifest,
            "dmtools-ai-docs/references/installation/README.md": install_readme,
        }.items()
        if inheritance_link in text or "Config inheritance via `parent`" in text
    ]
    assert discoverable_inheritance_surfaces, (
        "Agent Skill docs must expose the existing parent-based config inheritance path in a "
        "discoverable location."
    )
