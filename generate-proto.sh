#!/bin/bash
# 生成protobuf Java类的脚本

# 创建目标目录
mkdir -p src/main/java

# 使用protoc生成Java类
protoc --java_out=src/main/java src/main/proto/chat.proto

echo "Protobuf Java classes generated successfully!"