# CONFLUENCE MCP Tools

**Total Tools**: 19

## Quick Reference

```bash
# List all confluence tools
dmtools list | jq '.tools[] | select(.name | startswith("confluence_"))'

# Example usage
dmtools confluence_contents_by_urls [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for confluence tools
const result = confluence_contents_by_urls(...);
const result = confluence_search_content_by_text(...);
const result = confluence_content_by_id(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `confluence_content_by_id` | Get Confluence content by its unique content ID. Returns detailed content information including body, version, and metadata. Use format=md to convert body.storage.value to Markdown. | `contentId` (string, **required**)<br>`format` (string, optional) |
| `confluence_content_by_title` | Get Confluence content by title in the default space. Returns content result with metadata and body information. Use format=md to convert body.storage.value to Markdown. | `title` (string, **required**)<br>`format` (string, optional) |
| `confluence_content_by_title_and_space` | Get Confluence content by title and space key. Returns content result with metadata and body information. Use format=md to convert body.storage.value to Markdown. | `title` (string, **required**)<br>`format` (string, optional)<br>`space` (string, **required**) |
| `confluence_contents_by_urls` | Get Confluence content by multiple URLs. Returns a list of content objects for each valid URL. Use format=md to convert body.storage.value to Markdown. | `format` (string, optional)<br>`urlStrings` (array, **required**) |
| `confluence_create_page` | Create a new Confluence page with specified title, parent, body content, and space. Returns the created content object. | `title` (string, **required**)<br>`body` (string, **required**)<br>`parentId` (string, **required**)<br>`space` (string, **required**) |
| `confluence_download_attachment` | Download an attachment file from Confluence to a specified directory. | `attachment` (object, **required**)<br>`targetDir` (object, **required**) |
| `confluence_download_pages` | Download Confluence pages and their attachments to a local folder. Recursively follows linked pages, children macros, and internal ac:link references up to the specified depth. | `urlStrings` (array, **required**)<br>`depth` (number, optional)<br>`downloadAttachments` (boolean, optional)<br>`outputPath` (string, **required**) |
| `confluence_find_content` | Find a Confluence page by title in the default space. Returns the page content if found. Use format=md to convert body.storage.value to Markdown. | `title` (string, **required**)<br>`format` (string, optional) |
| `confluence_find_content_by_title_and_space` | Find Confluence content by title and space key. Returns the first matching content or null if not found. Use format=md to convert body.storage.value to Markdown. | `title` (string, **required**)<br>`format` (string, optional)<br>`space` (string, **required**) |
| `confluence_find_or_create` | Find a Confluence page by title in the default space, or create it if it doesn't exist. Returns the found or created content. | `title` (string, **required**)<br>`body` (string, **required**)<br>`parentId` (string, **required**) |
| `confluence_get_children_by_id` | Get child pages of a Confluence page by content ID. Returns a list of child content objects. Use format=md to convert body.storage.value to Markdown. | `contentId` (string, **required**)<br>`format` (string, optional) |
| `confluence_get_children_by_name` | Get child pages of a Confluence page by space key and content name. Returns a list of child content objects. Use format=md to convert body.storage.value to Markdown. | `spaceKey` (string, **required**)<br>`format` (string, optional)<br>`contentName` (string, **required**) |
| `confluence_get_content_attachments` | Get all attachments for a specific Confluence content. Returns a list of attachment objects with metadata. | `contentId` (string, **required**) |
| `confluence_get_current_user_profile` | Get the current user's profile information from Confluence. Returns user details for the authenticated user. | None |
| `confluence_get_user_profile_by_id` | Get a specific user's profile information from Confluence by user ID. Returns user details for the specified user. | `userId` (string, **required**) |
| `confluence_search_content_by_text` | Search Confluence content by text query using CQL (Confluence Query Language). Returns search results with content excerpts. Default limit is 20 if not specified. | `limit` (number, optional)<br>`query` (string, **required**) |
| `confluence_test` | Test Confluence connectivity by fetching the current user's profile | None |
| `confluence_update_page` | Update an existing Confluence page with new title, parent, body content, and space. Returns the updated content object. | `contentId` (string, **required**)<br>`title` (string, **required**)<br>`body` (string, **required**)<br>`parentId` (string, **required**)<br>`space` (string, **required**) |
| `confluence_update_page_with_history` | Update an existing Confluence page with new content and add a history comment. Returns the updated content object. | `contentId` (string, **required**)<br>`title` (string, **required**)<br>`body` (string, **required**)<br>`parentId` (string, **required**)<br>`space` (string, **required**)<br>`historyComment` (string, **required**) |

## Detailed Parameter Information

### `confluence_content_by_id`

Get Confluence content by its unique content ID. Returns detailed content information including body, version, and metadata. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`contentId`** (string) 🔴 Required
  - The unique content ID of the Confluence page
  - Example: `123456`

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

**Example:**
```bash
dmtools confluence_content_by_id "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_content_by_id("contentId", "format");
```

---

### `confluence_content_by_title`

Get Confluence content by title in the default space. Returns content result with metadata and body information. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`title`** (string) 🔴 Required
  - Title of the Confluence page to get
  - Example: `Project Documentation`

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

**Example:**
```bash
dmtools confluence_content_by_title "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_content_by_title("title", "format");
```

---

### `confluence_content_by_title_and_space`

Get Confluence content by title and space key. Returns content result with metadata and body information. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`title`** (string) 🔴 Required
  - The title of the Confluence page
  - Example: `Project Documentation`

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

- **`space`** (string) 🔴 Required
  - The space key where the content is located
  - Example: `PROJ`

**Example:**
```bash
dmtools confluence_content_by_title_and_space "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_content_by_title_and_space("title", "format");
```

---

### `confluence_contents_by_urls`

Get Confluence content by multiple URLs. Returns a list of content objects for each valid URL. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

- **`urlStrings`** (array) 🔴 Required
  - Array of Confluence URLs to retrieve content from
  - Example: `['https://confluence.example.com/wiki/spaces/SPACE/pages/123/Page+Title']`

**Example:**
```bash
dmtools confluence_contents_by_urls "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_contents_by_urls("format", "urlStrings");
```

---

### `confluence_create_page`

Create a new Confluence page with specified title, parent, body content, and space. Returns the created content object.

**Parameters:**

- **`title`** (string) 🔴 Required
  - The title of the new page
  - Example: `New Project Page`

- **`body`** (string) 🔴 Required
  - The body content of the page in Confluence storage format
  - Example: `<p>This is the page content.</p>`

- **`parentId`** (string) 🔴 Required
  - The ID of the parent page
  - Example: `123456`

- **`space`** (string) 🔴 Required
  - The space key where to create the page
  - Example: `PROJ`

**Example:**
```bash
dmtools confluence_create_page "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_create_page("title", "body");
```

---

### `confluence_download_attachment`

Download an attachment file from Confluence to a specified directory.

**Parameters:**

- **`attachment`** (object) 🔴 Required
  - The attachment object to download

- **`targetDir`** (object) 🔴 Required
  - The target directory to save the file
  - Example: `/path/to/directory`

**Example:**
```bash
dmtools confluence_download_attachment "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_download_attachment("attachment", "targetDir");
```

---

### `confluence_download_pages`

Download Confluence pages and their attachments to a local folder. Recursively follows linked pages, children macros, and internal ac:link references up to the specified depth.

**Parameters:**

- **`urlStrings`** (array) 🔴 Required
  - Array of Confluence page URLs to download
  - Example: `['https://wiki.example.com/wiki/spaces/SPACE/pages/123/Page']`

- **`depth`** (number) ⚪ Optional
  - How many levels of linked/child pages to follow. Default is 1.
  - Example: `1`

- **`downloadAttachments`** (boolean) ⚪ Optional
  - Whether to download page attachments. Default is true.
  - Example: `true`

- **`outputPath`** (string) 🔴 Required
  - Local folder path where pages and attachments will be saved
  - Example: `/tmp/confluence-pages`

**Example:**
```bash
dmtools confluence_download_pages "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_download_pages("urlStrings", "depth");
```

---

### `confluence_find_content`

Find a Confluence page by title in the default space. Returns the page content if found. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`title`** (string) 🔴 Required
  - Title of the Confluence page to find
  - Example: `Project Documentation`

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

**Example:**
```bash
dmtools confluence_find_content "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_find_content("title", "format");
```

---

### `confluence_find_content_by_title_and_space`

Find Confluence content by title and space key. Returns the first matching content or null if not found. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`title`** (string) 🔴 Required
  - The title of the content to find
  - Example: `Project Documentation`

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

- **`space`** (string) 🔴 Required
  - The space key where to search for the content
  - Example: `PROJ`

**Example:**
```bash
dmtools confluence_find_content_by_title_and_space "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_find_content_by_title_and_space("title", "format");
```

---

### `confluence_find_or_create`

Find a Confluence page by title in the default space, or create it if it doesn't exist. Returns the found or created content.

**Parameters:**

- **`title`** (string) 🔴 Required
  - Title of the page to find or create
  - Example: `Project Documentation`

- **`body`** (string) 🔴 Required
  - Body content for the new page (if creation is needed)
  - Example: `<p>This is the page content.</p>`

- **`parentId`** (string) 🔴 Required
  - ID of the parent page for creation
  - Example: `123456`

**Example:**
```bash
dmtools confluence_find_or_create "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_find_or_create("title", "body");
```

---

### `confluence_get_children_by_id`

Get child pages of a Confluence page by content ID. Returns a list of child content objects. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`contentId`** (string) 🔴 Required
  - The content ID of the parent page
  - Example: `123456`

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

**Example:**
```bash
dmtools confluence_get_children_by_id "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_get_children_by_id("contentId", "format");
```

---

### `confluence_get_children_by_name`

Get child pages of a Confluence page by space key and content name. Returns a list of child content objects. Use format=md to convert body.storage.value to Markdown.

**Parameters:**

- **`spaceKey`** (string) 🔴 Required
  - The space key where the parent page is located
  - Example: `PROJ`

- **`format`** (string) ⚪ Optional
  - Output format for the page body. Use 'md' or 'markdown' to convert Confluence storage format to Markdown.
  - Example: `md`

- **`contentName`** (string) 🔴 Required
  - The name/title of the parent page
  - Example: `Project Documentation`

**Example:**
```bash
dmtools confluence_get_children_by_name "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_get_children_by_name("spaceKey", "format");
```

---

### `confluence_get_content_attachments`

Get all attachments for a specific Confluence content. Returns a list of attachment objects with metadata.

**Parameters:**

- **`contentId`** (string) 🔴 Required
  - The content ID to get attachments for
  - Example: `123456`

**Example:**
```bash
dmtools confluence_get_content_attachments "value"
```

```javascript
// In JavaScript agent
const result = confluence_get_content_attachments("contentId");
```

---

### `confluence_get_current_user_profile`

Get the current user's profile information from Confluence. Returns user details for the authenticated user.

**Parameters:** None

**Example:**
```bash
dmtools confluence_get_current_user_profile
```

```javascript
// In JavaScript agent
const result = confluence_get_current_user_profile();
```

---

### `confluence_get_user_profile_by_id`

Get a specific user's profile information from Confluence by user ID. Returns user details for the specified user.

**Parameters:**

- **`userId`** (string) 🔴 Required
  - The account ID of the user to get profile for
  - Example: `123456:abcdef-1234-5678-90ab-cdef12345678`

**Example:**
```bash
dmtools confluence_get_user_profile_by_id "value"
```

```javascript
// In JavaScript agent
const result = confluence_get_user_profile_by_id("userId");
```

---

### `confluence_search_content_by_text`

Search Confluence content by text query using CQL (Confluence Query Language). Returns search results with content excerpts. Default limit is 20 if not specified.

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of search results to return. Default is 20 if not provided.
  - Example: `10`

- **`query`** (string) 🔴 Required
  - Search query text to find in Confluence content
  - Example: `project documentation`

**Example:**
```bash
dmtools confluence_search_content_by_text "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_search_content_by_text("limit", "query");
```

---

### `confluence_test`

Test Confluence connectivity by fetching the current user's profile

**Parameters:** None

**Example:**
```bash
dmtools confluence_test
```

```javascript
// In JavaScript agent
const result = confluence_test();
```

---

### `confluence_update_page`

Update an existing Confluence page with new title, parent, body content, and space. Returns the updated content object.

**Parameters:**

- **`contentId`** (string) 🔴 Required
  - The ID of the page to update
  - Example: `123456`

- **`title`** (string) 🔴 Required
  - The new title for the page
  - Example: `Updated Project Page`

- **`body`** (string) 🔴 Required
  - The new body content of the page in Confluence storage format
  - Example: `<p>This is the updated page content.</p>`

- **`parentId`** (string) 🔴 Required
  - The ID of the new parent page
  - Example: `123456`

- **`space`** (string) 🔴 Required
  - The space key where the page is located
  - Example: `PROJ`

**Example:**
```bash
dmtools confluence_update_page "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_update_page("contentId", "title");
```

---

### `confluence_update_page_with_history`

Update an existing Confluence page with new content and add a history comment. Returns the updated content object.

**Parameters:**

- **`contentId`** (string) 🔴 Required
  - The ID of the page to update
  - Example: `123456`

- **`title`** (string) 🔴 Required
  - The new title for the page
  - Example: `Updated Project Page`

- **`body`** (string) 🔴 Required
  - The new body content of the page in Confluence storage format
  - Example: `<p>This is the updated page content.</p>`

- **`parentId`** (string) 🔴 Required
  - The ID of the new parent page
  - Example: `123456`

- **`space`** (string) 🔴 Required
  - The space key where the page is located
  - Example: `PROJ`

- **`historyComment`** (string) 🔴 Required
  - Comment to add to the page history
  - Example: `Updated content based on user feedback`

**Example:**
```bash
dmtools confluence_update_page_with_history "value" "value"
```

```javascript
// In JavaScript agent
const result = confluence_update_page_with_history("contentId", "title");
```

---

