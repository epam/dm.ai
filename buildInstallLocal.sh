#!/bin/bash

# DMTools Local Build and Install Script
# This script builds the fat JAR and installs it to the local dmtools directory

set -e

echo "🔨 Building DMTools fat JAR..."
./gradlew :dmtools-core:shadowJar

echo "📦 Reading version from gradle.properties..."
VERSION=$(grep "^version=" gradle.properties | cut -d'=' -f2 | tr -d ' \t')
if [ -z "$VERSION" ]; then
    echo "❌ Error: Could not read version from gradle.properties"
    exit 1
fi
echo "📋 Found version: $VERSION"

echo "📦 Checking if fat JAR was created..."
FAT_JAR="build/libs/dmtools-v${VERSION}-all.jar"
if [ ! -f "$FAT_JAR" ]; then
    echo "❌ Error: Fat JAR not found at $FAT_JAR"
    exit 1
fi

echo "📁 Creating ~/.dmtools directory if it doesn't exist..."
mkdir -p ~/.dmtools

echo "🚀 Installing fat JAR to ~/.dmtools/dmtools.jar..."
cp "$FAT_JAR" ~/.dmtools/dmtools.jar

# Verify installation
echo "✅ Installation complete!"
echo "📊 JAR size: $(ls -lh ~/.dmtools/dmtools.jar | awk '{print $5}')"
echo "🕒 Modified: $(ls -l ~/.dmtools/dmtools.jar | awk '{print $6, $7, $8}')"

echo ""
echo "🎉 DMTools has been successfully built and installed locally!"
echo "💡 You can now run: ./dmtools.sh [command] [args...]"
