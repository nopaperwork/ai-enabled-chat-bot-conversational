package com.nopaper.work.voicebot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.voicebot.model.TranscriptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Speech-to-Text service using faster-whisper-server (open-source, OpenAI-compatible).
 * Calls the /v1/audio/transcriptions endpoint exposed by faster-whisper-server (Docker).
 */
@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${voicebot.stt.base-url}")
    private String baseUrl;

    @Value("${voicebot.stt.model}")
    private String model;

    @Value("${voicebot.stt.language}")
    private String language;

    public SpeechToTextService(RestClient ttsRestClient) {
        this.restClient = ttsRestClient;
    }

    /**
     * Transcribe audio bytes (WAV format) to text via faster-whisper-server.
     */
    public TranscriptionResult transcribe(byte[] audioData) throws IOException {
        log.info("Sending {} bytes of audio to STT service", audioData.length);

        // Build multipart request matching OpenAI /v1/audio/transcriptions API
        String responseJson = restClient.post()
                .uri(baseUrl + "/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(buildMultipartBody(audioData))
                .retrieve()
                .body(String.class);

        if (responseJson == null || responseJson.isBlank()) {
            log.warn("STT service returned empty response");
            return TranscriptionResult.of("");
        }

        JsonNode node = objectMapper.readTree(responseJson);
        String text = node.path("text").asText("").trim();

        if (text.isEmpty()) {
            log.warn("No speech detected in audio");
            return TranscriptionResult.of("");
        }

        log.info("Transcribed: {}", text);
        return TranscriptionResult.of(text);
    }

    /**
     * Build a multipart form body for the OpenAI-compatible transcription API.
     */
    private org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> buildMultipartBody(byte[] audioData) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder.part("file", new ByteArrayResource(audioData) {
            @Override
            public String getFilename() {
                return "audio.wav";
            }
        }).header("Content-Type", "audio/wav");

        builder.part("model", model);
        builder.part("language", language);
        builder.part("response_format", "json");

        return builder.build();
    }
}
