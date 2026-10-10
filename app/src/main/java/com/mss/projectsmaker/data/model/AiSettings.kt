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
            AiProvider.GEMINI -> "gemini-2.5-flash"
            AiProvider.OPENAI -> "gpt-4o-mini"
            AiProvider.GROQ -> "llama-3.3-70b-versatile"
            AiProvider.CLAUDE -> "claude-3-5-sonnet-20241022"
            AiProvider.DEEPSEEK -> "deepseek-chat"
        }
    }

    companion object {
        fun getPresetModels(provider: AiProvider): List<String> {
            return when (provider) {
                AiProvider.GEMINI -> listOf(
                    "gemini-2.5-flash",
                    "gemini-2.5-pro",
                    "gemini-3.8-flash",
                    "gemini-3.5-flash-lite"
                )
                AiProvider.OPENAI -> listOf(
                    "gpt-4o-mini",
                    "gpt-4o",
                    "o3-mini"
                )
                AiProvider.GROQ -> listOf(
                    "llama-3.3-70b-versatile",
                    "llama-3.1-8b-instant",
                    "mixtral-8x7b-32768"
                )
                AiProvider.CLAUDE -> listOf(
                    "claude-3-5-sonnet-20241022",
                    "claude-3-5-haiku-20241022"
                )
                AiProvider.DEEPSEEK -> listOf(
                    "deepseek-chat",
                    "deepseek-reasoner"
                )
            }
        }
    }
}
