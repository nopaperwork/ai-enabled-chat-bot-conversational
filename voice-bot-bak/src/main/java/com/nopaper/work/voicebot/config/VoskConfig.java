package com.nopaper.work.voicebot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class VoskConfig {

    private static final Logger log = LoggerFactory.getLogger(VoskConfig.class);

    @Value("${voicebot.vosk.model-path}")
    private String modelPath;

    @Bean(destroyMethod = "close")
    public Model voskModel() throws IOException {
        LibVosk.setLogLevel(LogLevel.INFO);

        Path path = Path.of(modelPath);
        if (!Files.exists(path)) {
            throw new IOException(
                    "Vosk model not found at '%s'. Download a model from https://alphacephei.com/vosk/models and extract it to this path. Recommended: vosk-model-small-en-us-0.15"
                            .formatted(path.toAbsolutePath()));
        }

        log.info("Loading Vosk model from: {}", path.toAbsolutePath());
        Model model = new Model(path.toString());
        log.info("Vosk model loaded successfully");
        return model;
    }
}
