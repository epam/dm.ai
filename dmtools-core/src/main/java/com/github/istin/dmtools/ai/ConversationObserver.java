package com.github.istin.dmtools.ai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ConversationObserver {

    private static final Logger logger = LogManager.getLogger(ConversationObserver.class);

    public static class Message {

        private String author;
        private String text;

        public Message(String author, String text) {
            this.text = text;
            this.author = author;
        }

        public String getAuthor() {
            return author;
        }

        public String getText() {
            return text;
        }
    }

    private List<Message> messages = new ArrayList<>();

    public void addMessage(Message message) {
        messages.add(message);
    }

    public List<Message> getMessages() {
        return messages;
    }

    public String printAndClear() {
        StringBuilder stringBuffer = new StringBuilder();
        for (Message message : messages) {
            stringBuffer.append(message.getAuthor()).append("\n").append(message.getText()).append("\n").append("=================\n");
            logger.info(stringBuffer);
        }
        messages.clear();
        return stringBuffer.toString();
    }

}
