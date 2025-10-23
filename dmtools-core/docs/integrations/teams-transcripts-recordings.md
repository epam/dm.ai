# Microsoft Teams Transcripts and Recordings

## Overview

DMTools provides MCP tools to find and download call transcripts and recordings from Microsoft Teams meetings. Recording and transcript information appears in the simplified message output automatically.

## Quick Start

### Finding Recordings and Transcripts

Recordings and transcripts are automatically included in message output when using simplified message tools:

```bash
# Get messages from a meeting chat
dmtools teams_messages "Meeting Chat Name" 100
```

**Output includes recording/transcript entries:**
```json
[
  {
    "date": "2025-10-16T12:58:00.094Z",
    "type": "transcript",
    "title": "Meeting Transcript",
    "hostedContentsUrl": "https://graph.microsoft.com/v1.0/chats/19:meeting_.../messages/1760619480094/hostedContents",
    "chatId": "19:meeting_...",
    "transcriptICalUid": "040000008200E00074C5B7101A82E0080000000091CB0806D239DC01...",
    "messageId": "1760619480094"
  },
  {
    "date": "2025-10-16T12:51:14.498Z",
    "type": "recording",
    "title": "AI.M-20251016_125114UTC-Meeting Recording.mp4",
    "duration": "PT47.0144064S",
    "url": "https://epam-my.sharepoint.com/:v:/p/ira_skrypnik/EXWM1FsAo-1PpSkyFHzGyqEBJKftYUcb6aQShdRdKUQLw"
  }
]
```

### Downloading Recordings

Use `teams_download_file` to download recordings. This tool auto-detects URL type (SharePoint vs Graph API) and uses the appropriate method:

**Syntax:**
```bash
dmtools teams_download_file <url> <outputPath>
```

**Parameters:**
- `url` - SharePoint sharing URL or Graph API hostedContents URL
- `outputPath` - Local file path to save to

**Examples:**
```bash
# Download SharePoint recording (auto-detected and delegated to SharePoint client)
dmtools teams_download_file \
  "https://epam-my.sharepoint.com/:v:/p/user/..." \
  "/tmp/meeting-recording.mp4"

# Download Graph API hosted content
dmtools teams_download_file \
  "https://graph.microsoft.com/v1.0/chats/19:meeting_.../messages/.../hostedContents/.../\$value" \
  "/tmp/recording-chunk.mp4"
```

### Downloading Transcripts

Transcripts require a multi-step process due to SharePoint REST API requirements:

#### Step 1: Extract Transcript Information

```bash
dmtools teams_extract_transcript_from_sharepoint "https://epam-my.sharepoint.com/:v:/p/user/..."
```

This parses the SharePoint HTML page and extracts:
- `transcriptId` (UUID)
- `driveId`
- `itemId`

#### Step 2: Download Transcript

```bash
dmtools teams_download_recording_transcript \
  "driveId" \
  "itemId" \
  "transcriptId" \
  "/tmp/transcript.vtt"
```

This uses the SharePoint REST API v2.1 endpoint to download the transcript file.

## Available Tools Reference

### Core Tools

- **`teams_messages`** - Get simplified messages including recordings/transcripts
- **`teams_download_file`** - Download recordings (auto-detects SharePoint vs Graph API URLs)
- **`teams_extract_transcript_from_sharepoint`** - Parse SharePoint page to extract transcript IDs
- **`teams_download_recording_transcript`** - Download transcript using SharePoint REST API

### Additional Tools (Advanced)

- **`teams_get_message_hosted_contents`** - Get hosted contents for a specific message
- **`teams_get_call_transcripts`** - Get transcripts via Call Records API (requires `CallRecords.Read.All`)
- **`teams_get_recording_transcripts`** - Get transcript metadata for a recording
- **`teams_list_recording_transcripts`** - List available transcripts for a recording
- **`teams_search_user_drive_files`** - Search for files in user's OneDrive

### SharePoint Integration Tools

- **`sharepoint_get_drive_item`** - Get DriveItem metadata from sharing URL
- **`sharepoint_download_file`** - Download file from SharePoint sharing URL

## Important Limitations

### Transcripts

**Current Implementation:**
- Transcripts are downloaded via **SharePoint REST API v2.1**
- This requires `Sites.Read.All` permission (with admin consent)
- The `teams_extract_transcript_from_sharepoint` tool parses the SharePoint HTML page to extract transcript IDs
- The `teams_download_recording_transcript` tool constructs the SharePoint REST API URL and downloads the `.vtt` file

**Alternative Method (Requires Admin Consent):**
- Microsoft Graph API provides `/communications/callRecords/{callId}/transcripts` endpoint
- This requires the `CallRecords.Read.All` permission
- This permission **requires admin consent** in Azure Active Directory
- Most deployments use the SharePoint REST API method instead

**Fallback (Manual Access):**
If programmatic access is blocked:
1. Open the meeting chat in Microsoft Teams
2. Click on the meeting details
3. View/download the transcript from the Teams UI

### Recordings

**Access Methods:**
1. **SharePoint Sharing URLs** (Recommended)
   - Format: `https://company-my.sharepoint.com/:v:/...`
   - **✅ CAN be downloaded programmatically** using `teams_download_file` or `sharepoint_download_file`
   - Uses Microsoft Graph API `/shares/{encoded}/driveItem` endpoint
   - Auto-detected by `teams_download_file` and delegated to SharePoint client
   - **Limitation**: May be blocked if tenant has `blockDownload` policy enabled
   - **Fallback**: Open the URL in a browser and download manually if blocked

