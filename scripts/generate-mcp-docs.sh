#!/bin/bash

# Generate MCP Tools Documentation from MCPToolRegistry
# This ensures 100% accuracy by reading from the actual generated registry

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REGISTRY_FILE="$PROJECT_ROOT/dmtools-core/build/generated/sources/annotationProcessor/java/main/com/github/istin/dmtools/mcp/generated/MCPToolRegistry.java"
OUTPUT_DIR="$PROJECT_ROOT/dmtools-ai-docs/references/mcp-tools"

# Check if registry exists
if [ ! -f "$REGISTRY_FILE" ]; then
    echo "Error: MCPToolRegistry not found. Run './gradlew :dmtools-core:compileJava' first"
    exit 1
fi

echo "Generating MCP tools documentation from registry..."

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Generate markdown from the registry using a simple Java parser
cat > /tmp/GenerateMCPDocs.java << 'EOF'
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class GenerateMCPDocs {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GenerateMCPDocs <registry-file> <output-dir>");
            System.exit(1);
        }

        String registryFile = args[0];
        String outputDir = args[1];

        // Read the registry file
        String content = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(registryFile)
        ));

        // Parse tool definitions
        Map<String, List<ToolDef>> toolsByIntegration = new LinkedHashMap<>();
        Pattern toolPattern = Pattern.compile(
            "tools\\.put\\(\"([^\"]+)\",\\s*new MCPToolDefinition\\(\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]*)\""
        );

        Matcher matcher = toolPattern.matcher(content);
        while (matcher.find()) {
            String key = matcher.group(1);
            String registryName = matcher.group(2);
            String description = matcher.group(3);
            String integration = matcher.group(4);
            String category = matcher.group(5);
            String name = key.isEmpty() ? registryName : key;

            ToolDef tool = new ToolDef(name, description, integration, category);
            toolsByIntegration.computeIfAbsent(integration, k -> new ArrayList<>()).add(tool);
        }

        // Generate markdown for each integration
        for (Map.Entry<String, List<ToolDef>> entry : toolsByIntegration.entrySet()) {
            String integration = entry.getKey();
            List<ToolDef> tools = entry.getValue();

            if (integration.isEmpty()) {
                integration = "misc";
            }

            String fileSlug = sanitizeFilename(integration);
            String filename = outputDir + "/" + fileSlug + "-tools.md";
            try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
                out.println("# " + capitalizeFirst(integration) + " MCP Tools");
                out.println();
                out.println("**Total Tools**: " + tools.size());
                out.println();
                out.println("## Available Tools");
                out.println();
                out.println("| Tool Name | Description | Category |");
                out.println("|-----------|-------------|----------|");

                for (ToolDef tool : tools) {
                    out.printf("| `%s` | %s | %s |%n",
                        tool.name,
                        tool.description.replace("|", "\\|"),
                        tool.category.isEmpty() ? "-" : tool.category
                    );
                }
                out.println();
            }

            System.out.println("Generated: " + filename);
        }

        // Calculate total tools
        int totalTools = toolsByIntegration.values().stream()
            .mapToInt(List::size)
            .sum();

        // Generate index file
        String indexFile = outputDir + "/README.md";
        try (PrintWriter out = new PrintWriter(new FileWriter(indexFile))) {
            out.println("# DMtools MCP Tools Reference");
            out.println();
            out.println("This documentation is auto-generated from the actual `MCPToolRegistry`.");
            out.println();
            out.println("**Total Integrations**: " + toolsByIntegration.size());
            out.println();
            out.println("**Total Tools**: " + totalTools);
            out.println();
            out.println("## Integrations");
            out.println();

            for (Map.Entry<String, List<ToolDef>> entry : toolsByIntegration.entrySet()) {
                String integration = entry.getKey();
                if (integration.isEmpty()) integration = "misc";
                int count = entry.getValue().size();
                String fileSlug = sanitizeFilename(integration);
                out.printf("- [%s](%s-tools.md) - %d tools%n",
                    capitalizeFirst(integration),
                    fileSlug,
                    count
                );
            }
            out.println();
            out.println("---");
            out.println();
            out.println("*Generated from MCPToolRegistry on: " + new Date() + "*");
        }

        System.out.println("Generated: " + indexFile);
        System.out.println("Total tools documented: " + totalTools);
    }

    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static String sanitizeFilename(String str) {
        if (str == null || str.isEmpty()) return "misc";

        String sanitized = str
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("^-+|-+$", "");

        return sanitized.isEmpty() ? "misc" : sanitized;
    }

    static class ToolDef {
        String name;
        String description;
        String integration;
        String category;

        ToolDef(String name, String description, String integration, String category) {
            this.name = name;
            this.description = description;
            this.integration = integration;
            this.category = category;
        }
    }
}
EOF

# Compile and run the generator
javac /tmp/GenerateMCPDocs.java
java -cp /tmp GenerateMCPDocs "$REGISTRY_FILE" "$OUTPUT_DIR"

# Cleanup
rm -f /tmp/GenerateMCPDocs*.class /tmp/GenerateMCPDocs.java

echo "✅ MCP tools documentation generated in: $OUTPUT_DIR"
echo "📄 Files created:"
ls -lh "$OUTPUT_DIR"
