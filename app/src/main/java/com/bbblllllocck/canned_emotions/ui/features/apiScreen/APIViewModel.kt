package com.bbblllllocck.canned_emotions.ui.features.apiScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbblllllocck.canned_emotions.core.api.ApiManager
import com.bbblllllocck.canned_emotions.core.api.ApiCredential
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiItemUi(
    val id: String,
    val name: String,
    val maskedKey: String
)

data class ApiScreenState(
    val items: List<ApiItemUi> = emptyList(),
    val isDialogVisible: Boolean = false,
    val dialogMode: DialogMode = DialogMode.Add,
    val editingId: String? = null,
    val deletingId: String? = null,
    val deletingName: String = "",
    val nameInput: String = "",
    val keyInput: String = "",
    val keyHint: String = "请输入 API Key",
    val errorMessage: String? = null
)

enum class DialogMode {
    Add,
    Edit
}

class APIViewModel : ViewModel() {

    private val _state = MutableStateFlow(ApiScreenState())
    val state: StateFlow<ApiScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ApiManager.observeAll().collectLatest { allApis ->
                val mapped = allApis.map {
                    ApiItemUi(
                        id = it.id,
                        name = it.name,
                        maskedKey = maskApiKey(it.apiKey)
                    )
                }
                _state.update { it.copy(items = mapped) }
            }
        }
    }

    fun showAddDialog() {
        _state.update {
            it.copy(
                isDialogVisible = true,
                dialogMode = DialogMode.Add,
                editingId = null,
                nameInput = "",
                keyInput = "",
                keyHint = "请输入 API Key",
                errorMessage = null
            )
        }
    }

    fun showEditDialog(id: String) {
        val target = ApiManager.getAll().firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                isDialogVisible = true,
                dialogMode = DialogMode.Edit,
                editingId = id,
                nameInput = target.name,
                keyInput = "",
                keyHint = "留空表示保持原 Key",
                errorMessage = null
            )
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(isDialogVisible = false, errorMessage = null) }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(nameInput = value, errorMessage = null) }
    }

    fun onKeyChange(value: String) {
        _state.update { it.copy(keyInput = value, errorMessage = null) }
    }

    fun saveDialog() {
        val current = _state.value
        val name = current.nameInput.trim()
        if (name.isBlank()) {
            _state.update { it.copy(errorMessage = "名称不能为空") }
            return
        }

        when (current.dialogMode) {
            DialogMode.Add -> {
                val key = current.keyInput.trim()
                if (key.isBlank()) {
                    _state.update { it.copy(errorMessage = "API Key 不能为空") }
                    return
                }
                val inserted = ApiManager.insert(
                    ApiCredential(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        apiKey = key
                    )
                )
                if (!inserted) {
                    _state.update { it.copy(errorMessage = "ID 冲突，请重试") }
                    return
                }
            }

            DialogMode.Edit -> {
                val id = current.editingId ?: return
                val existing = ApiManager.getAll().firstOrNull { it.id == id } ?: return
                val key = current.keyInput.trim().ifBlank { existing.apiKey }
                ApiManager.upsert(existing.copy(name = name, apiKey = key))
            }
        }

        _state.update { it.copy(isDialogVisible = false, errorMessage = null) }
    }

    fun requestDelete(id: String) {
        val target = ApiManager.getAll().firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                deletingId = target.id,
                deletingName = target.name
            )
        }
    }

    fun dismissDeleteConfirm() {
        _state.update { it.copy(deletingId = null, deletingName = "") }
    }

    fun confirmDelete() {
        val id = _state.value.deletingId ?: return
        ApiManager.delete(id)
        _state.update { it.copy(deletingId = null, deletingName = "") }
    }

    private fun maskApiKey(key: String): String {
        val tail = key.takeLast(4)
        return "**** **** **** $tail"
    }
}