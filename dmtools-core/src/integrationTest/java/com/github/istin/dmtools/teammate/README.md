# Teammate Integration Tests

## TeammateAdoAttachmentIntegrationTest

Integration test suite for verifying Teammate's ADO work item processing and attachment download functionality.

### Purpose

Tests that Teammate can:
1. Fetch work items from Azure DevOps
2. Access work item descriptions
3. Download attachments to the `input/[WORK_ITEM_ID]/` folder
4. Create proper input context for CLI execution

### Configuration

Required environment variables or `dmtools.env` settings:

```bash
ADO_ORGANIZATION=RustemAgziamov
ADO_PROJECT=ai-native-sdlc-blueprint
ADO_PAT_TOKEN=your-personal-access-token
```

### Running Tests

```bash
# Run all Teammate integration tests
./gradlew :dmtools-core:integrationTest --tests "TeammateAdoAttachmentIntegrationTest"

# Run specific test
./gradlew :dmtools-core:integrationTest --tests "TeammateAdoAttachmentIntegrationTest.testWorkItem755HasDescription"
```

### Test Coverage

#### Test 1: `testWorkItem755HasDescription()`
- ✅ Fetches work item 755 from ADO
- ✅ Verifies work item has description
- ✅ Validates basic work item fields (ID, title, type)

#### Test 2: `testAttachmentsDownloadedToInputFolder()`
- ✅ Creates input context using `CliExecutionHelper`
- ✅ Verifies `input/755/` folder creation
- ✅ Verifies `request.md` file creation
- ✅ Downloads all attachments to input folder
- ✅ Validates downloaded file sizes
- ⚠️ Skips if work item has no attachments

#### Test 3: `testTeammateWorkflowWithAttachments()`
- ✅ Simulates full Teammate workflow
- ✅ Creates input context with work item data
- ✅ Verifies `request.md` contains work item details
- ✅ Validates attachment count matches work item

#### Test 4: `testDirectAttachmentDownload()`
- ✅ Tests direct download via `convertUrlToFile()`
- ✅ Validates downloaded file exists and is not empty
- ⚠️ Skips if work item has no attachments

### Test Results (Work Item 755)

```
✓ Work item 755 found with description (244 characters)
✓ Work item title: New Auth And Login Flow
✓ Work item type: User Story
✓ Input folder created: input/755/
✓ request.md created (303 bytes)
⚠ Work item 755 has 0 attachments - attachment tests skipped
```

### Testing with Attachments

To test attachment download functionality, modify the test to use a work item that has attachments:

```java
private static final String TEST_WORK_ITEM_ID = "123"; // Replace with work item that has attachments
```

### Cleanup

The test automatically cleans up the `input/755/` folder after completion in the `@AfterAll` method.

### Expected Folder Structure

After running tests with attachments:

```
input/
└── 755/
    ├── request.md              # Work item context
    ├── attachment1.png         # Downloaded attachment 1
    └── attachment2.pdf         # Downloaded attachment 2
```

### Implementation Details

Uses `CliExecutionHelper.createInputContext()` which:
1. Creates `input/[TICKET-KEY]/` directory
2. Writes `request.md` with input parameters
3. Downloads all attachments from `ticket.getAttachments()`
4. Sanitizes filenames (replaces `/` and `\` with `_`)
5. Uses `TrackerClient.convertUrlToFile()` for downloads

### Dependencies

- `BasicAzureDevOpsClient` - ADO client with PropertyReader configuration
- `CliExecutionHelper` - Handles input context creation and attachment download
- `WorkItem` - ADO work item model
- Apache Commons IO - File utilities

### Related Files

- [CliExecutionHelper.java](../../main/java/com/github/istin/dmtools/teammate/CliExecutionHelper.java) - Input context creation
- [AzureDevOpsClient.java](../../main/java/com/github/istin/dmtools/microsoft/ado/AzureDevOpsClient.java) - ADO integration
- [Teammate.java](../../main/java/com/github/istin/dmtools/teammate/Teammate.java) - Teammate agent implementation

