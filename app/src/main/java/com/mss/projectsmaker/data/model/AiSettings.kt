package com.mss.projectsmaker.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AiProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI("OpenAI (ChatGPT)"),
    GROQ("Groq (Llama / Mixtral)"),
    CLAUDE("Anthropic Claude"),
    DEEPSEEK("DeepSeek")
}

@Serializable
data class AiSettings(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    val customModel: String = ""
) {
    fun getDefaultModel(): String {
        if (customModel.isNotBlank()) return customModel
        return when (provider) {
            AiProvider.GEMINI -> "gemini-1.5-flash"
            AiProvider.OPENAI -> "gpt-4o-mini"
            AiProvider.GROQ -> "llama-3.3-70b-versatile"
            AiProvider.CLAUDE -> "claude-3-5-sonnet-20241022"
            AiProvider.DEEPSEEK -> "deepseek-chat"
        }
    }
}
