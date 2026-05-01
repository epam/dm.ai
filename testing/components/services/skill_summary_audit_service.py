import re
from pathlib import Path

from testing.core.models.skill_summary_audit import SkillSummaryAudit


class SkillSummaryAuditService:
    ACTION_VERBS = {
        "aggregates",
        "audits",
        "builds",
        "calculates",
        "collects",
        "creates",
        "executes",
        "fetches",
        "generates",
        "loads",
        "orchestrates",
        "parses",
        "posts",
        "processes",
        "produces",
        "renders",
        "runs",
        "syncs",
        "transforms",
        "updates",
        "validates",
        "writes",
    }
    FILLER_WORDS = {
        "advanced",
        "comprehensive",
        "cutting-edge",
        "enhanced",
        "innovative",
        "intuitive",
        "powerful",
        "revolutionary",
        "robust",
        "seamless",
        "user-friendly",
    }
    PASSIVE_VOICE_PATTERN = re.compile(
        r"\b(?:is|are|was|were|be|been|being)\s+\w+(?:ed|en)\b",
        re.IGNORECASE,
    )

    def __init__(self, repository_root: Path, summary_table_path: str) -> None:
        self.repository_root = repository_root
        self.summary_table_path = summary_table_path

    def audit_all(self) -> list[SkillSummaryAudit]:
        audits: list[SkillSummaryAudit] = []
        for name, description in self._load_reference_summaries():
            normalized = re.sub(r"\s+", " ", description).strip()
            sentences = self._split_sentences(normalized)
            filler_words = self._find_filler_words(normalized)
            leading_word = self._leading_word(normalized)
            audits.append(
                SkillSummaryAudit(
                    path=self.repository_root / self.summary_table_path,
                    name=name,
                    description=normalized,
                    character_count=len(normalized),
                    sentence_count=len(sentences),
                    filler_words=filler_words,
                    leading_word=leading_word,
                    starts_with_action_verb=leading_word in self.ACTION_VERBS,
                    has_passive_voice=bool(
                        self.PASSIVE_VOICE_PATTERN.search(normalized)
                    ),
                )
            )
        return audits

    def _load_reference_summaries(self) -> list[tuple[str, str]]:
        table_path = self.repository_root / self.summary_table_path
        rows = self._parse_reference_table(table_path)
        return [
            (
                self._normalize_job_name(row["Job"]),
                row["Summary"],
            )
            for row in rows
        ]

    def _parse_reference_table(self, path: Path) -> list[dict[str, str]]:
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines[:-1]):
            if not line.strip().startswith("|"):
                continue
            header = self._parse_table_row(line)
            if "Job" not in header or "Summary" not in header:
                continue
            if not self._is_separator_row(lines[index + 1]):
                continue
            rows: list[dict[str, str]] = []
            for row_line in lines[index + 2 :]:
                if not row_line.strip().startswith("|"):
                    break
                values = self._parse_table_row(row_line)
                if len(values) != len(header):
                    continue
                rows.append(dict(zip(header, values)))
            if rows:
                return rows
        raise ValueError(f"Could not find the job summary table in {path}")

    def _parse_table_row(self, line: str) -> list[str]:
        return [cell.strip() for cell in line.strip().split("|")[1:-1]]

    def _is_separator_row(self, line: str) -> bool:
        cells = self._parse_table_row(line)
        return all(cells) and all(
            set(cell.replace(":", "")) == {"-"} for cell in cells
        )

    def _normalize_job_name(self, raw_name: str) -> str:
        return raw_name.replace("`", "").strip()

    def _split_sentences(self, description: str) -> list[str]:
        return [
            sentence.strip()
            for sentence in re.split(r"(?<=[.!?])\s+", description)
            if sentence.strip()
        ]

    def _find_filler_words(self, description: str) -> tuple[str, ...]:
        return tuple(
            sorted(
                {
                    word
                    for word in self.FILLER_WORDS
                    if re.search(rf"\b{re.escape(word)}\b", description, re.IGNORECASE)
                }
            )
        )

    def _leading_word(self, description: str) -> str:
        match = re.match(r"^[`*_]*([A-Za-z][A-Za-z-]*)", description)
        return match.group(1).lower() if match else ""