2. **Graph API Hosted Content URLs**
   - Format: `https://graph.microsoft.com/v1.0/chats/.../messages/.../hostedContents/.../$value`
   - These URLs point to recording chunks during meeting
   - Can be downloaded using `teams_download_file` with Teams API authentication
   - **Limitation**: May only contain partial recordings (chunks), not the full recording

## Complete Workflow Examples

### Example 1: Find and Download Recording

```bash
# Step 1: Get messages from meeting chat (includes recordings/transcripts)
dmtools teams_messages "Weekly Standup" 100

# Output will include entries like:
# {
#   "type": "recording",
#   "title": "Meeting Recording.mp4",
#   "url": "https://epam-my.sharepoint.com/:v:/p/user/..."
# }

# Step 2: Download the recording using the URL from output
dmtools teams_download_file \
  "https://epam-my.sharepoint.com/:v:/p/user/..." \
  "/tmp/standup-recording.mp4"
```

### Example 2: Download Transcript

```bash
# Step 1: Get messages to find transcript entry
dmtools teams_messages "Weekly Standup" 100

# Output will include:
# {
#   "type": "transcript",
#   "title": "Meeting Transcript",
#   "hostedContentsUrl": "https://graph.microsoft.com/v1.0/chats/...",
#   "chatId": "19:meeting_...",
#   "messageId": "1760619480094"
# }

# Step 2: Extract transcript info from SharePoint page
# (Use the recording URL from the same meeting)
dmtools teams_extract_transcript_from_sharepoint \
  "https://epam-my.sharepoint.com/:v:/p/user/..."

# Output:
# {
#   "success": true,
#   "transcriptId": "793dca08-063c-4a82-96b2-fa6abd6f1006",
#   "driveId": "b!TvcQBiJtlk-dInIt7Cr1OyAVESMTej5IoTgGhWigokPud4CxeC1GRqgC7Q74lVlg",
#   "itemId": "01IHGOLEFX4QZRVSCIJNAKIINVI6OVF2JR"
# }

# Step 3: Download transcript
dmtools teams_download_recording_transcript \
  "b!TvcQBiJtlk-dInIt7Cr1OyAVESMTej5IoTgGhWigokPud4CxeC1GRqgC7Q74lVlg" \
  "01IHGOLEFX4QZRVSCIJNAKIINVI6OVF2JR" \
  "793dca08-063c-4a82-96b2-fa6abd6f1006" \
  "/tmp/transcript.vtt"
```

### Example 3: Using SharePoint Tools Directly

```bash
# Get metadata for a SharePoint file
dmtools sharepoint_get_drive_item \
  "https://company-my.sharepoint.com/:v:/p/user/..."

# Download SharePoint file directly
dmtools sharepoint_download_file \
  "https://company-my.sharepoint.com/:v:/p/user/..." \
  "/tmp/recording.mp4"
```

## Permissions Summary

| Feature | API Endpoint | Required Permission | Admin Consent Required |
|---------|--------------|---------------------|------------------------|
| Find recording/transcript events | `/me/chats/{id}/messages` | `Chat.Read` | No |
| Download Graph API hosted content | `/chats/.../messages/.../hostedContents/.../$value` | `Chat.Read` | No |
| Get SharePoint drive item metadata | `/shares/{encoded}/driveItem` | `Sites.Read.All` | **Yes** (delegated) |
| Download SharePoint recordings | `/shares/{encoded}/driveItem/content` | `Sites.Read.All` | **Yes** (delegated) |
| Download SharePoint transcripts (REST API) | `/_api/v2.1/drives/{id}/items/{id}/media/transcripts/{id}/streamContent` | `Sites.Read.All` | **Yes** (delegated) |
| List call transcripts (Graph API) | `/communications/callRecords/{callId}/transcripts` | `CallRecords.Read.All` | **Yes** (admin only) |
| Download transcript content (Graph API) | `/communications/callRecords/{callId}/transcripts/{id}/content` | `CallRecords.Read.All` | **Yes** (admin only) |

## Recommended Approach

### With Admin Consent (`Sites.Read.All`)

This is the **recommended approach** and provides full programmatic access:

1. **Find recordings/transcripts**: Use `teams_messages` to get chat messages (includes recordings/transcripts)
2. **Download recordings**: Use `teams_download_file` with SharePoint URL (auto-delegates to SharePoint client)
3. **Download transcripts**:
   - Use `teams_extract_transcript_from_sharepoint` to get transcript IDs from SharePoint page
   - Use `teams_download_recording_transcript` to download transcript using SharePoint REST API

### Without Admin Consent

For basic chat access without admin approval:

1. **Find recordings/transcripts**: Use `teams_messages` to see recording/transcript metadata
2. **For recordings**: Click SharePoint URL in browser to view/download manually
3. **For transcripts**: Open meeting chat in Teams UI to view/download manually

### Alternative (Requires Additional Admin Consent)

If you have `CallRecords.Read.All` permission (rarely granted):
- Use `teams_get_call_transcripts` for Graph API-based transcript access
- This requires additional admin approval beyond `Sites.Read.All`

