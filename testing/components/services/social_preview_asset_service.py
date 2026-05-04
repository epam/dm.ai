from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree


STYLE_DECLARATION_PATTERN = re.compile(r"\s*([^:]+):\s*([^;]+)\s*")
NON_ALPHANUMERIC_PATTERN = re.compile(r"[^a-z0-9]+")
SVG_NAMESPACE = "{http://www.w3.org/2000/svg}"


@dataclass(frozen=True)
class ValidationFailure:
    step: int
    summary: str
    expected: str
    actual: str

    def format(self) -> str:
        return (
            f"Step {self.step}: {self.summary}\n"
            f"Expected: {self.expected}\n"
            f"Actual: {self.actual}"
        )


@dataclass(frozen=True)
class TextContrastObservation:
    text: str
    fill_hex: str
    contrast_ratio: float

    def format(self) -> str:
        return f"{self.text!r} ({self.fill_hex}) -> {self.contrast_ratio:.2f}:1"


@dataclass(frozen=True)
class SocialPreviewObservation:
    asset_path: Path
    hero_fill_hex: str | None
    wordmark_texts: tuple[str, ...]
    decorative_background_count: int
    text_contrast_observations: tuple[TextContrastObservation, ...]


class SocialPreviewAssetService:
    PLAYBOOK_RELATIVE_PATH = Path(
        "dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md"
    )
    PRIMARY_FILENAME_HINTS = (
        "social-preview",
        "social_preview",
        "socialpreview",
    )
    IGNORED_DIRECTORIES = {
        ".git",
        ".gradle",
        ".idea",
        ".repo-sandboxes",
        ".pytest_cache",
        "__pycache__",
        "build",
        "dist",
        "node_modules",
        "outputs",
        "testing",
    }
    SHAPE_TAGS = {"rect", "path", "line", "polyline", "polygon", "circle", "ellipse"}

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.playbook_path = repository_root / self.PLAYBOOK_RELATIVE_PATH

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []

        asset_path = self.locate_social_preview_asset()
        if asset_path is None:
            failures.extend(
                [
                    ValidationFailure(
                        step=1,
                        summary="The repository does not contain a versioned social-preview SVG source asset.",
                        expected=(
                            "A committed, versioned SVG asset whose filename clearly identifies "
                            "it as the repository social preview source."
                        ),
                        actual=(
                            "No versioned SVG file matching the social-preview naming contract "
                            "(for example, social-preview.v1.svg) was found."
                        ),
                    ),
                    ValidationFailure(
                        step=2,
                        summary="The social preview composition cannot be reviewed because the SVG source is missing.",
                        expected=(
                            "A versioned SVG that visibly contains a dark hero card, the DMTools "
                            "wordmark, and a subtle industrial background."
                        ),
                        actual="Step 1 did not locate any committed social-preview SVG source asset.",
                    ),
                    ValidationFailure(
                        step=3,
                        summary="Text contrast cannot be inspected because the SVG source is missing.",
                        expected=(
                            "A committed SVG source whose rendered text colors can be checked "
                            "against the hero-card background."
                        ),
                        actual="Step 1 did not locate any committed social-preview SVG source asset.",
                    ),
                ]
            )
        else:
            observation = self.inspect_asset(asset_path)
            composition_failures = self._validate_composition(observation)
            contrast_failures = self._validate_contrast(observation)
            failures.extend(composition_failures)
            failures.extend(contrast_failures)

        if not self.playbook_documents_export_requirements():
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="The discoverability playbook does not document the required PNG export sizes.",
                    expected=(
                        "The social preview guidance should state that PNG exports are "
                        "1280x640 minimum and 2560x1280 recommended."
                    ),
                    actual=self.playbook_social_preview_excerpt(),
                )
            )

        return failures

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def locate_social_preview_asset(self) -> Path | None:
        matches = [
            candidate for candidate in self._svg_candidates() if self._is_versioned_social_preview(candidate)
        ]
        if not matches:
            return None
        return sorted(matches, key=lambda path: path.as_posix())[0]

    def inspect_asset(self, asset_path: Path) -> SocialPreviewObservation:
        root = ElementTree.fromstring(asset_path.read_text(encoding="utf-8"))
        parent_map = self._parent_map(root)
        hero_rect, hero_fill = self._hero_rect(root)
        hero_fill_hex = self._format_rgb(hero_fill) if hero_fill else None

        wordmark_texts: list[str] = []
        text_contrasts: list[TextContrastObservation] = []
        for element in root.iter():
            if self._tag_name(element) != "text":
                continue

            text = " ".join(part.strip() for part in element.itertext() if part.strip())
            if not text:
                continue

            if "dmtools" in self._normalize(text):
                wordmark_texts.append(text)

            fill = self._parse_color(
                self._effective_presentation_attribute(element, "fill", parent_map)
            )
            if fill is None:
                fill = (0, 0, 0)
            if hero_fill is None:
                continue

            text_contrasts.append(
                TextContrastObservation(
                    text=text,
                    fill_hex=self._format_rgb(fill),
                    contrast_ratio=self._contrast_ratio(fill, hero_fill),
                )
            )

        decorative_background_count = 0
        for element in root.iter():
            tag_name = self._tag_name(element)
            if tag_name not in self.SHAPE_TAGS:
                continue
            if hero_rect is not None and element is hero_rect:
                continue
            if self._is_subtle_background_shape(element):
                decorative_background_count += 1

        return SocialPreviewObservation(
            asset_path=asset_path,
            hero_fill_hex=hero_fill_hex,
            wordmark_texts=tuple(wordmark_texts),
            decorative_background_count=decorative_background_count,
            text_contrast_observations=tuple(text_contrasts),
        )

    def playbook_documents_export_requirements(self) -> bool:
        normalized = self._normalize(self.playbook_social_preview_excerpt())
        return (
            "1280x640" in normalized
            and "2560x1280" in normalized
            and "minimum" in normalized
            and "recommended" in normalized
        )

    def playbook_social_preview_excerpt(self) -> str:
        if not self.playbook_path.is_file():
            return f"{self.PLAYBOOK_RELATIVE_PATH.as_posix()} is missing."

        lines = self.playbook_path.read_text(encoding="utf-8").splitlines()
        start_index = next(
            (
                index
                for index, line in enumerate(lines)
                if line.strip() == "### Social preview asset guidance"
            ),
            None,
        )
        if start_index is None:
            return f"{self.PLAYBOOK_RELATIVE_PATH.as_posix()} is missing the social preview guidance section."

        collected: list[str] = []
        for line in lines[start_index:]:
            stripped = line.strip()
            if collected and stripped.startswith("#"):
                break
            if stripped:
                collected.append(stripped)
        return " ".join(collected)

    def _validate_composition(
        self, observation: SocialPreviewObservation
    ) -> list[ValidationFailure]:
        missing_parts: list[str] = []
        hero_fill_rgb = self._parse_color(observation.hero_fill_hex)
        if hero_fill_rgb is None:
            missing_parts.append("dark hero card")
        elif self._relative_luminance(hero_fill_rgb) > 0.2:
            missing_parts.append(f"dark hero card (observed fill {observation.hero_fill_hex})")

        if not observation.wordmark_texts:
            missing_parts.append("DMTools wordmark")

        if observation.decorative_background_count == 0:
            missing_parts.append("subtle industrial background")

        if not missing_parts:
            return []

        return [
            ValidationFailure(
                step=2,
                summary="The social preview SVG does not satisfy the required visual composition.",
                expected=(
                    "A dark hero card, a visible DMTools wordmark, and at least one subtle "
                    "industrial background element."
                ),
                actual=(
                    f"{self.relative_path(observation.asset_path)} is missing or does not prove: "
                    + ", ".join(missing_parts)
                    + "."
                ),
            )
        ]

    def _validate_contrast(
        self, observation: SocialPreviewObservation
    ) -> list[ValidationFailure]:
        if observation.hero_fill_hex is None:
            return [
                ValidationFailure(
                    step=3,
                    summary="Text contrast could not be calculated because the hero-card background was not detected.",
                    expected="A dominant dark hero-card background rectangle behind the text.",
                    actual=f"No large background rectangle with a parseable fill was found in {self.relative_path(observation.asset_path)}.",
                )
            ]

        if not observation.text_contrast_observations:
            return [
                ValidationFailure(
                    step=3,
                    summary="Text contrast could not be calculated because no SVG text elements were found.",
                    expected="Visible text layers in the SVG source that can be checked for WCAG AA contrast.",
                    actual=f"No <text> elements were found in {self.relative_path(observation.asset_path)}.",
                )
            ]

        low_contrast = [
            observation
            for observation in observation.text_contrast_observations
            if observation.contrast_ratio < 4.5
        ]
        if not low_contrast:
            return []

        return [
            ValidationFailure(
                step=3,
                summary="One or more social-preview text layers are below the required 4.5:1 contrast ratio.",
                expected="Every rendered text element in the social preview should meet or exceed 4.5:1 contrast.",
                actual="; ".join(item.format() for item in low_contrast),
            )
        ]

    def _svg_candidates(self) -> list[Path]:
        candidates: list[Path] = []
        for path in self.repository_root.rglob("*.svg"):
            if any(part in self.IGNORED_DIRECTORIES for part in path.parts):
                continue
            if not path.is_file():
                continue
            candidates.append(path)
        return sorted(candidates)

    def _is_versioned_social_preview(self, path: Path) -> bool:
        raw_name = path.stem.casefold()
        normalized_name = self._normalize(path.stem)
        has_social_preview_name = any(hint in raw_name for hint in self.PRIMARY_FILENAME_HINTS) or (
            "social preview" in normalized_name
        )
        if not has_social_preview_name:
            return False

        return bool(re.search(r"(^|[.\-_])v(?:ersion)?[0-9]+($|[.\-_])", raw_name))

    def _hero_rect(
        self, root: ElementTree.Element
    ) -> tuple[ElementTree.Element | None, tuple[int, int, int] | None]:
        fallback_width, fallback_height = self._root_dimensions(root)
        largest_rect: ElementTree.Element | None = None
        largest_area = -1.0
        largest_fill: tuple[int, int, int] | None = None

        for element in root.iter():
            if self._tag_name(element) != "rect":
                continue
            fill = self._parse_color(self._presentation_attribute(element, "fill"))
            if fill is None:
                continue

            width = self._parse_length(element.get("width")) or fallback_width
            height = self._parse_length(element.get("height")) or fallback_height
            if width is None or height is None:
                continue

            area = width * height
            if area <= largest_area:
                continue

            largest_rect = element
            largest_area = area
            largest_fill = fill

        return largest_rect, largest_fill

    def _root_dimensions(self, root: ElementTree.Element) -> tuple[float | None, float | None]:
        width = self._parse_length(root.get("width"))
        height = self._parse_length(root.get("height"))
        if width is not None and height is not None:
            return width, height

        view_box = root.get("viewBox")
        if not view_box:
            return width, height

        parts = [part for part in view_box.replace(",", " ").split() if part]
        if len(parts) != 4:
            return width, height

        try:
            return float(parts[2]), float(parts[3])
        except ValueError:
            return width, height

    def _is_subtle_background_shape(self, element: ElementTree.Element) -> bool:
        opacity = self._parse_float(self._presentation_attribute(element, "opacity"))
        fill_opacity = self._parse_float(self._presentation_attribute(element, "fill-opacity"))
        stroke_opacity = self._parse_float(self._presentation_attribute(element, "stroke-opacity"))
        subtle_opacity = min(
            value
            for value in (opacity, fill_opacity, stroke_opacity)
            if value is not None
        ) if any(value is not None for value in (opacity, fill_opacity, stroke_opacity)) else None

        has_fill_or_stroke = (
            self._parse_color(self._presentation_attribute(element, "fill")) is not None
            or self._parse_color(self._presentation_attribute(element, "stroke")) is not None
        )
        return has_fill_or_stroke and subtle_opacity is not None and subtle_opacity <= 0.35

    def _presentation_attribute(self, element: ElementTree.Element, name: str) -> str | None:
        direct = element.get(name)
        if direct:
            return direct

        style = element.get("style")
        if not style:
            return None

        for declaration in style.split(";"):
            match = STYLE_DECLARATION_PATTERN.fullmatch(declaration)
            if not match:
                continue
            key = match.group(1).strip()
            value = match.group(2).strip()
            if key == name:
                return value
        return None

    def _effective_presentation_attribute(
        self,
        element: ElementTree.Element,
        name: str,
        parent_map: dict[ElementTree.Element, ElementTree.Element],
    ) -> str | None:
        current: ElementTree.Element | None = element
        while current is not None:
            value = self._presentation_attribute(current, name)
            if value is not None:
                return value
            current = parent_map.get(current)
        return None

    @staticmethod
    def _parent_map(root: ElementTree.Element) -> dict[ElementTree.Element, ElementTree.Element]:
        return {child: parent for parent in root.iter() for child in parent}

    @staticmethod
    def _tag_name(tag: ElementTree.Element | str) -> str:
        if isinstance(tag, ElementTree.Element):
            tag = tag.tag
        if tag.startswith(SVG_NAMESPACE):
            return tag[len(SVG_NAMESPACE) :]
        return tag.rsplit("}", 1)[-1]

    @staticmethod
    def _parse_length(raw_value: str | None) -> float | None:
        if raw_value is None:
            return None
        match = re.fullmatch(r"\s*([0-9]+(?:\.[0-9]+)?)", raw_value.strip().removesuffix("px"))
        if match is None:
            return None
        return float(match.group(1))

    @staticmethod
    def _parse_float(raw_value: str | None) -> float | None:
        if raw_value is None:
            return None
        try:
            return float(raw_value)
        except ValueError:
            return None

    @classmethod
    def _parse_color(cls, raw_value: str | None) -> tuple[int, int, int] | None:
        if raw_value is None:
            return None

        value = raw_value.strip().lower()
        if not value or value == "none":
            return None
        if value in {"#fff", "#ffffff", "white"}:
            return 255, 255, 255
        if value in {"#000", "#000000", "black"}:
            return 0, 0, 0
        if value.startswith("#") and len(value) == 4:
            return tuple(int(channel * 2, 16) for channel in value[1:])  # type: ignore[return-value]
        if value.startswith("#") and len(value) == 7:
            return (
                int(value[1:3], 16),
                int(value[3:5], 16),
                int(value[5:7], 16),
            )

        rgb_match = re.fullmatch(
            r"rgb\(\s*([0-9]{1,3})\s*,\s*([0-9]{1,3})\s*,\s*([0-9]{1,3})\s*\)",
            value,
        )
        if rgb_match is None:
            return None

        channels = tuple(int(rgb_match.group(index)) for index in range(1, 4))
        if any(channel < 0 or channel > 255 for channel in channels):
            return None
        return channels

    @staticmethod
    def _format_rgb(color: tuple[int, int, int]) -> str:
        return "#{:02X}{:02X}{:02X}".format(*color)

    @staticmethod
    def _normalize(text: str) -> str:
        return NON_ALPHANUMERIC_PATTERN.sub(" ", text.casefold()).strip()

    def relative_path(self, path: Path) -> str:
        return path.relative_to(self.repository_root).as_posix()

    @classmethod
    def _contrast_ratio(
        cls, foreground: tuple[int, int, int], background: tuple[int, int, int]
    ) -> float:
        foreground_luminance = cls._relative_luminance(foreground)
        background_luminance = cls._relative_luminance(background)
        lighter = max(foreground_luminance, background_luminance)
        darker = min(foreground_luminance, background_luminance)
        return (lighter + 0.05) / (darker + 0.05)

    @staticmethod
    def _relative_luminance(color: tuple[int, int, int]) -> float:
        def _channel_luminance(channel: int) -> float:
            normalized = channel / 255.0
            if normalized <= 0.03928:
                return normalized / 12.92
            return ((normalized + 0.055) / 1.055) ** 2.4

        red, green, blue = color
        return (
            0.2126 * _channel_luminance(red)
            + 0.7152 * _channel_luminance(green)
            + 0.0722 * _channel_luminance(blue)
        )
