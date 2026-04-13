package com.bbblllllocck.canned_emotions.ui.features.startScreen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bbblllllocck.canned_emotions.core.database.geminiRequestCall.EmbeddingCall
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StartUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val mode: SearchMode = SearchMode.SYMMETRIC,
    val playlist: List<MusicScanTaskEntity> = emptyList(),
    val currentIndex: Int? = null,
    val pendingAutoPlayIndex: Int? = null,
    val seedSongs: List<MusicScanTaskEntity> = emptyList(),
    val selectedSeedSong: MusicScanTaskEntity? = null,
    val isSeedPickerVisible: Boolean = false,
    val seedPickerQuery: String = ""
)

class StartViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        StartUiState(
            inputText = savedStateHandle[KEY_INPUT_TEXT] ?: "",
            mode = savedStateHandle.get<String>(KEY_MODE)
                ?.let { runCatching { SearchMode.valueOf(it) }.getOrNull() }
                ?: SearchMode.SYMMETRIC,
            currentIndex = savedStateHandle[KEY_CURRENT_INDEX]
        )
    )
    val state: StateFlow<StartUiState> = _state.asStateFlow()

    fun updateInputText(value: String) {
        savedStateHandle[KEY_INPUT_TEXT] = value
        _state.update { it.copy(inputText = value) }
    }

    fun toggleMode() {
        val nextMode = if (_state.value.mode == SearchMode.SYMMETRIC) SearchMode.ASSIST else SearchMode.SYMMETRIC
        savedStateHandle[KEY_MODE] = nextMode.name
        _state.update { it.copy(mode = nextMode) }
    }

    fun consumeAutoPlayRequest() {
        _state.update { it.copy(pendingAutoPlayIndex = null) }
    }

    fun setCurrentIndex(index: Int?) {
        savedStateHandle[KEY_CURRENT_INDEX] = index
        _state.update { it.copy(currentIndex = index) }
    }

    fun nextIndexOrNull(): Int? {
        val next = (_state.value.currentIndex ?: -1) + 1
        return next.takeIf { it in _state.value.playlist.indices }
    }

    fun openSeedPicker() {
        _state.update { it.copy(isSeedPickerVisible = true, seedPickerQuery = "") }
        viewModelScope.launch { ensureSeedSongsLoaded(force = false) }
    }

    fun dismissSeedPicker() {
        _state.update { it.copy(isSeedPickerVisible = false) }
    }

    fun updateSeedPickerQuery(value: String) {
        _state.update { it.copy(seedPickerQuery = value) }
    }

    fun chooseSeedSong(song: MusicScanTaskEntity) {
        savedStateHandle[KEY_SELECTED_SEED_ID] = song.id
        savedStateHandle[KEY_CURRENT_INDEX] = 0
        _state.update {
            it.copy(
                selectedSeedSong = song,
                playlist = listOf(song),
                currentIndex = 0,
                pendingAutoPlayIndex = 0,
                isSeedPickerVisible = false,
                seedPickerQuery = ""
            )
        }
    }

    fun chooseRandomSeedSong() {
        viewModelScope.launch {
            val songs = ensureSeedSongsLoaded(force = false)
            if (songs.isEmpty()) return@launch
            chooseSeedSong(songs.random())
        }
    }

    fun startFromSelectedSeed() {
        val snapshot = _state.value
        val seed = snapshot.selectedSeedSong ?: return
        val seedEmbedding = seed.embedding ?: return
        if (snapshot.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            runCatching {
                withContext(Dispatchers.IO) {
                    DatabaseManager.searchTopSimilarByEmbedding(seedEmbedding, limit = SEARCH_LIMIT)
                }
            }.onSuccess { incoming ->
                _state.update { current ->
                    val rebuilt = buildList {
                        add(seed)
                        addAll(incoming)
                    }.distinctBy { it.filePath }

                    savedStateHandle[KEY_CURRENT_INDEX] = if (rebuilt.isEmpty()) null else 0
                    current.copy(
                        playlist = rebuilt,
                        currentIndex = if (rebuilt.isEmpty()) null else 0,
                        pendingAutoPlayIndex = null,
                        isLoading = false
                    )
                }
            }.onFailure {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun searchAndAppend() {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.mode == SearchMode.ASSIST || snapshot.inputText.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            runCatching {
                withContext(Dispatchers.IO) {
                    val vector = EmbeddingCall.embed(textInput = snapshot.inputText)
                    DatabaseManager.searchTopSimilarByEmbedding(vector, limit = SEARCH_LIMIT)
                }
            }.onSuccess { incoming ->
                _state.update { current ->
                    val replaced = incoming.distinctBy { it.filePath }
                    val nextCurrentIndex = if (replaced.isEmpty()) null else 0
                    val autoPlayIndex = nextCurrentIndex
                    savedStateHandle[KEY_CURRENT_INDEX] = nextCurrentIndex
                    current.copy(
                        playlist = replaced,
                        currentIndex = nextCurrentIndex,
                        pendingAutoPlayIndex = autoPlayIndex,
                        isLoading = false
                    )
                }
            }.onFailure {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun ensureSeedSongsLoaded(force: Boolean): List<MusicScanTaskEntity> {
        val cached = _state.value.seedSongs
        if (!force && cached.isNotEmpty()) return cached

        val songs = withContext(Dispatchers.IO) { DatabaseManager.listVectorReadySongs() }
        val selectedId = savedStateHandle.get<Long>(KEY_SELECTED_SEED_ID)
        val selected = songs.firstOrNull { it.id == selectedId }
        _state.update {
            it.copy(
                seedSongs = songs,
                selectedSeedSong = selected ?: it.selectedSeedSong
            )
        }
        return songs
    }

    companion object {
        private const val SEARCH_LIMIT = 80
        private const val KEY_INPUT_TEXT = "start_input_text"
        private const val KEY_MODE = "start_mode"
        private const val KEY_CURRENT_INDEX = "start_current_index"
        private const val KEY_SELECTED_SEED_ID = "start_selected_seed_id"
    }
}


