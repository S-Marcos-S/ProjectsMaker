package com.mss.projectsmaker.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mss.projectsmaker.data.model.AiSettings
import com.mss.projectsmaker.data.repository.PreferencesRepository
import com.mss.projectsmaker.domain.ai.AiReportGenerator
import com.mss.projectsmaker.domain.docx.DocxProcessor
import com.mss.projectsmaker.domain.docx.GeneratedReportContent
import com.mss.projectsmaker.domain.pdf.PdfExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val templateDocxUri: Uri? = null,
    val templateFileName: String? = null,
    val guidePdfUri: Uri? = null,
    val guideFileName: String? = null,
    val extraContext: String = "",
    val isLoading: Boolean = false,
    val loadingStage: String = "",
    val statusMessage: String = "",
    val isError: Boolean = false,
    val detectedHeadings: List<String> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefRepo = PreferencesRepository(application.applicationContext)
    private val pdfExtractor = PdfExtractor(application.applicationContext)
    private val docxProcessor = DocxProcessor(application.applicationContext)
    private val aiGenerator = AiReportGenerator()

    val aiSettings: StateFlow<AiSettings> = prefRepo.aiSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings())

    val studentProfile: StateFlow<PreferencesRepository.StudentProfile> = prefRepo.studentProfileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesRepository.StudentProfile())

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun selectTemplateDocx(uri: Uri) {
        val fileName = getFileName(uri)
        _uiState.update {
            it.copy(
                templateDocxUri = uri,
                templateFileName = fileName,
                statusMessage = "",
                isError = false
            )
        }
        // Analyze template sections in background
        viewModelScope.launch {
            docxProcessor.analyzeTemplate(uri)
                .onSuccess { headings ->
                    _uiState.update { it.copy(detectedHeadings = headings) }
                }
        }
    }

    fun selectGuidePdf(uri: Uri) {
        val fileName = getFileName(uri)
        _uiState.update {
            it.copy(
                guidePdfUri = uri,
                guideFileName = fileName,
                statusMessage = "",
                isError = false
            )
        }
    }

    fun updateExtraContext(text: String) {
        _uiState.update { it.copy(extraContext = text) }
    }

    fun saveSettings(settings: AiSettings, profile: PreferencesRepository.StudentProfile) {
        viewModelScope.launch {
            prefRepo.saveAiSettings(settings)
            prefRepo.saveStudentProfile(profile)
        }
    }

    fun startGeneration(outputDocxUri: Uri) {
        val state = _uiState.value
        val templateUri = state.templateDocxUri
        val guideUri = state.guidePdfUri

        if (templateUri == null || guideUri == null) {
            _uiState.update {
                it.copy(
                    statusMessage = "Selecione o modelo Word e o guia em PDF antes de iniciar.",
                    isError = true
                )
            }
            return
        }

        val settings = aiSettings.value
        if (settings.apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    statusMessage = "Por favor, configure sua chave de API nas Configurações (ícone de engrenagem).",
                    isError = true
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingStage = "1/3 Extraindo texto do PDF do guia...",
                    statusMessage = "",
                    isError = false
                )
            }

            // Step 1: Extract PDF
            val pdfResult = pdfExtractor.extractText(guideUri)
            val pdfText = pdfResult.getOrElse {
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        statusMessage = "Erro ao ler o PDF: ${it.localizedMessage}",
                        isError = true
                    )
                }
                return@launch
            }

            // Step 2: Call AI
            _uiState.update {
                it.copy(loadingStage = "2/3 IA redigindo o relatório e inserindo pontos de prints...")
            }

            val aiResult = aiGenerator.generateReport(
                settings = settings,
                guidePdfText = pdfText,
                templateSections = state.detectedHeadings,
                userExtraContext = state.extraContext
            )

            val generatedReport = aiResult.getOrElse {
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        statusMessage = "Erro na geração pela IA: ${it.localizedMessage}",
                        isError = true
                    )
                }
                return@launch
            }

            // Merge student profile if configured
            val profile = studentProfile.value
            val enrichedReport = generatedReport.copy(
                studentName = profile.name,
                studentRa = profile.ra,
                studentCpf = profile.cpf
            )

            // Step 3: Inject into DOCX
            _uiState.update {
                it.copy(loadingStage = "3/3 Injetando conteúdo no modelo Word...")
            }

            val docxResult = docxProcessor.generateDocument(
                templateUri = templateUri,
                outputUri = outputDocxUri,
                content = enrichedReport
            )

            docxResult.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingStage = "",
                        statusMessage = "Documento gerado com sucesso! Arquivo salvo.",
                        isError = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingStage = "",
                        statusMessage = "Erro ao salvar Word: ${err.localizedMessage}",
                        isError = true
                    )
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Arquivo"
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
