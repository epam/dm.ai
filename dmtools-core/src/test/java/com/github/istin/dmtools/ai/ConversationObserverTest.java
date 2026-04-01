// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ConversationObserverTest {

    private ConversationObserver conversationObserver;

    @Before
    public void setUp() {
        conversationObserver = new ConversationObserver();
    }

    @Test
    public void testAddMessage() {
        ConversationObserver.Message message = new ConversationObserver.Message("Author1", "Text1");
        conversationObserver.addMessage(message);

        List<ConversationObserver.Message> messages = conversationObserver.getMessages();
        assertEquals(1, messages.size());
        assertEquals("Author1", messages.get(0).getAuthor());
        assertEquals("Text1", messages.get(0).getText());
    }

    @Test
    public void testGetMessages() {
        ConversationObserver.Message message1 = new ConversationObserver.Message("Author1", "Text1");
        ConversationObserver.Message message2 = new ConversationObserver.Message("Author2", "Text2");

        conversationObserver.addMessage(message1);
        conversationObserver.addMessage(message2);

        List<ConversationObserver.Message> messages = conversationObserver.getMessages();
        assertEquals(2, messages.size());
        assertEquals("Author1", messages.get(0).getAuthor());
        assertEquals("Text1", messages.get(0).getText());
        assertEquals("Author2", messages.get(1).getAuthor());
        assertEquals("Text2", messages.get(1).getText());
    }

    @Test
    public void testPrintAndClear() {
        ConversationObserver.Message message1 = new ConversationObserver.Message("Author1", "Text1");
        ConversationObserver.Message message2 = new ConversationObserver.Message("Author2", "Text2");

        conversationObserver.addMessage(message1);
        conversationObserver.addMessage(message2);

        String expectedOutput = "Author1\nText1\n=================\nAuthor2\nText2\n=================\n";
        String actualOutput = conversationObserver.printAndClear();

        assertEquals(expectedOutput, actualOutput);
        assertTrue(conversationObserver.getMessages().isEmpty());
    }
}