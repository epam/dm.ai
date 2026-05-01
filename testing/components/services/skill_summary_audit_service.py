import re
from pathlib import Path

from testing.core.models.skill_summary_audit import SkillSummaryAudit
from testing.core.utils.skill_frontmatter import extract_frontmatter_value


class SkillSummaryAuditService:
    FILLER_WORDS = {
        "advanced",
        "comprehensive",
        "cutting-edge",
        "enhanced",
        "innovative",
        "powerful",
        "revolutionary",
        "seamless",
    }

    def __init__(self, repository_root: Path, skill_roots: list[str]) -> None:
        self.repository_root = repository_root
        self.skill_roots = skill_roots

    def audit_all(self) -> list[SkillSummaryAudit]:
        audits: list[SkillSummaryAudit] = []
        for skill_file in self._discover_skill_files():
            description = extract_frontmatter_value(skill_file, "description")
            if not description:
                continue
            name = extract_frontmatter_value(skill_file, "name") or skill_file.parent.name
            normalized = re.sub(r"\s+", " ", description).strip()
            sentences = [
                sentence.strip()
                for sentence in re.split(r"(?<=[.!?])\s+", normalized)
                if sentence.strip()
            ]
            filler_words = tuple(
                sorted(
                    {
                        word
                        for word in self.FILLER_WORDS
                        if re.search(rf"\b{re.escape(word)}\b", normalized, re.IGNORECASE)
                    }
                )
            )
            has_use_sentence = len(sentences) <= 1 or (
                len(sentences) >= 2 and sentences[1].startswith("Use ")
            )
            audits.append(
                SkillSummaryAudit(
                    path=skill_file,
                    name=name,
                    description=normalized,
                    character_count=len(normalized),
                    sentence_count=len(sentences),
                    filler_words=filler_words,
                    has_use_sentence=has_use_sentence,
                )
            )
        return sorted(audits, key=lambda audit: str(audit.path))

    def _discover_skill_files(self) -> list[Path]:
        skill_files: list[Path] = []
        for root in self.skill_roots:
            absolute_root = self.repository_root / root
            if not absolute_root.exists():
                continue
            skill_files.extend(absolute_root.rglob("SKILL.md"))
        return sorted(set(skill_files))
