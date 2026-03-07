package com.nopaper.work.voicebot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.voicebot.model.TranscriptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Speech-to-Text service using Vosk (open-source, offline).
 * Accepts WAV audio and returns transcribed text.
 */
@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);
    private static final float SAMPLE_RATE = 16000.0f;

    private final Model voskModel;
    private final ObjectMapper objectMapper;

    public SpeechToTextService(Model voskModel, ObjectMapper objectMapper) {
        this.voskModel = voskModel;
        this.objectMapper = objectMapper;
    }

    /**
     * Transcribe audio bytes (WAV format) to text.
     */
    public TranscriptionResult transcribe(byte[] audioData) throws IOException {
        byte[] pcmData = convertToPcm16kMono(audioData);

        try (Recognizer recognizer = new Recognizer(voskModel, SAMPLE_RATE)) {
            int chunkSize = 4096;
            for (int i = 0; i < pcmData.length; i += chunkSize) {
                int len = Math.min(chunkSize, pcmData.length - i);
                byte[] chunk = new byte[len];
                System.arraycopy(pcmData, i, chunk, 0, len);
                recognizer.acceptWaveForm(chunk, len);
            }

            String resultJson = recognizer.getFinalResult();
            log.debug("Vosk result: {}", resultJson);

            JsonNode node = objectMapper.readTree(resultJson);
            String text = node.path("text").asText("").trim();

            if (text.isEmpty()) {
                log.warn("No speech detected in audio");
                return TranscriptionResult.of("");
            }

            log.info("Transcribed: {}", text);
            return TranscriptionResult.of(text);
        }
    }

    /**
     * Transcribe raw PCM audio bytes (16kHz, 16-bit, mono) to text.
     * Used for WebSocket streaming where audio is already in the correct format.
     */
    public TranscriptionResult transcribeRawPcm(byte[] pcmData) throws IOException {
        try (Recognizer recognizer = new Recognizer(voskModel, SAMPLE_RATE)) {
            int chunkSize = 4096;
            for (int i = 0; i < pcmData.length; i += chunkSize) {
                int len = Math.min(chunkSize, pcmData.length - i);
                byte[] chunk = new byte[len];
                System.arraycopy(pcmData, i, chunk, 0, len);
                recognizer.acceptWaveForm(chunk, len);
            }

            String resultJson = recognizer.getFinalResult();
            JsonNode node = objectMapper.readTree(resultJson);
            String text = node.path("text").asText("").trim();

            return TranscriptionResult.of(text);
        }
    }

    /**
     * Convert WAV audio to 16kHz, 16-bit, mono PCM suitable for Vosk.
     */
    private byte[] convertToPcm16kMono(byte[] audioData) throws IOException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream originalStream = AudioSystem.getAudioInputStream(bais);
            AudioFormat originalFormat = originalStream.getFormat();

            // Target format: 16kHz, 16-bit, mono, signed, little-endian
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    SAMPLE_RATE,
                    16,
                    1,
                    2,
                    SAMPLE_RATE,
                    false
            );

            AudioInputStream convertedStream;
            if (originalFormat.matches(targetFormat)) {
                convertedStream = originalStream;
            } else {
                convertedStream = AudioSystem.getAudioInputStream(targetFormat, originalStream);
            }

            return convertedStream.readAllBytes();
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio format. Please provide WAV audio.", e);
        }
    }
}
