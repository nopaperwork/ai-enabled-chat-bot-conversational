# Voice Bot - AI Voice Chat Engine

A voice-based conversational AI engine built with **Spring Boot 4.0.3**, **Spring AI 2.0.0-M2**, and a fully open-source infrastructure stack. Supports real-time voice and text conversations with per-session memory.

---

## Architecture

```
 ┌──────────────────────────────────────────────────────┐
 │                   Browser (Client)                    │
 │  HTML5 UI  +  Web Audio API  +  WebSocket / REST     │
 │  sessionStorage (conversation session ID)             │
 └──────────────────────┬───────────────────────────────┘
                        │ HTTP / WS
 ┌──────────────────────▼───────────────────────────────┐
 │              Spring Boot 4.0.3 (:8080)                │
 │                                                       │
 │   VoiceController (REST)    VoiceWebSocketHandler     │
 │           │                         │                 │
 │           └──────────┬──────────────┘                 │
 │                      ▼                                │
 │           VoicePipelineService                        │
 │            ┌─────┐ ┌─────┐ ┌─────┐                   │
 │         1. │ STT │→│Chat │→│ TTS │  3-step pipeline   │
 │            └──┬──┘ └──┬──┘ └──┬──┘                   │
 │               │       │       │                       │
 │               │   ChatMemory (in-memory, per-session) │
 └───────────────┼───────┼───────┼───────────────────────┘
                 │       │       │
       REST      │  REST │       │ REST
                 ▼       ▼       ▼
 ┌───────────┐ ┌─────────────┐ ┌────────────────┐
 │  faster-  │ │   Ollama    │ │ openedai-speech │
 │  whisper  │ │  (llama3.2) │ │  (Piper TTS)   │
 │  :8001    │ │   :11434    │ │    :8000        │
 │  (Docker) │ │  (Docker)   │ │   (Docker)      │
 └───────────┘ └─────────────┘ └────────────────┘
```

---

## Tech Stack

| Layer | Technology | Version | Role |
|-------|-----------|---------|------|
| **Runtime** | Java | 21 | Language |
| **Framework** | Spring Boot | 4.0.3 | Web + WebSocket server |
| **AI Framework** | Spring AI | 2.0.0-M2 | ChatClient, ChatMemory, Ollama integration |
| **LLM** | Ollama + llama3.2 | latest | Conversational AI responses |
| **Speech-to-Text** | faster-whisper-server (Whisper) | latest | OpenAI-compatible `/v1/audio/transcriptions` |
| **Text-to-Speech** | openedai-speech (Piper TTS) | latest | OpenAI-compatible `/v1/audio/speech` |
| **Build** | Apache Maven | 3.9+ | Dependency management and build |
| **Frontend** | HTML5 + Web Audio API | - | Voice capture, audio playback, chat UI |

---

## Prerequisites

| Requirement | Minimum Version | Verify Command |
|-------------|----------------|----------------|
| **Java** | 21 | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **Docker** | 20.10+ | `docker --version` |
| **Docker Compose** | v2+ | `docker compose version` |
| **Disk Space** | ~5 GB | For Docker images and LLM model |
| **RAM** | 8 GB+ | Ollama + Whisper + Piper running concurrently |

---

## Quick Start

```bash
# Clone / navigate to the project
cd voice-bot

# 1. Start infrastructure services
docker compose up -d

# 2. Pull the LLM model (first time only, ~2 GB download)
docker exec voicebot-ollama ollama pull llama3.2

# 3. Build the application
mvn clean package -DskipTests

# 4. Run the application
mvn spring-boot:run

# 5. Open in browser
open http://localhost:8080
```

---

## Detailed Setup

### Step 1 - Start Docker Services

```bash
docker compose up -d
```

This starts three containers:

| Container | Image | Host Port | Purpose |
|-----------|-------|-----------|---------|
| `voicebot-ollama` | `ollama/ollama:latest` | **11434** | LLM server |
| `voicebot-stt` | `fedirz/faster-whisper-server:latest-cpu` | **8001** | Speech-to-Text (Whisper) |
| `voicebot-tts` | `ghcr.io/matatonic/openedai-speech` | **8000** | Text-to-Speech (Piper) |

Verify all containers are running:

```bash
docker compose ps
```

### Step 2 - Pull the LLM Model

```bash
docker exec voicebot-ollama ollama pull llama3.2
```

This downloads the llama3.2 model (~2 GB). You only need to do this once; the model is persisted in a Docker volume.

To use a different model, change the property in `application.properties`:

```properties
spring.ai.ollama.chat.model=llama3.2
```

Other compatible models: `mistral`, `phi3`, `gemma2`, `qwen2.5`.

### Step 3 - Build the Application

```bash
mvn clean package -DskipTests
```

### Step 4 - Run the Application

```bash
mvn spring-boot:run
```

The application starts on **http://localhost:8080**.

Expected startup log:

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
  ...
 :: Spring Boot ::                (v4.0.3)

