#!/bin/bash

# Update DMtools AI Skill Documentation
# This script regenerates all auto-generated documentation

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🔄 Updating DMtools AI Skill Documentation"
echo ""

# Check if dmtools is installed
if ! command -v dmtools &> /dev/null && [ ! -f ~/.dmtools/dmtools.jar ]; then
    echo "❌ dmtools not found. Building and installing..."
    cd "$PROJECT_ROOT"
    ./gradlew :dmtools-core:shadowJar
    ./buildInstallLocal.sh
    echo "✅ DMtools installed"
    echo ""
fi

# Generate MCP tools documentation
echo "📝 Generating MCP tools documentation..."
cd "$PROJECT_ROOT"
./scripts/generate-mcp-tables.sh

echo "🔗 Syncing agent references in SKILL.md..."
python3 - "$PROJECT_ROOT" <<'PYTHON_SYNC'
from pathlib import Path
import re
import sys


def first_heading(markdown_lines: list[str]) -> tuple[int, str]:
    for index, line in enumerate(markdown_lines):
        if line.startswith("# "):
            return index, line[2:].strip()
    raise ValueError("No H1 heading found")


def is_summary_candidate(text: str) -> bool:
    if not text:
        return False

    disallowed_prefixes = (
        "#",
        ">",
        "- ",
        "* ",
        "|",
        "![",
        "<",
        "**→",
        "**->",
        "*See also",
        "**See also",
        "```",
    )
    return not text.startswith(disallowed_prefixes)


def paragraph_after(lines: list[str], start_index: int) -> str | None:
    in_code_block = False
    paragraph: list[str] = []

    for line in lines[start_index + 1 :]:
        stripped = line.strip()

        if stripped.startswith("```"):
            in_code_block = not in_code_block
            if paragraph:
                break
            continue
        if in_code_block:
            continue

        if not stripped:
            if paragraph:
                candidate = " ".join(paragraph).strip()
                return candidate if is_summary_candidate(candidate) else None
            continue

        if stripped.startswith("#"):
            if paragraph:
                candidate = " ".join(paragraph).strip()
                return candidate if is_summary_candidate(candidate) else None
            continue

        paragraph.append(stripped)

    if paragraph:
        candidate = " ".join(paragraph).strip()
        return candidate if is_summary_candidate(candidate) else None

    return None


def extract_title_and_summary(markdown_path: Path) -> tuple[str, str]:
    lines = markdown_path.read_text(encoding="utf-8").splitlines()
    heading_index, title = first_heading(lines)

    summary = paragraph_after(lines, heading_index)
    if summary:
        return title, summary

    overview_pattern = re.compile(r"^##+\s+(?:[^\w]*\s*)?(overview|summary)\b", re.IGNORECASE)
    for index in range(heading_index + 1, len(lines)):
        if overview_pattern.match(lines[index].strip()):
            summary = paragraph_after(lines, index)
            if summary:
                return title, summary

    for index in range(heading_index + 1, len(lines)):
        summary = paragraph_after(lines, index)
        if summary:
            return title, summary

    raise ValueError(f"No summary paragraph found in {markdown_path}")


project_root = Path(sys.argv[1])
skill_path = project_root / "dmtools-ai-docs" / "SKILL.md"
skill_lines = skill_path.read_text(encoding="utf-8").splitlines()

row_pattern = re.compile(
    r"^\|(?P<category>[^|]*)\|\s*\[(?P<title>[^\]]+)\]\((?P<path>references/agents/[^)#]+\.md)\)\s*\|\s*(?P<description>.*?)\s*\|$"
)

updated_lines: list[str] = []
for line in skill_lines:
    match = row_pattern.match(line)
    if not match:
        updated_lines.append(line)
        continue

    source_path = project_root / "dmtools-ai-docs" / match.group("path")
    if not source_path.exists():
        updated_lines.append(line)
        continue

    title, summary = extract_title_and_summary(source_path)
    category = match.group("category").strip()
    updated_lines.append(f"| {category} | [{title}]({match.group('path')}) | {summary} |")

skill_path.write_text("\n".join(updated_lines) + "\n", encoding="utf-8")
PYTHON_SYNC

echo ""
echo "📊 Documentation Statistics:"
echo ""

# Count tools by integration
TOOLS_JSON="$PROJECT_ROOT/dmtools-ai-docs/references/mcp-tools/tools-raw.json"
if [ -f "$TOOLS_JSON" ]; then
    TOTAL_TOOLS=$(jq '.tools | length' "$TOOLS_JSON")
    echo "   Total MCP Tools: $TOTAL_TOOLS"
    echo ""
    echo "   By Integration:"

    # Extract integration counts
    jq -r '.tools | group_by(.name | split("_")[0]) | map({integration: (.[0].name | split("_")[0]), count: length}) | .[] | "      - \(.integration): \(.count) tools"' "$TOOLS_JSON"
fi

echo ""
echo "✅ Documentation update complete!"
echo ""
echo "📁 Generated files:"
echo "   - references/mcp-tools/README.md (Main index)"
echo "   - references/mcp-tools/jira-tools.md"
echo "   - references/mcp-tools/teams-tools.md"
echo "   - references/mcp-tools/figma-tools.md"
echo "   - references/mcp-tools/file-tools.md"
echo "   - references/mcp-tools/cli-tools.md"
echo ""
echo "💡 Next steps:"
echo "   1. Review generated documentation"
echo "   2. Review synced SKILL.md reference entries"
echo "   3. Commit changes: git add dmtools-ai-docs/"
echo "   4. Create release: ./release-skill.sh X.Y.Z"
