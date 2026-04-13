package com.bbblllllocck.canned_emotions.ui.features.scanScreen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScanScreen() {
    val scanViewModel: ScanViewModel = viewModel()
    val state by scanViewModel.state.collectAsState()

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri: Uri? ->
        scanViewModel.onDirectoryPicked(pickedUri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = scanViewModel::startScan,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isScanning
        ) {
            Text(if (state.isScanning) "扫描中..." else "开始扫描")
        }

        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "自定义目录",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "已添加目录 (${state.selectedDirectories.size})",
            style = MaterialTheme.typography.bodyMedium
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.selectedDirectories, key = { it.treeUri }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = item.displayPath, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Uri: ${item.treeUri}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "上次写入: ${item.lastInsertedCount} 条", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Button(
            onClick = { directoryPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isScanning
        ) {
            Text("添加目录")
        }
    }
}
