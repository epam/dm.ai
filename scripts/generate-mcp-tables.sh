#!/bin/bash

# Generate MCP Tools Documentation as Markdown Tables
# Parses JSON from dmtools list and creates organized markdown files

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/dmtools-ai-docs/references/mcp-tools"

echo "🔄 Generating MCP tools documentation as tables..."

# Check if dmtools is available
# Create output directory
mkdir -p "$OUTPUT_DIR"

if [ -n "$SKIP_DMTOOLS_LIST" ]; then
    echo "⏭️  Skipping dmtools list fetch; using existing $OUTPUT_DIR/tools-raw.json"
    if [ ! -f "$OUTPUT_DIR/tools-raw.json" ]; then
        echo "❌ Error: $OUTPUT_DIR/tools-raw.json not found"
        exit 1
    fi
else
    if ! command -v dmtools &> /dev/null && [ ! -f ~/.dmtools/dmtools.jar ]; then
        echo "❌ Error: dmtools not found. Run ./buildInstallLocal.sh first"
        exit 1
    fi

    # Get the tools list as JSON
    echo "📥 Fetching tools from dmtools..."
    TOOLS_JSON=$(dmtools list 2>/dev/null || java -jar ~/.dmtools/dmtools.jar list 2>/dev/null || ./dmtools.sh list 2>/dev/null)

    # Save raw JSON for reference
    echo "$TOOLS_JSON" > "$OUTPUT_DIR/tools-raw.json"
fi

# Create a Python script to parse and generate markdown
cat > /tmp/parse_mcp_tools.py << 'PYTHON_SCRIPT'
import json
import sys
from collections import defaultdict
from datetime import datetime

def get_integration_name(tool_name):
    """Extract integration name from tool name (prefix before first underscore)"""
    parts = tool_name.split('_')
    if len(parts) > 1:
        return parts[0]
    return 'misc'

def format_params(properties, required):
    """Format parameters as a readable string"""
    if not properties:
        return "None"

    params = []
    for name, details in properties.items():
        req = "**required**" if name in required else "optional"
        param_type = details.get('type', 'string')
        desc = details.get('description', 'No description')
        # Truncate long descriptions
        if len(desc) > 60:
            desc = desc[:57] + "..."
        params.append(f"`{name}` ({param_type}, {req})")

    return "<br>".join(params)

def escape_markdown(text):
    """Escape markdown special characters"""
    if not text:
        return ""
    return text.replace('|', '\\|').replace('\n', ' ')

def generate_integration_file(integration, tools, output_dir):
    """Generate markdown file for a specific integration"""
    filename = f"{output_dir}/{integration}-tools.md"

    with open(filename, 'w') as f:
        # Header
        f.write(f"# {integration.upper()} MCP Tools\n\n")
        f.write(f"**Total Tools**: {len(tools)}\n\n")

        # Quick reference
        f.write("## Quick Reference\n\n")
        f.write("```bash\n")
        f.write(f"# List all {integration} tools\n")
        f.write(f"dmtools list | jq '.tools[] | select(.name | startswith(\"{integration}_\"))'\n\n")
        f.write("# Example usage\n")
        if tools:
            example_tool = tools[0]['name']
            f.write(f"dmtools {example_tool} [arguments]\n")
        f.write("```\n\n")

        # JavaScript usage
        f.write("## Usage in JavaScript Agents\n\n")
        f.write("```javascript\n")
        f.write(f"// Direct function calls for {integration} tools\n")
        for tool in tools[:3]:  # Show first 3 as examples
            f.write(f"const result = {tool['name']}(...);\n")
        f.write("```\n\n")

        # Tools table
        f.write("## Available Tools\n\n")
        f.write("| Tool Name | Description | Parameters |\n")
        f.write("|-----------|-------------|------------|\n")

        for tool in sorted(tools, key=lambda x: x['name']):
            name = tool['name']
            desc = escape_markdown(tool.get('description', 'No description'))

            # Extract parameters
            schema = tool.get('inputSchema', {})
            properties = schema.get('properties', {})
            required = schema.get('required', [])

            params = format_params(properties, required)

            f.write(f"| `{name}` | {desc} | {params} |\n")

        f.write("\n")

        # Detailed parameter information
        f.write("## Detailed Parameter Information\n\n")
        for tool in sorted(tools, key=lambda x: x['name']):
            name = tool['name']
            desc = tool.get('description', 'No description')

            f.write(f"### `{name}`\n\n")
            f.write(f"{desc}\n\n")

            schema = tool.get('inputSchema', {})
            properties = schema.get('properties', {})
            required = schema.get('required', [])

            if properties:
                f.write("**Parameters:**\n\n")
                for param_name, details in properties.items():
                    is_required = param_name in required
                    param_type = details.get('type', 'string')
                    param_desc = details.get('description', 'No description')
                    example = details.get('example', '')

                    req_badge = "🔴 Required" if is_required else "⚪ Optional"
                    f.write(f"- **`{param_name}`** ({param_type}) {req_badge}\n")
                    f.write(f"  - {param_desc}\n")
                    if example:
                        f.write(f"  - Example: `{example}`\n")
                    f.write("\n")
            else:
                f.write("**Parameters:** None\n\n")

            # Usage example
            f.write("**Example:**\n")
            f.write("```bash\n")
            if properties:
                # Generate example with parameters
                example_params = []
                for param_name in list(properties.keys())[:2]:  # Show first 2 params
                    example_params.append(f'"value"')
                f.write(f"dmtools {name} {' '.join(example_params)}\n")
            else:
                f.write(f"dmtools {name}\n")
            f.write("```\n\n")
            f.write("```javascript\n")
            f.write(f"// In JavaScript agent\n")
            if properties:
                param_names = ', '.join([f'"{p}"' for p in list(properties.keys())[:2]])
                f.write(f"const result = {name}({param_names});\n")
            else:
                f.write(f"const result = {name}();\n")
            f.write("```\n\n")
            f.write("---\n\n")

    print(f"✅ Generated: {filename}")

