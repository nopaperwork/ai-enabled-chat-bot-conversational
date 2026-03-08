package com.nopaper.work.voicebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Conversational chat service using Spring AI with Ollama (open-source LLM).
 * Maintains per-session conversation history using ChatMemory + MessageChatMemoryAdvisor.
 * Each session (identified by conversationId) gets its own sliding window of message history.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Value("${voicebot.chat.system-prompt}")
    private String systemPrompt;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        // In-memory conversation store (per-session message history)
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(50) // Keep last 50 messages per conversation
                .build();

        // Wire the memory advisor into the ChatClient so all prompts
        // automatically load/save conversation history
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * Send a message to the LLM within a conversation session.
     * The conversation history is automatically managed by the ChatMemoryAdvisor.
     *
     * @param conversationId unique session identifier (e.g., WebSocket session ID or HTTP session ID)
     * @param userMessage    the user's message
     * @return the assistant's response
     */
    public String chat(String conversationId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "I didn't catch that. Could you please repeat?";
        }

        log.info("[{}] Chat request: {}", conversationId, userMessage);

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        log.info("[{}] Chat response: {}", conversationId, response);
        return response;
    }

    /**
     * Clear conversation history for a session.
     */
    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
        log.info("[{}] Conversation history cleared", conversationId);
    }
}
