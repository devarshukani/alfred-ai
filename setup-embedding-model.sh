#!/bin/bash
# Downloads the all-MiniLM-L6-v2 ONNX embedding model and vocab for Alfred AI.
# Run this once before building.

set -e

ASSETS="app/src/main/assets/embedding-model"
MODEL_FILE="$ASSETS/all-MiniLM-L6-v2.onnx"
VOCAB_FILE="$ASSETS/vocab.txt"

HF_REPO="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"
ONNX_URL="${HF_REPO}/onnx/model.onnx"
VOCAB_URL="${HF_REPO}/vocab.txt"

mkdir -p "$ASSETS"

echo "=== Downloading all-MiniLM-L6-v2 embedding model ==="

if [ -f "$MODEL_FILE" ]; then
    echo "  Model already exists at $MODEL_FILE, skipping."
else
    echo "  Downloading ONNX model (~22MB)..."
    wget -q --show-progress -O "$MODEL_FILE" "$ONNX_URL"
    echo "  Saved to $MODEL_FILE"
fi

if [ -f "$VOCAB_FILE" ]; then
    echo "  Vocab already exists at $VOCAB_FILE, skipping."
else
    echo "  Downloading vocab.txt..."
    wget -q --show-progress -O "$VOCAB_FILE" "$VOCAB_URL"
    echo "  Saved to $VOCAB_FILE"
fi

echo ""
echo "=== Done ==="
echo "  Model: $MODEL_FILE"
echo "  Vocab: $VOCAB_FILE"
