package com.cornspace.aichat.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornspace.aichat.ui.screen.components.*
import com.cornspace.aichat.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var serverUrlInput by remember(uiState.serverUrl) { mutableStateOf(uiState.serverUrl) }
    var showSettings by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS
            )
        }
    }

    var hasAllPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAllPermissions = permissions.values.all { it }
        if (hasAllPermissions) {
            viewModel.loadDeviceInfo()
            // Request battery optimization after permissions granted
            requestBatteryOptimization(context)
        }
    }

    LaunchedEffect(hasAllPermissions) {
        if (!hasAllPermissions) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            // Already has permissions, still check battery optimization
            requestBatteryOptimization(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SMS Gateway",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            StatusCard(
                isServiceRunning = uiState.isServiceRunning,
                connectionState = uiState.connectionState,
                smsCount = uiState.smsCount
            )

            // // Server URL Input
            // OutlinedTextField(
            //     value = serverUrlInput,
            //     onValueChange = { serverUrlInput = it },
            //     label = { Text("Server URL") },
            //     placeholder = { Text("https://your-server.com") },
            //     modifier = Modifier.fillMaxWidth(),
            //     leadingIcon = {
            //         Icon(
            //             imageVector = Icons.Default.Cloud,
            //             contentDescription = null
            //         )
            //     },
            //     trailingIcon = {
            //         IconButton(onClick = {
            //             viewModel.setServerUrl(serverUrlInput)
            //         }) {
            //             Icon(
            //                 imageVector = Icons.Default.Save,
            //                 contentDescription = "Save"
            //             )
            //         }
            //     },
            //     singleLine = true
            // )

            // Action Buttons
            ActionButtons(
                isServiceRunning = uiState.isServiceRunning,
                isServerUrlConfigured = uiState.serverUrl.isNotBlank(),
                onStartClick = {
                    if (hasAllPermissions) {
                        viewModel.startService()
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                },
                onStopClick = {
                    viewModel.stopService()
                }
            )

            // SIM Info Card
            SimInfoCard(deviceInfo = uiState.deviceInfo)

            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Error display
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // // Settings Dialog
    // if (showSettings) {
    //     SettingsDialog(
    //         serverUrl = uiState.serverUrl,
    //         deviceId = uiState.deviceId,
    //         onDismiss = { showSettings = false },
    //         onSaveServerUrl = { url ->
    //             viewModel.setServerUrl(url)
    //             showSettings = false
    //         }
    //     )
    // }
}

// Battery optimization helper
private fun requestBatteryOptimization(context: Context) {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        // Some devices may not support this
    }
}

@Composable
fun SettingsDialog(
    serverUrl: String,
    deviceId: String,
    onDismiss: () -> Unit,
    onSaveServerUrl: (String) -> Unit
) {
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://your-server.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { },
                    label = { Text("Device ID") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveServerUrl(urlInput) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}