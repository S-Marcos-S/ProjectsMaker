package com.mss.projectsmaker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mss.projectsmaker.data.model.AiProvider
import com.mss.projectsmaker.data.model.AiSettings
import com.mss.projectsmaker.data.repository.PreferencesRepository
import com.mss.projectsmaker.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val aiSettings by viewModel.aiSettings.collectAsState()
    val studentProfile by viewModel.studentProfile.collectAsState()

    var selectedProvider by remember(aiSettings) { mutableStateOf(aiSettings.provider) }
    var apiKey by remember(aiSettings) { mutableStateOf(aiSettings.apiKey) }
    var customModel by remember(aiSettings) { mutableStateOf(aiSettings.customModel) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var studentName by remember(studentProfile) { mutableStateOf(studentProfile.name) }
    var studentRa by remember(studentProfile) { mutableStateOf(studentProfile.ra) }
    var studentCpf by remember(studentProfile) { mutableStateOf(studentProfile.cpf) }

    var providerMenuExpanded by remember { mutableStateOf(false) }
    var savedFeedback by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Configuration Section
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Provedor e Chave da IA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Provider Dropdown
                    ExposedDropdownMenuBox(
                        expanded = providerMenuExpanded,
                        onExpandedChange = { providerMenuExpanded = !providerMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedProvider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Provedor de IA") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false }
                        ) {
                            AiProvider.values().forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        selectedProvider = provider
                                        providerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // API Key Field
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Chave de API (${selectedProvider.displayName})") },
                        placeholder = { Text("Cole sua API Key aqui...") },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Alternar visibilidade"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom model (optional)
                    OutlinedTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        label = { Text("Modelo (Opcional)") },
                        placeholder = { Text(AiSettings(provider = selectedProvider).getDefaultModel()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Student Information Section
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Dados do Aluno (Cabeçalho do Word)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Nome do Aluno") },
                        placeholder = { Text("Ex: Marcos Silqueira Sabino") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = studentRa,
                        onValueChange = { studentRa = it },
                        label = { Text("RA (Registro Acadêmico)") },
                        placeholder = { Text("Ex: 3836450702") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = studentCpf,
                        onValueChange = { studentCpf = it },
                        label = { Text("CPF") },
                        placeholder = { Text("Ex: 160.358.706-35") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (savedFeedback) {
                Text(
                    text = "Configurações salvas com sucesso!",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = {
                    viewModel.saveSettings(
                        AiSettings(
                            provider = selectedProvider,
                            apiKey = apiKey.trim(),
                            customModel = customModel.trim()
                        ),
                        PreferencesRepository.StudentProfile(
                            name = studentName.trim(),
                            ra = studentRa.trim(),
                            cpf = studentCpf.trim()
                        )
                    )
                    savedFeedback = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Salvar Configurações", fontWeight = FontWeight.Bold)
            }
        }
    }
}
