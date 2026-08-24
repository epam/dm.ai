#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate the public CLI manifest and landing-page release data.

This script is invoked by the release site workflow after a GitHub release is
published. It reads the release notes and the published checksums file, then
updates:

  - landing/public/cli-manifest.json
  - landing/src/data/releases.json

The landing data file is the source of truth for the /releases pages, while the
public manifest is consumed by the install scripts to avoid GitHub API rate
limits.
"""

import argparse
import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO = os.environ.get("GITHUB_REPOSITORY", "epam/dm.ai")
DEFAULT_SITE_URL = "https://dmtools.lab.epam.com"
MAX_RELEASES = 50


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate release site data")
    parser.add_argument("--version", required=True, help="Release tag, e.g. v1.7.200")
    parser.add_argument("--release-notes", default="release_notes.md", help="Path to release notes markdown")
    parser.add_argument("--checksums", default="dmtools-checksums.sha256", help="Path to SHA-256 checksums file")
    parser.add_argument("--site-url", default=DEFAULT_SITE_URL, help="Base URL of the landing site")
    parser.add_argument(
        "--manifest-path",
        default="landing/public/cli-manifest.json",
        help="Output path for cli-manifest.json",
    )
    parser.add_argument(
        "--releases-path",
        default="landing/src/data/releases.json",
        help="Output path for releases.json",
    )
    return parser.parse_args()


def read_file(path: str) -> str:
    if not path or not os.path.isfile(path):
        return ""
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def parse_checksums(checksums_text: str) -> dict[str, str]:
    """Parse output of sha256sum into {filename: hash}."""
    result: dict[str, str] = {}
    for line in checksums_text.strip().splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) != 2:
            continue
        hash_value, filename = parts
        # sha256sum lines are "<hash>  <filename>" (two spaces). Strip leading
        # asterisk used for binary mode markers.
        filename = filename.lstrip("*").strip()
        if len(hash_value) == 64 and re.fullmatch(r"[0-9a-fA-F]+", hash_value):
            result[filename] = hash_value.lower()
    return result


def asset_filenames(version: str) -> dict[str, str]:
    return {
        "jar": f"dmtools-{version}-all.jar",
        "install_sh": "install.sh",
        "install_bat": "install.bat",
        "install_ps1": "install.ps1",
        "wrapper": "dmtools.sh",
        "checksums": "dmtools-checksums.sha256",
    }


def release_notes_url(site_url: str, version: str) -> str:
    base = site_url.rstrip("/")
    return f"{base}/releases/{version}/"


def semver_key(version: str) -> tuple[int, int, int]:
    """Sort key for stable vX.Y.Z tags. Pre-release tags sort lowest."""
    match = re.match(r"^v?(\d+)\.(\d+)\.(\d+)(?:[+-].*)?$", version)
    if not match:
        return (0, 0, 0)
    return (int(match.group(1)), int(match.group(2)), int(match.group(3)))


def is_stable_cli_release(version: str) -> bool:
    return re.fullmatch(r"^v\d+\.\d+\.\d+$", version) is not None


def build_release_entry(
    version: str,
    release_date: str,
    release_notes: str,
    checksums: dict[str, str],
    site_url: str,
) -> dict[str, Any]:
    assets = asset_filenames(version)
    download_base = f"https://github.com/{REPO}/releases/download/{version}"
    asset_checksums = {
        filename: checksums.get(filename, "")
        for filename in assets.values()
        if filename in checksums
    }
    return {
        "version": version,
        "tag": version,
        "date": release_date,
        "download_base": download_base,
        "assets": assets,
        "checksums_sha256": asset_checksums,
        "releaseNotes": release_notes,
        "releaseNotesUrl": release_notes_url(site_url, version),
    }


def build_manifest(
    latest_entry: dict[str, Any],
    all_releases: list[dict[str, Any]],
    site_url: str,
) -> dict[str, Any]:
    version = latest_entry["version"]
    excerpt = latest_entry["releaseNotes"]
    if len(excerpt) > 2000:
        excerpt = excerpt[:1997].rstrip() + "..."

    return {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "repository": REPO,
        "site_url": site_url.rstrip("/"),
        "latest": {
            "version": version,
            "tag": version,
            "release_date": latest_entry["date"],
            "download_base": latest_entry["download_base"],
            "assets": latest_entry["assets"],
            "checksums_sha256": latest_entry["checksums_sha256"],
            "release_notes_url": latest_entry["releaseNotesUrl"],
            "changelog_excerpt": excerpt,
        },
        "releases": [
            {
                "version": r["version"],
                "date": r["date"],
                "release_notes_url": r["releaseNotesUrl"],
            }
            for r in all_releases
        ],
    }


def load_existing_releases(path: str) -> list[dict[str, Any]]:
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, list):
            return data
    except (FileNotFoundError, json.JSONDecodeError):
        pass
    return []


def write_json(path: str, data: Any) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main() -> int:
    args = parse_args()

    version = args.version
    if not version.startswith("v"):
        version = f"v{version}"

    if not is_stable_cli_release(version):
        print(f"Skipping non-CLI release tag: {version}", file=os.sys.stderr)
        return 0

    release_date = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    release_notes = read_file(args.release_notes)
    checksums_text = read_file(args.checksums)
    checksums = parse_checksums(checksums_text)

    new_entry = build_release_entry(version, release_date, release_notes, checksums, args.site_url)

    existing = load_existing_releases(args.releases_path)
    # Replace existing entry for the same version or prepend.
    merged = [r for r in existing if r.get("version") != version]
    merged.insert(0, new_entry)
    # Keep only the newest stable releases.
    merged.sort(key=lambda r: semver_key(r.get("version", "")), reverse=True)
    merged = merged[:MAX_RELEASES]

    manifest = build_manifest(new_entry, merged, args.site_url)

    write_json(args.manifest_path, manifest)
    write_json(args.releases_path, merged)

    print(f"Generated manifest for {version}")
    print(f"  - {args.manifest_path}")
    print(f"  - {args.releases_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
