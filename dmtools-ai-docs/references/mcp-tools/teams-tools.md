# TEAMS MCP Tools

**Total Tools**: 31

## Quick Reference

```bash
# List all teams tools
dmtools list | jq '.tools[] | select(.name | startswith("teams_"))'

# Example usage
dmtools teams_test [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for teams tools
const result = teams_test(...);
const result = teams_chats_raw(...);
const result = teams_chats(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `teams_auth_complete` | Complete device code authentication after user approval. Polls for tokens and saves refresh token. | None |
| `teams_auth_start` | Start device code authentication for Microsoft Teams. Returns URL and code for user to authenticate. | None |
| `teams_auth_status` | Check current Microsoft Teams authentication status and configuration. | None |
| `teams_chat_by_name_raw` | Find a chat by topic/name or participant name (case-insensitive partial match). Works for group chats and 1-on-1 chats. (returns raw JSON) | `chatName` (string, **required**) |
| `teams_chats` | List chats showing only chat/contact names, last message (truncated to 100 chars), and date | `limit` (number, optional) |
| `teams_chats_raw` | List chats for the current user with topic, type, and participant information (returns raw JSON) | `limit` (number, optional) |
| `teams_download_file` | Download a file from Teams (Graph API hostedContents or SharePoint sharing URL). Auto-detects URL type and uses appropriate method. | `url` (string, **required**)<br>`outputPath` (string, **required**) |
| `teams_download_recording_transcript` | Download transcript (VTT) file from a Teams recording using SharePoint API. Requires driveId, itemId, and transcriptId. | `itemId` (string, **required**)<br>`driveId` (string, **required**)<br>`outputPath` (string, **required**)<br>`transcriptId` (string, **required**) |
| `teams_extract_transcript_from_sharepoint` | Extract transcript information by parsing SharePoint HTML page. Useful for finding transcript IDs. | `webUrl` (string, **required**) |
| `teams_find_channel_by_name_raw` | Find a channel by name within a team (case-insensitive partial match) (returns raw JSON) | `channelName` (string, **required**)<br>`teamId` (string, **required**) |
| `teams_find_team_by_name_raw` | Find a team by display name (case-insensitive partial match) (returns raw JSON) | `teamName` (string, **required**) |
| `teams_get_call_transcripts` | Get transcripts for a call/meeting using Call Records API. Returns list of transcripts with download URLs. | `callId` (string, **required**) |
| `teams_get_channel_messages_by_name_raw` | Get messages from a channel by team and channel names (returns raw JSON) | `teamName` (string, **required**)<br>`limit` (number, optional)<br>`channelName` (string, **required**) |
| `teams_get_joined_teams_raw` | List teams the user is a member of (returns raw JSON) | None |
| `teams_get_message_hosted_contents` | Get hosted contents (files/transcripts) for a specific message. Returns list of files with download URLs. | `chatId` (string, **required**)<br>`messageId` (string, **required**) |
| `teams_get_recording_transcripts` | Get transcript metadata for a recording file. Returns list of available transcripts with download URLs. | `itemId` (string, **required**)<br>`driveId` (string, **required**) |
| `teams_get_team_channels_raw` | Get channels in a specific team (returns raw JSON) | `teamId` (string, **required**) |
| `teams_list_recording_transcripts` | List available transcripts for a recording file. Returns transcript IDs that can be downloaded. | `itemId` (string, **required**)<br>`driveId` (string, **required**) |
| `teams_messages` | Get messages from a chat with simplified output showing only: author, body, date, reactions, mentions, and attachments | `limit` (number, optional)<br>`chatName` (string, **required**)<br>`sorting` (string, optional) |
| `teams_messages_by_chat_id_raw` | Get messages from a chat by ID with optional server-side filtering. Use $filter syntax with lastModifiedDateTime: 'lastModifiedDateTime gt 2025-01-01T00:00:00Z' (returns raw JSON). Note: createdDateTime is not supported in filters. | `chatId` (string, **required**)<br>`limit` (number, optional)<br>`filter` (string, optional) |
| `teams_messages_raw` | Get messages from a chat by name (combines find + get messages) (returns raw JSON) | `limit` (number, optional)<br>`chatName` (string, **required**) |
| `teams_messages_since` | Get messages from a chat by name starting from a specific date (ISO 8601 format). Returns simplified format. Uses smart pagination with early exit for performance. | `chatName` (string, **required**)<br>`sinceDate` (string, **required**)<br>`sorting` (string, optional) |
| `teams_messages_since_by_id` | Get messages from a chat starting from a specific date (ISO 8601 format, e.g., '2025-10-08T00:00:00Z'). Returns simplified format. Uses smart pagination with early exit for performance. | `chatId` (string, **required**)<br>`sinceDate` (string, **required**)<br>`sorting` (string, optional) |
| `teams_myself_messages` | Get messages from your personal self chat (notes to yourself) with simplified output | `limit` (number, optional) |
| `teams_myself_messages_raw` | Get messages from your personal self chat (notes to yourself) with full raw data | `limit` (number, optional) |
| `teams_recent_chats` | Get recent chats sorted by last activity showing chat/contact names, last message with author, and date. Shows 'new: true' for unread messages. Filter by type: 'oneOnOne' for 1-on-1 chats, 'group' for group chats, 'meeting' for meeting chats, or 'all' (default). Only shows chats with activity in the last 90 days. | `limit` (number, optional)<br>`chatType` (string, optional) |
| `teams_search_user_drive_files` | Search for files in a user's OneDrive (e.g., meeting transcripts/recordings). Returns list of files with download URLs. | `userId` (string, **required**)<br>`searchQuery` (string, **required**) |
| `teams_send_message` | Send a message to a chat by name or participant name (finds chat, then sends message) | `chatName` (string, **required**)<br>`content` (string, **required**) |
| `teams_send_message_by_id` | Send a message to a chat by ID (returns raw JSON) | `chatId` (string, **required**)<br>`content` (string, **required**) |
| `teams_send_myself_message` | Send a message to your personal self chat (notes to yourself) | `content` (string, **required**) |
| `teams_test` | Test Microsoft Teams connectivity by fetching the current user's profile | None |

## Detailed Parameter Information

### `teams_auth_complete`

Complete device code authentication after user approval. Polls for tokens and saves refresh token.

**Parameters:** None

**Example:**
```bash
dmtools teams_auth_complete
```

```javascript
// In JavaScript agent
const result = teams_auth_complete();
```

---

### `teams_auth_start`

Start device code authentication for Microsoft Teams. Returns URL and code for user to authenticate.

**Parameters:** None

**Example:**
```bash
dmtools teams_auth_start
```

```javascript
// In JavaScript agent
const result = teams_auth_start();
```

---

### `teams_auth_status`

Check current Microsoft Teams authentication status and configuration.

**Parameters:** None

**Example:**
```bash
dmtools teams_auth_status
```

```javascript
// In JavaScript agent
const result = teams_auth_status();
```

---

### `teams_chat_by_name_raw`

Find a chat by topic/name or participant name (case-insensitive partial match). Works for group chats and 1-on-1 chats. (returns raw JSON)

**Parameters:**

- **`chatName`** (string) 🔴 Required
  - The chat topic or participant name to search for

**Example:**
```bash
dmtools teams_chat_by_name_raw "value"
```

```javascript
// In JavaScript agent
const result = teams_chat_by_name_raw("chatName");
```

---

### `teams_chats`

List chats showing only chat/contact names, last message (truncated to 100 chars), and date

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of chats (0 for all, default: 50)
  - Example: `50`

**Example:**
```bash
dmtools teams_chats "value"
```

```javascript
// In JavaScript agent
const result = teams_chats("limit");
```

---

### `teams_chats_raw`

List chats for the current user with topic, type, and participant information (returns raw JSON)

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of chats (0 for all, default: 50)
  - Example: `50`

**Example:**
```bash
dmtools teams_chats_raw "value"
```

```javascript
// In JavaScript agent
const result = teams_chats_raw("limit");
```

---

### `teams_download_file`

Download a file from Teams (Graph API hostedContents or SharePoint sharing URL). Auto-detects URL type and uses appropriate method.

**Parameters:**

- **`url`** (string) 🔴 Required
  - Graph API URL or SharePoint sharing URL
  - Example: `https://graph.microsoft.com/v1.0/chats/.../hostedContents/.../$value`

