#!/bin/bash
# Downloads sherpa-onnx native libraries and the TTS model for Alfred AI
# Run this once before building the app.

set -e

SHERPA_VERSION="1.12.28"
MODEL="vits-piper-en_GB-southern_english_male-medium"

echo "=== Downloading sherpa-onnx v${SHERPA_VERSION} Android native libraries ==="

LIBS_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-v${SHERPA_VERSION}-android.tar.bz2"
LIBS_ARCHIVE="sherpa-onnx-android.tar.bz2"

if [ ! -f "$LIBS_ARCHIVE" ]; then
    wget -O "$LIBS_ARCHIVE" "$LIBS_URL"
fi

echo "Extracting native libraries..."
tar xjf "$LIBS_ARCHIVE"

# The archive extracts to ./jniLibs/{abi}/*.so
# Copy into the Android project's jniLibs directory
DEST="app/src/main/jniLibs"

for ABI in arm64-v8a armeabi-v7a; do
    mkdir -p "$DEST/$ABI"
    cp jniLibs/$ABI/libsherpa-onnx-jni.so "$DEST/$ABI/"
    cp jniLibs/$ABI/libonnxruntime.so "$DEST/$ABI/"
    echo "  Copied $ABI libs"
done

echo ""
echo "=== Downloading TTS model: ${MODEL} ==="

ASSETS="app/src/main/assets"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${MODEL}.tar.bz2"
MODEL_ARCHIVE="${MODEL}.tar.bz2"

if [ ! -f "$MODEL_ARCHIVE" ]; then
    wget -O "$MODEL_ARCHIVE" "$MODEL_URL"
fi

echo "Extracting model to assets..."
mkdir -p "$ASSETS"
tar xjf "$MODEL_ARCHIVE" -C "$ASSETS"

# Clean up test files to reduce APK size
rm -rf "$ASSETS/$MODEL/test_wavs" 2>/dev/null || true
rm -f "$ASSETS/$MODEL"/*.sh "$ASSETS/$MODEL"/README.md 2>/dev/null || true

echo ""
echo "=== Done! ==="
echo ""
echo "Files placed:"
echo "  JNI libs:  app/src/main/jniLibs/arm64-v8a/"
echo "  Model:     app/src/main/assets/${MODEL}/"
echo ""
echo "You can now build the app in Android Studio."
