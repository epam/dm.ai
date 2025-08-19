#!/bin/bash

# DMTools MCP Command Wrapper
# This script acts as a stdio transport bridge for Gemini CLI to communicate with DMTools MCP server

# Server URL (can be overridden by environment variable)
SERVER_URL="${DMTOOLS_SERVER_URL:-http://localhost:8080}"

# Function to call the HTTP MCP server with proper JSON-RPC format
call_mcp_server() {
    local request="$1"
    
    # Forward the complete JSON-RPC request to our HTTP endpoint
    response=$(curl -s -X POST "${SERVER_URL}/mcp/" \
        -H "Content-Type: application/json" \
        -d "$request" \
        --max-time 30)
    
    # Check if curl succeeded
    if [ $? -eq 0 ] && [ -n "$response" ]; then
        echo "$response"
    else
        # Generate error response if server call failed
        local request_id=$(echo "$request" | jq -r '.id // "unknown"' 2>/dev/null || echo "unknown")
        echo "{\"jsonrpc\":\"2.0\",\"id\":\"$request_id\",\"error\":{\"code\":-32603,\"message\":\"Server communication failed\"}}"
    fi
}

# Function to validate JSON-RPC request
validate_jsonrpc() {
    local request="$1"
    
    # Check if it's valid JSON and has required fields
    if echo "$request" | jq -e '.jsonrpc and .method and (.id != null)' >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Read JSON-RPC requests from stdin and forward to HTTP server
while IFS= read -r line; do
    # Skip empty lines
    [ -z "$line" ] && continue
    
    # Log request to stderr for debugging
    echo "DEBUG: Received request: $line" >&2
    
    # Validate JSON-RPC format
    if validate_jsonrpc "$line"; then
        # Forward valid request to HTTP server
        call_mcp_server "$line"
    else
        # Generate error response for invalid requests
        echo "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"Invalid JSON-RPC request\"}}"
    fi
done