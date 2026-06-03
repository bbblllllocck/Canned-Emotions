package com.bbblllllocck.canned_emotions.core.player


import com.bbblllllocck.canned_emotions.core.algorithm.Algorithm
import com.bbblllllocck.canned_emotions.core.algorithm.TemplateManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class WeightBreakdown(
    val baseSimilarity: Float,
    val integratedParameter: Float,
    val baseScore: Float,
    val artistDuplicateWeight: Float,
    val albumDuplicateWeight: Float,
    val songTypeWeight: Float,
    val punishmentWeight: Float,
    val artistDuplicatePenalty: Float,
    val albumDuplicatePenalty: Float,
    val songTypePenalty: Float,
    val punishmentPenalty: Float,
    val finalScore: Float
)

object Playlist {

    // Core lists，这里我想的是，这俩list就直接和UI绑一块得了。
    val certainList = mutableListOf<MusicScanTaskEntity>()
    //uncertainList可能包含很多，但是UI只显示前50
    val uncertainList = mutableListOf<MusicScanTaskEntity>()
    //两个列表的交界处在UI上是一个云朵

    //UI不显示
    val deletedSongs = mutableListOf<MusicScanTaskEntity>()

    // 权重展示缓存（songId -> breakdown）
    val weightBreakdowns = mutableMapOf<Long, WeightBreakdown>()

    // 当前播放位置（certainList 内的索引）
    var currentIndex: Int = 0

    // Buffer size between current song and the uncertain list.
    // 以当前正在播放的歌为基准，在certainList内往后数certainBufferSize首歌为缓冲区。也就是说，现在播放的歌总是云朵上方的第certainBufferSize首歌。
    // 这个云朵在UI上是可以拖动的，所以这个长度要可以实时更新
    // 这个值可以为0，所以切歌的逻辑要考虑列表沟通的延迟
    var certainBufferSize: Int = 5

    // Session-level parameters persisted outside the lists.
    val sessionParameters = SessionParameters()

    //以上参数都要做持久化储存防止activity被杀

    var usingTemplate= TemplateManager.getUsingTemplate()

    // 持久化相关
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    var isRestoredFromPersistence: Boolean = false
        private set

    fun restoreFromPersistence() {
        val state = PlaylistPersistence.restore() ?: return
        certainList.clear()
        certainList.addAll(state.certainList)
        uncertainList.clear()
        uncertainList.addAll(state.uncertainList)
        deletedSongs.clear()
        deletedSongs.addAll(state.deletedSongs)
        currentIndex = state.currentIndex
        certainBufferSize = state.certainBufferSize
        sessionParameters.punishmentWeights.clear()
        sessionParameters.punishmentWeights.putAll(state.sessionParameters.punishmentWeights)
        sessionParameters.lineageRoamingDirection = state.sessionParameters.lineageRoamingDirection
        isRestoredFromPersistence = certainList.isNotEmpty()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = persistenceScope.launch {
            delay(500) // 防抖 500ms
            PlaylistPersistence.save(
                certainList = certainList.toList(),
                uncertainList = uncertainList.toList(),
                deletedSongs = deletedSongs.toList(),
                currentIndex = currentIndex,
                certainBufferSize = certainBufferSize,
                sessionParameters = sessionParameters
            )
        }
    }



    //列表长度监听/控制。
    //以当前正在播放的歌为基准（可能还要声明当前的位置 ，是的应该是要声明的），在certainList内往后数certainBufferSize首歌为缓冲区。
    //如果缓冲区里面没有这么多首歌，把uncertainList的第一首歌挪到certainList末尾来，直到缓冲区有certainBufferSize首歌。
    //如果uncertainList里面的歌小于或大于50首，触发rebuildFromCertainList()




    //方法相关

    fun selectInitialSong(songId: Long) {
        //对应UI的选定按钮，选了之后，certainList变成只有这一首歌，uncertainList清空，session参数重置，deletedSongs也清空。
        val songs = DatabaseManager.listVectorReadySongs()
        val seed = songs.firstOrNull { it.id == songId } ?: return

        certainList.clear()
        uncertainList.clear()
        deletedSongs.clear()
        weightBreakdowns.clear()
        sessionParameters.reset()

        certainList.add(seed)
        currentIndex = 0
        scheduleSave()
        //播放器播放
    }

    fun randomInitialSong() {
        //对应UI的随机按钮，随机选一首歌，certainList变成只有这一首歌，uncertainList清空，session参数重置，deletedSongs也清空。
        val songs = DatabaseManager.listVectorReadySongs()
        val seed = songs.randomOrNull() ?: return

        certainList.clear()
        uncertainList.clear()
        deletedSongs.clear()
        weightBreakdowns.clear()
        sessionParameters.reset()

        certainList.add(seed)
        currentIndex = 0
        scheduleSave()
        //播放
    }

    fun buildFromSeed() {
        if (certainList.isEmpty()) return
        rebuild()
        ensureBuffer()
        scheduleSave()
    }