Started VoiceBotApplication in 1.3 seconds
```

### Step 5 - Verify Services

```bash
# Health check
curl http://localhost:8080/api/voice/health

# Expected response:
# {"status":"UP","stt":"faster-whisper","tts":"openedai-speech (piper)","llm":"ollama","memory":"in-memory (per-session)"}
```

---

## Server / Production Deployment

### Using the JAR

```bash
# Build the fat JAR
mvn clean package -DskipTests

# Run as a standalone process
java -jar target/voice-bot-0.0.1-SNAPSHOT.jar
```

### Using systemd (Linux)

Create `/etc/systemd/system/voice-bot.service`:

```ini
[Unit]
Description=Voice Bot - AI Voice Chat Engine
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/voice-bot
ExecStart=/usr/bin/java -jar /opt/voice-bot/voice-bot-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
Environment=JAVA_HOME=/usr/lib/jvm/java-21

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable voice-bot
sudo systemctl start voice-bot
sudo journalctl -u voice-bot -f   # View logs
```

### Environment Variable Overrides

All `application.properties` can be overridden via environment variables:

```bash
# Override Ollama URL (e.g., remote server)
export SPRING_AI_OLLAMA_BASE_URL=http://gpu-server:11434

# Override STT and TTS endpoints
export VOICEBOT_STT_BASE_URL=http://stt-server:8001
export VOICEBOT_TTS_BASE_URL=http://tts-server:8000

# Override server port
export SERVER_PORT=9090

java -jar target/voice-bot-0.0.1-SNAPSHOT.jar
```

### GPU Support (NVIDIA)

For significantly faster LLM and Whisper inference, enable GPU in `docker-compose.yml`:

```yaml
# Ollama - uncomment the deploy block:
ollama:
  image: ollama/ollama:latest
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: all
            capabilities: [gpu]

# faster-whisper - switch to GPU image:
faster-whisper:
  image: fedirz/faster-whisper-server:latest-cuda
  environment:
    - WHISPER__INFERENCE_DEVICE=cuda
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
```

---

## API Reference

### POST `/api/voice/transcribe`

Upload audio and get transcribed text.

```bash
curl -X POST http://localhost:8080/api/voice/transcribe \
  -F "audio=@recording.wav"
```

**Response:**

```json
{ "text": "hello how are you", "partial": false }
```

### POST `/api/voice/chat`

Send a text message with conversation memory.

```bash
curl -X POST http://localhost:8080/api/voice/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "My name is Alice", "sessionId": "session-001"}'
```

**Response:**

```json
{ "userMessage": "My name is Alice", "assistantMessage": "Nice to meet you, Alice!" }
```

Subsequent calls with the same `sessionId` retain conversation context:

```bash
curl -X POST http://localhost:8080/api/voice/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is my name?", "sessionId": "session-001"}'
# Response: { ..., "assistantMessage": "Your name is Alice!" }
```

### POST `/api/voice/synthesize`

Convert text to WAV audio.

```bash
curl -X POST http://localhost:8080/api/voice/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text": "Hello, how are you today?"}' \
  --output speech.wav
```

### POST `/api/voice/converse`

Full voice pipeline: audio in, AI response + audio out.

```bash
curl -X POST http://localhost:8080/api/voice/converse \
  -F "audio=@recording.wav" \
  -F "sessionId=session-001"
```

**Response:**

```json
{
  "transcribedText": "what is the weather like",
  "assistantText": "I don't have access to weather data, but I can help with other questions.",
  "audioBase64": "UklGRi4AAABXQVZFZm10IBA..."
}
```

### DELETE `/api/voice/session/{sessionId}`

Clear conversation memory for a session.

```bash
curl -X DELETE http://localhost:8080/api/voice/session/session-001
```

### GET `/api/voice/health`

Health check for all services.

```bash
curl http://localhost:8080/api/voice/health
```

### WebSocket `/ws/voice`

Real-time voice chat. Connect via:

```
ws://localhost:8080/ws/voice
```

**Protocol:**

| Direction | Type | Payload |
|-----------|------|---------|
| Client -> Server | Text | `{"type": "start_recording"}` |
| Client -> Server | Binary | Raw PCM audio chunks (16kHz, 16-bit, mono) |
| Client -> Server | Text | `{"type": "stop_recording"}` |
| Server -> Client | Text | `{"type": "transcription", "text": "..."}` |
| Server -> Client | Text | `{"type": "chat_response", "text": "..."}` |
| Server -> Client | Text | `{"type": "audio_response", "text": "...", "audioBase64": "..."}` |
| Server -> Client | Text | `{"type": "error", "text": "..."}` |

---

## Configuration Reference

All configuration is in `src/main/resources/application.properties`:

```properties
# ── Server ──────────────────────────────────────────
server.port=8080

# ── Ollama (LLM) ───────────────────────────────────
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=llama3.2
spring.ai.ollama.chat.options.temperature=0.7
spring.ai.ollama.chat.options.num-predict=512

# ── faster-whisper-server (STT) ────────────────────
voicebot.stt.base-url=http://localhost:8001
voicebot.stt.model=Systran/faster-whisper-small
voicebot.stt.language=en

