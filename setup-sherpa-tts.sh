#!/bin/bash
# Downloads sherpa-onnx native libraries and a TTS model for Alfred AI.
# Run this once before building. To swap models, just change MODEL below.

set -e

SHERPA_VERSION="1.12.28"

# ============================================================
# CHANGE MODEL HERE — everything else adapts automatically.
# Browse models: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
# ============================================================
MODEL="vits-piper-en_GB-southern_english_male-medium"

ASSETS="app/src/main/assets"
GENERIC_DIR="$ASSETS/tts-model"

# --- Native libraries ---

echo "=== Downloading sherpa-onnx v${SHERPA_VERSION} Android native libraries ==="

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

# --- TTS model ---

echo ""
echo "=== Downloading TTS model: ${MODEL} ==="

MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${MODEL}.tar.bz2"
MODEL_ARCHIVE="${MODEL}.tar.bz2"

if [ ! -f "$MODEL_ARCHIVE" ]; then
    wget -O "$MODEL_ARCHIVE" "$MODEL_URL"
fi

echo "Extracting model..."
mkdir -p "$ASSETS"
tar xjf "$MODEL_ARCHIVE" -C "$ASSETS"

# Clean up test files to reduce APK size
rm -rf "$ASSETS/$MODEL/test_wavs" 2>/dev/null || true
rm -f "$ASSETS/$MODEL"/*.sh "$ASSETS/$MODEL"/README.md 2>/dev/null || true

# Rename to generic directory so Kotlin code never needs model-specific names
rm -rf "$GENERIC_DIR" 2>/dev/null || true
mv "$ASSETS/$MODEL" "$GENERIC_DIR"

echo ""
echo "=== Done! ==="
echo ""
echo "  JNI libs:  app/src/main/jniLibs/"
echo "  Model:     $GENERIC_DIR/"
echo "  .onnx:     $(ls "$GENERIC_DIR"/*.onnx 2>/dev/null)"
echo ""
echo "To swap models, edit MODEL= at the top of this script and re-run."
