from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class McpDocsGenerationResult:
    compile_java: CommandResult
    generate_docs: CommandResult
    generated_files: tuple[str, ...]
    mermaid_tools_generated: bool
    mermaid_index_linked: bool
    mermaid_heading_correct: bool


class McpDocsGenerationService:
    TEAMMATE_DOC_PATH = "dmtools-ai-docs/references/agents/teammate-configs.md"
    REGISTRY_PATH = (
        "dmtools-core/build/generated/sources/annotationProcessor/java/main/"
        "com/github/istin/dmtools/mcp/generated/MCPToolRegistry.java"
    )
    GENERATED_INDEX_PATH = "dmtools-ai-docs/references/mcp-tools/README.md"
    MERMAID_DOC_PATH = "dmtools-ai-docs/references/mcp-tools/mermaid-tools.md"
    SPECIAL_CHARACTER_AGENT_NAME = "Mermaid/Docs: Audit Agent"

    def __init__(
        self,
        repository_root: Path,
        sandbox_factory: Callable[[Path], RepoSandbox] = RepoSandbox,
    ) -> None:
        self.repository_root = repository_root
        self._sandbox_factory = sandbox_factory

    def run(self) -> McpDocsGenerationResult:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            self._inject_special_character_agent_name(sandbox)
            compile_java = sandbox.run("./gradlew :dmtools-core:compileJava --quiet")
            generate_docs = sandbox.run("./scripts/generate-mcp-docs.sh")
            generated_files = self._generated_files(sandbox)
            mermaid_markdown = self._read_if_present(sandbox, self.MERMAID_DOC_PATH)
            generated_index = self._read_if_present(sandbox, self.GENERATED_INDEX_PATH)

            return McpDocsGenerationResult(
                compile_java=compile_java,
                generate_docs=generate_docs,
                generated_files=generated_files,
                mermaid_tools_generated="mermaid-tools.md" in generated_files,
                mermaid_index_linked="(mermaid-tools.md)" in generated_index,
                mermaid_heading_correct=mermaid_markdown.startswith("# Mermaid MCP Tools"),
            )
        finally:
            sandbox.cleanup()

    def _inject_special_character_agent_name(self, sandbox: RepoSandbox) -> None:
        source_text = sandbox.read_text(self.TEAMMATE_DOC_PATH)
        heading = "# AI Teammate Configuration Guide"
        special_character_summary = (
            "Regression coverage keeps the "
            f"{self.SPECIAL_CHARACTER_AGENT_NAME} entry visible while MCP docs regenerate."
        )

        if heading not in source_text:
            raise AssertionError(f"Expected heading {heading!r} was not found in {self.TEAMMATE_DOC_PATH}")

        sandbox.write_text(
            self.TEAMMATE_DOC_PATH,
            source_text.replace(
                heading,
                f"{heading}\n\n{special_character_summary}",
                1,
            ),
        )

    def _generated_files(self, sandbox: RepoSandbox) -> tuple[str, ...]:
        files_result = sandbox.run(
            "find dmtools-ai-docs/references/mcp-tools -maxdepth 1 -type f -printf '%f\n' | sort"
        )
        return tuple(
            line.strip()
            for line in files_result.stdout.splitlines()
            if line.strip()
        )

    @staticmethod
    def _read_if_present(sandbox: RepoSandbox, relative_path: str) -> str:
        try:
            return sandbox.read_text(relative_path)
        except FileNotFoundError:
            return ""
