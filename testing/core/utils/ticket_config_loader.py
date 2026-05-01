from pathlib import Path


def load_ticket_config(path: Path) -> dict[str, object]:
    config: dict[str, object] = {}
    current_list_key: str | None = None

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.rstrip()
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue

        if line.startswith("  - "):
            if current_list_key is None:
                raise ValueError(f"List entry found before a key in {path}: {line}")
            config.setdefault(current_list_key, [])
            values = config[current_list_key]
            if not isinstance(values, list):
                raise ValueError(f"Key {current_list_key!r} is not a list in {path}")
            values.append(line[4:].strip())
            continue

        current_list_key = None
        if ":" not in line:
            raise ValueError(f"Unsupported config line in {path}: {line}")

        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()

        if value == "[]":
            config[key] = []
        elif value:
            config[key] = value
        else:
            config[key] = []
            current_list_key = key

    return config
