import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.mcp_docs_generation_service import (  # noqa: E402
    McpDocsGenerationService,
)
from testing.core.utils.repo_sandbox import CommandResult  # noqa: E402


def test_dmc_972_generate_mcp_docs_uses_sanitized_mermaid_filename() -> None:
    service = McpDocsGenerationService(REPOSITORY_ROOT)

    result = service.run()

    failures: list[str] = []
    compile_output = result.compile_java.combined_output
    generate_output = result.generate_docs.combined_output
    combined_output = "\n".join(part for part in (compile_output, generate_output) if part)

    if result.compile_java.returncode != 0:
        failures.append(
            "Gradle failed to generate MCPToolRegistry.java before the docs script ran.\n"
            f"{compile_output}"
        )
    if result.generate_docs.returncode != 0:
        failures.append(
            "scripts/generate-mcp-docs.sh failed.\n"
            f"{generate_output}"
        )
    if result.compile_java.returncode == 0 and not result.registry_updated:
        failures.append(
            "The sandboxed MCPToolRegistry.java still kept the original 'mermaid' integration, "
            "so the docs generator never received the punctuation-heavy integration name."
        )
    if "FileNotFoundException" in combined_output:
        failures.append(
            "The docs generation flow still emitted java.io.FileNotFoundException while "
            "processing the special-character Mermaid tool documentation.\n"
            f"{combined_output}"
        )
    if not result.special_character_tools_generated:
        failures.append(
            "Expected the sanitized special-character output file to be generated as "
            f"'{result.expected_tools_doc_filename}', but the output directory contained: "
            f"{result.generated_files}"
        )
    if not result.special_character_index_linked:
        failures.append(
            "The generated MCP docs index does not link the special-character integration to "
            f"{result.expected_tools_doc_filename}, so the user-facing documentation entry is "
            "still missing."
        )
    if not result.special_character_heading_correct:
        failures.append(
            "The generated special-character tools page did not render the expected visible "
            f"title '{result.expected_tools_heading}'."
        )

    assert not failures, "\n\n".join(failures)


class FakeSandbox:
    REGISTRY_PATH = (
        "dmtools-core/build/generated/sources/annotationProcessor/java/main/"
        "com/github/istin/dmtools/mcp/generated/MCPToolRegistry.java"
    )

    def __init__(self) -> None:
        self._files = {
            "dmtools-ai-docs/references/mcp-tools/README.md": "",
        }
        self.commands: list[str] = []
        self.cleaned_up = False

    def cleanup(self) -> None:
        self.cleaned_up = True

    def read_text(self, relative_path: str) -> str:
        return self._files[relative_path]

    def write_text(self, relative_path: str, content: str) -> None:
        self._files[relative_path] = content

    def run(self, command: str, timeout: int = 1800) -> CommandResult:
        del timeout
        self.commands.append(command)

        if command == "./gradlew :dmtools-core:compileJava --quiet":
            self._files[self.REGISTRY_PATH] = (
                'tools.put("mermaid_render", new MCPToolDefinition('
                '"mermaid_render", "Render Mermaid diagrams", "mermaid", "diagram"));\n'
                'tools.put("mermaid_validate", new MCPToolDefinition('
                '"mermaid_validate", "Validate Mermaid syntax", "mermaid", "diagram"));'
            )
            return CommandResult(command=command, returncode=0, stdout="", stderr="")

        if command == "./scripts/generate-mcp-docs.sh":
            registry_text = self._files[self.REGISTRY_PATH]
            if McpDocsGenerationService.SPECIAL_CHARACTER_AGENT_NAME in registry_text:
                integration_name = McpDocsGenerationService.SPECIAL_CHARACTER_AGENT_NAME
            else:
                integration_name = "mermaid"

            sanitized_name = _sanitize_filename(integration_name)
            tools_doc_filename = f"{sanitized_name}-tools.md"
            heading = f"# {integration_name} MCP Tools"
            self._files["dmtools-ai-docs/references/mcp-tools/README.md"] = (
                "# DMtools MCP Tools Reference\n\n"
                f"- [{integration_name}]({tools_doc_filename}) - 2 tools\n"
            )
            self._files[f"dmtools-ai-docs/references/mcp-tools/{tools_doc_filename}"] = (
                f"{heading}\n\n"
                "Generated content"
            )
            return CommandResult(command=command, returncode=0, stdout="Generated", stderr="")

        if command == "find dmtools-ai-docs/references/mcp-tools -maxdepth 1 -type f -printf '%f\n' | sort":
            files = sorted(path.split("/")[-1] for path in self._files if path.startswith(
                "dmtools-ai-docs/references/mcp-tools/"
            ))
            return CommandResult(
                command=command,
                returncode=0,
                stdout="".join(f"{filename}\n" for filename in files),
                stderr="",
            )

        raise AssertionError(f"Unexpected command: {command}")


def test_dmc_972_service_reports_generated_mermaid_docs_with_injected_sandbox() -> None:
    sandbox = FakeSandbox()
    service = McpDocsGenerationService(REPOSITORY_ROOT, sandbox_factory=lambda _: sandbox)

    result = service.run()

    assert sandbox.commands == [
        "./gradlew :dmtools-core:compileJava --quiet",
        "./scripts/generate-mcp-docs.sh",
        "find dmtools-ai-docs/references/mcp-tools -maxdepth 1 -type f -printf '%f\n' | sort",
    ]
    assert result.registry_updated is True
    assert result.special_character_tools_generated is True
    assert result.special_character_index_linked is True
    assert result.special_character_heading_correct is True
    assert sandbox.cleaned_up is True
    assert result.expected_tools_doc_filename == "mermaid-docs-audit-agent-tools.md"
    assert result.expected_tools_heading == "# Mermaid/Docs: Audit Agent MCP Tools"
    assert (
        McpDocsGenerationService.SPECIAL_CHARACTER_AGENT_NAME in sandbox.read_text(FakeSandbox.REGISTRY_PATH)
    )


def _sanitize_filename(value: str) -> str:
    sanitized = []
    previous_was_separator = False

    for character in value.lower():
        if character.isalnum() or character in "._-":
            sanitized.append(character)
            previous_was_separator = False
            continue

        if not previous_was_separator:
            sanitized.append("-")
            previous_was_separator = True

    return "".join(sanitized).strip("-") or "misc"
