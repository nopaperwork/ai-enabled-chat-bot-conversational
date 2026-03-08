package com.nopaper.work.voicebot.controller;

import com.nopaper.work.voicebot.model.ChatRequest;
import com.nopaper.work.voicebot.model.ChatResponse;
import com.nopaper.work.voicebot.model.ConversationResponse;
import com.nopaper.work.voicebot.model.TranscriptionResult;
import com.nopaper.work.voicebot.service.ChatService;
import com.nopaper.work.voicebot.service.SpeechToTextService;
import com.nopaper.work.voicebot.service.TextToSpeechService;
import com.nopaper.work.voicebot.service.VoicePipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    private final SpeechToTextService sttService;
    private final TextToSpeechService ttsService;
    private final ChatService chatService;
    private final VoicePipelineService pipelineService;

    public VoiceController(SpeechToTextService sttService,
                           TextToSpeechService ttsService,
                           ChatService chatService,
                           VoicePipelineService pipelineService) {
        this.sttService = sttService;
        this.ttsService = ttsService;
        this.chatService = chatService;
        this.pipelineService = pipelineService;
    }

    /**
     * Transcribe audio file to text.
     * POST /api/voice/transcribe
     */
    @PostMapping("/transcribe")
    public ResponseEntity<TranscriptionResult> transcribe(
            @RequestParam("audio") MultipartFile audioFile) throws IOException {

        log.info("Transcription request: {} ({} bytes)", audioFile.getOriginalFilename(), audioFile.getSize());
        TranscriptionResult result = sttService.transcribe(audioFile.getBytes());
        return ResponseEntity.ok(result);
    }

    /**
     * Chat with the AI assistant (text only, with conversation memory).
     * POST /api/voice/chat
     * Body: { "message": "Hello", "sessionId": "abc-123" }
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = resolveSessionId(request.sessionId());
        String response = chatService.chat(sessionId, request.message());
        return ResponseEntity.ok(new ChatResponse(request.message(), response));
    }

    /**
     * Convert text to speech audio.
     * POST /api/voice/synthesize
     */
    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        byte[] audio = ttsService.synthesize(text);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"speech.wav\"")
                .body(audio);
    }

    /**
     * Full voice conversation: Audio in -> AI response audio out (with memory).
     * POST /api/voice/converse
     * Params: audio (file), sessionId (optional)
     */
    @PostMapping("/converse")
    public ResponseEntity<Map<String, String>> converse(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "sessionId", required = false) String sessionId) throws IOException {

        sessionId = resolveSessionId(sessionId);
        log.info("[{}] Conversation request: {} ({} bytes)", sessionId,
                audioFile.getOriginalFilename(), audioFile.getSize());

        ConversationResponse response = pipelineService.converse(sessionId, audioFile.getBytes());

        return ResponseEntity.ok(Map.of(
                "transcribedText", response.transcribedText(),
                "assistantText", response.assistantText(),
                "audioBase64", Base64.getEncoder().encodeToString(response.audioResponse())
        ));
    }

    /**
     * Clear conversation history for a session.
     * DELETE /api/voice/session/{sessionId}
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId) {
        chatService.clearConversation(sessionId);
        return ResponseEntity.ok(Map.of("status", "cleared", "sessionId", sessionId));
    }

    /**
     * Health check for the voice services.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "stt", "vosk",
                "tts", "openedai-speech (piper)",
                "llm", "ollama",
                "memory", "in-memory (per-session)"
        ));
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }
}