- **`outputPath`** (string) 🔴 Required
  - Local file path to save to
  - Example: `/tmp/file.ext`

**Example:**
```bash
dmtools teams_download_file "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_download_file("url", "outputPath");
```

---

### `teams_download_recording_transcript`

Download transcript (VTT) file from a Teams recording using SharePoint API. Requires driveId, itemId, and transcriptId.

**Parameters:**

- **`itemId`** (string) 🔴 Required
  - Recording item ID

- **`driveId`** (string) 🔴 Required
  - Drive ID

- **`outputPath`** (string) 🔴 Required
  - Local file path to save

- **`transcriptId`** (string) 🔴 Required
  - Transcript ID (UUID)

**Example:**
```bash
dmtools teams_download_recording_transcript "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_download_recording_transcript("itemId", "driveId");
```

---

### `teams_extract_transcript_from_sharepoint`

Extract transcript information by parsing SharePoint HTML page. Useful for finding transcript IDs.

**Parameters:**

- **`webUrl`** (string) 🔴 Required
  - SharePoint webUrl of the recording

**Example:**
```bash
dmtools teams_extract_transcript_from_sharepoint "value"
```

```javascript
// In JavaScript agent
const result = teams_extract_transcript_from_sharepoint("webUrl");
```

---

### `teams_find_channel_by_name_raw`

