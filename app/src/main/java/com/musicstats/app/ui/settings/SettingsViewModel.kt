package com.musicstats.app.ui.settings

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.export.ExportImportManager
import com.musicstats.app.data.export.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExportState {
    data object Idle : ExportState
    data object Exporting : ExportState
    data class Success(val json: String) : ExportState
    data class Error(val message: String) : ExportState
}

sealed interface ImportState {
    data object Idle : ImportState
    data object Importing : ImportState
    data class Success(val result: ImportResult) : ImportState
    data class Error(val message: String) : ImportState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val exportImportManager: ExportImportManager
) : AndroidViewModel(application) {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun exportData(onResult: (String) -> Unit) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            try {
                val json = exportImportManager.exportToJson()
                _exportState.value = ExportState.Success(json)
                onResult(json)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun importData(jsonString: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing
            try {
                val result = exportImportManager.importFromJson(jsonString)
                _importState.value = ImportState.Success(result)
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun isNotificationListenerEnabled(): Boolean {
        val context = getApplication<Application>()
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val componentName = ComponentName(
            context,
            "com.musicstats.app.service.MusicNotificationListener"
        )
        return flat?.contains(componentName.flattenToString()) == true
    }
}
