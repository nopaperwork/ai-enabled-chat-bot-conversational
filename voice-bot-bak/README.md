# Voice Bot - AI Voice Chat Engine

A prototype voice-based chat engine built with Spring Boot 4.x and Spring AI, using an entirely open-source tech stack.

## Architecture

```
                    ┌──────────────────────────────────────────┐
                    │              Browser UI                   │
                    │  (Web Audio API + WebSocket/REST)         │
                    └─────────────┬────────────────────────────┘
                                  │
                    ┌─────────────▼────────────────────────────┐
                    │         Spring Boot 4.0.3                 │
                    │                                           │
                    │  ┌─────────┐  ┌────────┐  ┌───────────┐ │
                    │  │   STT   │  │  Chat  │  │    TTS    │ │
                    │  │  (Vosk) │→ │(Spring │→ │  (Piper)  │ │
                    │  │         │  │   AI)  │  │           │ │
                    │  └─────────┘  └────────┘  └───────────┘ │
                    └──────┬───────────┬───────────┬───────────┘
                           │           │           │
                    ┌──────▼──┐  ┌─────▼────┐  ┌──▼────────────┐
                    │  Vosk   │  │  Ollama  │  │ openedai-     │
                    │  Model  │  │ (LLM)   │  │ speech(Piper) │
                    │(on-disk)│  │ :11434   │  │    :8000      │
                    └─────────┘  └──────────┘  └───────────────┘
```

## Tech Stack

| Component | Technology | Description |
|-----------|-----------|-------------|
| Framework | Spring Boot 4.0.3 | Java 21, Spring Framework 7 |
| AI Chat | Spring AI 2.0.0-M2 + Ollama | Open-source LLM (llama3.2) |
| Speech-to-Text | Vosk 0.3.45 | Embedded Java library, offline |
| Text-to-Speech | openedai-speech (Piper) | Docker, OpenAI-compatible API |
| Frontend | HTML5 + Web Audio API | Browser-based voice capture |

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

## Quick Start

```bash
# 1. Run the setup script (downloads models, starts Docker services)
./setup.sh

# 2. Start the application
./mvnw spring-boot:run

# 3. Open in browser
open http://localhost:8080
```

## Manual Setup

### 1. Download Vosk Model

```bash
# Small model (~40MB) - good for quick testing
curl -LO https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
mv vosk-model-small-en-us-0.15/* vosk-model/

# Or for better accuracy (~1.8GB):
# curl -LO https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip
```

### 2. Start Docker Services

```bash
docker compose up -d
```

This starts:
- **Ollama** on port 11434 (LLM server)
- **openedai-speech** on port 8000 (TTS server with Piper)

### 3. Pull the LLM Model

```bash
docker exec voicebot-ollama ollama pull llama3.2
```

### 4. Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/voice/transcribe` | POST | Upload WAV audio, get text back |
| `/api/voice/chat` | POST | Send text message, get AI response |
| `/api/voice/synthesize` | POST | Send text, get WAV audio back |
| `/api/voice/converse` | POST | Full pipeline: audio in, audio + text out |
| `/api/voice/health` | GET | Service health check |
| `/ws/voice` | WS | WebSocket for real-time voice chat |

### Example: Transcribe Audio

```bash
curl -X POST http://localhost:8080/api/voice/transcribe \
  -F "audio=@recording.wav"
```

### Example: Text Chat

```bash
curl -X POST http://localhost:8080/api/voice/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the capital of France?"}'
```

### Example: Text to Speech

```bash
curl -X POST http://localhost:8080/api/voice/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text": "Hello, how are you today?"}' \
  --output speech.wav
```

## Configuration

All configuration is in `src/main/resources/application.properties`:

```properties
# Ollama LLM
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=llama3.2

# Vosk STT model path
voicebot.vosk.model-path=vosk-model

# Piper TTS endpoint
voicebot.tts.base-url=http://localhost:8000
voicebot.tts.voice=alloy

# System prompt for the voice assistant
voicebot.chat.system-prompt=You are a helpful voice assistant...
```

## Project Structure

```
voice-bot/
├── pom.xml                          # Maven config (Spring Boot 4.0.3)
├── docker-compose.yml               # Ollama + Piper TTS services
├── setup.sh                         # Quick setup script
├── vosk-model/                      # Vosk speech model (downloaded)
└── src/main/
    ├── java/com/nopaper/work/voicebot/
    │   ├── VoiceBotApplication.java       # Entry point
    │   ├── config/
    │   │   ├── AppConfig.java             # REST client bean
    │   │   ├── VoskConfig.java            # Vosk model bean
    │   │   └── WebSocketConfig.java       # WebSocket registration
    │   ├── controller/
    │   │   ├── VoiceController.java       # REST API endpoints
    │   │   └── VoiceWebSocketHandler.java # Real-time voice WebSocket
    │   ├── model/
    │   │   ├── ChatRequest.java           # Chat input DTO
    │   │   ├── ChatResponse.java          # Chat output DTO
    │   │   ├── ConversationResponse.java  # Full pipeline response
    │   │   ├── TranscriptionResult.java   # STT result
    │   │   └── VoiceWebSocketMessage.java # WebSocket message envelope
    │   └── service/
    │       ├── ChatService.java           # Spring AI + Ollama chat
    │       ├── SpeechToTextService.java   # Vosk STT
    │       ├── TextToSpeechService.java   # Piper TTS via REST
    │       └── VoicePipelineService.java  # STT -> Chat -> TTS orchestrator
    └── resources/
        ├── application.properties
        └── static/
            └── index.html                 # Voice chat UI
```
