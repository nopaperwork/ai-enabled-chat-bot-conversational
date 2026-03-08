package com.nopaper.work.voicebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Text-to-Speech service using openedai-speech (Piper TTS backend).
 * Calls an OpenAI-compatible /v1/audio/speech endpoint served locally.
 */
@Service
public class TextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechService.class);

    private final RestClient restClient;

    @Value("${voicebot.tts.base-url}")
    private String baseUrl;

    @Value("${voicebot.tts.voice}")
    private String voice;

    @Value("${voicebot.tts.model}")
    private String model;

    @Value("${voicebot.tts.response-format}")
    private String responseFormat;

    @Value("${voicebot.tts.speed}")
    private double speed;

    public TextToSpeechService(RestClient ttsRestClient) {
        this.restClient = ttsRestClient;
    }

    /**
     * Convert text to speech audio bytes (WAV format).
     */
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text must not be empty");
        }

        log.info("Synthesizing speech for text: {}...", text.substring(0, Math.min(50, text.length())));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text,
                "voice", voice,
                "response_format", responseFormat,
                "speed", speed
        );

        byte[] audioBytes = restClient.post()
                .uri(baseUrl + "/v1/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(byte[].class);

        if (audioBytes == null || audioBytes.length == 0) {
            throw new RuntimeException("TTS service returned empty audio");
        }

        log.info("Generated {} bytes of audio", audioBytes.length);
        return audioBytes;
    }
}
