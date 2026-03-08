package com.nopaper.work.voicebot.service;

import com.nopaper.work.voicebot.model.ConversationResponse;
import com.nopaper.work.voicebot.model.TranscriptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Orchestrates the full voice pipeline: STT -> Chat (with memory) -> TTS.
 */
@Service
public class VoicePipelineService {

    private static final Logger log = LoggerFactory.getLogger(VoicePipelineService.class);

    private final SpeechToTextService sttService;
    private final ChatService chatService;
    private final TextToSpeechService ttsService;

    public VoicePipelineService(SpeechToTextService sttService,
                                 ChatService chatService,
                                 TextToSpeechService ttsService) {
        this.sttService = sttService;
        this.chatService = chatService;
        this.ttsService = ttsService;
    }

    /**
     * Full voice conversation pipeline:
     * 1. Transcribe audio to text (STT)
     * 2. Send text to LLM for response with conversation memory (Chat)
     * 3. Convert response to audio (TTS)
     *
     * @param sessionId unique conversation session identifier for memory continuity
     * @param audioData WAV audio bytes
     */
    public ConversationResponse converse(String sessionId, byte[] audioData) throws IOException {
        // Step 1: Speech-to-Text
        log.info("[{}] Pipeline: Starting transcription...", sessionId);
        TranscriptionResult transcription = sttService.transcribe(audioData);

        if (transcription.text().isEmpty()) {
            log.warn("[{}] Pipeline: No speech detected", sessionId);
            String fallbackText = "I didn't catch that. Could you please repeat?";
            byte[] fallbackAudio = ttsService.synthesize(fallbackText);
            return new ConversationResponse("", fallbackText, fallbackAudio);
        }

        // Step 2: Chat with LLM (conversation history is maintained per sessionId)
        log.info("[{}] Pipeline: Sending to LLM: {}", sessionId, transcription.text());
        String chatResponse = chatService.chat(sessionId, transcription.text());

        // Step 3: Text-to-Speech
        log.info("[{}] Pipeline: Synthesizing response...", sessionId);
        byte[] responseAudio = ttsService.synthesize(chatResponse);

        log.info("[{}] Pipeline: Complete", sessionId);
        return new ConversationResponse(transcription.text(), chatResponse, responseAudio);
    }
}
