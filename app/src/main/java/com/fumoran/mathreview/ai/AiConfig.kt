package com.fumoran.mathreview.ai

import android.content.Context
import kotlinx.serialization.Serializable

@Serializable
data class AiConfig(
    val preset: String = "gemini",
    val protocol: String = "gemini",
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val maxTokens: Int = 4096
)

data class AiPreset(
    val id: String,
    val name: String,
    val protocol: String,
    val baseUrl: String,
    val model: String
)

object AiPresets {
    val all: List<AiPreset> = listOf(
        AiPreset("gemini", "Google Gemini", "gemini", "https://generativelanguage.googleapis.com", "gemini-2.0-flash"),
        AiPreset("openai", "OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o-mini"),
        AiPreset("anthropic", "Anthropic Claude", "anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-20241022"),
        AiPreset("ark", "火山方舟", "openai", "https://ark.cn-beijing.volces.com/api/v3", "doubao-1-5-vision-pro-32k-250115"),
        AiPreset("qwen", "通义千问", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-vl-max"),
        AiPreset("zhipu", "智谱 GLM", "openai", "https://open.bigmodel.cn/api/paas/v4", "glm-4v-flash"),
        AiPreset("kimi", "Kimi", "openai", "https://api.moonshot.cn/v1", "moonshot-v1-8k-vision-preview"),
        AiPreset("openrouter", "OpenRouter", "openai", "https://openrouter.ai/api/v1", "google/gemini-2.0-flash-001"),
        AiPreset("custom", "自定义", "openai", "", "")
    )

    fun of(id: String): AiPreset = all.firstOrNull { it.id == id } ?: all.first()

    fun defaultConfig(): AiConfig {
        val p = of("gemini")
        return AiConfig(preset = p.id, protocol = p.protocol, baseUrl = p.baseUrl, model = p.model)
    }
}

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("mathreview", Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(value) {
            sp.edit().putString("theme", value).apply()
        }

    fun loadAiConfig(): AiConfig {
        val preset = sp.getString("ai_preset", "gemini") ?: "gemini"
        val def = AiPresets.of(preset)
        return AiConfig(
            preset = preset,
            protocol = sp.getString("ai_protocol", def.protocol) ?: def.protocol,
            baseUrl = sp.getString("ai_base", def.baseUrl) ?: def.baseUrl,
            model = sp.getString("ai_model", def.model) ?: def.model,
            apiKey = sp.getString("ai_key", "") ?: "",
            maxTokens = sp.getInt("ai_max_tokens", 4096)
        )
    }

    fun saveAiConfig(cfg: AiConfig) {
        sp.edit()
            .putString("ai_preset", cfg.preset)
            .putString("ai_protocol", cfg.protocol)
            .putString("ai_base", cfg.baseUrl)
            .putString("ai_model", cfg.model)
            .putString("ai_key", cfg.apiKey)
            .putInt("ai_max_tokens", cfg.maxTokens)
            .apply()
    }
}
