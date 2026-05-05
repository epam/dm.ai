from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class InstallerCommandReference:
    path: Path
    line_number: int
    command: str
    installer_url: str


class AITeammateBootstrapContractService:
    EXPECTED_OWNER = "epam"
    EXPECTED_REPOSITORY = "dm.ai"
    EXPECTED_PUBLIC_INSTALL_COMMAND = (
        "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash"
    )
    RELEASE_ASSET_INSTALLER_PATTERN = re.compile(
        r"https://github\.com/(?P<owner>[^/\s]+)/(?P<repo>[^/\s]+)/releases/"
        r"(?:(?P<latest>latest)/download|download/v(?P<version>\d+\.\d+\.\d+))/install\.sh\b"
    )
    VERSION_FLAG_PATTERN = re.compile(r"\bbash\s+-s\s+--\s+v(?P<version>\d+\.\d+\.\d+)\b")
    LEGACY_INSTALLER_MARKERS = (
        "raw.githubusercontent.com",
        "IstiN/dmtools",
        "IstiN/",
    )

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.workflow_path = repository_root / ".github/workflows/ai-teammate.yml"
        self.installation_readme_path = (
            repository_root / "dmtools-ai-docs/references/installation/README.md"
        )

    def workflow_install_reference(self) -> InstallerCommandReference:
        return self._find_first_install_reference(self.workflow_path)

    def public_installation_reference(self) -> InstallerCommandReference:
        return self._find_expected_command_reference(
            self.installation_readme_path,
            self.EXPECTED_PUBLIC_INSTALL_COMMAND,
        )

    def is_release_asset_installer_url(self, installer_url: str) -> bool:
        return self.RELEASE_ASSET_INSTALLER_PATTERN.fullmatch(installer_url) is not None

    def uses_expected_repository(self, installer_url: str) -> bool:
        match = self.RELEASE_ASSET_INSTALLER_PATTERN.fullmatch(installer_url)
        return bool(
            match
            and match.group("owner") == self.EXPECTED_OWNER
            and match.group("repo") == self.EXPECTED_REPOSITORY
        )

    def version_flag_matches_url(self, command: str, installer_url: str) -> bool:
        url_match = self.RELEASE_ASSET_INSTALLER_PATTERN.fullmatch(installer_url)
        if not url_match:
            return False

        flag_match = self.VERSION_FLAG_PATTERN.search(command)
        url_version = url_match.group("version")
        if url_version is None:
            return flag_match is None
        return flag_match is not None and flag_match.group("version") == url_version

    def contains_legacy_source_reference(self, text: str) -> bool:
        return any(marker in text for marker in self.LEGACY_INSTALLER_MARKERS)

    def format_reference(self, reference: InstallerCommandReference) -> str:
        return (
            f"{reference.path.relative_to(self.repository_root)}:{reference.line_number} "
            f"{reference.command}"
        )

    @staticmethod
    def installer_url(reference: InstallerCommandReference) -> str:
        return reference.installer_url

    def _find_first_install_reference(self, path: Path) -> InstallerCommandReference:
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines, start=1):
            if "curl -fsSL" not in line or "install.sh" not in line or "| bash" not in line:
                continue
            installer_url = self._extract_installer_url(line)
            if installer_url:
                return InstallerCommandReference(
                    path=path,
                    line_number=index,
                    command=line.strip(),
                    installer_url=installer_url,
                )
        raise AssertionError(f"No DMTools install command found in {path.relative_to(self.repository_root)}.")

    def _find_expected_command_reference(self, path: Path, expected_command: str) -> InstallerCommandReference:
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines, start=1):
            if expected_command not in line:
                continue
            installer_url = self._extract_installer_url(line)
            if installer_url:
                return InstallerCommandReference(
                    path=path,
                    line_number=index,
                    command=line.strip(),
                    installer_url=installer_url,
                )
        raise AssertionError(
            f"Expected public install command not found in {path.relative_to(self.repository_root)}."
        )

    def _extract_installer_url(self, line: str) -> str | None:
        match = self.RELEASE_ASSET_INSTALLER_PATTERN.search(line)
        if match:
            return match.group(0)

        generic_match = re.search(r"https?://[^\s\"'`|]+/install\.sh\b", line)
        if generic_match:
            return generic_match.group(0)
        return None
