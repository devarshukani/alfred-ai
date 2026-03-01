#!/bin/bash
# Downloads all models for Alfred AI:
#   models/tts/model.onnx, tokens.txt, espeak-ng-data/
#   models/embedding/model.onnx, vocab.txt
#   Sherpa-onnx AAR (native libs)
#
# NOTE: The large ONNX models (tts/model.onnx, embedding/model.onnx, embedding/vocab.txt)
# are downloaded at runtime during onboarding. They do NOT need to be in assets for production.
# This script is only needed for local development/testing.
# The small files (tokens.txt, espeak-ng-data/) MUST remain in assets.
#
# Run once before building. To swap TTS voice, change TTS_MODEL below.

set -e

SHERPA_VERSION="1.12.28"
TTS_MODEL="vits-piper-en_US-ryan-medium"

ASSETS="app/src/main/assets"
TTS_DIR="$ASSETS/models/tts"
EMB_DIR="$ASSETS/models/embedding"

# ---------------------------------------------------------------
# 1. Sherpa-onnx AAR
# ---------------------------------------------------------------
echo "=== [1/3] Sherpa-onnx AAR v${SHERPA_VERSION} ==="

AAR_NAME="sherpa-onnx-static-link-onnxruntime-${SHERPA_VERSION}.aar"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/${AAR_NAME}"
AAR_DIR="app/libs"

mkdir -p "$AAR_DIR"
if [ ! -f "$AAR_DIR/$AAR_NAME" ]; then
    wget -q --show-progress -O "$AAR_DIR/$AAR_NAME" "$AAR_URL"
fi
echo "  $AAR_DIR/$AAR_NAME"

# ---------------------------------------------------------------
# 2. TTS model
# ---------------------------------------------------------------
echo ""
echo "=== [2/3] TTS model: ${TTS_MODEL} ==="

TTS_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${TTS_MODEL}.tar.bz2"
TTS_ARCHIVE="${TTS_MODEL}.tar.bz2"

mkdir -p "$TTS_DIR"

if [ ! -f "$TTS_DIR/model.onnx" ]; then
    if [ ! -f "$TTS_ARCHIVE" ]; then
        echo "  Downloading archive..."
        wget -q --show-progress -O "$TTS_ARCHIVE" "$TTS_URL"
    fi

    echo "  Extracting..."
    TMP_DIR=$(mktemp -d)
    tar xjf "$TTS_ARCHIVE" -C "$TMP_DIR"
    EXTRACTED="$TMP_DIR/$TTS_MODEL"

    ONNX_FILE=$(find "$EXTRACTED" -maxdepth 1 -name '*.onnx' ! -name '*.onnx.json' | head -1)
    cp "$ONNX_FILE" "$TTS_DIR/model.onnx"
    cp "$EXTRACTED/tokens.txt" "$TTS_DIR/tokens.txt"
    rm -rf "$TTS_DIR/espeak-ng-data" 2>/dev/null || true
    cp -r "$EXTRACTED/espeak-ng-data" "$TTS_DIR/espeak-ng-data"

    rm -rf "$TMP_DIR"
    echo "  Extracted to $TTS_DIR/"
else
    echo "  model.onnx already exists, skipping."
fi

# ---------------------------------------------------------------
# 3. Embedding model (all-MiniLM-L6-v2)
# ---------------------------------------------------------------
echo ""
echo "=== [3/3] Embedding model ==="

HF_REPO="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"

mkdir -p "$EMB_DIR"

if [ ! -f "$EMB_DIR/model.onnx" ]; then
    echo "  Downloading model.onnx (~22MB)..."
    wget -q --show-progress -O "$EMB_DIR/model.onnx" "${HF_REPO}/onnx/model.onnx"
else
    echo "  model.onnx already exists, skipping."
fi

if [ ! -f "$EMB_DIR/vocab.txt" ]; then
    echo "  Downloading vocab.txt..."
    wget -q --show-progress -O "$EMB_DIR/vocab.txt" "${HF_REPO}/vocab.txt"
else
    echo "  vocab.txt already exists, skipping."
fi

# ---------------------------------------------------------------
# Done
# ---------------------------------------------------------------
echo ""
echo "=== All done ==="
echo "  $AAR_DIR/$AAR_NAME"
echo "  $TTS_DIR/  (model.onnx, tokens.txt, espeak-ng-data/)"
echo "  $EMB_DIR/  (model.onnx, vocab.txt)"
