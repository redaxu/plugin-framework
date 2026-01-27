#!/bin/bash

# 构建插件脚本
echo "Building activity-plugin..."
cd activity-plugin
mvn clean package -P plugin,!spring-boot -DskipTests=true

if [ $? -eq 0 ]; then
    echo "Plugin built successfully!"
    echo "Copying plugin to plugins directory..."
    mkdir -p ../plugins
    cp target/activity-plugin.jar ../plugins/
    echo "Plugin deployed to plugins/"
else
    echo "Plugin build failed!"
    exit 1
fi