    fun updateBufferSize(newSize: Int) {
        val clamped = newSize.coerceAtLeast(0)
        if (clamped == certainBufferSize) return
        certainBufferSize = clamped
        rebalanceBuffer(allowRebuild = false, forceShrink = true)
        scheduleSave()
    }

    fun rebuild() {
        // 这个函数的作用是如果Activity被杀uncertainList的长度不足，用这玩意生成后续
        // uncertainList最多取前50，certainList，session参数和deletedSongs作为参数传给algorithm的那个函数rebuild
        // 然后返回的列表接在uncertainlist上。如果此时uncertainList的长度大于50，那后面的都不要，把返回值接上就好了。
        val head = uncertainList.take(50).toMutableList()
        val generated = Algorithm().rebuild(
            certainList = certainList,
            uncertainList = head,
            deletedSongs = deletedSongs,
            sessionParameters = sessionParameters
        )

        uncertainList.clear()
        uncertainList.addAll(head)
        uncertainList.addAll(generated.songs)
        weightBreakdowns.clear()
        weightBreakdowns.putAll(generated.breakdowns)
    }

    fun recalculateUncertainList() {
        val regenerated = Algorithm().rebuild(
            certainList = certainList,
            uncertainList = null,
            deletedSongs = deletedSongs,
            sessionParameters = sessionParameters
        )

        uncertainList.clear()
        uncertainList.addAll(regenerated.songs)
        weightBreakdowns.clear()
        weightBreakdowns.putAll(regenerated.breakdowns)
        // 这个函数的作用是根据certainList和session参数来重计算uncertainList，只受新增的惩罚向量触发，毕竟别的可以迭代计算。
        // 把certainList，deletedSongs，session参数作为参数传给algorithm的那个函数rebuild，返回的列表直接替换uncertainList。
    }

    fun playFront() {
        if (currentIndex > 0) {
            currentIndex -= 1
            val lastSong = certainList.getOrNull(currentIndex)
            if (lastSong != null) {
                DatabaseManager.updateIntegratedParameter(lastSong.id)
            }
        }
    }

    fun playNext() {
        // 只对应Ui的切歌按钮，正常播放不会触发机制。
        // 当前播放位置往后挪，同时通知播放器播放CertainList里的下一首。
        // sessionParameters.punishmentWeights里面新增一个惩罚权重，键是被切掉的这首歌前面所有歌的duration(ms)之和，值是被切掉的这首歌的向量与其它所有歌的向量的相似度与歌曲id的键值对。
        // 触发一次recalculateUncertainList()，因为session参数变了。
        val currentSong = certainList.getOrNull(currentIndex) ?: return
        if (shouldApplySwitchPunishment()) {
            addPunishmentWeight(currentSong, usingTemplate?.punishmentVectorWeight ?: 0.15f, currentIndex)
        }

        if (shouldApplySwitchPunishment()) {
            recalculateUncertainList()
        }

        currentIndex += 1
        if (currentIndex >= certainList.size && uncertainList.isNotEmpty()) {
            certainList.add(uncertainList.removeAt(0))
        }
        if (currentIndex >= certainList.size) {
            currentIndex = certainList.lastIndex
            return
        }

        val nextSong = certainList.getOrNull(currentIndex)
        if (nextSong != null) {
            DatabaseManager.updateIntegratedParameter(nextSong.id)
        }

        ensureBuffer()
        scheduleSave()
    }

    //暂停和拖进度条就直接让UI和播放器沟通吧

    fun onAutoAdvance() {
        currentIndex += 1
        if (currentIndex >= certainList.size && uncertainList.isNotEmpty()) {
            certainList.add(uncertainList.removeAt(0))
        }
        if (currentIndex >= certainList.size) {
            currentIndex = certainList.lastIndex
            return
        }

        val nextSong = certainList.getOrNull(currentIndex)
        if (nextSong != null) {
            DatabaseManager.updateIntegratedParameter(nextSong.id)
        }

        ensureBuffer()
        scheduleSave()
    }

    fun switchToSong(targetIndex: Int) {
        // 这里要跳转到的歌可以是在certainList里面也可以在uncertainList里面，所以前面的长度监听要考虑这一点
        // sessionParameters.punishmentWeights里面新增一个惩罚权重，键是被切掉的这首歌前面所有歌的duration(ms)之和，值是被切掉的这首歌的向量与其它所有歌的向量的相似度*0.5倍与歌曲id的键值对。
        // 将当前播放位置改成要播放的位置，同时通知播放器播放CertainList里的对应位置的歌。
        // 触发一次recalculateUncertainList()，因为session参数变了。
        val currentSong = certainList.getOrNull(currentIndex) ?: return
        if (shouldApplySwitchPunishment()) {
            addPunishmentWeight(currentSong,
                usingTemplate?.punishmentVectorWeightWhenSwitch ?: 0.075f, currentIndex)
        }

        if (targetIndex < certainList.size) {
            currentIndex = targetIndex.coerceAtLeast(0)
        } else {
            val uncertainIndex = targetIndex - certainList.size
            val picked = uncertainList.getOrNull(uncertainIndex) ?: return
            uncertainList.removeAt(uncertainIndex)
            certainList.add(picked)
            currentIndex = certainList.lastIndex
        }

        val nextSong = certainList.getOrNull(currentIndex)
        if (nextSong != null) {
            DatabaseManager.updateIntegratedParameter(nextSong.id)
        }

        ensureBuffer()
        if (shouldApplySwitchPunishment()) {
            recalculateUncertainList()
        }
        scheduleSave()
    }

