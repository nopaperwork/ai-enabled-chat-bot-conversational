package com.nopaper.work.voicebot.model;

public record ConversationResponse(
        String transcribedText,
        String assistantText,
        byte[] audioResponse
) {}
