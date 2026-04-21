#!/bin/bash
# RikkaHub Build Environment Setup for ARM64 Android
# This script sets up the correct environment variables and ensures ARM64 tools are used

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/data/local/android-sdk-full
export ANDROID_SDK_ROOT=/data/local/android-sdk-full
export PATH="/data/local/android-tools/usr/bin:$PATH"

# Verify ARM64 aapt2 is in place
AAPT2_PATH="/data/local/android-sdk-full/build-tools/36.0.0/aapt2"
GRADLE_AAPT2_JAR="/root/.gradle/caches/modules-2/files-2.1/com.android.tools.build/aapt2/9.0.1-14304508/ebaf8ea0051e6e61f9cd82d16f3c2bef107cf0d/aapt2-9.0.1-14304508-linux.jar"

# Check if aapt2 in build-tools is ARM64
if [ -f "$AAPT2_PATH" ]; then
    ARCH=$(file "$AAPT2_PATH" | grep -o "arm64\|x86-64")
    if [ "$ARCH" != "arm64" ]; then
        echo "Warning: aapt2 is not ARM64, replacing..."
        cp /data/local/android-tools/usr/lib/android-sdk/build-tools/debian/aapt2 "$AAPT2_PATH"
    fi
fi

# Check if aapt2 in Gradle cache is ARM64 (repack jar if needed)
if [ -f "$GRADLE_AAPT2_JAR" ]; then
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR"
    jar xf "$GRADLE_AAPT2_JAR"
    CURRENT_ARCH=$(file aapt2 | grep -o "arm64\|x86-64")
    if [ "$CURRENT_ARCH" != "arm64" ]; then
        echo "Warning: Gradle cached aapt2 is x86-64, replacing with ARM64..."
        cp /data/local/android-sdk-full/build-tools/36.0.0/aapt2 aapt2
        jar cf aapt2-9.0.1-14304508-linux.jar META-INF NOTICE aapt2
        cp aapt2-9.0.1-14304508-linux.jar "$GRADLE_AAPT2_JAR"
    fi
    rm -rf "$TEMP_DIR"
fi

echo "Build environment ready for ARM64 Android"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
