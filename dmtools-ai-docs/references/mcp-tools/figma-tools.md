# FIGMA MCP Tools

**Total Tools**: 19

## Quick Reference

```bash
# List all figma tools
dmtools list | jq '.tools[] | select(.name | startswith("figma_"))'

# Example usage
dmtools figma_oauth2_get_auth_url [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for figma tools
const result = figma_oauth2_get_auth_url(...);
const result = figma_oauth2_exchange_code(...);
const result = figma_test(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `figma_download_image_as_file` | Download image as file by node ID and format. Use this after figma_get_icons to download actual icon files. | `format` (string, **required**)<br>`nodeId` (string, **required**)<br>`href` (string, **required**) |
| `figma_download_image_of_file` | Download image by URL as File type. Converts Figma design URL to downloadable image file. | `href` (string, **required**) |
| `figma_download_node_image` | Download image of specific node/component. Useful for visual preview of design pieces before processing structure. | `format` (string, optional)<br>`scale` (number, optional)<br>`href` (string, **required**)<br>`nodeId` (string, **required**) |
| `figma_get_file_structure` | Get full JSON structure of a Figma design file by URL. Returns the complete document tree (nodes, frames, components, text, styles). Response is large by design — intended for pre-CLI artifact preparation and file output, not inline AI context. If the URL contains node-id, returns only that subtree. | `href` (string, **required**) |
| `figma_get_icons` | Find and extract all exportable visual elements (vectors, shapes, graphics, text) from Figma design by URL. Focuses on actual visual elements to avoid complex component references. | `href` (string, **required**) |
| `figma_get_image_fills` | Get original image fill URLs for all imageRefs in a Figma file. Resolves imageRef placeholders to actual downloadable S3 URLs. Use after inspecting file structure to download original photos/images embedded by the designer. | `href` (string, **required**) |
| `figma_get_layers` | Get first-level layers (direct children) to understand structure. Returns layer names, IDs, types, sizes. Essential first step before getting details. | `href` (string, **required**) |
| `figma_get_layers_batch` | Get layers for multiple nodes at once. More efficient for analyzing multiple screens/containers. Returns map of nodeId to layers. | `nodeIds` (string, **required**)<br>`href` (string, **required**) |
| `figma_get_node_children` | Get immediate children IDs and basic info for a node. Non-recursive, returns only direct children. | `href` (string, **required**) |
| `figma_get_node_details` | Get detailed properties for specific node(s) including colors, fonts, text, dimensions, and styles. Returns small focused response. | `nodeIds` (string, **required**)<br>`href` (string, **required**) |
| `figma_get_screen_source` | Get screen source content by URL. Returns the image URL for the specified Figma design node. | `url` (string, **required**) |
| `figma_get_styles` | Get design tokens (colors, text styles) defined in Figma file. | `href` (string, **required**) |
| `figma_get_svg_content` | Get SVG content as text by node ID. Use this after figma_get_icons to get SVG code for vector icons. | `nodeId` (string, **required**)<br>`href` (string, **required**) |
| `figma_get_text_content` | Extract text content from text nodes. Returns map of nodeId to text content. | `nodeIds` (string, **required**)<br>`href` (string, **required**) |
| `figma_me` | Gets current user information from the Figma API using the /me endpoint. Returns user details including id, handle, and email. Can also be used to verify API connectivity. | None |
| `figma_oauth2_exchange_code` | Exchanges a Figma OAuth2 authorization code for access and refresh tokens. Use the code from the redirect URL after completing the browser authorization flow started by figma_oauth2_get_auth_url. Store FIGMA_OAUTH_REFRESH_TOKEN from the response in your dmtools.env to enable automatic token refresh. | `redirectUri` (string, optional)<br>`code` (string, **required**) |
| `figma_oauth2_get_auth_url` | Generates the Figma OAuth2 authorization URL for the initial authorization code flow. Open the returned URL in a browser, authorize the app, and copy the 'code' parameter from the redirect URL. Then call figma_oauth2_exchange_code to get access and refresh tokens. Requires FIGMA_CLIENT_ID and FIGMA_CLIENT_SECRET to be configured. Optional scope can be passed explicitly or via FIGMA_SCOPE/FIGMA_OAUTH_SCOPES env variable. | `redirectUri` (string, optional)<br>`state` (string, optional)<br>`scope` (string, optional) |
| `figma_render_nodes` | Render multiple Figma nodes as images in a single batched API call. Automatically batches up to 100 node IDs per request. Returns map of nodeId to render URL. Use for exporting many icons or frames efficiently. | `format` (string, optional)<br>`nodeIds` (string, **required**)<br>`href` (string, **required**) |
| `figma_test` | Test Figma connectivity by fetching the current user's profile | None |

## Detailed Parameter Information

### `figma_download_image_as_file`

Download image as file by node ID and format. Use this after figma_get_icons to download actual icon files.

**Parameters:**

- **`format`** (string) 🔴 Required
  - Export format
  - Example: `png`

- **`nodeId`** (string) 🔴 Required
  - Node ID to export (from figma_get_icons result)
  - Example: `123:456`

- **`href`** (string) 🔴 Required
  - Figma design URL to extract file ID from
  - Example: `https://www.figma.com/file/abc123/Design`