def main():
    if len(sys.argv) < 3:
        print("Usage: parse_mcp_tools.py <json_file> <output_dir>")
        sys.exit(1)

    json_file = sys.argv[1]
    output_dir = sys.argv[2]

    # Read JSON
    with open(json_file, 'r') as f:
        data = json.load(f)

    tools = data.get('tools', [])

    if not tools:
        print("❌ No tools found in JSON")
        sys.exit(1)

    # Group tools by integration
    by_integration = defaultdict(list)
    for tool in tools:
        integration = get_integration_name(tool['name'])
        by_integration[integration].append(tool)

    # Generate file for each integration
    for integration, integration_tools in sorted(by_integration.items()):
        generate_integration_file(integration, integration_tools, output_dir)

    # Generate main README
    readme_file = f"{output_dir}/README.md"
    with open(readme_file, 'w') as f:
        f.write("# DMtools MCP Tools Reference\n\n")
        f.write("Complete reference for all MCP tools available in DMtools.\n\n")
        f.write(f"**Total Integrations**: {len(by_integration)}\n")
        f.write(f"**Total Tools**: {len(tools)}\n\n")

        f.write("*Auto-generated from `dmtools list` on: " + datetime.now().strftime("%Y-%m-%d %H:%M:%S") + "*\n\n")

        f.write("## Quick Start\n\n")
        f.write("```bash\n")
        f.write("# List all available tools\n")
        f.write("dmtools list\n\n")
        f.write("# List tools for specific integration\n")
        f.write("dmtools list | jq '.tools[] | select(.name | startswith(\"jira_\"))'\n\n")
        f.write("# Execute a tool\n")
        f.write("dmtools <tool_name> [arguments]\n")
        f.write("```\n\n")

        f.write("## Integrations\n\n")
        f.write("| Integration | Tools | Documentation |\n")
        f.write("|-------------|-------|---------------|\n")

        for integration in sorted(by_integration.keys()):
            count = len(by_integration[integration])
            f.write(f"| **{integration.upper()}** | {count} | [{integration}-tools.md]({integration}-tools.md) |\n")

        f.write("\n## Usage in JavaScript Agents\n\n")
        f.write("All MCP tools are directly accessible as JavaScript functions:\n\n")
        f.write("```javascript\n")
        f.write("// Direct MCP tool access\n")
        f.write("const ticket = jira_get_ticket('PROJ-123');\n")
        f.write("const workItem = ado_get_work_item(12345);\n")
        f.write("const response = gemini_ai_chat('Analyze this');\n")
        f.write("file_write('output.txt', 'content');\n")
        f.write("```\n\n")

        f.write("## Integration Categories\n\n")

        # Group integrations by category
        categories = {
            'Issue Tracking': ['jira', 'ado'],
            'Communication': ['teams', 'slack'],
            'Design': ['figma'],
            'Documentation': ['confluence', 'sharepoint'],
            'AI Providers': ['gemini', 'openai', 'anthropic', 'ollama', 'bedrock', 'dial'],
            'Authentication': ['teams'],
            'File Operations': ['file'],
            'CLI Operations': ['cli'],
            'Knowledge Base': ['kb'],
            'Miscellaneous': ['misc']
        }

        for category, integrations in categories.items():
            category_tools = []
            for integration in integrations:
                if integration in by_integration:
                    category_tools.append((integration, len(by_integration[integration])))

            if category_tools:
                f.write(f"### {category}\n\n")
                for integration, count in category_tools:
                    f.write(f"- [{integration.upper()}]({integration}-tools.md) - {count} tools\n")
                f.write("\n")

    print(f"✅ Generated: {readme_file}")
    print(f"\n📊 Summary:")
    print(f"   - Integrations: {len(by_integration)}")
    print(f"   - Total Tools: {len(tools)}")
    for integration in sorted(by_integration.keys()):
        print(f"   - {integration}: {len(by_integration[integration])} tools")

if __name__ == '__main__':
    main()
PYTHON_SCRIPT

# Run the Python script
python3 /tmp/parse_mcp_tools.py "$OUTPUT_DIR/tools-raw.json" "$OUTPUT_DIR"

# Cleanup
rm /tmp/parse_mcp_tools.py

echo ""
echo "✅ Documentation generated successfully!"
echo "📁 Output directory: $OUTPUT_DIR"
echo ""
echo "📄 Files created:"
ls -lh "$OUTPUT_DIR"/*.md | awk '{print "   -", $9, "(" $5 ")"}'