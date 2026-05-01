#!/bin/bash

# DMtools Agent Skill Release Script
# Usage: ./release-skill.sh [version] [--prerelease]

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

# Get version from argument or prompt
VERSION=$1
PRERELEASE=$2

if [ -z "$VERSION" ]; then
    echo "Enter release version (e.g., 1.0.0):"
    read VERSION
fi

if [ -z "$VERSION" ]; then
    echo -e "${RED}Error: Version is required${NC}"
    exit 1
fi

echo -e "${GREEN}Preparing to release DMtools Agent Skill v$VERSION${NC}"
echo ""
echo -e "${CYAN}This skill works with:${NC}"
echo "  • Cursor IDE"
echo "  • Claude Desktop"
echo "  • Codex"
echo "  • Any Agent Skills compatible system"
echo ""

# Update version in SKILL.md
if [ -f "dmtools-ai-docs/SKILL.md" ]; then
    echo "Updating SKILL.md version..."
    sed -i.bak "s/version: .*/version: $VERSION/" dmtools-ai-docs/SKILL.md
    rm dmtools-ai-docs/SKILL.md.bak
fi

# Create or update changelog
CHANGELOG_FILE="dmtools-ai-docs/CHANGELOG.md"
if [ ! -f "$CHANGELOG_FILE" ]; then
    cat > "$CHANGELOG_FILE" << 'EOF'
# Changelog

All notable changes to the DMtools Agent Skill will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

EOF
fi

# Add new version to changelog
DATE=$(date +%Y-%m-%d)
TEMP_CHANGELOG=$(mktemp)
cat > "$TEMP_CHANGELOG" << EOF
## [$VERSION] - $DATE

### Added
- [Add new features here]

### Changed
- [Add changes here]

### Fixed
- [Add fixes here]

EOF
cat "$CHANGELOG_FILE" >> "$TEMP_CHANGELOG"
mv "$TEMP_CHANGELOG" "$CHANGELOG_FILE"

echo -e "${YELLOW}Please edit $CHANGELOG_FILE to add release notes${NC}"
echo "Press Enter when done..."
read

# Commit changes
git add dmtools-ai-docs/
git commit -m "Release Agent Skill v$VERSION

- Universal Agent Skills standard support
- Works with Cursor, Claude, Codex
- Single package for all platforms
- Comprehensive DMtools documentation"

# Create and push tag
TAG="skill-v$VERSION"
git tag -a "$TAG" -m "DMtools Agent Skill v$VERSION"

echo ""
echo -e "${GREEN}Ready to push release!${NC}"
echo ""
echo "This will:"
echo "  1. Push commits to main branch"
echo "  2. Push tag $TAG"
echo "  3. Trigger GitHub workflow to:"
echo "     • Create single skill package"
echo "     • Generate release with install script"
echo "     • Publish to NPM as @dmtools/agent-skill"
echo ""
echo -e "${CYAN}Continue? (y/n)${NC}"
read CONFIRM

if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
    # Push to remote
    git push origin main
    git push origin "$TAG"

    echo ""
    echo -e "${GREEN}✅ Release triggered!${NC}"
    echo ""
    echo "GitHub Actions will now create the release."
    echo ""
    echo "Monitor progress at:"
    echo "https://github.com/epam/dm.ai/actions"
    echo ""
    echo "Once released, users can install with:"
    echo -e "${CYAN}curl -fsSL https://github.com/epam/dm.ai/releases/download/$TAG/install.sh | bash${NC}"
    echo ""
    echo "Release will be available at:"
    echo "https://github.com/epam/dm.ai/releases/tag/$TAG"
else
    echo "Release cancelled"
    echo ""
    echo "To manually release later:"
    echo "  git push origin main"
    echo "  git push origin $TAG"
fi
