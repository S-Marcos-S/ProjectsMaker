package com.mss.projectsmaker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mss.projectsmaker.data.model.AiProvider
import com.mss.projectsmaker.data.model.AiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ai_settings_pref")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("ai_provider")
        val API_KEY = stringPreferencesKey("ai_api_key")
        val CUSTOM_MODEL = stringPreferencesKey("ai_custom_model")
        val STUDENT_NAME = stringPreferencesKey("student_name")
        val STUDENT_RA = stringPreferencesKey("student_ra")
        val STUDENT_CPF = stringPreferencesKey("student_cpf")
        val COURSE_NAME = stringPreferencesKey("course_name")
    }

    val aiSettingsFlow: Flow<AiSettings> = context.dataStore.data.map { preferences ->
        val providerStr = preferences[Keys.PROVIDER] ?: AiProvider.GEMINI.name
        val provider = try {
            AiProvider.valueOf(providerStr)
        } catch (e: Exception) {
            AiProvider.GEMINI
        }
        val apiKey = preferences[Keys.API_KEY] ?: ""
        val customModel = preferences[Keys.CUSTOM_MODEL] ?: ""
        AiSettings(provider = provider, apiKey = apiKey, customModel = customModel)
    }

    suspend fun saveAiSettings(settings: AiSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PROVIDER] = settings.provider.name
            preferences[Keys.API_KEY] = settings.apiKey
            preferences[Keys.CUSTOM_MODEL] = settings.customModel
        }
    }

    data class StudentProfile(
        val name: String = "",
        val ra: String = "",
        val cpf: String = "",
        val course: String = ""
    )

    val studentProfileFlow: Flow<StudentProfile> = context.dataStore.data.map { preferences ->
        StudentProfile(
            name = preferences[Keys.STUDENT_NAME] ?: "",
            ra = preferences[Keys.STUDENT_RA] ?: "",
            cpf = preferences[Keys.STUDENT_CPF] ?: "",
            course = preferences[Keys.COURSE_NAME] ?: ""
        )
    }

    suspend fun saveStudentProfile(profile: StudentProfile) {
        context.dataStore.edit { preferences ->
            preferences[Keys.STUDENT_NAME] = profile.name
            preferences[Keys.STUDENT_RA] = profile.ra
            preferences[Keys.STUDENT_CPF] = profile.cpf
            preferences[Keys.COURSE_NAME] = profile.course
        }
    }
}