**Example:**
```bash
dmtools figma_download_image_as_file "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_download_image_as_file("format", "nodeId");
```

---

### `figma_download_image_of_file`

Download image by URL as File type. Converts Figma design URL to downloadable image file.

**Parameters:**

- **`href`** (string) 🔴 Required
  - Figma design URL to download as image file
  - Example: `https://www.figma.com/file/abc123/Design?node-id=1%3A2`

**Example:**
```bash
dmtools figma_download_image_of_file "value"
```

```javascript
// In JavaScript agent
const result = figma_download_image_of_file("href");
```

---

### `figma_download_node_image`

Download image of specific node/component. Useful for visual preview of design pieces before processing structure.

**Parameters:**

- **`format`** (string) ⚪ Optional
  - Image format: png or jpg

- **`scale`** (number) ⚪ Optional
  - Scale factor: 1, 2, or 4

- **`href`** (string) 🔴 Required
  - Figma design URL

- **`nodeId`** (string) 🔴 Required
  - Node ID to download

**Example:**
```bash
dmtools figma_download_node_image "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_download_node_image("format", "scale");
```

---

### `figma_get_file_structure`

Get full JSON structure of a Figma design file by URL. Returns the complete document tree (nodes, frames, components, text, styles). Response is large by design — intended for pre-CLI artifact preparation and file output, not inline AI context. If the URL contains node-id, returns only that subtree.

**Parameters:**

- **`href`** (string) 🔴 Required
  - Parameter href

**Example:**
```bash
dmtools figma_get_file_structure "value"
```

```javascript
// In JavaScript agent
const result = figma_get_file_structure("href");
```

---

### `figma_get_icons`

Find and extract all exportable visual elements (vectors, shapes, graphics, text) from Figma design by URL. Focuses on actual visual elements to avoid complex component references.

**Parameters:**

- **`href`** (string) 🔴 Required
  - Figma design URL to extract visual elements from
  - Example: `https://www.figma.com/file/abc123/Design`

**Example:**
```bash
dmtools figma_get_icons "value"
```

```javascript
// In JavaScript agent
const result = figma_get_icons("href");
```

---

### `figma_get_image_fills`

Get original image fill URLs for all imageRefs in a Figma file. Resolves imageRef placeholders to actual downloadable S3 URLs. Use after inspecting file structure to download original photos/images embedded by the designer.

**Parameters:**

- **`href`** (string) 🔴 Required
  - Figma design URL

**Example:**
```bash
dmtools figma_get_image_fills "value"
```

```javascript
// In JavaScript agent
const result = figma_get_image_fills("href");
```

---

### `figma_get_layers`

Get first-level layers (direct children) to understand structure. Returns layer names, IDs, types, sizes. Essential first step before getting details.

**Parameters:**

- **`href`** (string) 🔴 Required
  - Figma design URL with node-id

**Example:**
```bash
dmtools figma_get_layers "value"
```

```javascript
// In JavaScript agent
const result = figma_get_layers("href");
```

---

### `figma_get_layers_batch`

Get layers for multiple nodes at once. More efficient for analyzing multiple screens/containers. Returns map of nodeId to layers.

**Parameters:**

- **`nodeIds`** (string) 🔴 Required
  - Comma-separated node IDs (max 10)

- **`href`** (string) 🔴 Required
  - Figma design URL

**Example:**
```bash
dmtools figma_get_layers_batch "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_get_layers_batch("nodeIds", "href");
```

---

### `figma_get_node_children`

Get immediate children IDs and basic info for a node. Non-recursive, returns only direct children.

**Parameters:**

- **`href`** (string) 🔴 Required
  - Figma design URL with node-id

**Example:**
```bash
dmtools figma_get_node_children "value"
```

```javascript
// In JavaScript agent
const result = figma_get_node_children("href");
```

---

### `figma_get_node_details`

Get detailed properties for specific node(s) including colors, fonts, text, dimensions, and styles. Returns small focused response.

**Parameters:**

- **`nodeIds`** (string) 🔴 Required
  - Comma-separated node IDs (max 10)

- **`href`** (string) 🔴 Required
  - Figma design URL

**Example:**
```bash
dmtools figma_get_node_details "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_get_node_details("nodeIds", "href");
```

---

### `figma_get_screen_source`

Get screen source content by URL. Returns the image URL for the specified Figma design node.

**Parameters:**

- **`url`** (string) 🔴 Required
  - Figma design URL with node-id parameter
  - Example: `https://www.figma.com/file/abc123/Design?node-id=1%3A2`