# ── openedai-speech / Piper (TTS) ──────────────────
voicebot.tts.base-url=http://localhost:8000
voicebot.tts.voice=alloy
voicebot.tts.model=tts-1
voicebot.tts.response-format=wav
voicebot.tts.speed=1.0

# ── Chat Memory ────────────────────────────────────
voicebot.chat.system-prompt=You are a helpful voice assistant...
voicebot.chat.memory.max-messages=50

# ── File Upload ────────────────────────────────────
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

---

## Project Structure

```
voice-bot/
├── pom.xml                                # Maven dependencies (Boot 4.0.3, Spring AI 2.0.0-M2)
├── docker-compose.yml                     # Ollama + Whisper + Piper services
├── setup.sh                               # Automated setup script
├── .gitignore
│
└── src/
    ├── main/
    │   ├── java/com/nopaper/work/voicebot/
    │   │   ├── VoiceBotApplication.java          # @SpringBootApplication entry point
    │   │   │
    │   │   ├── config/
    │   │   │   ├── AppConfig.java                # RestClient bean
    │   │   │   └── WebSocketConfig.java          # Registers /ws/voice endpoint
    │   │   │
    │   │   ├── controller/
    │   │   │   ├── VoiceController.java          # REST API (6 endpoints)
    │   │   │   └── VoiceWebSocketHandler.java    # WebSocket handler, audio buffering
    │   │   │
    │   │   ├── model/
    │   │   │   ├── ChatRequest.java              # { message, sessionId }
    │   │   │   ├── ChatResponse.java             # { userMessage, assistantMessage }
    │   │   │   ├── ConversationResponse.java     # { transcribedText, assistantText, audioResponse }
    │   │   │   ├── TranscriptionResult.java      # { text, partial }
    │   │   │   └── VoiceWebSocketMessage.java    # WebSocket message envelope
    │   │   │
    │   │   └── service/
    │   │       ├── SpeechToTextService.java      # REST client -> faster-whisper-server
    │   │       ├── ChatService.java              # Spring AI ChatClient + ChatMemory + Ollama
    │   │       ├── TextToSpeechService.java      # REST client -> openedai-speech (Piper)
    │   │       └── VoicePipelineService.java     # Orchestrator: STT -> Chat -> TTS
    │   │
    │   └── resources/
    │       ├── application.properties
    │       └── static/
    │           └── index.html                    # Single-page voice chat UI
    │
    └── test/
        └── java/com/nopaper/work/voicebot/
            └── VoiceBotApplicationTests.java
```

---

## Key Features

- **Conversational Memory** - Per-session chat history using Spring AI `MessageWindowChatMemory` (50-message sliding window). Each browser tab gets its own conversation.
- **Full Voice Pipeline** - Microphone audio -> Whisper transcription -> LLM response -> Piper speech synthesis -> speaker playback. All in one click.
- **Dual Input Modes** - Voice (microphone) and text (keyboard), switchable in the UI.
- **WebSocket Support** - Real-time streaming voice interaction alongside REST API endpoints.
- **100% Open Source** - No proprietary APIs. Ollama, Whisper, and Piper all run locally.
- **Session Management** - `sessionId` tracked via `sessionStorage` in the browser. "New Chat" button clears server-side memory and resets the session.

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|---------|
| `Connection refused` on port 8001 | faster-whisper container not running | `docker compose up -d faster-whisper` |
| `Connection refused` on port 8000 | openedai-speech container not running | `docker compose up -d openedai-speech` |
| `Connection refused` on port 11434 | Ollama container not running | `docker compose up -d ollama` |
| LLM returns empty response | Model not pulled yet | `docker exec voicebot-ollama ollama pull llama3.2` |
| Slow first STT request | Whisper model downloading on first use | Wait ~2 min for the model download to complete |
| `OutOfMemoryError` | Not enough RAM for all services | Increase Docker memory limit to 8 GB+ |
| Audio not playing in browser | Browser autoplay policy | Click the "Replay audio" button manually |
| WebSocket disconnects | Server restart | UI auto-reconnects after 3 seconds |

---

## Ports Summary

| Port | Service | Protocol |
|------|---------|----------|
| **8080** | Voice Bot (Spring Boot) | HTTP / WebSocket |
| **8000** | openedai-speech (Piper TTS) | HTTP |
| **8001** | faster-whisper-server (Whisper STT) | HTTP |
| **11434** | Ollama (LLM) | HTTP |

---

## License

This project uses exclusively open-source components:

- [Spring Boot](https://spring.io/projects/spring-boot) - Apache 2.0
- [Spring AI](https://spring.io/projects/spring-ai) - Apache 2.0
- [Ollama](https://ollama.com) - MIT
- [faster-whisper](https://github.com/SYSTRAN/faster-whisper) - MIT
- [Piper TTS](https://github.com/rhasspy/piper) - MIT
- [openedai-speech](https://github.com/matatonic/openedai-speech) - AGPL-3.0
