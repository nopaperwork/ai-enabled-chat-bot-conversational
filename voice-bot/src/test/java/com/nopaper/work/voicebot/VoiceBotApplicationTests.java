package com.nopaper.work.voicebot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "voicebot.vosk.model-path=test-model",
        "spring.ai.ollama.base-url=http://localhost:11434"
})
class VoiceBotApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context loads (will need Vosk model and Ollama running)
    }
}
