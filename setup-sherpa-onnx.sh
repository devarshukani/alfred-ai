#!/bin/bash
# Downloads sherpa-onnx native libraries, TTS model, and ASR model for Alfred AI.
# Run this once before building. To swap models, change the variables below.

set -e

SHERPA_VERSION="1.12.28"

# ============================================================
# CHANGE MODELS HERE — everything else adapts automatically.
#
# TTS models:  https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
# ASR models:  https://k2-fsa.github.io/sherpa/onnx/pretrained_models/
# ============================================================
TTS_MODEL="vits-piper-en_US-ryan-medium"
ASR_MODEL="sherpa-onnx-streaming-zipformer-en-20M-2023-02-17"

ASSETS="app/src/main/assets"
TTS_DIR="$ASSETS/tts-model"
ASR_DIR="$ASSETS/asr-model"

# ---------------------------------------------------------------
# 1. Native JNI libraries (shared by TTS + ASR)
# ---------------------------------------------------------------
echo "=== [1/3] Downloading sherpa-onnx v${SHERPA_VERSION} Android native libraries ==="

LIBS_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-v${SHERPA_VERSION}-android.tar.bz2"
LIBS_ARCHIVE="sherpa-onnx-android.tar.bz2"

if [ ! -f "$LIBS_ARCHIVE" ]; then
    wget -O "$LIBS_ARCHIVE" "$LIBS_URL"
fi

echo "Extracting native libraries..."
tar xjf "$LIBS_ARCHIVE"

DEST="app/src/main/jniLibs"
for ABI in arm64-v8a armeabi-v7a; do
    mkdir -p "$DEST/$ABI"
    cp jniLibs/$ABI/libsherpa-onnx-jni.so "$DEST/$ABI/"
    cp jniLibs/$ABI/libonnxruntime.so "$DEST/$ABI/"
    echo "  Copied $ABI libs"
done

# ---------------------------------------------------------------
# 2. TTS model
# ---------------------------------------------------------------
echo ""
echo "=== [2/3] Downloading TTS model: ${TTS_MODEL} ==="

TTS_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${TTS_MODEL}.tar.bz2"
TTS_ARCHIVE="${TTS_MODEL}.tar.bz2"

if [ ! -f "$TTS_ARCHIVE" ]; then
    wget -O "$TTS_ARCHIVE" "$TTS_URL"
fi

echo "Extracting TTS model..."
mkdir -p "$ASSETS"
tar xjf "$TTS_ARCHIVE" -C "$ASSETS"

rm -rf "$ASSETS/$TTS_MODEL/test_wavs" 2>/dev/null || true
rm -f "$ASSETS/$TTS_MODEL"/*.sh "$ASSETS/$TTS_MODEL"/README.md 2>/dev/null || true

rm -rf "$TTS_DIR" 2>/dev/null || true
mv "$ASSETS/$TTS_MODEL" "$TTS_DIR"

# ---------------------------------------------------------------
# 3. ASR model (streaming zipformer, int8 quantised)
# ---------------------------------------------------------------
echo ""
echo "=== [3/3] Downloading ASR model: ${ASR_MODEL} ==="

ASR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/${ASR_MODEL}.tar.bz2"
ASR_ARCHIVE="${ASR_MODEL}.tar.bz2"

if [ ! -f "$ASR_ARCHIVE" ]; then
    wget -O "$ASR_ARCHIVE" "$ASR_URL"
fi

echo "Extracting ASR model..."
tar xjf "$ASR_ARCHIVE" -C "$ASSETS"

rm -rf "$ASSETS/$ASR_MODEL/test_wavs" 2>/dev/null || true
rm -f "$ASSETS/$ASR_MODEL"/*.sh "$ASSETS/$ASR_MODEL"/README.md 2>/dev/null || true
# Keep only int8 encoder/joiner + fp32 decoder (smallest combo)
rm -f "$ASSETS/$ASR_MODEL"/encoder-*-avg-1.onnx 2>/dev/null || true
rm -f "$ASSETS/$ASR_MODEL"/joiner-*-avg-1.onnx 2>/dev/null || true
rm -f "$ASSETS/$ASR_MODEL"/decoder-*-avg-1.int8.onnx 2>/dev/null || true

rm -rf "$ASR_DIR" 2>/dev/null || true
mv "$ASSETS/$ASR_MODEL" "$ASR_DIR"

# ---------------------------------------------------------------
# Done
# ---------------------------------------------------------------
echo ""
echo "=== All done! ==="
echo ""
echo "  JNI libs:  app/src/main/jniLibs/"
echo "  TTS model: $TTS_DIR/"
echo "  ASR model: $ASR_DIR/"
echo ""
echo "To swap models, edit TTS_MODEL / ASR_MODEL at the top and re-run."
