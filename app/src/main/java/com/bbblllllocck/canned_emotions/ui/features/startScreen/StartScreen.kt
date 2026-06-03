//这一整块配套的逻辑和功能全部由AI生成，易变动，不保证稳定性，可用性
package com.bbblllllocck.canned_emotions.ui.features.startScreen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.bbblllllocck.canned_emotions.R
import com.bbblllllocck.canned_emotions.core.player.WeightBreakdown
import com.bbblllllocck.canned_emotions.core.player.SimpleAudioPlayer
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Surface
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset

enum class SearchMode {
    SYMMETRIC,
    ASSIST
}

// ──── 权重详情展示组件 ────

@Composable
private fun WeightBreakdownDisplay(
    breakdown: WeightBreakdown,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "基础计算: 相似度(%.3f) - 疲劳(%.3f) = 基准分(%.3f)".format(breakdown.baseSimilarity, breakdown.integratedParameter, breakdown.baseScore),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "查重项权重: 艺术家(%.3f) | 专辑(%.3f) | 类型(%.3f) | 惩罚(%.3f)".format(
                    breakdown.artistDuplicateWeight, breakdown.albumDuplicateWeight, breakdown.songTypeWeight, breakdown.punishmentWeight
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "实际扣除: 艺术家(%.3f) | 专辑(%.3f) | 类型(%.3f) | 惩罚(%.3f)".format(
                    breakdown.artistDuplicatePenalty, breakdown.albumDuplicatePenalty, breakdown.songTypePenalty, breakdown.punishmentPenalty
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )

            Text(
                text = "最终得分: %.4f".format(breakdown.finalScore),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ──── 分界条组件（缓冲区滑块） ────

@Composable
private fun BufferDivider(
    certainBufferSize: Int,
    maxBufferSize: Int,
    onBufferSizeChange: (Int) -> Unit,
    isExpandedMode: Boolean = false,
    certainList: List<MusicScanTaskEntity> = emptyList(),
    uncertainList: List<MusicScanTaskEntity> = emptyList(),
    itemHeights: Map<Long, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val visualOffsetPx = remember { Animatable(0f) }
    var accumulatedLogicPx by remember { mutableStateOf(0f) }

    val currentBufferSize by rememberUpdatedState(certainBufferSize)
    val currentMaxBuffer by rememberUpdatedState(maxBufferSize)
    val currentCallback by rememberUpdatedState(onBufferSizeChange)
    val defaultStepHeightPx = with(density) { if (isExpandedMode) 170.dp.toPx() else 56.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, visualOffsetPx.value.roundToInt()) }
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        accumulatedLogicPx = 0f
                        scope.launch { visualOffsetPx.animateTo(0f, animationSpec = spring()) }
                    },
                    onDragCancel = {
                        accumulatedLogicPx = 0f
                        scope.launch { visualOffsetPx.animateTo(0f, animationSpec = spring()) }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (currentMaxBuffer <= 0) return@detectVerticalDragGestures

                        val newLogicPx = accumulatedLogicPx + dragAmount
                        // 硬限制边界：如果在顶端且往上拉，或在底端且往下拉，直接钳制
                        val clampedLogicPx = if (currentBufferSize == 0 && newLogicPx < 0) {
                            0f
                        } else if (currentBufferSize == currentMaxBuffer && newLogicPx > 0) {
                            0f
                        } else {
                            newLogicPx
                        }

                        accumulatedLogicPx = clampedLogicPx
                        
                        var steps = 0
                        var tempPx = accumulatedLogicPx
                        
                        if (tempPx > 0) {
                            // 向下拉，吃掉下面的 uncertainList 元素
                            for (i in 0 until uncertainList.size) {
                                val item = uncertainList[i]
                                val stepSize = itemHeights[item.id]?.toFloat() ?: defaultStepHeightPx
                                if (tempPx >= stepSize) {
                                    tempPx -= stepSize
                                    steps++
                                } else break
                            }
                        } else if (tempPx < 0) {
                            // 向上拉，吐出元素回到 uncertainList
                            for (i in certainList.indices.reversed()) {
                                val item = certainList[i]
                                val stepSize = itemHeights[item.id]?.toFloat() ?: defaultStepHeightPx
                                if (-tempPx >= stepSize) {
                                    tempPx += stepSize
                                    steps--
                                } else break
                            }
                        }

                        if (steps != 0) {
                            val next = (currentBufferSize + steps).coerceIn(0, currentMaxBuffer)
                            if (next != currentBufferSize) {
                                currentCallback(next)
                                accumulatedLogicPx = tempPx
                            }
                        }
                        scope.launch { visualOffsetPx.snapTo(accumulatedLogicPx) }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    painter = painterResource(id = R.drawable.ic_cloud),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(20.dp)
                        .width(20.dp)
                )
                Text(
                    text = "云朵分界",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "缓冲 $currentBufferSize",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ──── 歌曲列表项组件 ────

@Composable
private fun SongListItem(
    item: MusicScanTaskEntity,
    isCurrent: Boolean,
    breakdown: WeightBreakdown?,
    isVisible: Boolean,
    alphaModifier: Float = 1f,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        exit = shrinkVertically(
            animationSpec = tween(300)
        ) + fadeOut(
            animationSpec = tween(200)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alphaModifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = if (isCurrent) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isCurrent) "▶ ${item.title.ifBlank { "(无标题)" }} - ${item.artist.ifBlank { "未知艺术家" }}" else "${item.title.ifBlank { "(无标题)" }} - ${item.artist.ifBlank { "未知艺术家" }}",
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (breakdown != null) {
                    WeightBreakdownDisplay(
                        breakdown = breakdown,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text("删除")
            }
        }
    }
}

// ──── 主界面 ────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StartScreen() {
    val context = LocalContext.current
    val player = SimpleAudioPlayer
    val startViewModel: StartViewModel = viewModel()
    val uiState by startViewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()
    val listState = rememberLazyListState()
    val inputScrollState = rememberScrollState()
    val inputFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val certainList = uiState.certainList
    val uncertainList = uiState.uncertainList
    val combinedList = remember(certainList, uncertainList) { certainList + uncertainList }
    val currentItem = uiState.currentIndex?.let { certainList.getOrNull(it) }
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
    // 删除动画跟踪
    val removingIds = remember { mutableStateOf(setOf<Long>()) }
    val itemHeights = remember { mutableMapOf<Long, Int>() }

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

    fun forcePlaySong(item: MusicScanTaskEntity?) {
        if (item == null) return
        player.play(
            source = item.filePath,
            onPreparing = {},
            onPlaying = {},
            onCompleted = {
                val next = startViewModel.autoAdvance()
                forcePlaySong(next)
            },
            onError = {}
        )
    }

    fun playOrToggleSong(item: MusicScanTaskEntity?) {
        if (item == null) return
        player.playOrToggle(
            source = item.filePath,
            onPreparing = {},
            onPlaying = {},
            onCompleted = {
                val next = startViewModel.autoAdvance()
                forcePlaySong(next)
            },
            onError = {}
        )
    }

    fun playFromIndex(index: Int) {
        val item = startViewModel.switchToIndex(index)
        forcePlaySong(item)
    }

    fun triggerDelete(songId: Long) {
        if (removingIds.value.contains(songId)) return
        removingIds.value = removingIds.value + songId
        scope.launch {
            delay(320) // 等动画播完
            startViewModel.deleteById(songId)
            removingIds.value = removingIds.value - songId
        }
    }

    LaunchedEffect(uiState.pendingAutoPlayIndex) {
        val index = uiState.pendingAutoPlayIndex
        if (index != null) {
            val song = certainList.getOrNull(index) ?: uncertainList.getOrNull(index - certainList.size)
            if (song != null) {
                forcePlaySong(song)
            }
            startViewModel.consumeAutoPlayRequest()
        }
    }

    LaunchedEffect(bottomSheetScaffoldState.bottomSheetState.currentValue) {
        if (bottomSheetScaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            val index = uiState.currentIndex ?: return@LaunchedEffect
            val firstCertainItemIndex = 1
            val targetIndex = (firstCertainItemIndex + index).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
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
                        items(filteredSeedSongs.size, key = { filteredSeedSongs[it].id }) { index ->
                            val item = filteredSeedSongs[index]
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "从...开始",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.selectedSeedSong?.let {
                        "已选: ${it.title.ifBlank { "(无标题)" }} - ${it.artist.ifBlank { "未知艺术家" }}"
                    } ?: "未选择起始歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { startViewModel.openSeedPicker() },
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("选定")
                    }
                    
                    Surface(
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = ButtonDefaults.shape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .combinedClickable(
                                    onClick = { Toast.makeText(context, "请长按生效", Toast.LENGTH_SHORT).show() },
                                    onLongClick = {
                                        focusManager.clearFocus(force = true)
                                        isEditing = false
                                        startViewModel.chooseRandomSeedSong()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("随机", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    
                    val isStartEnabled = !uiState.isLoading && uiState.selectedSeedSong != null
                    Surface(
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = ButtonDefaults.shape,
                        color = if (isStartEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isStartEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .combinedClickable(
                                    enabled = isStartEnabled,
                                    onClick = { Toast.makeText(context, "请长按生效", Toast.LENGTH_SHORT).show() },
                                    onLongClick = {
                                        focusManager.clearFocus(force = true)
                                        isEditing = false
                                        startViewModel.startPlaylist()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (uiState.isLoading) "处理中" else "开始", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "播放列表",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Removed "确定区" text
                    itemsIndexed(certainList, key = { _, item -> item.id }) { index, item ->
                    val isCurrent = index == uiState.currentIndex
                    val breakdown = if (uiState.showWeightDetails) {
                        uiState.weightBreakdowns[item.id]
                    } else {
                        null
                    }
                    val isVisible = !removingIds.value.contains(item.id)

                    SongListItem(
                        item = item,
                        isCurrent = isCurrent,
                        breakdown = breakdown,
                        isVisible = isVisible,
                        onClick = { playFromIndex(index) },
                        onDelete = { triggerDelete(item.id) },
                        modifier = Modifier
                            .animateItem()
                            .onGloballyPositioned { itemHeights[item.id] = it.size.height }
                    )
                }

                if (uncertainList.isNotEmpty()) {
                    // ── 缓冲区分界条 ──
                    item(key = "BufferDivider") {
                        val bufferMax = (certainList.size + uncertainList.size - 1).coerceAtLeast(0)
                        val physicalBufferSize = (certainList.size - 1 - (uiState.currentIndex ?: 0)).coerceAtLeast(0)
                        BufferDivider(
                            certainBufferSize = physicalBufferSize,
                            maxBufferSize = bufferMax,
                            onBufferSizeChange = { startViewModel.updateBufferSize(it) },
                            isExpandedMode = uiState.showWeightDetails,
                            certainList = certainList,
                            uncertainList = uncertainList,
                            itemHeights = itemHeights
                        )
                    }

                    // Removed "不确定区" text
                    if (uiState.showUncertaintyArea) {
                        itemsIndexed(uncertainList, key = { _, item -> item.id }) { index, item ->
                            val breakdown = if (uiState.showWeightDetails) {
                                uiState.weightBreakdowns[item.id]
                            } else {
                                null
                            }
                            val listIndex = certainList.size + index
                            val isVisible = !removingIds.value.contains(item.id)
    
                            SongListItem(
                                item = item,
                                isCurrent = false,
                                breakdown = breakdown,
                                isVisible = isVisible,
                                alphaModifier = 0.6f,
                                onClick = { playFromIndex(listIndex) },
                                onDelete = { triggerDelete(item.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .onGloballyPositioned { itemHeights[item.id] = it.size.height }
                            )
                        }
                    } else {
                        item {
                            Spacer(modifier = Modifier.height(300.dp))
                        }
                    }
                }
            }
        }
    }
    ) { innerPadding ->
        // ── 自适应尺寸主内容区 ──
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            val screenHeight = maxHeight
            val isCompact = screenHeight < 600.dp
            val coverFraction = if (isCompact) 0.65f else 0.9f

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 5.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth(coverFraction)
                        .widthIn(max = 400.dp)
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
                        .fillMaxWidth(coverFraction)
                        .widthIn(max = 400.dp)
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
                            startViewModel.searchAndStart()
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.height(36.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text(if (uiState.isLoading) "检索中" else "检索")
                    }
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
                        .fillMaxWidth(coverFraction)
                        .widthIn(max = 400.dp)
                        .align(Alignment.CenterHorizontally)
                        .height(24.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isCompact) 32.dp else 56.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            isEditing = false
                            val previous = startViewModel.playPrevious()
                            forcePlaySong(previous)
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
                            if (combinedList.isEmpty()) return@Button
                            playOrToggleSong(currentItem ?: combinedList.firstOrNull())
                        },
                        modifier = Modifier.height(42.dp)
                    ) {
                        val activeTrackSource = uiState.currentIndex?.let { certainList.getOrNull(it)?.filePath }
                        val isCurrentPlaying = activeTrackSource != null && player.currentSource() == activeTrackSource && player.isPlaying()
                        Text(if (isCurrentPlaying) "暂停" else "播放")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            isEditing = false
                            val next = startViewModel.playNext()
                            forcePlaySong(next)
                        },
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("下一曲")
                    }
                }
            }
        }
    }
}
