package com.fumoran.mathreview.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 三种协议：Gemini / Anthropic / OpenAI 兼容。 */
class AiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(240, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    suspend fun complete(
        cfg: AiConfig,
        system: String,
        user: String,
        images: List<ByteArray> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        if (cfg.apiKey.isBlank()) throw IOException("请先在设置中填写 API Key")
        if (cfg.model.isBlank()) throw IOException("请先填写模型名称")
        when (cfg.protocol) {
            "gemini" -> gemini(cfg, system, user, images)
            "anthropic" -> anthropic(cfg, system, user, images)
            else -> openai(cfg, system, user, images)
        }
    }

    suspend fun test(cfg: AiConfig): String =
        complete(cfg, "你是连通性测试助手。", "只回复两个字：正常")

    // ---------- Gemini ----------

    private fun gemini(cfg: AiConfig, system: String, user: String, images: List<ByteArray>): String {
        val base = cfg.baseUrl.trim().trimEnd('/').ifBlank { "https://generativelanguage.googleapis.com" }
        val url = "$base/v1beta/models/${cfg.model}:generateContent?key=${cfg.apiKey}"
        val body = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", user) })
                        images.forEach { img ->
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", "image/jpeg")
                                    put("data", Base64.encodeToString(img, Base64.NO_WRAP))
                                })
                            })
                        }
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", cfg.maxTokens)
                put("temperature", 0.2)
            })
        }
        val resp = post(url, body.toString(), mapOf("Content-Type" to "application/json"))
        val root = runCatching { json.parseToJsonElement(resp).jsonObject }
            .getOrElse { throw IOException("Gemini 响应解析失败：${resp.take(300)}") }
        val candidates = root["candidates"] as? JsonArray
            ?: throw IOException("Gemini 返回异常：${resp.take(300)}")
        val parts = candidates.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts") as? JsonArray
            ?: throw IOException("Gemini 返回为空")
        val text = parts.mapNotNull {
            runCatching { it.jsonObject["text"]?.jsonPrimitive?.content }.getOrNull()
        }.joinToString("")
        if (text.isBlank()) throw IOException("Gemini 返回为空")
        return text
    }

    // ---------- Anthropic ----------

    private fun anthropic(cfg: AiConfig, system: String, user: String, images: List<ByteArray>): String {
        val base = cfg.baseUrl.trim().trimEnd('/').ifBlank { "https://api.anthropic.com" }
        val url = if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
        val content = buildJsonArray {
            images.forEach { img ->
                add(buildJsonObject {
                    put("type", "image")
                    put("source", buildJsonObject {
                        put("type", "base64")
                        put("media_type", "image/jpeg")
                        put("data", Base64.encodeToString(img, Base64.NO_WRAP))
                    })
                })
            }
            add(buildJsonObject { put("type", "text"); put("text", user) })
        }
        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", cfg.maxTokens)
            if (system.isNotBlank()) put("system", system)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", content)
                })
            })
        }
        val resp = post(
            url,
            body.toString(),
            mapOf(
                "Content-Type" to "application/json",
                "x-api-key" to cfg.apiKey,
                "anthropic-version" to "2023-06-01"
            )
        )
        val root = runCatching { json.parseToJsonElement(resp).jsonObject }
            .getOrElse { throw IOException("Claude 响应解析失败：${resp.take(300)}") }
        val blocks = root["content"] as? JsonArray
            ?: throw IOException("Claude 返回异常：${resp.take(300)}")
        val text = blocks.mapNotNull {
            runCatching { it.jsonObject["text"]?.jsonPrimitive?.content }.getOrNull()
        }.joinToString("")
        if (text.isBlank()) throw IOException("Claude 返回为空")
        return text
    }

    // ---------- OpenAI 兼容 ----------

    private fun openai(cfg: AiConfig, system: String, user: String, images: List<ByteArray>): String {
        val base = cfg.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw IOException("请先填写 Base URL")
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val userContent = if (images.isEmpty()) {
            user
        } else {
            buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", user) })
                images.forEach { img ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:image/jpeg;base64," + Base64.encodeToString(img, Base64.NO_WRAP))
                        })
                    })
                }
            }.toString()
        }

        val messages = buildJsonArray {
            if (system.isNotBlank()) {
                add(buildJsonObject { put("role", "system"); put("content", system) })
            }
            add(buildJsonObject {
                put("role", "user")
                if (images.isEmpty()) put("content", user) else put("content", userContent)
            })
        }

        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", cfg.maxTokens)
            put("temperature", 0.2)
            put("messages", messages)
        }
        val resp = post(
            url,
            body.toString(),
            mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${cfg.apiKey}"
            )
        )
        val root = runCatching { json.parseToJsonElement(resp).jsonObject }
            .getOrElse { throw IOException("接口响应解析失败：${resp.take(300)}") }
        val message = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw IOException("接口返回异常：${resp.take(300)}")
        val content = message["content"]
        val text = when {
            content == null -> ""
            content is JsonArray -> content.mapNotNull {
                runCatching { it.jsonObject["text"]?.jsonPrimitive?.content }.getOrNull()
            }.joinToString("")
            else -> runCatching { content.jsonPrimitive.content }.getOrElse { "" }
        }
        if (text.isBlank()) throw IOException("接口返回为空")
        return text
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}：${text.take(300)}")
            }
            return text
        }
    }
}
