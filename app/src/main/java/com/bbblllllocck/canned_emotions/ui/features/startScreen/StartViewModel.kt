package com.bbblllllocck.canned_emotions.ui.features.startScreen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bbblllllocck.canned_emotions.core.algorithm.TemplateManager
import com.bbblllllocck.canned_emotions.core.player.WeightBreakdown
import com.bbblllllocck.canned_emotions.core.database.geminiRequestCall.EmbeddingCall
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import com.bbblllllocck.canned_emotions.core.player.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StartUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val mode: SearchMode = SearchMode.SYMMETRIC,
    val playlist: List<MusicScanTaskEntity> = emptyList(),
    val certainList: List<MusicScanTaskEntity> = emptyList(),
    val uncertainList: List<MusicScanTaskEntity> = emptyList(),
    val currentIndex: Int? = null,
    val pendingAutoPlayIndex: Int? = null,
    val seedSongs: List<MusicScanTaskEntity> = emptyList(),
    val selectedSeedSong: MusicScanTaskEntity? = null,
    val isSeedPickerVisible: Boolean = false,
    val seedPickerQuery: String = "",
    val weightBreakdowns: Map<Long, WeightBreakdown> = emptyMap(),
    val showWeightDetails: Boolean = false,
    val certainBufferSize: Int = 0
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

    init {
        refreshFromPlaylist()
        viewModelScope.launch {
            TemplateManager.observeWeightDisplayEnabled().collectLatest { enabled ->
                _state.update { it.copy(showWeightDetails = enabled) }
            }
        }
    }

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

    fun playPrevious(): MusicScanTaskEntity? {
        Playlist.playFront()
        return refreshFromPlaylist()
    }

    fun playNext(): MusicScanTaskEntity? {
        Playlist.playNext()
        return refreshFromPlaylist()
    }

    fun autoAdvance(): MusicScanTaskEntity? {
        Playlist.onAutoAdvance()
        return refreshFromPlaylist()
    }

    fun switchToIndex(index: Int): MusicScanTaskEntity? {
        Playlist.switchToSong(index)
        return refreshFromPlaylist()
    }

    fun deleteAtIndex(index: Int) {
        if (index < 0) return
        Playlist.delFromList(index)
        refreshFromPlaylist()
    }

    fun deleteById(songId: Long) {
        val certainIndex = Playlist.certainList.indexOfFirst { it.id == songId }
        if (certainIndex >= 0) {
            deleteAtIndex(certainIndex)
            return
        }
        val uncertainIndex = Playlist.uncertainList.indexOfFirst { it.id == songId }
        if (uncertainIndex >= 0) {
            deleteAtIndex(Playlist.certainList.size + uncertainIndex)
        }
    }

    fun updateBufferSize(size: Int) {
        Playlist.updateBufferSize(size)
        refreshFromPlaylist()
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
        Playlist.selectInitialSong(song.id)
        savedStateHandle[KEY_SELECTED_SEED_ID] = song.id
        _state.update { it.copy(isSeedPickerVisible = false, seedPickerQuery = "") }
        refreshFromPlaylist(selectedSeedSong = song)
    }

    fun chooseRandomSeedSong() {
        Playlist.randomInitialSong()
        val seed = Playlist.certainList.firstOrNull()
        if (seed != null) {
            savedStateHandle[KEY_SELECTED_SEED_ID] = seed.id
        }
        refreshFromPlaylist(selectedSeedSong = seed)
    }

    fun startPlaylist() {
        val seed = _state.value.selectedSeedSong ?: return
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            if (Playlist.certainList.isEmpty() || Playlist.certainList.firstOrNull()?.id != seed.id) {
                Playlist.selectInitialSong(seed.id)
            }
            Playlist.buildFromSeed()
            savedStateHandle[KEY_SELECTED_SEED_ID] = seed.id
            refreshFromPlaylist(pendingAutoPlayIndex = Playlist.currentIndex, selectedSeedSong = seed)
        }
    }

    fun searchAndStart() {
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
                val seed = incoming.firstOrNull()
                if (seed != null) {
                    Playlist.selectInitialSong(seed.id)
                    Playlist.buildFromSeed()
                    savedStateHandle[KEY_SELECTED_SEED_ID] = seed.id
                }
                refreshFromPlaylist(pendingAutoPlayIndex = Playlist.currentIndex, selectedSeedSong = seed)
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

    private fun refreshFromPlaylist(
        pendingAutoPlayIndex: Int? = null,
        selectedSeedSong: MusicScanTaskEntity? = _state.value.selectedSeedSong
    ): MusicScanTaskEntity? {
        val certainSnapshot = Playlist.certainList.toList()
        val uncertainSnapshot = Playlist.uncertainList.take(MAX_UNCERTAIN_SHOWN)
        val playlistSnapshot = buildList {
            addAll(certainSnapshot)
            addAll(uncertainSnapshot)
        }
        val currentSong = certainSnapshot.getOrNull(Playlist.currentIndex)
        val currentIndex = if (certainSnapshot.isNotEmpty()) {
            Playlist.currentIndex.coerceAtLeast(0)
        } else {
            null
        }
        val breakdownSnapshot = Playlist.weightBreakdowns.toMap()

        savedStateHandle[KEY_CURRENT_INDEX] = currentIndex
        _state.update {
            it.copy(
                playlist = playlistSnapshot,
                certainList = certainSnapshot,
                uncertainList = uncertainSnapshot,
                currentIndex = currentIndex,
                pendingAutoPlayIndex = pendingAutoPlayIndex,
                isLoading = false,
                selectedSeedSong = selectedSeedSong,
                weightBreakdowns = breakdownSnapshot,
                certainBufferSize = Playlist.certainBufferSize
            )
        }
        return currentSong
    }

    companion object {
        private const val SEARCH_LIMIT = 80
        private const val MAX_UNCERTAIN_SHOWN = 50
        private const val KEY_INPUT_TEXT = "start_input_text"
        private const val KEY_MODE = "start_mode"
        private const val KEY_CURRENT_INDEX = "start_current_index"
        private const val KEY_SELECTED_SEED_ID = "start_selected_seed_id"
    }
}

