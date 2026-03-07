package com.nopaper.work.voicebot.model;

public record TranscriptionResult(
        String text,
        boolean partial
) {
    public static TranscriptionResult of(String text) {
        return new TranscriptionResult(text, false);
    }

    public static TranscriptionResult partial(String text) {
        return new TranscriptionResult(text, true);
    }
}
