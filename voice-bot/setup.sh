#!/bin/bash
# =============================================================================
# Voice Bot - Quick Setup Script
# =============================================================================
set -e

echo "========================================"
echo "  Voice Bot - Setup"
echo "========================================"

# 1. Download Vosk model
VOSK_MODEL_DIR="vosk-model"
VOSK_MODEL_URL="https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

if [ ! -d "$VOSK_MODEL_DIR" ] || [ -z "$(ls -A $VOSK_MODEL_DIR 2>/dev/null)" ]; then
    echo ""
    echo "[1/4] Downloading Vosk speech recognition model..."
    curl -L -o vosk-model.zip "$VOSK_MODEL_URL"
    unzip -q vosk-model.zip
    mv vosk-model-small-en-us-0.15/* "$VOSK_MODEL_DIR"/
    rmdir vosk-model-small-en-us-0.15
    rm vosk-model.zip
    echo "  -> Vosk model downloaded."
else
    echo "[1/4] Vosk model already exists. Skipping."
fi

# 2. Start Docker services
echo ""
echo "[2/4] Starting Docker services (Ollama + Piper TTS)..."
docker compose up -d
echo "  -> Docker services started."

# 3. Wait for Ollama and pull model
echo ""
echo "[3/4] Waiting for Ollama to be ready..."
sleep 5
until curl -s http://localhost:11434/api/tags > /dev/null 2>&1; do
    echo "  Waiting for Ollama..."
    sleep 2
done
echo "  -> Ollama is ready."

echo "  Pulling llama3.2 model (this may take a few minutes on first run)..."
docker exec voicebot-ollama ollama pull llama3.2
echo "  -> Model pulled."

# 4. Build and run the app
echo ""
echo "[4/4] Building the application..."
./mvnw clean package -DskipTests
echo "  -> Build complete."

echo ""
echo "========================================"
echo "  Setup complete!"
echo "========================================"
echo ""
echo "  Start the app:  ./mvnw spring-boot:run"
echo "  Open browser:   http://localhost:8080"
echo ""
echo "  Services:"
echo "    Ollama:   http://localhost:11434"
echo "    TTS:      http://localhost:8000"
echo "    App:      http://localhost:8080"
echo ""
