from __future__ import annotations

from dataclasses import dataclass
import re

SUMMARY_TOKEN_PATTERN = re.compile(r"[A-Za-z0-9]+")
SUMMARY_STOP_WORDS = frozenset(
    {
        "a",
        "an",
        "and",
        "as",
        "for",
        "from",
        "in",
        "into",
        "it",
        "of",
        "on",
        "or",
        "the",
        "to",
        "with",
    }
)
SHORT_ALLOWED_KEYWORDS = frozenset({"ai", "js", "kb", "qa"})


@dataclass(frozen=True)
class JobReference:
    job: str
    summary: str
    accepted_names: tuple[str, ...]
    example: str

    @classmethod
    def from_markdown_cells(
        cls,
        job: str,
        summary: str,
        accepted_names: str,
        example: str,
    ) -> "JobReference":
        return cls(
            job=_clean_markdown_cell(job),
            summary=_clean_markdown_cell(summary),
            accepted_names=tuple(
                _clean_markdown_cell(name)
                for name in accepted_names.split("/")
                if _clean_markdown_cell(name)
            ),
            example=_clean_markdown_cell(example),
        )

    @property
    def all_names(self) -> tuple[str, ...]:
        return (self.job, *self.accepted_names)

    @property
    def summary_keywords(self) -> frozenset[str]:
        keywords: set[str] = set()
        for token in SUMMARY_TOKEN_PATTERN.findall(self.summary.lower()):
            if token in SUMMARY_STOP_WORDS:
                continue
            if len(token) >= 4 or token in SHORT_ALLOWED_KEYWORDS:
                keywords.add(token)
        return frozenset(keywords)


def _clean_markdown_cell(value: str) -> str:
    return value.strip().strip("`").strip()
