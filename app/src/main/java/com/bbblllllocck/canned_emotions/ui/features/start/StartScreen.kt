//这一整块配套的逻辑和功能全部由AI生成，易变动，不保证稳定性，可用性
package com.bbblllllocck.canned_emotions.ui.features.start

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbblllllocck.canned_emotions.core.player.SimpleAudioPlayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

enum class SearchMode {
    SYMMETRIC,
    ASSIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen() {
    val context = LocalContext.current
    val player = remember { SimpleAudioPlayer(context) }
    val startViewModel: StartViewModel = viewModel()
    val uiState by startViewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()
    val inputScrollState = rememberScrollState()
    val inputFocusRequester = remember { FocusRequester() }
    val currentItem = uiState.currentIndex?.let { uiState.playlist.getOrNull(it) }
    val seedSongs = uiState.seedSongs
    val seedQuery = uiState.seedPickerQuery.trim()
    val filteredSeedSongs = remember(seedSongs, seedQuery) {
        if (seedQuery.isBlank()) {
            seedSongs
        } else {
            seedSongs.filter { song ->
                song.title.contains(seedQuery, ignoreCase = true) ||
                    song.artist.contains(seedQuery, ignoreCase = true) ||
                    song.album.contains(seedQuery, ignoreCase = true)
            }
        }
    }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var albumCover by remember { mutableStateOf<Bitmap?>(null) }
    var playbackPositionMs by remember { mutableStateOf(0) }
    var playbackDurationMs by remember { mutableStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableStateOf(0f) }

    fun loadAlbumCover(source: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (source.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(source))
            } else {
                retriever.setDataSource(source)
            }
            val bytes = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun playItem(index: Int) {
        if (index !in uiState.playlist.indices) return

        startViewModel.setCurrentIndex(index)
        val item = uiState.playlist[index]

        player.playOrToggle(
            source = item.filePath,
            onPreparing = {},
            onPlaying = {},
            onCompleted = {
                val nextIndex = startViewModel.nextIndexOrNull()
                if (nextIndex != null) {
                    playItem(nextIndex)
                }
            },
            onError = {}
        )
    }

    LaunchedEffect(uiState.pendingAutoPlayIndex) {
        val index = uiState.pendingAutoPlayIndex
        if (index != null) {
            playItem(index)
            startViewModel.consumeAutoPlayRequest()
        }
    }

    LaunchedEffect(currentItem?.filePath) {
        albumCover = currentItem?.filePath?.let(::loadAlbumCover)
    }

    LaunchedEffect(currentItem?.filePath) {
        while (true) {
            if (!isSeeking) {
                playbackPositionMs = player.currentPositionMs()
            }
            playbackDurationMs = player.durationMs()
            delay(300)
        }
    }

    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        isEditing = false
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            yield()
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    if (uiState.isSeedPickerVisible) {
        AlertDialog(
            onDismissRequest = { startViewModel.dismissSeedPicker() },
            title = { Text("选定起始歌曲") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.seedPickerQuery,
                        onValueChange = startViewModel::updateSeedPickerQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索歌曲") }
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredSeedSongs, key = { it.id }) { item ->
                            FilledTonalButton(
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    startViewModel.chooseSeedSong(item)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${item.title.ifBlank { "(无标题)" }} - ${item.artist.ifBlank { "未知艺术家" }}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { startViewModel.dismissSeedPicker() }) {
                    Text("关闭")
                }
            }
        )
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetScaffoldState,
        sheetPeekHeight = 48.dp,
        sheetDragHandle = {
            BottomSheetDefaults.DragHandle()
        },
        sheetContent = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "从...开始",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                item {
                    Text(
                        text = uiState.selectedSeedSong?.let {
                            "已选: ${it.title.ifBlank { "(无标题)" }} - ${it.artist.ifBlank { "未知艺术家" }}"
                        } ?: "未选择起始歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { startViewModel.openSeedPicker() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("选定")
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus(force = true)
                                isEditing = false
                                startViewModel.chooseRandomSeedSong()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("随机")
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus(force = true)
                                isEditing = false
                                startViewModel.startFromSelectedSeed()
                            },
                            enabled = !uiState.isLoading && uiState.selectedSeedSong != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (uiState.isLoading) "处理中" else "开始")
                        }
                    }
                }
                item {
                    Text(
                        text = "播放列表",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.playlist, key = { it.id }) { item ->
                    val listIndex = uiState.playlist.indexOfFirst { it.id == item.id }
                    FilledTonalButton(
                        onClick = { playItem(listIndex) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "${item.title.ifBlank { "(无标题)" }} - ${item.artist.ifBlank { "未知艺术家" }}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(5.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = currentItem?.title?.ifBlank { "(无标题)" } ?: "(无标题)",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentItem?.artist?.ifBlank { "未知艺术家" } ?: "未知艺术家",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(28.dp)
            ) {
                if (uiState.mode == SearchMode.SYMMETRIC) {
                    if (isEditing) {
                        BasicTextField(
                            value = uiState.inputText,
                            onValueChange = startViewModel::updateInputText,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .focusRequester(inputFocusRequester)
                                .verticalScroll(inputScrollState),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.inputText.isBlank()) {
                                        Text(
                                            text = "在这里输入检索文本",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { isEditing = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (albumCover != null) {
                                Image(
                                    bitmap = albumCover!!.asImageBitmap(),
                                    contentDescription = "当前播放专辑封面",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = currentItem?.album?.ifBlank { "未知专辑" } ?: "未知专辑",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        isEditing = false
                        startViewModel.toggleMode()
                    },
                    modifier = Modifier.height(36.dp),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text(if (uiState.mode == SearchMode.SYMMETRIC) "对称" else "辅助")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {},
                    modifier = Modifier.height(36.dp),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text("相机")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        isEditing = false
                        val previousPlaylistSize = uiState.playlist.size
                        startViewModel.searchAndAppend()
                        if (previousPlaylistSize == 0) {
                            player.stop()
                        }
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.height(36.dp),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text(if (uiState.isLoading) "检索中" else "检索")
                }
                // Spacer(modifier = Modifier.width(8.dp))
                // Button(
                //     onClick = {
                //         focusManager.clearFocus(force = true)
                //         isEditing = false
                //         scope.launch {
                //             bottomSheetScaffoldState.bottomSheetState.expand()
                //         }
                //     },
                //     modifier = Modifier.height(36.dp),
                //     contentPadding = ButtonDefaults.ContentPadding
                // ) {
                //     Text("列表")
                // }
            }

            Spacer(modifier = Modifier.weight(1f))

            Slider(
                value = if (isSeeking) seekPositionMs else playbackPositionMs.toFloat(),
                onValueChange = { value ->
                    isSeeking = true
                    val maxValue = playbackDurationMs.toFloat().coerceAtLeast(0f)
                    seekPositionMs = value.coerceIn(0f, maxValue)
                },
                onValueChangeFinished = {
                    val target = seekPositionMs.toInt().coerceAtLeast(0)
                    player.seekToMs(target)
                    playbackPositionMs = target
                    isSeeking = false
                },
                valueRange = 0f..playbackDurationMs.toFloat().coerceAtLeast(0f),
                enabled = playbackDurationMs > 0,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally)
                    .height(24.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        isEditing = false
                        val activeIndex = uiState.currentIndex ?: return@Button
                        val previousIndex = activeIndex - 1
                        if (previousIndex in uiState.playlist.indices) {
                            playItem(previousIndex)
                        }
                    },
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("上一曲")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        isEditing = false
                        if (uiState.playlist.isEmpty()) return@Button
                        val activeIndex = uiState.currentIndex ?: 0
                        playItem(activeIndex)
                    },
                    modifier = Modifier.height(42.dp)
                ) {
                    val activeTrackSource = uiState.currentIndex?.let { uiState.playlist.getOrNull(it)?.filePath }
                    val isCurrentPlaying = activeTrackSource != null && player.currentSource() == activeTrackSource && player.isPlaying()
                    Text(if (isCurrentPlaying) "暂停" else "播放")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        isEditing = false
                        val nextIndex = startViewModel.nextIndexOrNull()
                        if (nextIndex != null) {
                            playItem(nextIndex)
                        }
                    },
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("下一曲")
                }
            }
        }
    }
}
