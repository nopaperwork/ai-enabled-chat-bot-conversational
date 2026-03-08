package com.nopaper.work.voicebot.model;

public record ChatRequest(
        String message,
        String sessionId
) {}
