#!/bin/bash
# JavaCLI 一键启动脚本 (macOS / Linux)
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# 若未设置 JAVA_HOME，尝试检测 SDKMAN 默认 Java 路径
if [ -z "$JAVA_HOME" ] && [ -d "$HOME/.sdkman/candidates/java/current" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ ! -f "target/javacli-1.0-SNAPSHOT.jar" ]; then
    echo "📦 未检测到 target/javacli-1.0-SNAPSHOT.jar，正在快速编译..."
    mvn clean package -DskipTests
fi

exec java --enable-native-access=ALL-UNNAMED \
     -Dfile.encoding=UTF-8 \
     -Dstdout.encoding=UTF-8 \
     -Dstderr.encoding=UTF-8 \
     -jar target/javacli-1.0-SNAPSHOT.jar "$@"
