package com.musicstats.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.BuildConfig
import kotlinx.coroutines.launch

private val lenientJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportState by viewModel.exportState.collectAsState()
    val importState by viewModel.importState.collectAsState()

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPreviewText by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportJson != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingExportJson!!.toByteArray())
                }
                scope.launch { snackbarHostState.showSnackbar("Export saved successfully") }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Export failed: ${e.message}") }
            }
            pendingExportJson = null
            viewModel.resetExportState()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: return@rememberLauncherForActivityResult
                pendingImportJson = json
                // Parse to show preview count
                try {
                    val data = lenientJson.decodeFromString(
                        com.musicstats.app.data.export.ExportData.serializer(),
                        json
                    )
                    importPreviewText = "Import ${data.songs.size} songs and ${data.listeningEvents.size} events?"
                } catch (_: Exception) {
                    importPreviewText = "Import data from file?"
                }
                showImportDialog = true
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Failed to read file: ${e.message}") }
            }
        }
    }

    // React to import state changes
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> {
                snackbarHostState.showSnackbar(
                    "Imported ${state.result.songsImported} songs and ${state.result.eventsImported} events"
                )
                viewModel.resetImportState()
            }
            is ImportState.Error -> {
                snackbarHostState.showSnackbar("Import failed: ${state.message}")
                viewModel.resetImportState()
            }
            else -> {}
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportJson = null
            },
            title = { Text("Confirm Import") },
            text = { Text(importPreviewText) },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    pendingImportJson?.let { viewModel.importData(it) }
                    pendingImportJson = null
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    pendingImportJson = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // Data section
            SectionHeader("Data")

            SettingsRow(
                icon = Icons.Default.FileUpload,
                title = "Export Stats",
                description = "Save your listening data as a JSON file",
                onClick = {
                    viewModel.exportData { json ->
                        pendingExportJson = json
                        exportLauncher.launch("music_stats_export.json")
                    }
                }
            )

            SettingsRow(
                icon = Icons.Default.FileDownload,
                title = "Import Stats",
                description = "Restore listening data from a JSON file",
                onClick = {
                    importLauncher.launch(arrayOf("application/json"))
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Permissions section
            SectionHeader("Permissions")

            val listenerEnabled = viewModel.isNotificationListenerEnabled()
            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Notification Access",
                description = if (listenerEnabled) "Enabled" else "Tap to enable notification access",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                trailing = {
                    Icon(
                        imageVector = if (listenerEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = if (listenerEnabled) "Enabled" else "Disabled",
                        tint = if (listenerEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // About section
            SectionHeader("About")

            SettingsRow(
                icon = Icons.Default.Info,
                title = "Music Stats",
                description = "Track and visualize your music listening habits",
                onClick = null
            )

            SettingsRow(
                icon = null,
                title = "Version",
                description = BuildConfig.VERSION_NAME,
                onClick = null
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector?,
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}
