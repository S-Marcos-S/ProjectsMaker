package com.mss.projectsmaker.domain.docx

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream
import java.io.OutputStream

data class DocxSection(
    val title: String,
    val initialParagraphIndex: Int
)

data class GeneratedReportContent(
    val title: String = "",
    val studentName: String = "",
    val studentRa: String = "",
    val studentCpf: String = "",
    val tutor: String = "",
    val resumo: String = "",
    val introducao: String = "",
    val objetivos: String = "",
    val metodos: String = "",
    val resultados: String = "",
    val conclusao: String = ""
)

class DocxProcessor(private val context: Context) {

    suspend fun analyzeTemplate(uri: Uri): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = XWPFDocument(inputStream)
                val detectedHeadings = mutableListOf<String>()

                for (p in document.paragraphs) {
                    val text = p.text.trim()
                    if (text.isNotBlank() && (p.style?.contains("Heading", ignoreCase = true) == true ||
                                text.equals("Introdução", ignoreCase = true) ||
                                text.equals("Objetivos", ignoreCase = true) ||
                                text.equals("Métodos", ignoreCase = true) ||
                                text.equals("Resultados", ignoreCase = true) ||
                                text.equals("Conclusão", ignoreCase = true) ||
                                text.equals("Resumo", ignoreCase = true))) {
                        detectedHeadings.add(text)
                    }
                }
                document.close()
                detectedHeadings
            } ?: throw IllegalStateException("Não foi possível ler o arquivo modelo .docx.")
        }
    }

    suspend fun generateDocument(
        templateUri: Uri,
        outputUri: Uri,
        content: GeneratedReportContent
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val templateBytes = context.contentResolver.openInputStream(templateUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Não foi possível abrir o template.")

            val document = XWPFDocument(templateBytes.inputStream())

            // Define which section we are currently inside
            var currentSection: String? = null
            var firstParagraphInSection = false

            // Replace personal metadata if present
            for (p in document.paragraphs) {
                val fullText = p.text

                // Replace student info if configured
                if (content.studentName.isNotBlank() && fullText.contains("Aluno:", ignoreCase = true)) {
                    replaceParagraphText(p, "Aluno: ${content.studentName}")
                    continue
                }
                if (content.studentRa.isNotBlank() && fullText.contains("RA:", ignoreCase = true)) {
                    replaceParagraphText(p, "RA: ${content.studentRa}")
                    continue
                }
                if (content.studentCpf.isNotBlank() && fullText.contains("CPF:", ignoreCase = true)) {
                    replaceParagraphText(p, "CPF: ${content.studentCpf}")
                    continue
                }
                if (content.title.isNotBlank() && (fullText.contains("Relatório de Aula Prática -", ignoreCase = true) || fullText.contains("Relatorio de Aula", ignoreCase = true))) {
                    replaceParagraphText(p, "Relatório de Aula Prática - ${content.title}")
                    continue
                }

                // Detect Section Headings
                val trimmed = fullText.trim()
                val normalized = trimmed.lowercase()

                when {
                    normalized == "resumo" -> {
                        currentSection = "resumo"
                        firstParagraphInSection = true
                        continue
                    }
                    normalized == "introdução" || normalized == "introducao" -> {
                        currentSection = "introducao"
                        firstParagraphInSection = true
                        continue
                    }
                    normalized == "objetivos" || normalized == "objetivo" -> {
                        currentSection = "objetivos"
                        firstParagraphInSection = true
                        continue
                    }
                    normalized == "métodos" || normalized == "metodos" || normalized == "metodologia" -> {
                        currentSection = "metodos"
                        firstParagraphInSection = true
                        continue
                    }
                    normalized == "resultados" || normalized == "resultado" -> {
                        currentSection = "resultados"
                        firstParagraphInSection = true
                        continue
                    }
                    normalized == "conclusão" || normalized == "conclusao" -> {
                        currentSection = "conclusao"
                        firstParagraphInSection = true
                        continue
                    }
                    normalized.startsWith("sumário") || normalized.startsWith("sumario") -> {
                        currentSection = "sumario"
                        continue
                    }
                }

                // Fill sections
                if (currentSection != null && currentSection != "sumario") {
                    val sectionContent = when (currentSection) {
                        "resumo" -> content.resumo
                        "introducao" -> content.introducao
                        "objetivos" -> content.objetivos
                        "metodos" -> content.metodos
                        "resultados" -> content.resultados
                        "conclusao" -> content.conclusao
                        else -> null
                    }

                    if (sectionContent != null && sectionContent.isNotBlank()) {
                        if (firstParagraphInSection) {
                            // Inject content into the first paragraph of this section
                            replaceParagraphText(p, sectionContent)
                            firstParagraphInSection = false
                        } else {
                            // Clear succeeding paragraphs until the next section
                            replaceParagraphText(p, "")
                        }
                    }
                }
            }

            // Save output
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                document.write(out)
                out.flush()
            } ?: throw IllegalStateException("Não foi possível salvar o arquivo de saída.")

            document.close()
        }
    }

    private fun replaceParagraphText(p: org.apache.poi.xwpf.usermodel.XWPFParagraph, newText: String) {
        // Remove existing runs
        while (p.runs.isNotEmpty()) {
            p.removeRun(0)
        }
        if (newText.isNotBlank()) {
            // Split by lines or format placeholders
            val lines = newText.split("\n")
            lines.forEachIndexed { index, line ->
                val run = p.createRun()
                run.fontSize = 12
                run.fontFamily = "Calibri"
                run.setText(line)
                if (index < lines.size - 1) {
                    run.addBreak()
                }
            }
        }
    }
}
