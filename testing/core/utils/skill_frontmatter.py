import re
from pathlib import Path


def extract_frontmatter_value(path: Path, field_name: str) -> str | None:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return None

    in_frontmatter = True
    for index in range(1, len(lines)):
        line = lines[index]
        if in_frontmatter and line.strip() == "---":
            break

        match = re.match(rf"^{re.escape(field_name)}:\s*(.*)$", line)
        if not match:
            continue

        value = match.group(1).strip()
        if value in {">", "|"}:
            collected: list[str] = []
            for continuation in lines[index + 1 :]:
                if continuation.strip() == "---":
                    break
                if continuation and not continuation[0].isspace():
                    break
                stripped = continuation.strip()
                if stripped:
                    collected.append(stripped)
            return " ".join(collected).strip() or None

        collected = [value] if value else []
        for continuation in lines[index + 1 :]:
            if continuation.strip() == "---":
                break
            if continuation and not continuation[0].isspace():
                break
            stripped = continuation.strip()
            if stripped:
                collected.append(stripped)
        return " ".join(collected).strip() or None

    return None
