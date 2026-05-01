from __future__ import annotations

import re
from pathlib import Path

from testing.core.models.versioned_artifact_reference import VersionedArtifactReference
from testing.core.models.versioned_install_reference import VersionedInstallReference


class InstallationReferenceService:
    EXPECTED_GITHUB_OWNER = "epam"
    EXPECTED_GITHUB_REPOSITORY = "dm.ai"
    EXPECTED_VERSION_PINNED_ARTIFACT_URL = (
        "https://github.com/epam/dm.ai/releases/download/"
        "v${DMTOOLS_VERSION}/dmtools-v${DMTOOLS_VERSION}-all.jar"
    )
    _VERSION_TOKEN = r"(?:\$\{DMTOOLS_VERSION\}|\d+\.\d+\.\d+)"

    _VERSION_PINNED_ARTIFACT_URL_PATTERN = re.compile(
        rf"https?://[^\s\"'`)<>\]]+/dmtools-v(?P<file_version>{_VERSION_TOKEN})-all\.jar"
    )
    _GITHUB_RELEASE_ARTIFACT_PATTERN = re.compile(
        rf"https://github\.com/(?P<owner>[^/\s]+)/(?P<repo>[^/\s]+)/releases/download/"
        rf"v(?P<path_version>{_VERSION_TOKEN})/"
        rf"dmtools-v(?P<file_version>{_VERSION_TOKEN})-all\.jar"
    )
    _VERSIONED_INSTALLER_PATTERN = re.compile(
        r"https://github\.com/epam/dm\.ai/releases/download/"
        r"v(?P<url_version>\d+\.\d+\.\d+)/install(?:\.(?:sh|bat|ps1))?\b"
    )
    _BASH_FLAG_PATTERN = re.compile(r"\bbash -s -- v(?P<flag_version>\d+\.\d+\.\d+)\b")
    _ENV_FLAG_PATTERN = re.compile(r"\bDMTOOLS_VERSION=v(?P<flag_version>\d+\.\d+\.\d+)\b")

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.installation_readme_path = (
            repository_root / "dmtools-ai-docs/references/installation/README.md"
        )
        self.root_readme_path = repository_root / "README.md"

    @property
    def installation_markdown_paths(self) -> tuple[Path, ...]:
        return tuple(
            sorted(
                (self.repository_root / "dmtools-ai-docs/references/installation").glob("*.md")
            )
        )

    @property
    def installer_reference_paths(self) -> tuple[Path, Path]:
        return self.installation_readme_path, self.root_readme_path

    def artifact_reference_urls(self) -> list[str]:
        return [reference.url for reference in self.artifact_references()]

    def artifact_references(self) -> list[VersionedArtifactReference]:
        references: list[VersionedArtifactReference] = []
        for path in self.installation_markdown_paths:
            for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
                for match in self._VERSION_PINNED_ARTIFACT_URL_PATTERN.finditer(line):
                    url = match.group(0)
                    github_release_match = self._GITHUB_RELEASE_ARTIFACT_PATTERN.fullmatch(url)
                    references.append(
                        VersionedArtifactReference(
                            path=path,
                            line_number=line_number,
                            line=line.strip(),
                            url=url,
                            owner=(
                                github_release_match.group("owner")
                                if github_release_match
                                else None
                            ),
                            repo=(
                                github_release_match.group("repo")
                                if github_release_match
                                else None
                            ),
                            path_version=(
                                github_release_match.group("path_version")
                                if github_release_match
                                else None
                            ),
                            file_version=match.group("file_version"),
                        )
                    )
        return references

    def non_canonical_artifact_references(self) -> list[str]:
        findings: list[str] = []
        for reference in self.artifact_references():
            if reference.owner is None or reference.repo is None or reference.path_version is None:
                findings.append(
                    f"{reference.path.relative_to(self.repository_root)}:{reference.line_number} "
                    f"uses a non-canonical version-pinned DMTools artifact URL: {reference.url}"
                )
                continue

            if (
                reference.owner != self.EXPECTED_GITHUB_OWNER
                or reference.repo != self.EXPECTED_GITHUB_REPOSITORY
            ):
                findings.append(
                    f"{reference.path.relative_to(self.repository_root)}:{reference.line_number} "
                    f"uses GitHub repository {reference.owner}/{reference.repo} instead of "
                    f"{self.EXPECTED_GITHUB_OWNER}/{self.EXPECTED_GITHUB_REPOSITORY}: "
                    f"{reference.url}"
                )

            if reference.path_version != reference.file_version:
                findings.append(
                    f"{reference.path.relative_to(self.repository_root)}:{reference.line_number} "
                    f"uses path version {reference.path_version} but artifact version "
                    f"{reference.file_version}: {reference.url}"
                )
        return findings

    def versioned_installer_examples(self) -> list[VersionedInstallReference]:
        examples: list[VersionedInstallReference] = []
        for path in self.installer_reference_paths:
            for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
                url_match = self._VERSIONED_INSTALLER_PATTERN.search(line)
                if not url_match:
                    continue
                flag_match = self._BASH_FLAG_PATTERN.search(line) or self._ENV_FLAG_PATTERN.search(line)
                examples.append(
                    VersionedInstallReference(
                        path=path,
                        line_number=line_number,
                        line=line.strip(),
                        url_version=url_match.group("url_version"),
                        flag_version=flag_match.group("flag_version") if flag_match else None,
                    )
                )
        return examples

    @staticmethod
    def mismatched_installer_examples(
        examples: list[VersionedInstallReference],
    ) -> list[VersionedInstallReference]:
        return [
            example
            for example in examples
            if example.flag_version is not None and example.url_version != example.flag_version
        ]

    @staticmethod
    def format_installer_example_mismatches(
        examples: list[VersionedInstallReference],
    ) -> str:
        return "\n".join(
            (
                "Found specific-version installer examples where the release URL version",
                "does not match the version passed to the installer:",
                *[
                    (
                        f"- {example.path.name}:{example.line_number} "
                        f"(URL v{example.url_version}, flag v{example.flag_version}): "
                        f"{example.line}"
                    )
                    for example in examples
                ],
            )
        )

    @staticmethod
    def format_non_canonical_artifact_references(findings: list[str]) -> str:
        return "\n".join(
            (
                "Found non-canonical version-pinned DMTools artifact references in the",
                "installation documentation:",
                *[f"- {finding}" for finding in findings],
            )
        )
