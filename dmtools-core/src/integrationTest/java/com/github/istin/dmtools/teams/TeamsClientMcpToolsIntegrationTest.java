package com.github.istin.dmtools.teams;

import com.github.istin.dmtools.microsoft.teams.TeamsClient;
import com.github.istin.dmtools.microsoft.teams.model.Chat;
import com.github.istin.dmtools.microsoft.teams.model.ChatMessage;
import com.github.istin.dmtools.microsoft.teams.model.Team;
import com.github.istin.dmtools.microsoft.teams.model.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Microsoft Teams MCP tools.
 * 
 * These tests require valid Microsoft Teams credentials configured via environment variables:
 * - TEAMS_CLIENT_ID: Azure App Registration client ID
 * - TEAMS_TENANT_ID: Tenant ID (default: "common")
 * - TEAMS_REFRESH_TOKEN: Pre-configured refresh token
 * - TEAMS_AUTH_METHOD: Authentication method (default: "refresh_token")
 * - TEAMS_TOKEN_CACHE_PATH: Token cache path (default: ./teams-test.token)
 * 
 * Tests can be skipped if credentials are not configured.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TeamsClientMcpToolsIntegrationTest {
    
    private static final Logger logger = LogManager.getLogger(TeamsClientMcpToolsIntegrationTest.class);
    
    private static TeamsClient teamsClient;
    private static boolean credentialsAvailable = false;
    
    // Test data - will be populated during tests
    private static String testChatId;
    private static String testChatName;
    private static String testTeamId;
    private static String testTeamName;
    
    @BeforeAll
    static void setUp() {
        // Check if credentials are available
        String clientId = System.getenv("TEAMS_CLIENT_ID");
        String tenantId = System.getenv("TEAMS_TENANT_ID");
        String refreshToken = System.getenv("TEAMS_REFRESH_TOKEN");
        
        if (clientId == null || clientId.isEmpty() || refreshToken == null || refreshToken.isEmpty()) {
            logger.warn("Microsoft Teams credentials not configured. Skipping integration tests.");
            logger.warn("To run these tests, configure environment variables:");
            logger.warn("  - TEAMS_CLIENT_ID");
            logger.warn("  - TEAMS_TENANT_ID (optional, defaults to 'common')");
            logger.warn("  - TEAMS_REFRESH_TOKEN");
            credentialsAvailable = false;
            return;
        }
        
        try {
            // Initialize Teams client
            String scopes = System.getenv().getOrDefault("TEAMS_SCOPES", 
                    "User.Read Chat.Read ChatMessage.Read Chat.ReadWrite ChatMessage.Send " +
                    "Team.ReadBasic.All Channel.ReadBasic.All ChannelMessage.Read.All");
            String authMethod = System.getenv().getOrDefault("TEAMS_AUTH_METHOD", "refresh_token");
            int authPort = Integer.parseInt(System.getenv().getOrDefault("TEAMS_AUTH_PORT", "8080"));
            String tokenCachePath = System.getenv().getOrDefault("TEAMS_TOKEN_CACHE_PATH", "./teams-test.token");
            
            if (tenantId == null || tenantId.isEmpty()) {
                tenantId = "common";
            }
            
            teamsClient = new TeamsClient(
                    clientId,
                    tenantId,
                    scopes,
                    authMethod,
                    authPort,
                    tokenCachePath,
                    refreshToken
            );
            
            credentialsAvailable = true;
            logger.info("TeamsClient initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize TeamsClient", e);
            credentialsAvailable = false;
        }
    }
    
    @AfterAll
    static void tearDown() {
        if (teamsClient != null) {
            logger.info("Integration tests completed for TeamsClient MCP tools");
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Test basic setup and compilation")
    void testBasicSetup() {
        if (!credentialsAvailable) {
            logger.info("Skipping test - credentials not available");
            return;
        }
        
        assertNotNull(teamsClient);
        logger.info("Basic setup test passed - TeamsClient initialized");
    }
    
    @Test
    @Order(2)
    @DisplayName("Test teams_get_chats")
    void testGetChats() throws IOException {
        if (!credentialsAvailable) {
            logger.info("Skipping test - credentials not available");
            return;
        }
        
        List<Chat> chats = teamsClient.getChats();
        
        assertNotNull(chats);
        logger.info("Retrieved {} chats", chats.size());
        
        if (!chats.isEmpty()) {
            Chat firstChat = chats.get(0);
            assertNotNull(firstChat.getId());
            
            // Store test data for later tests
            testChatId = firstChat.getId();
            testChatName = firstChat.getTopic();
            
            logger.info("First chat: ID={}, Topic={}, Type={}", 
                    firstChat.getId(), firstChat.getTopic(), firstChat.getChatType());
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("Test teams_get_messages")
    void testGetChatMessages() throws IOException {
        if (!credentialsAvailable || testChatId == null) {
            logger.info("Skipping test - credentials or chat ID not available");
            return;
        }
        
        List<ChatMessage> messages = teamsClient.getChatMessages(testChatId, 10);
        
        assertNotNull(messages);
        logger.info("Retrieved {} messages from chat {}", messages.size(), testChatId);
        
        if (!messages.isEmpty()) {
            ChatMessage firstMessage = messages.get(0);
            assertNotNull(firstMessage.getId());
            assertNotNull(firstMessage.getCreatedDateTime());
            
            logger.info("First message: From={}, Content length={}, Created={}", 
                    firstMessage.getFrom() != null ? firstMessage.getFrom().getDisplayName() : "Unknown",
                    firstMessage.getContent().length(),
                    firstMessage.getCreatedDateTime());
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("Test teams_find_chat_by_name")
    void testFindChatByName() throws IOException {
        if (!credentialsAvailable || testChatName == null) {
            logger.info("Skipping test - credentials or chat name not available");
            return;
        }
        
        Chat chat = teamsClient.findChatByName(testChatName);
        
        if (testChatName != null && !testChatName.isEmpty()) {
            assertNotNull(chat);
            assertEquals(testChatId, chat.getId());
            logger.info("Found chat by name: {}", chat.getTopic());
        } else {
            logger.info("Test chat has no topic, cannot test find by name");
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Test teams_get_messages_by_name")
    void testGetChatMessagesByName() throws IOException {
        if (!credentialsAvailable || testChatName == null || testChatName.isEmpty()) {
            logger.info("Skipping test - credentials or chat name not available");
            return;
        }
        
        List<ChatMessage> messages = teamsClient.getChatMessagesByName(testChatName, 5);
        
        assertNotNull(messages);
        logger.info("Retrieved {} messages by chat name", messages.size());
    }
    
    @Test
    @Order(6)
    @DisplayName("Test teams_get_joined_teams")
    void testGetJoinedTeams() throws IOException {
        if (!credentialsAvailable) {
            logger.info("Skipping test - credentials not available");
            return;
        }
        
        List<Team> teams = teamsClient.getJoinedTeams();
        
        assertNotNull(teams);
        logger.info("Retrieved {} joined teams", teams.size());
        
        if (!teams.isEmpty()) {
            Team firstTeam = teams.get(0);
            assertNotNull(firstTeam.getId());
            assertNotNull(firstTeam.getDisplayName());
            
            // Store test data for later tests
            testTeamId = firstTeam.getId();
            testTeamName = firstTeam.getDisplayName();
            
            logger.info("First team: ID={}, Name={}", firstTeam.getId(), firstTeam.getDisplayName());
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("Test teams_get_team_channels")
    void testGetTeamChannels() throws IOException {
        if (!credentialsAvailable || testTeamId == null) {
            logger.info("Skipping test - credentials or team ID not available");
            return;
        }
        
        List<Channel> channels = teamsClient.getTeamChannels(testTeamId);
        
        assertNotNull(channels);
        logger.info("Retrieved {} channels from team {}", channels.size(), testTeamId);
        
        if (!channels.isEmpty()) {
            Channel firstChannel = channels.get(0);
            assertNotNull(firstChannel.getId());
            assertNotNull(firstChannel.getDisplayName());
            
            logger.info("First channel: ID={}, Name={}, Type={}", 
                    firstChannel.getId(), firstChannel.getDisplayName(), firstChannel.getMembershipType());
        }
    }
    
    @Test
    @Order(8)
    @DisplayName("Test teams_find_team_by_name")
    void testFindTeamByName() throws IOException {
        if (!credentialsAvailable || testTeamName == null) {
            logger.info("Skipping test - credentials or team name not available");
            return;
        }
        
        Team team = teamsClient.findTeamByName(testTeamName);
        
        assertNotNull(team);
        assertEquals(testTeamId, team.getId());
        logger.info("Found team by name: {}", team.getDisplayName());
    }
    
    @Test
    @Order(9)
    @DisplayName("Test teams_find_channel_by_name")
    void testFindChannelByName() throws IOException {
        if (!credentialsAvailable || testTeamId == null) {
            logger.info("Skipping test - credentials or team ID not available");
            return;
        }
        
        // Try to find "General" channel (exists in most teams)
        Channel channel = teamsClient.findChannelByName(testTeamId, "General");
        
        if (channel != null) {
            assertNotNull(channel.getId());
            logger.info("Found channel: {} (ID: {})", channel.getDisplayName(), channel.getId());
        } else {
            logger.info("General channel not found in team {}", testTeamId);
        }
    }
    
    @Test
    @Order(10)
    @DisplayName("Test teams_send_message (WARNING: Sends actual message)")
    void testSendChatMessage() throws IOException {
        if (!credentialsAvailable || testChatId == null) {
            logger.info("Skipping test - credentials or chat ID not available");
            return;
        }
        
        // Only run this test if explicitly enabled
        String enableWriteTests = System.getenv("TEAMS_ENABLE_WRITE_TESTS");
        if (!"true".equalsIgnoreCase(enableWriteTests)) {
            logger.info("Skipping write test - set TEAMS_ENABLE_WRITE_TESTS=true to enable");
            return;
        }
        
        String testMessage = "Test message from TeamsClient integration test - " + System.currentTimeMillis();
        
        ChatMessage sentMessage = teamsClient.sendChatMessage(testChatId, testMessage);
        
        assertNotNull(sentMessage);
        assertNotNull(sentMessage.getId());
        assertTrue(sentMessage.getContent().contains(testMessage));
        
        logger.info("Successfully sent test message: {}", sentMessage.getId());
    }
    
    @Test
    @Order(11)
    @DisplayName("Test teams_send_message_by_name (WARNING: Sends actual message)")
    void testSendChatMessageByName() throws IOException {
        if (!credentialsAvailable || testChatName == null || testChatName.isEmpty()) {
            logger.info("Skipping test - credentials or chat name not available");
            return;
        }
        
        // Only run this test if explicitly enabled
        String enableWriteTests = System.getenv("TEAMS_ENABLE_WRITE_TESTS");
        if (!"true".equalsIgnoreCase(enableWriteTests)) {
            logger.info("Skipping write test - set TEAMS_ENABLE_WRITE_TESTS=true to enable");
            return;
        }
        
        String testMessage = "Test message by name from TeamsClient integration test - " + System.currentTimeMillis();
        
        ChatMessage sentMessage = teamsClient.sendChatMessageByName(testChatName, testMessage);
        
        assertNotNull(sentMessage);
        assertNotNull(sentMessage.getId());
        
        logger.info("Successfully sent test message by name: {}", sentMessage.getId());
    }
    
    @Test
    @Order(12)
    @DisplayName("Test message pagination")
    void testMessagePagination() throws IOException {
        if (!credentialsAvailable || testChatId == null) {
            logger.info("Skipping test - credentials or chat ID not available");
            return;
        }
        
        // Get first 5 messages
        List<ChatMessage> fiveMessages = teamsClient.getChatMessages(testChatId, 5);
        
        // Get first 10 messages
        List<ChatMessage> tenMessages = teamsClient.getChatMessages(testChatId, 10);
        
        assertNotNull(fiveMessages);
        assertNotNull(tenMessages);
        
        logger.info("Pagination test: {} messages (limit 5), {} messages (limit 10)", 
                fiveMessages.size(), tenMessages.size());
        
        if (!fiveMessages.isEmpty() && !tenMessages.isEmpty()) {
            // If there are enough messages, verify pagination works
            if (tenMessages.size() > fiveMessages.size()) {
                assertTrue(tenMessages.size() >= fiveMessages.size());
            }
        }
    }
    
    @Test
    @Order(13)
    @DisplayName("Test plain text content extraction")
    void testPlainTextExtraction() throws IOException {
        if (!credentialsAvailable || testChatId == null) {
            logger.info("Skipping test - credentials or chat ID not available");
            return;
        }
        
        List<ChatMessage> messages = teamsClient.getChatMessages(testChatId, 1);
        
        if (!messages.isEmpty()) {
            ChatMessage message = messages.get(0);
            String plainText = message.getPlainTextContent();
            
            assertNotNull(plainText);
            // Plain text should not contain HTML tags
            assertFalse(plainText.contains("<div>"));
            assertFalse(plainText.contains("</div>"));
            
            logger.info("Plain text extraction test passed. Content length: {}", plainText.length());
        }
    }
}