**Example:**
```bash
dmtools figma_get_screen_source "value"
```

```javascript
// In JavaScript agent
const result = figma_get_screen_source("url");
```

---

### `figma_get_styles`

Get design tokens (colors, text styles) defined in Figma file.

**Parameters:**

- **`href`** (string) 🔴 Required
  - Figma design URL

**Example:**
```bash
dmtools figma_get_styles "value"
```

```javascript
// In JavaScript agent
const result = figma_get_styles("href");
```

---

### `figma_get_svg_content`

Get SVG content as text by node ID. Use this after figma_get_icons to get SVG code for vector icons.

**Parameters:**

- **`nodeId`** (string) 🔴 Required
  - Node ID to export as SVG (from figma_get_icons result)
  - Example: `123:456`

- **`href`** (string) 🔴 Required
  - Figma design URL to extract file ID from
  - Example: `https://www.figma.com/file/abc123/Design`

**Example:**
```bash
dmtools figma_get_svg_content "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_get_svg_content("nodeId", "href");
```

---

### `figma_get_text_content`

Extract text content from text nodes. Returns map of nodeId to text content.

**Parameters:**

- **`nodeIds`** (string) 🔴 Required
  - Comma-separated text node IDs (max 20)

- **`href`** (string) 🔴 Required
  - Figma design URL

**Example:**
```bash
dmtools figma_get_text_content "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_get_text_content("nodeIds", "href");
```

---

### `figma_me`

Gets current user information from the Figma API using the /me endpoint. Returns user details including id, handle, and email. Can also be used to verify API connectivity.

**Parameters:** None

**Example:**
```bash
dmtools figma_me
```

```javascript
// In JavaScript agent
const result = figma_me();
```

---

### `figma_oauth2_exchange_code`

Exchanges a Figma OAuth2 authorization code for access and refresh tokens. Use the code from the redirect URL after completing the browser authorization flow started by figma_oauth2_get_auth_url. Store FIGMA_OAUTH_REFRESH_TOKEN from the response in your dmtools.env to enable automatic token refresh.

**Parameters:**

- **`redirectUri`** (string) ⚪ Optional
  - Same redirect URI used in figma_oauth2_get_auth_url. If omitted, uses FIGMA_REDIRECT_URI env variable.
  - Example: `http://localhost:8080/callback`

- **`code`** (string) 🔴 Required
  - Authorization code received from Figma OAuth2 redirect
  - Example: `figma_auth_code_abc123`

**Example:**
```bash
dmtools figma_oauth2_exchange_code "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_oauth2_exchange_code("redirectUri", "code");
```

---

### `figma_oauth2_get_auth_url`

Generates the Figma OAuth2 authorization URL for the initial authorization code flow. Open the returned URL in a browser, authorize the app, and copy the 'code' parameter from the redirect URL. Then call figma_oauth2_exchange_code to get access and refresh tokens. Requires FIGMA_CLIENT_ID and FIGMA_CLIENT_SECRET to be configured. Optional scope can be passed explicitly or via FIGMA_SCOPE/FIGMA_OAUTH_SCOPES env variable.

**Parameters:**

- **`redirectUri`** (string) ⚪ Optional
  - Redirect URI registered in your Figma OAuth app (e.g. http://localhost:8080/callback). If omitted, uses FIGMA_REDIRECT_URI env variable.
  - Example: `http://localhost:8080/callback`

- **`state`** (string) ⚪ Optional
  - Random state string for CSRF protection
  - Example: `random_state_123`

- **`scope`** (string) ⚪ Optional
  - Optional OAuth scope list (space-separated), e.g. file_content:read file_metadata:read. If omitted, uses FIGMA_SCOPE (or FIGMA_OAUTH_SCOPES) env or default minimal read scope.
  - Example: `file_content:read file_metadata:read`

**Example:**
```bash
dmtools figma_oauth2_get_auth_url "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_oauth2_get_auth_url("redirectUri", "state");
```

---

### `figma_render_nodes`

Render multiple Figma nodes as images in a single batched API call. Automatically batches up to 100 node IDs per request. Returns map of nodeId to render URL. Use for exporting many icons or frames efficiently.

**Parameters:**

- **`format`** (string) ⚪ Optional
  - Export format: png, jpg, svg, pdf. Default: png

- **`nodeIds`** (string) 🔴 Required
  - Comma-separated node IDs to render (e.g. '1:2,3:4,5:6')

- **`href`** (string) 🔴 Required
  - Figma design URL

**Example:**
```bash
dmtools figma_render_nodes "value" "value"
```

```javascript
// In JavaScript agent
const result = figma_render_nodes("format", "nodeIds");
```

---

### `figma_test`

Test Figma connectivity by fetching the current user's profile

**Parameters:** None

**Example:**
```bash
dmtools figma_test
```

```javascript
// In JavaScript agent
const result = figma_test();
```

---

