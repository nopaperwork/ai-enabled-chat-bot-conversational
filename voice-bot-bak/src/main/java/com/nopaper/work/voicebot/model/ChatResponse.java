package com.nopaper.work.voicebot.model;

public record ChatResponse(
        String userMessage,
        String assistantMessage
) {}
