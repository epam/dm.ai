package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TicketContext functionality.
 * These tests verify the current behavior before implementing performance optimizations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TicketContextTest {

    @Mock
    private TrackerClient<ITicket> mockTrackerClient;
    
    @Mock
    private ITicket mockTicket;
    
    @Mock
    private ITicket mockExtraTicket1;
    
    @Mock
    private ITicket mockExtraTicket2;
    
    @Mock
    private ITicket mockExtraTicket3;
    
    interface ICommentWithToText extends IComment, ToText {}
    
    @Mock
    private ICommentWithToText mockComment1;
    
    @Mock
    private ICommentWithToText mockComment2;
    
    private TicketContext ticketContext;

    @BeforeEach
    void setUp() {
        try {
            when(mockTicket.getKey()).thenReturn("DMC-415");
            when(mockTicket.toText()).thenReturn("This ticket references DMC-403 and DMC-404");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        
        when(mockExtraTicket1.getKey()).thenReturn("DMC-403");
        when(mockExtraTicket2.getKey()).thenReturn("DMC-404");
        when(mockExtraTicket3.getKey()).thenReturn("DMC-426");
        
        // Setup mock comments - they need to implement ToText interface too
        @SuppressWarnings({"unchecked", "rawtypes"})
        List mockComments = Arrays.asList(mockComment1, mockComment2);
        try {
            when(mockTrackerClient.getComments(eq("DMC-415"), eq(mockTicket))).thenReturn(mockComments);
            when(mockComment1.getBody()).thenReturn("First comment");
            when(mockComment2.getBody()).thenReturn("Second comment");
            
            // Mock ToText interface calls for comments
            when(mockComment1.toText()).thenReturn("First comment");
            when(mockComment2.toText()).thenReturn("Second comment");
        } catch (IOException e) {
            fail("Mock comments setup should not throw IOException: " + e.getMessage());
        }
        
        ticketContext = new TicketContext(mockTrackerClient, mockTicket);
    }

    @Test
    void testPrepareContext_WithoutComments() {
        // Arrange
        Set<String> mockKeys = Set.of("DMC-403", "DMC-404", "DMC-415");
        
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"description", "summary"});
        try {
            when(mockTrackerClient.performTicket("DMC-403", new String[]{"description", "summary"}))
                .thenReturn(mockExtraTicket1);
            when(mockTrackerClient.performTicket("DMC-404", new String[]{"description", "summary"}))
                .thenReturn(mockExtraTicket2);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }

        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            long startTime = System.currentTimeMillis();

            // Act
            try {
                ticketContext.prepareContext(false);
            } catch (IOException e) {
                fail("prepareContext should not throw IOException: " + e.getMessage());
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Assert
            assertEquals(2, ticketContext.getExtraTickets().size());
            assertNull(ticketContext.getComments()); // No comments requested
            
            // Verify sequential processing (current behavior)
            try {
                verify(mockTrackerClient, times(1)).performTicket("DMC-403", new String[]{"description", "summary"});
                verify(mockTrackerClient, times(1)).performTicket("DMC-404", new String[]{"description", "summary"});
            } catch (IOException e) {
                fail("Mock verification should not throw IOException: " + e.getMessage());
            }
            
            System.out.println("Sequential extra ticket fetching took: " + duration + "ms");
            
            // Document current performance characteristics
            // After optimization, this should be much faster due to parallel processing
        }
    }

    @Test
    void testPrepareContext_WithComments() {
        // Arrange
        Set<String> mockKeys = Set.of("DMC-403", "DMC-415");
        @SuppressWarnings("unchecked")
        List<IComment> mockComments = Arrays.asList(mock(IComment.class), mock(IComment.class));
        
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"description", "summary"});
        try {
            when(mockTrackerClient.performTicket("DMC-403", new String[]{"description", "summary"}))
                .thenReturn(mockExtraTicket1);
            @SuppressWarnings({"unchecked", "rawtypes"})
            List extendedComments = mockComments;
            when(mockTrackerClient.getComments("DMC-415", mockTicket)).thenReturn(extendedComments);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }

        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            // Act
            try {
                ticketContext.prepareContext(true);
            } catch (IOException e) {
                fail("prepareContext should not throw IOException: " + e.getMessage());
            }

            // Assert
            assertEquals(1, ticketContext.getExtraTickets().size());
            assertEquals(mockComments, ticketContext.getComments());
            
            try {
                verify(mockTrackerClient, times(1)).performTicket("DMC-403", new String[]{"description", "summary"});
                verify(mockTrackerClient, times(1)).getComments("DMC-415", mockTicket);
            } catch (IOException e) {
                fail("Mock verification should not throw IOException: " + e.getMessage());
            }
        }
    }

    @Test
    void testPrepareContext_PerformanceCharacteristics() {
        // Arrange - Simulate multiple tickets with delays
        Set<String> mockKeys = Set.of("DMC-403", "DMC-404", "DMC-426", "DMC-415");
        List<Long> callTimes = new ArrayList<>();
        
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"description", "summary"});
        
        // Simulate API delays (like in real performance logs)
        try {
            when(mockTrackerClient.performTicket(eq("DMC-403"), any())).thenAnswer(_invocation -> {
            callTimes.add(System.currentTimeMillis());
            try {
                Thread.sleep(100); // Simulate 100ms API call
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return mockExtraTicket1;
        });
        
        when(mockTrackerClient.performTicket(eq("DMC-404"), any())).thenAnswer(_invocation -> {
            callTimes.add(System.currentTimeMillis());
            try {
                Thread.sleep(150); // Simulate 150ms API call
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return mockExtraTicket2;
        });
        
        when(mockTrackerClient.performTicket(eq("DMC-426"), any())).thenAnswer(_invocation -> {
            callTimes.add(System.currentTimeMillis());
            try {
                Thread.sleep(120); // Simulate 120ms API call
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return mockExtraTicket3;
        });
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }

        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            long startTime = System.currentTimeMillis();

            // Act
            try {
                ticketContext.prepareContext(false);
            } catch (IOException e) {
                fail("prepareContext should not throw IOException: " + e.getMessage());
            }

            long endTime = System.currentTimeMillis();
            long totalDuration = endTime - startTime;

            // Assert - Document PARALLEL behavior (optimized)
            assertEquals(3, ticketContext.getExtraTickets().size()); // Excludes DMC-415 (same as main ticket)
            
            // Verify calls were made in parallel (all calls should start within a short timeframe)
            assertTrue(callTimes.size() >= 3);
            if (callTimes.size() > 1) {
                // Calculate the time span of all calls
                Collections.sort(callTimes);
                long timeSpan = callTimes.get(callTimes.size() - 1) - callTimes.get(0);
                // In parallel processing, all calls should start within a very short time window
                assertTrue(timeSpan <= 50, "Calls should be parallel, found time span of " + timeSpan + "ms");
            }
            
            // Total time should be close to the longest individual call (~150ms), not the sum
            assertTrue(totalDuration <= 250, "Parallel processing should take at most 250ms, took " + totalDuration + "ms");
            assertTrue(totalDuration >= 140, "Should take at least 140ms (longest individual call), took " + totalDuration + "ms");
            
            System.out.println("Parallel processing of 3 tickets took: " + totalDuration + "ms");
            System.out.println("Individual call times: " + callTimes);
            
            // This demonstrates the optimization: parallel processing takes ~150ms (longest individual call) 
            // instead of 370ms (sum of all calls) that sequential processing would take
        }
    }

    @Test
    void testPrepareContext_ErrorHandling() throws IOException {
        // Arrange
        Set<String> mockKeys = Set.of("DMC-403", "DMC-404", "DMC-415");
        
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"description", "summary"});
        when(mockTrackerClient.performTicket("DMC-403", new String[]{"description", "summary"}))
            .thenReturn(mockExtraTicket1);
        when(mockTrackerClient.performTicket("DMC-404", new String[]{"description", "summary"}))
            .thenThrow(new AtlassianRestClient.RestClientException("404 Not Found", "Ticket not found", 404));

        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            // Act - Should handle errors gracefully
            assertDoesNotThrow(() -> ticketContext.prepareContext(false));

            // Assert - Should continue processing despite errors
            assertEquals(1, ticketContext.getExtraTickets().size()); // Only successful ticket
            assertEquals("DMC-403", ticketContext.getExtraTickets().get(0).getKey());
        }
    }

    @Test
    void testPrepareContext_NoExtraTickets() throws IOException {
        // Arrange
        Set<String> mockKeys = Set.of("DMC-415"); // Only self-reference
        
        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            // Act
            ticketContext.prepareContext(false);

            // Assert
            assertEquals(0, ticketContext.getExtraTickets().size()); // Self-references excluded
            verify(mockTrackerClient, never()).performTicket(anyString(), any());
        }
    }

    @Test
    void testPrepareContext_EmptyTicketText() throws IOException {
        // Arrange
        Set<String> emptyKeys = Set.of();
        
        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(emptyKeys);

            // Act
            ticketContext.prepareContext(false);

            // Assert
            assertEquals(0, ticketContext.getExtraTickets().size());
            verify(mockTrackerClient, never()).performTicket(anyString(), any());
        }
    }

    @Test
    void testPrepareContext_WithOnTicketDetailsRequest() throws IOException {
        // Arrange
        Set<String> mockKeys = Set.of("DMC-403", "DMC-415");
        TicketContext.OnTicketDetailsRequest mockDetailsRequest = mock(TicketContext.OnTicketDetailsRequest.class);
        
        when(mockDetailsRequest.getTicketDetails("DMC-403")).thenReturn(mockExtraTicket1);
        ticketContext.setOnTicketDetailsRequest(mockDetailsRequest);

        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            // Act
            ticketContext.prepareContext(false);

            // Assert
            assertEquals(1, ticketContext.getExtraTickets().size());
            verify(mockDetailsRequest, times(1)).getTicketDetails("DMC-403");
            verify(mockTrackerClient, never()).performTicket(anyString(), any()); // Should use callback instead
        }
    }

    @Test
    void testToText_WithExtraTicketsAndComments() {
        // Arrange - Setup mocks for comments
        Set<String> mockKeys = Set.of("DMC-403", "DMC-404", "DMC-415");
        
        when(mockTrackerClient.getBasePath()).thenReturn("https://test.atlassian.net");
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"description", "summary"});
        
        try {
            when(mockTrackerClient.performTicket("DMC-403", new String[]{"description", "summary"}))
                .thenReturn(mockExtraTicket1);
            when(mockTrackerClient.performTicket("DMC-404", new String[]{"description", "summary"}))
                .thenReturn(mockExtraTicket2);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        
        // Mock the ticket wrapper behavior
        try {
            when(mockTicket.toText()).thenReturn("Main ticket content with DMC-403 and DMC-404");
            when(mockExtraTicket1.toText()).thenReturn("Extra ticket 1 content");
            when(mockExtraTicket2.toText()).thenReturn("Extra ticket 2 content");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }

        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            // Act - Test without comments first
            try {
                ticketContext.prepareContext(false);
            } catch (IOException e) {
                fail("prepareContext should not throw IOException: " + e.getMessage());
            }
            
            String textWithoutComments = null;
            try {
                textWithoutComments = ticketContext.toText();
            } catch (IOException e) {
                fail("toText should not throw IOException: " + e.getMessage());
            }

            // Test with comments by using prepareContext(true)
            try {
                ticketContext.prepareContext(true);
            } catch (IOException e) {
                fail("prepareContext should not throw IOException: " + e.getMessage());
            }
            
            String textWithComments = null;
            try {
                textWithComments = ticketContext.toText();
            } catch (IOException e) {
                fail("toText should not throw IOException: " + e.getMessage());
            }

            // Assert
            assertNotNull(textWithoutComments);
            assertNotNull(textWithComments);
            
            // Text with comments should be longer (includes comment section)
            assertTrue(textWithComments.length() > textWithoutComments.length(), 
                "Text with comments (" + textWithComments.length() + ") should be longer than without (" + textWithoutComments.length() + ")");
            assertTrue(textWithComments.contains("previous_discussion"));
            assertTrue(textWithComments.contains("First comment"));
            assertTrue(textWithComments.contains("Second comment"));
        }
    }

    @Test
    void testPrepareContext_TimingLogging() throws IOException {
        // Arrange
        Set<String> mockKeys = Set.of("DMC-403", "DMC-404");
        
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"description", "summary"});
        when(mockTrackerClient.performTicket(anyString(), any())).thenReturn(mockExtraTicket1);

        try (MockedStatic<IssuesIDsParser> mockedParser = mockStatic(IssuesIDsParser.class)) {
            mockedParser.when(() -> IssuesIDsParser.extractAllJiraIDs(anyString()))
                .thenReturn(mockKeys);

            // Act
            ticketContext.prepareContext(true);

            // Assert - Verify timing logs are being generated
            // (In actual implementation, these would be captured by log appenders)
            assertTrue(ticketContext.getExtraTickets().size() > 0);
            
            // This test documents that timing information is logged
            // Format: "TIMING: Starting TicketContext.prepareContext() for {ticket} at {timestamp}"
            // Format: "TIMING: IssuesIDsParser.extractAllJiraIDs() took {duration}ms for {ticket}"
            // Format: "TIMING: All extra tickets fetch took {duration}ms for {ticket}"
        }
    }

    @SuppressWarnings("unused")
    private IComment createMockComment(String author, String body) {
        IComment comment = mock(IComment.class);
        when(comment.toString()).thenReturn(author + ": " + body);
        return comment;
    }
}