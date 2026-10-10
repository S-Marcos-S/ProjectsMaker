package com.mss.projectsmaker.domain.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfExtractor(private val context: Context) {

    suspend fun extractText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    val fullText = stripper.getText(document)
                    fullText.trim()
                }
            } ?: throw IllegalStateException("Não foi possível abrir o arquivo PDF selecionado.")
        }
    }
}