Find a channel by name within a team (case-insensitive partial match) (returns raw JSON)

**Parameters:**

- **`channelName`** (string) 🔴 Required
  - The channel name to search for

- **`teamId`** (string) 🔴 Required
  - The team ID

**Example:**
```bash
dmtools teams_find_channel_by_name_raw "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_find_channel_by_name_raw("channelName", "teamId");
```

---

### `teams_find_team_by_name_raw`

Find a team by display name (case-insensitive partial match) (returns raw JSON)

**Parameters:**

- **`teamName`** (string) 🔴 Required
  - The team name to search for

**Example:**
```bash
dmtools teams_find_team_by_name_raw "value"
```

```javascript
// In JavaScript agent
const result = teams_find_team_by_name_raw("teamName");
```

---

### `teams_get_call_transcripts`

Get transcripts for a call/meeting using Call Records API. Returns list of transcripts with download URLs.

**Parameters:**

- **`callId`** (string) 🔴 Required
  - Call ID from the meeting

**Example:**
```bash
dmtools teams_get_call_transcripts "value"
```

```javascript
// In JavaScript agent
const result = teams_get_call_transcripts("callId");
```

---

### `teams_get_channel_messages_by_name_raw`

Get messages from a channel by team and channel names (returns raw JSON)

**Parameters:**

- **`teamName`** (string) 🔴 Required
  - The team name to search for

- **`limit`** (number) ⚪ Optional
  - Maximum number of messages (0 for all, default: 100)
  - Example: `100`

- **`channelName`** (string) 🔴 Required
  - The channel name to search for

**Example:**
```bash
dmtools teams_get_channel_messages_by_name_raw "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_get_channel_messages_by_name_raw("teamName", "limit");
```

---

### `teams_get_joined_teams_raw`

List teams the user is a member of (returns raw JSON)

**Parameters:** None

**Example:**
```bash
dmtools teams_get_joined_teams_raw
```

```javascript
// In JavaScript agent
const result = teams_get_joined_teams_raw();
```

---

### `teams_get_message_hosted_contents`

Get hosted contents (files/transcripts) for a specific message. Returns list of files with download URLs.

**Parameters:**

- **`chatId`** (string) 🔴 Required
  - Chat ID

