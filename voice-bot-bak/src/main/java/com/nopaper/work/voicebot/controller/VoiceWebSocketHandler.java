package com.nopaper.work.voicebot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.voicebot.model.ConversationResponse;
import com.nopaper.work.voicebot.model.VoiceWebSocketMessage;
import com.nopaper.work.voicebot.service.VoicePipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time voice chat.
 * Accepts binary audio data, processes through the voice pipeline,
 * and returns text + audio responses.
 *
 * Protocol:
 * - Client sends text message: {"type": "start_recording"} to begin
 * - Client sends binary messages with audio chunks (PCM 16kHz 16-bit mono)
 * - Client sends text message: {"type": "stop_recording"} to process
 * - Server responds with text messages containing transcription, chat response, and audio
 */
@Component
public class VoiceWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketHandler.class);

    private final VoicePipelineService pipelineService;
    private final ObjectMapper objectMapper;

    // Buffer audio chunks per session
    private final Map<String, ByteArrayOutputStream> audioBuffers = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler(VoicePipelineService pipelineService, ObjectMapper objectMapper) {
        this.pipelineService = pipelineService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
        sendMessage(session, VoiceWebSocketMessage.status("Connected to voice bot"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        var payload = objectMapper.readTree(message.getPayload());
        String type = payload.path("type").asText("");

        switch (type) {
            case "start_recording" -> {
                audioBuffers.put(session.getId(), new ByteArrayOutputStream());
                sendMessage(session, VoiceWebSocketMessage.status("Recording started"));
                log.debug("Recording started for session: {}", session.getId());
            }
            case "stop_recording" -> {
                processAudioBuffer(session);
            }
            default -> {
                sendMessage(session, VoiceWebSocketMessage.error("Unknown message type: " + type));
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteArrayOutputStream buffer = audioBuffers.get(session.getId());
        if (buffer == null) {
            // Auto-start buffering if not explicitly started
            buffer = new ByteArrayOutputStream();
            audioBuffers.put(session.getId(), buffer);
        }

        byte[] data = message.getPayload().array();
        buffer.write(data, 0, data.length);
    }

    private void processAudioBuffer(WebSocketSession session) {
        ByteArrayOutputStream buffer = audioBuffers.remove(session.getId());
        if (buffer == null || buffer.size() == 0) {
            sendMessage(session, VoiceWebSocketMessage.error("No audio data received"));
            return;
        }

        byte[] audioData = buffer.toByteArray();
        log.info("Processing {} bytes of audio for session: {}", audioData.length, session.getId());

        // Process asynchronously to not block the WebSocket thread
        Thread.startVirtualThread(() -> {
            try {
                sendMessage(session, VoiceWebSocketMessage.status("Processing audio..."));

                // Build a minimal WAV header for the raw PCM data
                byte[] wavData = addWavHeader(audioData, 16000, 16, 1);
                // Use WebSocket session ID as conversation ID for memory continuity
                ConversationResponse response = pipelineService.converse(session.getId(), wavData);

                // Send transcription
                sendMessage(session, VoiceWebSocketMessage.transcription(response.transcribedText()));

                // Send chat response text
                sendMessage(session, VoiceWebSocketMessage.chatResponse(response.assistantText()));

                // Send audio response as base64
                String audioBase64 = Base64.getEncoder().encodeToString(response.audioResponse());
                sendMessage(session, VoiceWebSocketMessage.audioResponse(response.assistantText(), audioBase64));

            } catch (Exception e) {
                log.error("Error processing audio for session {}", session.getId(), e);
                sendMessage(session, VoiceWebSocketMessage.error("Failed to process audio: " + e.getMessage()));
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        audioBuffers.remove(session.getId());
        log.info("WebSocket disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}", session.getId(), exception);
        audioBuffers.remove(session.getId());
    }

    private void sendMessage(WebSocketSession session, VoiceWebSocketMessage message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to session {}", session.getId(), e);
        }
    }

    /**
     * Wrap raw PCM data in a WAV header.
     */
    private byte[] addWavHeader(byte[] pcmData, int sampleRate, int bitsPerSample, int channels) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        ByteArrayOutputStream wav = new ByteArrayOutputStream(44 + dataSize);
        try {
            // RIFF header
            wav.write("RIFF".getBytes());
            wav.write(intToLittleEndian(chunkSize));
            wav.write("WAVE".getBytes());

            // fmt subchunk
            wav.write("fmt ".getBytes());
            wav.write(intToLittleEndian(16)); // subchunk size
            wav.write(shortToLittleEndian((short) 1)); // PCM format
            wav.write(shortToLittleEndian((short) channels));
            wav.write(intToLittleEndian(sampleRate));
            wav.write(intToLittleEndian(byteRate));
            wav.write(shortToLittleEndian((short) blockAlign));
            wav.write(shortToLittleEndian((short) bitsPerSample));

            // data subchunk
            wav.write("data".getBytes());
            wav.write(intToLittleEndian(dataSize));
            wav.write(pcmData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create WAV header", e);
        }

        return wav.toByteArray();
    }

    private byte[] intToLittleEndian(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    private byte[] shortToLittleEndian(short value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }
}
