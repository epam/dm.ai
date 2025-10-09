# Microsoft Teams Transcripts and Recordings

## Overview

DMTools provides MCP tools to find and download call transcripts and recordings from Microsoft Teams meetings.

## Available Tools

### 1. `teams_find_transcripts`

Finds call transcripts and recordings in a chat.

**Syntax:**
```bash
dmtools teams_find_transcripts <chatIdOrName>
```

**Parameters:**
- `chatIdOrName` - Chat ID or chat name to search

**Example:**
```bash
dmtools teams_find_transcripts "AI/Run R&D x AI Champions: Insights & Ideas"
```

**Output:**
```json
{
  "chatId": "19:meeting_...",
  "transcripts": [
    {
      "messageId": "1759930378623",
      "chatId": "19:meeting_...",
      "createdDateTime": "2025-10-08T13:32:58.623Z",
      "callTranscriptICalUid": "040000008200E00074C5B7101A82E00807E90A0820250E630C32DC01...",
      "organizerId": "f8996b8d-f980-4fd9-aba1-94b063ec52d6"
    }
  ],
  "recordings": [
    {
      "messageId": "1759934163545",
      "chatId": "19:meeting_...",
      "createdDateTime": "2025-10-08T14:36:03.545Z",
      "recordingUrl": "https://epam-my.sharepoint.com/:v:/p/user/...",
      "displayName": "Meeting Recording.mp4",
      "duration": "PT1H21.7S",
      "callId": "bb159952-cc39-4229-b39e-e0f21ddb7b17"
    }
  ],
  "transcriptCount": 1,
  "recordingCount": 1
}
```

### 2. `teams_download_file`

Downloads a file from a URL (recording, hosted content, etc.) to a local file path.

**Syntax:**
```bash
dmtools teams_download_file <url> <outputPath>
```

**Parameters:**
- `url` - URL to download from
- `outputPath` - Local file path to save to

**Example:**
```bash
dmtools teams_download_file \
  "https://graph.microsoft.com/v1.0/chats/19:meeting_.../messages/.../hostedContents/.../\$value" \
  "/tmp/meeting-recording.mp4"
```

## Important Limitations

### Transcripts

**Access Method:**
- Transcripts are accessed via the Microsoft Graph API: `GET /communications/callRecords/{callId}/transcripts`
- This requires the `CallRecords.Read.All` permission
- This permission **requires admin consent** in Azure Active Directory

**Current Implementation:**
- The `teams_find_transcripts` tool identifies transcript events and returns the `callTranscriptICalUid`
- To actually download transcript content, you would need:
  1. Admin to grant `CallRecords.Read.All` permission
  2. Use the callId to query: `GET /communications/callRecords/{callId}/transcripts`
  3. Download the transcript using the transcript ID

**Workaround:**
If admin consent is not available, transcripts can be accessed manually:
1. Open the meeting chat in Microsoft Teams
2. Click on the meeting details
3. View/download the transcript from the Teams UI

### Recordings

**Access Methods:**
1. **SharePoint URLs** (e.g., `https://company-my.sharepoint.com/:v:/...`)
   - These URLs are provided for recordings stored in SharePoint
   - **✅ CAN be downloaded programmatically** using the new `sharepoint_download_file` tool
   - Uses Microsoft Graph API `/shares/{encoded}/driveItem` endpoint
   - **Limitation**: May be blocked if tenant has `blockDownload` policy enabled
   - **Fallback**: Open the URL in a browser and download manually if blocked

2. **Graph API Hosted Content URLs** (e.g., `https://graph.microsoft.com/v1.0/chats/.../messages/.../hostedContents/.../$value`)
   - These URLs are provided for recording chunks during meeting
   - Can be downloaded using the `teams_download_file` tool with Teams API authentication
   - **Limitation**: May only contain partial recordings (chunks)

## SharePoint Tools (NEW!)

### `sharepoint_get_drive_item`

Gets DriveItem metadata from a SharePoint sharing URL.

**Syntax:**
```bash
dmtools sharepoint_get_drive_item <sharingUrl>
```

**Example:**
```bash
dmtools sharepoint_get_drive_item "https://company-my.sharepoint.com/:v:/p/user/..."
```

**Output:** JSON with file metadata including name, size, download URLs, and drive/item IDs.

### `sharepoint_download_file`

Downloads a file from a SharePoint sharing URL.

**Syntax:**
```bash
dmtools sharepoint_download_file <sharingUrl> <outputPath>
```

**Example:**
```bash
dmtools sharepoint_download_file \
  "https://company-my.sharepoint.com/:v:/p/user/..." \
  "/tmp/recording.mp4"
```

**How it works:**
1. Encodes the sharing URL to base64url format
2. Calls Microsoft Graph API `/shares/{encoded}/driveItem`
3. Downloads using direct download URL or drive/item content endpoint
4. Saves to specified local path

**Limitations:**
- May be blocked if tenant has `blockDownload` policy
- Requires `Files.Read.All` or `Sites.Read.All` permissions
- Falls back to browser download if blocked

## Complete Workflow Example

```bash
# Step 1: Get recent chats to find meetings
dmtools teams_get_recent_chats 10

# Step 2: Find transcripts and recordings in a specific chat
dmtools teams_find_transcripts "My Meeting Chat Name"

# Step 3: Download SharePoint recording (NEW!)
dmtools sharepoint_download_file \
  "https://company-my.sharepoint.com/:v:/p/user/..." \
  "/tmp/meeting-recording.mp4"

# OR download Graph API hosted content
dmtools teams_download_file \
  "https://graph.microsoft.com/v1.0/chats/19:meeting_.../messages/.../hostedContents/.../$value" \
  "/tmp/meeting-recording-chunk.mp4"
```

## Permissions Summary

| Feature | API Endpoint | Required Permission | Admin Consent Required |
|---------|--------------|---------------------|------------------------|
| Find recording events | `/me/chats/{id}/messages` | `Chat.Read` | No |
| Download Graph API hosted content | `/chats/.../messages/.../hostedContents/.../$value` | `Chat.Read` | No |
| Get SharePoint drive item metadata | `/shares/{encoded}/driveItem` | `Files.Read.All` or `Sites.Read.All` | **Yes** (delegated) |
| Download SharePoint files | `/shares/{encoded}/driveItem` + download | `Files.Read.All` or `Sites.Read.All` | **Yes** (delegated) |
| List call transcripts | `/communications/callRecords/{callId}/transcripts` | `CallRecords.Read.All` | **Yes** |
| Download transcript content | `/communications/callRecords/{callId}/transcripts/{id}/content` | `CallRecords.Read.All` | **Yes** |

## Recommended Approach

For most use cases without admin access:

1. **Use `teams_find_transcripts`** to discover recordings and transcript metadata
2. **For recordings with SharePoint URLs**: Open in browser and download manually
3. **For recordings with Graph API URLs**: Use `teams_download_file` to download programmatically
4. **For transcripts**: Open the meeting chat in Teams UI to view/download manually

If you have admin access and need programmatic transcript access, work with your Azure AD admin to grant `CallRecords.Read.All` permission.