- **`messageId`** (string) 🔴 Required
  - Message ID

**Example:**
```bash
dmtools teams_get_message_hosted_contents "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_get_message_hosted_contents("chatId", "messageId");
```

---

### `teams_get_recording_transcripts`

Get transcript metadata for a recording file. Returns list of available transcripts with download URLs.

**Parameters:**

- **`itemId`** (string) 🔴 Required
  - Item ID of the recording file

- **`driveId`** (string) 🔴 Required
  - Drive ID from the recording file

**Example:**
```bash
dmtools teams_get_recording_transcripts "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_get_recording_transcripts("itemId", "driveId");
```

---

### `teams_get_team_channels_raw`

Get channels in a specific team (returns raw JSON)

**Parameters:**

- **`teamId`** (string) 🔴 Required
  - The team ID

**Example:**
```bash
dmtools teams_get_team_channels_raw "value"
```

```javascript
// In JavaScript agent
const result = teams_get_team_channels_raw("teamId");
```

---

### `teams_list_recording_transcripts`

List available transcripts for a recording file. Returns transcript IDs that can be downloaded.

**Parameters:**

- **`itemId`** (string) 🔴 Required
  - Recording item ID

- **`driveId`** (string) 🔴 Required
  - Drive ID

**Example:**
```bash
dmtools teams_list_recording_transcripts "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_list_recording_transcripts("itemId", "driveId");
```

---

### `teams_messages`

Get messages from a chat with simplified output showing only: author, body, date, reactions, mentions, and attachments

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of messages (0 for all, default: 100)
  - Example: `100`

- **`chatName`** (string) 🔴 Required
  - The chat name to search for

- **`sorting`** (string) ⚪ Optional
  - Sort order: 'asc' for oldest first, 'desc' for newest first (default: 'desc')
  - Example: `desc`

**Example:**
```bash
dmtools teams_messages "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_messages("limit", "chatName");
```

---

### `teams_messages_by_chat_id_raw`

Get messages from a chat by ID with optional server-side filtering. Use $filter syntax with lastModifiedDateTime: 'lastModifiedDateTime gt 2025-01-01T00:00:00Z' (returns raw JSON). Note: createdDateTime is not supported in filters.

**Parameters:**

- **`chatId`** (string) 🔴 Required
  - The chat ID

- **`limit`** (number) ⚪ Optional
  - Maximum number of messages (0 for all, default: 100)
  - Example: `100`

- **`filter`** (string) ⚪ Optional
  - Optional OData filter (e.g., 'lastModifiedDateTime gt 2025-01-01T00:00:00Z')
  - Example: `lastModifiedDateTime gt 2025-01-01T00:00:00Z`

**Example:**
```bash
dmtools teams_messages_by_chat_id_raw "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_messages_by_chat_id_raw("chatId", "limit");
```

---

### `teams_messages_raw`

Get messages from a chat by name (combines find + get messages) (returns raw JSON)

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of messages (0 for all, default: 100)
  - Example: `100`

- **`chatName`** (string) 🔴 Required
  - The chat name to search for

**Example:**
```bash
dmtools teams_messages_raw "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_messages_raw("limit", "chatName");
```

---

### `teams_messages_since`

Get messages from a chat by name starting from a specific date (ISO 8601 format). Returns simplified format. Uses smart pagination with early exit for performance.

**Parameters:**

- **`chatName`** (string) 🔴 Required
  - The chat name to search for

- **`sinceDate`** (string) 🔴 Required
  - ISO 8601 date string (e.g., '2025-10-08T00:00:00Z')
  - Example: `2025-10-08T00:00:00Z`

- **`sorting`** (string) ⚪ Optional
  - Sort order: 'asc' for oldest first, 'desc' for newest first (default: 'desc')
  - Example: `desc`

**Example:**
```bash
dmtools teams_messages_since "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_messages_since("chatName", "sinceDate");
```

---

### `teams_messages_since_by_id`

