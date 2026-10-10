package com.mss.projectsmaker.domain.ai

import com.mss.projectsmaker.data.model.AiProvider
import com.mss.projectsmaker.data.model.AiSettings
import com.mss.projectsmaker.domain.docx.GeneratedReportContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiReportGenerator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    suspend fun generateReport(
        settings: AiSettings,
        guidePdfText: String,
        templateSections: List<String>,
        userExtraContext: String
    ): Result<GeneratedReportContent> = withContext(Dispatchers.IO) {
        runCatching {
            if (settings.apiKey.isBlank()) {
                throw IllegalArgumentException("A chave de API não foi informada nas configurações.")
            }

            val prompt = buildPrompt(guidePdfText, templateSections, userExtraContext)

            val rawResponse = when (settings.provider) {
                AiProvider.GEMINI -> callGemini(settings, prompt)
                AiProvider.OPENAI,
                AiProvider.GROQ,
                AiProvider.DEEPSEEK -> callOpenAiCompatible(settings, prompt)
                AiProvider.CLAUDE -> callClaude(settings, prompt)
            }

            parseReportJson(rawResponse)
        }
    }

    private fun buildPrompt(
        guidePdfText: String,
        templateSections: List<String>,
        userExtraContext: String
    ): String {
        return """
Você é um especialista acadêmico e técnico encarregado de redigir um RELATÓRIO DE AULA PRÁTICA acadêmico completo e impecável.

OBJETIVO:
Você deve escrever o relatório como se o aluno TIVESSE REALIZADO E CONCLUÍDO COM SUCESSO todas as etapas descritas no guia de resolução da aula prática.
Escreva de maneira formal, clara, técnica e explicativa (linguagem acadêmica em 1ª pessoa do plural ou impessoal).

IMPORTANTE SOBRE IMAGENS/PRINTS:
- O relatório DEVE conter referências a figuras/prints e deixar o marcador explicito para a imagem ser inserida depois pelo aluno.
- Exemplo no texto: "Após a instalação do serviço, iniciamos a criação das tabelas no banco de dados, como ilustrado na Figura 1."
- E logo abaixo no parágrafo seguinte, coloque a marcação:
[FIGURA 1: Inserir print da tela mostrando a criação das tabelas no console/interface]
- Faça isso para todas as etapas práticas necessárias (Figura 1, Figura 2, Figura 3...).

CONTEÚDO DO GUIA DA AULA PRÁTICA (PDF):
$guidePdfText

CONTEXTO ADICIONAL DO ALUNO:
${if (userExtraContext.isNotBlank()) userExtraContext else "Nenhum contexto extra fornecido. Siga fielmente o roteiro."}

FORMATO DA RESPOSTA (MUITO IMPORTANTE):
Retorne ESTRITAMENTE um objeto JSON válido (sem tags markdown adicionais fora do JSON, ou com ```json) com a seguinte estrutura exata de chaves:
{
  "title": "Título resumido da aula prática ou tema abordado",
  "resumo": "Texto completo do resumo sintetizando o objetivo da aula, ferramentas usadas e resultados obtidos.",
  "introducao": "Texto completo da introdução conceituando o tema, tecnologias envolvidas e importância prática.",
  "objetivos": "Lista clara e detalhada dos objetivos atingidos no trabalho.",
  "metodos": "Passo a passo minucioso e explicativo de como a atividade foi executada, ferramentas instaladas/configuradas, com as marcações de figuras/prints.",
  "resultados": "Apresentação dos resultados finais, código/diagrama/consultas executadas, detalhamento do que foi alcançado e prints finais.",
  "conclusao": "Conclusão e considerações finais, explicando o aprendizado, classes/recursos criados e cumprimento de todos os requisitos do roteiro."
}
""".trimIndent()
    }

    private fun callGemini(settings: AiSettings, prompt: String): String {
        // Query the Gemini API directly for models that actually exist and support generateContent for this key
        val availableModels = fetchAvailableGeminiModels(settings.apiKey)

        val candidateModels = mutableListOf<String>()
        val requestedModel = settings.getDefaultModel().trim()

        if (requestedModel.isNotBlank()) {
            candidateModels.add(requestedModel)
        }

        // Add available models returned by the API
        for (m in availableModels) {
            val simpleName = m.removePrefix("models/")
            if (!candidateModels.contains(simpleName)) {
                candidateModels.add(simpleName)
            }
        }

        var lastException: Exception? = null

        for (model in candidateModels) {
            var attempts = 0
            val maxAttempts = 2
            var delayMs = 1500L

            while (attempts < maxAttempts) {
                attempts++
                val cleanModel = model.removePrefix("models/")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=${settings.apiKey}"

                val requestBody = buildJsonObject {
                    putJsonObject("system_instruction") {
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", "Você é um assistente acadêmico técnico. Você DEVE responder ESTRITAMENTE um JSON estruturado com os campos solicitados, sem preâmbulo, sem saudações e sem comentários fora do JSON.")
                            })
                        }
                    }
                    putJsonArray("contents") {
                        add(buildJsonObject {
                            putJsonArray("parts") {
                                add(buildJsonObject {
                                    put("text", prompt)
                                })
                            }
                        })
                    }
                    putJsonObject("generationConfig") {
                        put("responseMimeType", "application/json")
                        putJsonObject("responseSchema") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("title") { put("type", "string") }
                                putJsonObject("resumo") { put("type", "string") }
                                putJsonObject("introducao") { put("type", "string") }
                                putJsonObject("objetivos") { put("type", "string") }
                                putJsonObject("metodos") { put("type", "string") }
                                putJsonObject("resultados") { put("type", "string") }
                                putJsonObject("conclusao") { put("type", "string") }
                            }
                            putJsonArray("required") {
                                add(kotlinx.serialization.json.JsonPrimitive("title"))
                                add(kotlinx.serialization.json.JsonPrimitive("resumo"))
                                add(kotlinx.serialization.json.JsonPrimitive("introducao"))
                                add(kotlinx.serialization.json.JsonPrimitive("objetivos"))
                                add(kotlinx.serialization.json.JsonPrimitive("metodos"))
                                add(kotlinx.serialization.json.JsonPrimitive("resultados"))
                                add(kotlinx.serialization.json.JsonPrimitive("conclusao"))
                            }
                        }
                    }
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val responseString = response.body?.string().orEmpty()

                        if (response.code == 503 || response.code == 429) {
                            if (attempts < maxAttempts) {
                                Thread.sleep(delayMs)
                                delayMs *= 2
                                return@use
                            } else {
                                lastException = IllegalStateException("Modelo $cleanModel sobrecarregado (503/429). Tentando outro modelo...")
                                return@use
                            }
                        }

                        if (response.code == 404) {
                            // Model not found or retired, silently move to the next valid model
                            lastException = IllegalStateException("Modelo $cleanModel não encontrado (404).")
                            return@use
                        }

                        if (!response.isSuccessful) {
                            lastException = IllegalStateException("Erro na Gemini API ($cleanModel - ${response.code}): $responseString")
                            return@use
                        }

                        val root = json.parseToJsonElement(responseString).jsonObject
                        val text = root["candidates"]?.jsonArray?.getOrNull(0)?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.getOrNull(0)?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content
                            ?: throw IllegalStateException("Resposta inesperada do Gemini: $responseString")
                        return text
                    }
                } catch (e: java.net.UnknownHostException) {
                    throw IllegalStateException("Sem conexão com a internet ou erro de DNS ao acessar generativelanguage.googleapis.com.")
                } catch (e: Exception) {
                    lastException = e
                    if (attempts < maxAttempts) {
                        Thread.sleep(delayMs)
                        delayMs *= 2
                    }
                }
            }
        }

        throw lastException ?: IllegalStateException("Não foi possível gerar resposta com nenhum modelo Gemini disponível na sua conta.")
    }

    suspend fun getAvailableGeminiModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        fetchAvailableGeminiModels(apiKey).map { it.removePrefix("models/") }
    }

    private fun fetchAvailableGeminiModels(apiKey: String): List<String> {
        if (apiKey.isBlank()) return emptyList()
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val modelsArray = root["models"]?.jsonArray ?: return emptyList()

                modelsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val methods = obj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    if (methods.contains("generateContent")) name else null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun callOpenAiCompatible(settings: AiSettings, prompt: String): String {
        val baseUrl = when (settings.provider) {
            AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
            AiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
            AiProvider.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }

        val model = settings.getDefaultModel()

        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "Você é um assistente acadêmico técnico. Retorne SEMPRE sua resposta em formato JSON válido.")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(baseUrl)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Erro na API ${settings.provider.displayName} (${response.code}): $responseString")
            }
            val root = json.parseToJsonElement(responseString).jsonObject
            val text = root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
                ?: throw IllegalStateException("Resposta inesperada: $responseString")
            return text
        }
    }

    private fun callClaude(settings: AiSettings, prompt: String): String {
        val url = "https://api.anthropic.com/v1/messages"
        val model = settings.getDefaultModel()

        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", settings.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Erro na Anthropic Claude API (${response.code}): $responseString")
            }
            val root = json.parseToJsonElement(responseString).jsonObject
            val text = root["content"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
                ?: throw IllegalStateException("Resposta vazia da Claude API.")
            return text
        }
    }

    private fun parseReportJson(rawText: String): GeneratedReportContent {
        var clean = rawText.trim()

        // 1. If wrapped in markdown code blocks, extract inner content
        val markdownRegex = """```(?:json)?\s*([\s\S]*?)\s*```""".toRegex()
        val match = markdownRegex.find(clean)
        if (match != null) {
            clean = match.groupValues[1].trim()
        }

        // 2. Extract JSON object from first '{' to last '}' in case the model outputs introductory or conversational text
        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1).trim()
        }

        val jsonObject = try {
            json.parseToJsonElement(clean).jsonObject
        } catch (e: Exception) {
            throw IllegalStateException("Falha ao interpretar resposta da IA como JSON: ${e.message}\nResposta bruta: ${rawText.take(300)}")
        }

        return GeneratedReportContent(
            title = jsonObject["title"]?.jsonPrimitive?.content.orEmpty(),
            resumo = jsonObject["resumo"]?.jsonPrimitive?.content.orEmpty(),
            introducao = jsonObject["introducao"]?.jsonPrimitive?.content.orEmpty(),
            objetivos = jsonObject["objetivos"]?.jsonPrimitive?.content.orEmpty(),
            metodos = jsonObject["metodos"]?.jsonPrimitive?.content.orEmpty(),
            resultados = jsonObject["resultados"]?.jsonPrimitive?.content.orEmpty(),
            conclusao = jsonObject["conclusao"]?.jsonPrimitive?.content.orEmpty()
        )
    }
}
