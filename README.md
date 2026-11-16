# 数学复习本 · Android

原生 Android 客户端（Kotlin + Jetpack Compose），**不使用 WebView**。公式通过 JLatexMath 在本地渲染，定界符与裸公式的识别规则与网页端保持一致。

## 功能

- 错题库 / 知识点库双 Tab，搜索、状态、收藏、待复习、合集筛选
- 间隔重复（SRS）：未复习 / 复习中 / 已掌握，自动计算下次复习时间
- 合集树：嵌套、重命名、移动、删除（条目转为未归档）
- 配图：相册多选，自动压缩（最长边 1600，JPEG 85）并修正 EXIF 方向
- AI 能力：图片识别、整理为结构化条目、为已有条目建议标签 / 合集 / 关联知识点
- 三种 AI 协议：Gemini、Anthropic Claude、OpenAI 兼容（火山方舟、通义、智谱、Kimi、OpenRouter 等）
- 数据导入 / 导出：兼容网页版 v4 JSON（普通备份与含图完整备份），忽略试卷数据

## 与网页版数据兼容

导入时自动兼容：

- 顶层数组（旧版错题列表）
- `{version:4, questions, knowledge, collections:{q,k}, images}` 备份
- 字段名与网页版一致：`stem / analysis / knowledge / myAnswer / tags / status / star / images / collectionId / kpIds / srs`
- `papers` 字段会被忽略

导出为 v4 完整备份，图片以 base64 内嵌，可直接导回网页版。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 35）。

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

发布签名从 `keystore.properties` 读取（该文件与 `keystore/` 不入库）：

```properties
storeFile=keystore/release.jks
storePassword=***
keyAlias=***
keyPassword=***
```

```bash
./gradlew :app:assembleRelease
```

## 目录结构

```
app/src/main/java/com/fumoran/mathreview/
├── MainActivity.kt
├── AppViewModel.kt
├── data/        模型、JSON 兼容、合集树、持久化、图片压缩
├── ai/          AI 配置、三协议客户端、提示词
└── ui/          Compose 页面与组件
```

## 隐私

- 数据保存在应用私有目录（`filesDir/library.json` 与 `filesDir/images/`）
- API Key 仅保存在本机 SharedPreferences，不随备份导出
- 仅在你主动调用 AI 时才会把内容发送到你配置的服务商

## 许可

Copyright © FUMoRan. All Rights Reserved.

本仓库仅公开源代码供查看，未授予复制、修改、分发或商用许可，详见 [NOTICE.txt](NOTICE.txt)。