Get messages from a chat starting from a specific date (ISO 8601 format, e.g., '2025-10-08T00:00:00Z'). Returns simplified format. Uses smart pagination with early exit for performance.

**Parameters:**

- **`chatId`** (string) 🔴 Required
  - The chat ID

- **`sinceDate`** (string) 🔴 Required
  - ISO 8601 date string (e.g., '2025-10-08T00:00:00Z')
  - Example: `2025-10-08T00:00:00Z`

- **`sorting`** (string) ⚪ Optional
  - Sort order: 'asc' for oldest first, 'desc' for newest first (default: 'desc')
  - Example: `desc`

**Example:**
```bash
dmtools teams_messages_since_by_id "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_messages_since_by_id("chatId", "sinceDate");
```

---

### `teams_myself_messages`

Get messages from your personal self chat (notes to yourself) with simplified output

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of messages (0 for all, default: 100)
  - Example: `100`

**Example:**
```bash
dmtools teams_myself_messages "value"
```

```javascript
// In JavaScript agent
const result = teams_myself_messages("limit");
```

---

### `teams_myself_messages_raw`

Get messages from your personal self chat (notes to yourself) with full raw data

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of messages (0 for all, default: 100)
  - Example: `100`

**Example:**
```bash
dmtools teams_myself_messages_raw "value"
```

```javascript
// In JavaScript agent
const result = teams_myself_messages_raw("limit");
```

---

### `teams_recent_chats`

Get recent chats sorted by last activity showing chat/contact names, last message with author, and date. Shows 'new: true' for unread messages. Filter by type: 'oneOnOne' for 1-on-1 chats, 'group' for group chats, 'meeting' for meeting chats, or 'all' (default). Only shows chats with activity in the last 90 days.

**Parameters:**

- **`limit`** (number) ⚪ Optional
  - Maximum number of recent chats (0 for all, default: 50)
  - Example: `50`

- **`chatType`** (string) ⚪ Optional
  - Filter by chat type: 'oneOnOne', 'group', 'meeting', or 'all' (default: 'all')
  - Example: `oneOnOne`

**Example:**
```bash
dmtools teams_recent_chats "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_recent_chats("limit", "chatType");
```

---

### `teams_search_user_drive_files`

Search for files in a user's OneDrive (e.g., meeting transcripts/recordings). Returns list of files with download URLs.

**Parameters:**

- **`userId`** (string) 🔴 Required
  - User ID to search OneDrive

- **`searchQuery`** (string) 🔴 Required
  - Search term (meeting name, 'transcript', '.vtt', etc.)

**Example:**
```bash
dmtools teams_search_user_drive_files "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_search_user_drive_files("userId", "searchQuery");
```

---

### `teams_send_message`

Send a message to a chat by name or participant name (finds chat, then sends message)

**Parameters:**

- **`chatName`** (string) 🔴 Required
  - The chat topic or participant name

- **`content`** (string) 🔴 Required
  - Message content (plain text or HTML)

**Example:**
```bash
dmtools teams_send_message "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_send_message("chatName", "content");
```

---

### `teams_send_message_by_id`

Send a message to a chat by ID (returns raw JSON)

**Parameters:**

- **`chatId`** (string) 🔴 Required
  - The chat ID

- **`content`** (string) 🔴 Required
  - Message content (plain text or HTML)

**Example:**
```bash
dmtools teams_send_message_by_id "value" "value"
```

```javascript
// In JavaScript agent
const result = teams_send_message_by_id("chatId", "content");
```

---

### `teams_send_myself_message`

Send a message to your personal self chat (notes to yourself)

**Parameters:**

- **`content`** (string) 🔴 Required
  - Message content (plain text or HTML)

**Example:**
```bash
dmtools teams_send_myself_message "value"
```

```javascript
// In JavaScript agent
const result = teams_send_myself_message("content");
```

---

### `teams_test`

Test Microsoft Teams connectivity by fetching the current user's profile

**Parameters:** None

**Example:**
```bash
dmtools teams_test
```

```javascript
// In JavaScript agent
const result = teams_test();
```

---

