package com.bbblllllocck.canned_emotions.ui.features.scanScreen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbblllllocck.canned_emotions.core.scan.FileScanner
import com.bbblllllocck.canned_emotions.core.scan.ScanDirectoryStore
import com.bbblllllocck.canned_emotions.core.scan.ScanPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectedDirectoryUi(
    val treeUri: String,
    val displayPath: String,
    val lastInsertedCount: Int = 0
)

data class ScanUiState(
    val selectedDirectories: List<SelectedDirectoryUi> = emptyList(),
    val statusMessage: String = "请选择目录并开始扫描",
    val isScanning: Boolean = false
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val directoryStore = ScanDirectoryStore(appContext)

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            directoryStore.directoriesFlow.collect { persisted ->
                val previous = _state.value.selectedDirectories.associateBy { it.treeUri }
                val merged = persisted.map { item ->
                    previous[item.treeUri]?.copy(displayPath = item.displayPath)
                        ?: SelectedDirectoryUi(
                            treeUri = item.treeUri,
                            displayPath = item.displayPath
                        )
                }
                _state.update { it.copy(selectedDirectories = merged) }
            }
        }
    }

    fun onDirectoryPicked(uri: Uri?) {
        if (uri == null) {
            _state.update { it.copy(statusMessage = "已取消目录选择") }
            return
        }

        ScanPathResolver.takePersistableReadPermission(appContext, uri)
        val displayPath = ScanPathResolver.resolveTreeUriToAbsolutePath(uri) ?: uri.toString()

        viewModelScope.launch {
            directoryStore.addOrReplaceDirectory(
                treeUri = uri.toString(),
                displayPath = displayPath
            )
            _state.update { it.copy(statusMessage = "已添加目录: $displayPath") }
        }
    }

    fun startScan() {
        val directories = _state.value.selectedDirectories
        if (directories.isEmpty()) {
            _state.update { it.copy(statusMessage = "请先添加至少一个目录") }
            return
        }
        if (_state.value.isScanning) return

        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, statusMessage = "准备开始扫描...") }

            val insertedByUri = mutableMapOf<String, Int>()
            var totalInserted = 0

            withContext(Dispatchers.IO) {
                directories.forEachIndexed { index, directory ->
                    _state.update {
                        it.copy(
                            statusMessage = "正在扫描 (${index + 1}/${directories.size}): ${directory.displayPath}"
                        )
                    }

                    val inserted = runCatching {
                        FileScanner.scanAndSave(appContext, Uri.parse(directory.treeUri))
                    }.getOrDefault(0)

                    insertedByUri[directory.treeUri] = inserted
                    totalInserted += inserted
                }
            }

            _state.update { current ->
                val updatedDirectories = current.selectedDirectories.map { directory ->
                    directory.copy(
                        lastInsertedCount = insertedByUri[directory.treeUri] ?: directory.lastInsertedCount
                    )
                }
                current.copy(
                    selectedDirectories = updatedDirectories,
                    statusMessage = "扫描完成：共写入 $totalInserted 条",
                    isScanning = false
                )
            }
        }
    }
}