    fun delFromList(targetIndex: Int) {
        // 把一首歌从播放列表里面删除，被删除的歌加入deletedSongs，这里被删的歌可以是在certainList里面也可以在uncertainList里面，所以前面的长度监听要考虑这一点
        // sessionParameters.punishmentWeights里面新增一个惩罚权重，键是被切掉的这首歌前面所有歌的duration(ms)之和，值是被切掉的这首歌的向量与其它所有歌的相似度与歌曲id的键值对。
        if (targetIndex < certainList.size) {
            val removed = certainList.removeAt(targetIndex)
            deletedSongs.add(removed)
            val timeIndex = if (targetIndex <= currentIndex) targetIndex else currentIndex
            addPunishmentWeight(removed, 1f, timeIndex)
            if (currentIndex > targetIndex) {
                currentIndex -= 1
            }
        } else {
            val uncertainIndex = targetIndex - certainList.size
            val removed = uncertainList.getOrNull(uncertainIndex) ?: return
            uncertainList.removeAt(uncertainIndex)
            deletedSongs.add(removed)
            addPunishmentWeight(removed, usingTemplate?.punishmentVectorWeightWhenSwitch ?: 0.075f, currentIndex)
        }

        ensureBuffer()
        recalculateUncertainList()
        scheduleSave()
    }

    //那么这里需要有一个函数监听播放器来考虑自动切歌（正常播放而不手动跳转）的情况。


    private fun ensureBuffer() {
        rebalanceBuffer(allowRebuild = true, forceShrink = false)
    }

    private fun rebalanceBuffer(allowRebuild: Boolean, forceShrink: Boolean = false) {
        val targetSize = (currentIndex + certainBufferSize + 1).coerceAtLeast(0)
        while (certainList.size < targetSize && uncertainList.isNotEmpty()) {
            certainList.add(uncertainList.removeAt(0))
        }
        if (forceShrink) {
            while (certainList.size > targetSize && certainList.size > currentIndex + 1) {
                val moved = certainList.removeAt(certainList.lastIndex)
                uncertainList.add(0, moved)
            }
        }

        if (allowRebuild && uncertainList.size <= 50) {
            rebuild()
        }
    }

    private fun addPunishmentWeight(
        sourceSong: MusicScanTaskEntity,
        scale: Float,
        timeIndex: Int
    ) {
        val sourceEmbedding = sourceSong.embedding ?: return
        val allSongs = DatabaseManager.listVectorReadySongs()
        if (allSongs.isEmpty()) return
        val timeKey = totalDurationBefore(timeIndex)

        // 关键点：获取或初始化该时间点的惩罚权重 Map 引用
        val weights = sessionParameters.punishmentWeights.getOrPut(timeKey) { mutableMapOf() }

        for (song in allSongs) {
            val embedding = song.embedding ?: continue
            val similarity = kotlin.math.max(0f, cosineSimilarity(sourceEmbedding, embedding) - 0.6f)
            val currentWeight = weights[song.id] ?: 0f
            // 直接修改 weights 引用对应的 Map 内容
            weights[song.id] = currentWeight + similarity * scale
        }
    }

    private fun totalDurationBefore(index: Int): Long {
        var sum = 0L
        val end = index.coerceAtMost(certainList.size)
        for (i in 0 until end) {
            val duration = certainList[i].durationMs
            if (duration > 0) sum += duration
        }
        return sum
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val size = minOf(a.size, b.size)
        if (size == 0) return 0f

        var dot = 0f
        for (i in 0 until size) {
            dot += a[i] * b[i]
        }
        // 既然 Gemini Embedding 2 已经做过 L2 归一化，直接返回内积即可
        return dot
    }

    private fun shouldApplySwitchPunishment(): Boolean {
        val template = TemplateManager.getUsingTemplate()
        return (template?.punishmentVectorWeight ?: 0f) > 0f
    }
}

// Session parameters are intentionally minimal for now.
data class SessionParameters(
    // key = trigger time (ms), value = songId -> weight
    val punishmentWeights: MutableMap<Long, MutableMap<Long, Float>> = mutableMapOf(),
    var lineageRoamingDirection: FloatArray? = null
) {
    fun reset() {
        punishmentWeights.clear()
        lineageRoamingDirection = null
    }
}