package com.cornspace.aichat.ui.screen.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cornspace.aichat.data.remote.ConnectionState

@Composable
fun StatusCard(
    isServiceRunning: Boolean,
    connectionState: ConnectionState,
    smsCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gateway Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusIndicator(
                    isRunning = isServiceRunning,
                    connectionState = connectionState
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusItem(
                    icon = if (isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    label = "Service",
                    value = if (isServiceRunning) "Running" else "Stopped",
                    color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
                )

                StatusItem(
                    icon = when (connectionState) {
                        is ConnectionState.Connected -> Icons.Default.CloudDone
                        is ConnectionState.Connecting -> Icons.Default.CloudSync
                        else -> Icons.Default.CloudOff
                    },
                    label = "WebSocket",
                    value = when (connectionState) {
                        is ConnectionState.Connected -> "Connected"
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.Error -> "Error"
                        is ConnectionState.Disconnected -> "Disconnected"
                    },
                    color = when (connectionState) {
                        is ConnectionState.Connected -> Color(0xFF4CAF50)
                        is ConnectionState.Connecting -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(
    isRunning: Boolean,
    connectionState: ConnectionState
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(12.dp),
            shape = MaterialTheme.shapes.small,
            color = when {
                connectionState is ConnectionState.Connected -> Color(0xFF4CAF50)
                connectionState is ConnectionState.Connecting -> Color(0xFFFF9800)
                isRunning -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
        ) {}

        Text(
            text = when {
                connectionState is ConnectionState.Connected -> "Online"
                connectionState is ConnectionState.Connecting -> "Connecting"
                isRunning -> "Starting"
                else -> "Offline"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                connectionState is ConnectionState.Connected -> Color(0xFF4CAF50)
                connectionState is ConnectionState.Connecting -> Color(0xFFFF9800)
                isRunning -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
        )
    }
}

@Composable
fun StatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
