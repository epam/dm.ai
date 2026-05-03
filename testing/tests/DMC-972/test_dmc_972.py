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
    if "FileNotFoundException" in combined_output:
        failures.append(
            "The docs generation flow still emitted java.io.FileNotFoundException while "
            "processing the Mermaid tool documentation.\n"
            f"{combined_output}"
        )
    if not result.mermaid_tools_generated:
        failures.append(
            "Expected the sanitized Mermaid output file to be generated as "
            f"'mermaid-tools.md', but the output directory contained: {result.generated_files}"
        )
    if not result.mermaid_index_linked:
        failures.append(
            "The generated MCP docs index does not link to mermaid-tools.md, so the "
            "user-facing documentation entry is still missing."
        )
    if not result.mermaid_heading_correct:
        failures.append(
            "The generated mermaid-tools.md file did not render the expected visible "
            "title '# Mermaid MCP Tools'."
        )

    assert not failures, "\n\n".join(failures)


class FakeSandbox:
    def __init__(self) -> None:
        self._files = {
            "dmtools-ai-docs/references/agents/teammate-configs.md": (
                "# AI Teammate Configuration Guide\n\n"
                "Original summary."
            ),
            "dmtools-ai-docs/references/mcp-tools/README.md": "",
            "dmtools-ai-docs/references/mcp-tools/mermaid-tools.md": "",
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
            self._files[
                "dmtools-core/build/generated/sources/annotationProcessor/java/main/"
                "com/github/istin/dmtools/mcp/generated/MCPToolRegistry.java"
            ] = "// generated"
            return CommandResult(command=command, returncode=0, stdout="", stderr="")

        if command == "./scripts/generate-mcp-docs.sh":
            self._files["dmtools-ai-docs/references/mcp-tools/README.md"] = (
                "# DMtools MCP Tools Reference\n\n"
                "- [Mermaid](mermaid-tools.md) - 3 tools\n"
            )
            self._files["dmtools-ai-docs/references/mcp-tools/mermaid-tools.md"] = (
                "# Mermaid MCP Tools\n\n"
                "Generated content"
            )
            return CommandResult(command=command, returncode=0, stdout="Generated", stderr="")

        if command == "find dmtools-ai-docs/references/mcp-tools -maxdepth 1 -type f -printf '%f\n' | sort":
            return CommandResult(
                command=command,
                returncode=0,
                stdout="README.md\nmermaid-tools.md\n",
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
    assert result.mermaid_tools_generated is True
    assert result.mermaid_index_linked is True
    assert result.mermaid_heading_correct is True
    assert sandbox.cleaned_up is True
    assert (
        McpDocsGenerationService.SPECIAL_CHARACTER_AGENT_NAME
        in sandbox.read_text("dmtools-ai-docs/references/agents/teammate-configs.md")
    )
