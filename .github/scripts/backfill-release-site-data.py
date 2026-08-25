#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Backfill landing-page release data from all published GitHub releases.

Fetches every stable CLI release (vX.Y.Z, not a prerelease) via the GitHub API
and regenerates:

  - landing/src/data/releases.json  (full list with notes + checksums)
  - landing/public/cli-manifest.json (latest release summary for installers)

Asset SHA-256 checksums are taken from the `digest` field exposed by the
releases API, so no large asset downloads are needed.

Requires the `gh` CLI to be authenticated for the repository.
"""

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import importlib.util

_spec = importlib.util.spec_from_file_location(
    "generate_release_site_data", SCRIPT_DIR / "generate-release-site-data.py"
)
gen = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gen)

REPO = os.environ.get("GITHUB_REPOSITORY", "epam/dm.ai")
MAX_RELEASES = gen.MAX_RELEASES


def gh(*args: str) -> Any:
    result = subprocess.run(
        ["gh", *args], capture_output=True, text=True, check=True
    )
    return json.loads(result.stdout)


def fetch_stable_releases() -> list[dict[str, Any]]:
    releases = gh(
        "release", "list", "--repo", REPO, "--limit", "200",
        "--json", "tagName,isPrerelease,publishedAt",
    )
    stable = [
        r for r in releases
        if not r.get("isPrerelease") and gen.is_stable_cli_release(r.get("tagName", ""))
    ]
    stable.sort(key=lambda r: gen.semver_key(r["tagName"]), reverse=True)
    return stable[:MAX_RELEASES]


def fetch_release_details(tag: str) -> dict[str, Any]:
    return gh(
        "release", "view", tag, "--repo", REPO,
        "--json", "tagName,publishedAt,body,assets",
    )


def checksums_from_assets(assets: list[dict[str, Any]]) -> dict[str, str]:
    checksums: dict[str, str] = {}
    for asset in assets or []:
        name = asset.get("name") or ""
        digest = asset.get("digest") or ""
        if digest.startswith("sha256:"):
            checksums[name] = digest[len("sha256:"):].lower()
    return checksums


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill release site data")
    parser.add_argument("--site-url", default=gen.DEFAULT_SITE_URL)
    parser.add_argument("--manifest-path", default="landing/public/cli-manifest.json")
    parser.add_argument("--releases-path", default="landing/src/data/releases.json")
    args = parser.parse_args()

    tags = fetch_stable_releases()
    print(f"Found {len(tags)} stable CLI releases")
    if not tags:
        print("Nothing to backfill")
        return 0

    entries: list[dict[str, Any]] = []
    for item in tags:
        tag = item["tagName"]
        details = fetch_release_details(tag)
        date = (details.get("publishedAt") or "")[:10]
        notes = details.get("body") or ""
        checksums = checksums_from_assets(details.get("assets") or [])
        entry = gen.build_release_entry(tag, date, notes, checksums, args.site_url)
        entries.append(entry)
        print(f"  {tag}: {date}, {len(checksums)} checksums, {len(notes)} chars of notes")

    gen.write_json(args.releases_path, entries)
    manifest = gen.build_manifest(entries[0], entries, args.site_url)
    gen.write_json(args.manifest_path, manifest)

    print(f"Backfilled {len(entries)} releases (latest: {entries[0]['version']})")
    print(f"  - {args.manifest_path}")
    print(f"  - {args.releases_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
