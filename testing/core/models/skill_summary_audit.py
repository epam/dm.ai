from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SkillSummaryAudit:
    path: Path
    name: str
    description: str
    character_count: int
    sentence_count: int
    filler_words: tuple[str, ...]
    has_use_sentence: bool

    @property
    def is_valid(self) -> bool:
        return (
            self.character_count <= 160
            and self.sentence_count <= 2
            and not self.filler_words
            and self.has_use_sentence
        )

    def failure_reasons(self) -> list[str]:
        reasons: list[str] = []
        if self.character_count > 160:
            reasons.append(f"expected <= 160 characters, got {self.character_count}")
        if self.sentence_count > 2:
            reasons.append(f"expected <= 2 sentences, got {self.sentence_count}")
        if self.filler_words:
            reasons.append(
                "contains filler wording: " + ", ".join(sorted(self.filler_words))
            )
        if not self.has_use_sentence:
            reasons.append("expected a concise 'Use ...' guidance sentence")
        return reasons
