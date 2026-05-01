from __future__ import annotations

import re
from pathlib import Path

from testing.core.models.versioned_install_reference import VersionedInstallReference


class InstallationReferenceService:
    EXPECTED_VERSION_PINNED_ARTIFACT_URL = (
        "https://github.com/epam/dm.ai/releases/download/"
        "v${DMTOOLS_VERSION}/dmtools-v${DMTOOLS_VERSION}-all.jar"
    )

    _RELEASE_ARTIFACT_PATTERN = re.compile(
        r"https://github\.com/epam/dm\.ai/releases/download/"
        r"v(?P<path_version>\$\{DMTOOLS_VERSION\}|\d+\.\d+\.\d+)/"
        r"dmtools-v(?P<file_version>\$\{DMTOOLS_VERSION\}|\d+\.\d+\.\d+)-all\.jar"
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
        urls: list[str] = []
        for path in self.installation_markdown_paths:
            content = path.read_text(encoding="utf-8")
            urls.extend(match.group(0) for match in self._RELEASE_ARTIFACT_PATTERN.finditer(content))
        return urls

    def artifact_reference_version_mismatches(self) -> list[str]:
        mismatches: list[str] = []
        for path in self.installation_markdown_paths:
            content = path.read_text(encoding="utf-8")
            for match in self._RELEASE_ARTIFACT_PATTERN.finditer(content):
                path_version = match.group("path_version")
                file_version = match.group("file_version")
                if path_version != file_version:
                    mismatches.append(
                        f"{path.relative_to(self.repository_root)} uses path version "
                        f"{path_version} but artifact version {file_version}: {match.group(0)}"
                    )
        return mismatches

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
