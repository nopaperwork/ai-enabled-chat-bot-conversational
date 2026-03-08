package com.nopaper.work.voicebot.model;

/**
 * WebSocket message envelope for voice chat communication.
 */
public record VoiceWebSocketMessage(
        String type,
        String text,
        String audioBase64
) {
    public static VoiceWebSocketMessage transcription(String text) {
        return new VoiceWebSocketMessage("transcription", text, null);
    }

    public static VoiceWebSocketMessage chatResponse(String text) {
        return new VoiceWebSocketMessage("chat_response", text, null);
    }

    public static VoiceWebSocketMessage audioResponse(String text, String audioBase64) {
        return new VoiceWebSocketMessage("audio_response", text, audioBase64);
    }

    public static VoiceWebSocketMessage error(String message) {
        return new VoiceWebSocketMessage("error", message, null);
    }

    public static VoiceWebSocketMessage status(String message) {
        return new VoiceWebSocketMessage("status", message, null);
    }
}
