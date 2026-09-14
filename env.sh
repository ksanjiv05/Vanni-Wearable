#!/usr/bin/env bash
# Vaani Android toolchain env. `source` before any gradle/adb command.
export JAVA_HOME="$HOME/.jdks/jdk-17.0.20.1+1"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
# Available: platforms android-36.1/37.0/34-ext12/33 ; build-tools 36.0.0/37.0.0 ; ndk 27.1.x
