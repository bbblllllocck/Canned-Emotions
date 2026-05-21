package com.bbblllllocck.canned_emotions.core.algorithm

import android.util.Log
import com.bbblllllocck.canned_emotions.core.player.SessionParameters
import com.bbblllllocck.canned_emotions.core.player.WeightBreakdown
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

class Algorithm {
    fun rebuild(
        certainList: List<MusicScanTaskEntity>,
        // 新增传入一个 uncertainList，默认是 null，但是如果在调用时被传入，取其前 50 也进行遍历
        uncertainList: List<MusicScanTaskEntity>? = null,
        // 用于排除已删除歌曲
        deletedSongs: List<MusicScanTaskEntity> = emptyList(),
        sessionParameters: SessionParameters
    ): AlgorithmResult {

        /////////////////////////////权重演算////////////////////////////

        val artistDuplicateWeights: MutableMap<String, Float> = mutableMapOf()
        val albumDuplicateWeights: MutableMap<String, Float> = mutableMapOf()
        val songTypeWeight: MutableMap<String, Float> = mutableMapOf()

        // 这个函数的作用是根据 certainList 来构建 uncertainList。
        // 把 certainList 和 sessionParameters 传给 algorithm，接收 uncertainList。
        // 从第一首歌开始初始化也应该用这个。

        val usingTemplate = TemplateManager.getUsingTemplate() ?: return AlgorithmResult.empty()

        var timeTillNow = 0f
        var previous: MusicScanTaskEntity? = null
        var lastChosen: MusicScanTaskEntity? = certainList.lastOrNull()

        val recentUncertain = uncertainList?.take(50).orEmpty()
        val weightSources = buildList {
            addAll(certainList)
            addAll(recentUncertain)
        }

        val artistPardonFloor = -usingTemplate.artistPardonTime.toFloat() * usingTemplate.artistDuplicateCoefficient
        val albumPardonFloor = -usingTemplate.albumPardonTime.toFloat() * usingTemplate.albumDuplicateCoefficient

        for (song in weightSources) { // uncertainList 不为 null 的话就把 uncertainList 前 50 首也进行遍历
            val duration = song.durationMs.toFloat()
            timeTillNow += duration

            if (previous == null || previous.artist != song.artist) {
                val existing = artistDuplicateWeights[song.artist]
                artistDuplicateWeights[song.artist] = if (existing != null && existing < 0f) {
                    artistPardonFloor
                } else {
                    (existing ?: 0f) + artistPardonFloor
                }
            }

            if (previous == null || previous.album != song.album) {
                val existing = albumDuplicateWeights[song.album]
                albumDuplicateWeights[song.album] = if (existing != null && existing < 0f) {
                    albumPardonFloor
                } else {
                    (existing ?: 0f) + albumPardonFloor
                }
            }

            artistDuplicateWeights[song.artist] =
                (artistDuplicateWeights[song.artist] ?: 0f) + usingTemplate.artistDuplicateCoefficient * duration

            if (usingTemplate.artistDuplicateFadeTime > 0) {
                val decay = duration / usingTemplate.artistDuplicateFadeTime
                val keys = artistDuplicateWeights.keys.toList()
                for (key in keys) {
                    if (key != song.artist) {
                        val next = (artistDuplicateWeights[key] ?: 0f) - decay
                        artistDuplicateWeights[key] = if (next < artistPardonFloor) artistPardonFloor else next
                    }
                }
            }

            albumDuplicateWeights[song.album] =
                (albumDuplicateWeights[song.album] ?: 0f) + usingTemplate.albumDuplicateCoefficient * duration

            if (usingTemplate.albumDuplicateFadeTime > 0) {
                val decay = duration / usingTemplate.albumDuplicateFadeTime
                val keys = albumDuplicateWeights.keys.toList()
                for (key in keys) {
                    if (key != song.album) {
                        val next = (albumDuplicateWeights[key] ?: 0f) - decay
                        albumDuplicateWeights[key] = if (next < albumPardonFloor) albumPardonFloor else next
                    }
                }
            }

            if (song.musicType == 0) {//score -=this*系数
                songTypeWeight["PureMusic"] =
                    (songTypeWeight["PureMusic"] ?: 0f) + usingTemplate.songProportion * duration / usingTemplate.tolerance
                songTypeWeight["Song"] =
                    (songTypeWeight["Song"] ?: 0f) - usingTemplate.songProportion * duration / usingTemplate.tolerance
            } else if (song.musicType == 1) {
                songTypeWeight["Song"] =
                    (songTypeWeight["Song"] ?: 0f) + (1 - usingTemplate.songProportion) * duration / usingTemplate.tolerance
                songTypeWeight["PureMusic"] =
                    (songTypeWeight["PureMusic"] ?: 0f) - (1 - usingTemplate.songProportion) * duration / usingTemplate.tolerance
            }

            
            // integratedParameter 先不增加，因为它由具体的播放事件触发。

            previous = song
        }

        val baseExcludedIds = buildSet {
            addAll(certainList.map { it.id })
            addAll(deletedSongs.map { it.id })
            addAll(recentUncertain.map { it.id })
        }

        val integratedSimulated = mutableMapOf<Long, MusicScanTaskEntity>()
        fun getIntegratedState(song: MusicScanTaskEntity): MusicScanTaskEntity {
            return integratedSimulated.getOrPut(song.id) { song.copy() }
        }

        fun decayedIntegrated(song: MusicScanTaskEntity, now: Long): Float {
            val state = getIntegratedState(song)
            val lastPlayed = state.lastPlayedDate
            if (lastPlayed <= 0L || usingTemplate.integratedParameterHalfLife <= 0) {
                return state.integratedTimeParameter
            }
            val days = (now - lastPlayed).toFloat() / (24f * 60f * 60f * 1000f)
            val decay = exp(ln(0.5f) * (days / usingTemplate.integratedParameterHalfLife))
            return state.integratedTimeParameter * decay * usingTemplate.COEFFICIENT_OF_UNRELATED
        }

        fun baseScore(queryEmbedding: FloatArray, song: MusicScanTaskEntity, now: Long): Float {
            val embedding = song.embedding ?: return 0f
            val similarity = cosineSimilarity(queryEmbedding, embedding)
            val integrated = decayedIntegrated(song, now)
            Log.d("baseScore","歌：${song.title}，原始相似度${similarity}，integrated参数${integrated}，最终得分${similarity - integrated}")
            return similarity - integrated
        }

        fun applyWeightsFromSong(song: MusicScanTaskEntity) {//checked
            val duration = song.durationMs.toFloat()
            timeTillNow += duration

            if (lastChosen == null || lastChosen?.artist != song.artist) {
                val existing = artistDuplicateWeights[song.artist]
                artistDuplicateWeights[song.artist] = if (existing != null && existing < 0f) {
                    artistPardonFloor
                } else {
                    (existing ?: 0f) + artistPardonFloor
                }
            }

            if (lastChosen == null || lastChosen?.album != song.album) {
                val existing = albumDuplicateWeights[song.album]
                albumDuplicateWeights[song.album] = if (existing != null && existing < 0f) {
                    albumPardonFloor
                } else {
                    (existing ?: 0f) + albumPardonFloor
                }
            }

            artistDuplicateWeights[song.artist] =
                (artistDuplicateWeights[song.artist] ?: 0f) + usingTemplate.artistDuplicateCoefficient * duration

            if (usingTemplate.artistDuplicateFadeTime > 0) {
                val decay = duration / usingTemplate.artistDuplicateFadeTime
                val keys = artistDuplicateWeights.keys.toList()
                for (key in keys) {
                    if (key != song.artist) {
                        val next = (artistDuplicateWeights[key] ?: 0f) - decay
                        artistDuplicateWeights[key] = if (next < artistPardonFloor) artistPardonFloor else next
                    }
                }
            }

            albumDuplicateWeights[song.album] =
                (albumDuplicateWeights[song.album] ?: 0f) + usingTemplate.albumDuplicateCoefficient * duration

            if (usingTemplate.albumDuplicateFadeTime > 0) {
                val decay = duration / usingTemplate.albumDuplicateFadeTime
                val keys = albumDuplicateWeights.keys.toList()
                for (key in keys) {
                    if (key != song.album) {
                        val next = (albumDuplicateWeights[key] ?: 0f) - decay
                        albumDuplicateWeights[key] = if (next < albumPardonFloor) albumPardonFloor else next
                    }
                }
            }

            if (song.musicType == 0) {//score -=this*系数
                songTypeWeight["PureMusic"] =
                    (songTypeWeight["PureMusic"] ?: 0f) + usingTemplate.songProportion * duration / usingTemplate.tolerance
                songTypeWeight["Song"] =
                    (songTypeWeight["Song"] ?: 0f) - usingTemplate.songProportion * duration / usingTemplate.tolerance
            } else if (song.musicType == 1) {
                songTypeWeight["Song"] =
                    (songTypeWeight["Song"] ?: 0f) + (1 - usingTemplate.songProportion) * duration / usingTemplate.tolerance
                songTypeWeight["PureMusic"] =
                    (songTypeWeight["PureMusic"] ?: 0f) - (1 - usingTemplate.songProportion) * duration / usingTemplate.tolerance
            }

            lastChosen = song
        }

        fun adjustedScore(base: Float, song: MusicScanTaskEntity, elapsedMs: Long): Float {//checked？
            var score = base

            val artistWeight = artistDuplicateWeights[song.artist] ?: 0f
            if (artistWeight > 0f) {
                score -= artistWeight * usingTemplate.COEFFICIENT_OF_UNRELATED
            }

            val albumWeight = albumDuplicateWeights[song.album] ?: 0f
            if (albumWeight > 0f) {
                score -= albumWeight * usingTemplate.COEFFICIENT_OF_UNRELATED
            }

            val typeKey = when (song.musicType) {
                0 -> "PureMusic"
                1 -> "Song"
                else -> null
            }
            if (typeKey != null) {
                val typeWeight = songTypeWeight[typeKey] ?: 0f

                score -= typeWeight * usingTemplate.COEFFICIENT_OF_UNRELATED

            }

            val fade = usingTemplate.punishmentVectorFadeTime
            if (fade > 0) {
                for ((time, weightsBySong) in sessionParameters.punishmentWeights) {
                    val weight = weightsBySong[song.id] ?: continue
                    val age = elapsedMs - time
                    if (age in 0..fade) {
                        val decay = 1f - (age.toFloat() / fade.toFloat())
                        score -= weight * decay //* usingTemplate.punishmentVectorWeight * usingTemplate.COEFFICIENT_OF_UNRELATED
                    }
                }
            }

            return score
        }

        fun softmaxPick(items: List<ScoreEntry>, temperature: Float): Int {
            if (items.isEmpty()) return -1
            if (temperature <= 0f) return 0

            val maxScore = items.maxOf { it.score }
            val expScores = items.map { exp((it.score - maxScore) / temperature) }
            val sum = expScores.sum()
            if (sum == 0f) return 0

            val r = Random.nextFloat() * sum
            var acc = 0f
            for (i in items.indices) {
                acc += expScores[i]
                if (acc >= r) return i
            }
            return items.lastIndex
        }

        ////////////////////////后文推演//////////////////////
        """
        if (usingTemplate.roamingType == 0){
            不知道什么类型 originalWeight = 这里和现在的那个漫游算法一样，用种子歌在数据库里面翻所有近似的歌，然后整出百分数的相似度来。至于数据元素得长什么样子，可能是一个以歌曲id或者名字为索引，相似度为值的表，和惩罚权重一样
                    如果在底下说要新建的模拟integratedTimeParameter变化的列表里面没有这首歌，就从数据库里面获取integratedTimeParameter
            直接给originalWeight加上一个integratedTimeParameter*0.5^(时间差/usingTemplate.integratedParameterHalfLife)
            //这里不修改数据库里面integretedTimeParameter的值，因为这个值是由播放事件触发增加的，这里只是用来计算权重的一个参数，不一定播。
            为不在CertainList和deletedSongs以及传入的uncertainList（不为空则取前50首）里的歌曲进行如下迭代计算：
            取加在一起后的结果的前300个值，不包括CertainList和deletedSongs的歌曲。
            如果这首歌的艺术家在artistDuplicateWeights里面有权重，为这个总的权重减去这个权重 * usingTemplate.COEFFICIENT_OF_UNRELATED，如果artistDuplicateWeights为负就不做操作
            如果这首歌的专辑在albumDuplicateWeights里面有权重，为这个总的权重减去这个权重 * usingTemplate.COEFFICIENT_OF_UNRELATED，如果albumDuplicateWeights为负就不做操作
            如果这首歌的类型在songTypeWeight里面有权重，为这个总的权重减去这个权重 * usingTemplate.COEFFICIENT_OF_UNRELATED，如果songTypeWeight为负就不做操作
            遍历punishmentWeights里面的Int（也就是时间）：
            如果当前时间 - 这个Int > usingTemplate.punishmentVectorFadeTime，就不加权重。
            否则的话，为这个总的权重减去punishmentWeights里面这个Int对应的Float（也就是权重） * （当前时间-惩罚时间/消逝时间） * usingTemplate.PUNISHMENT_VECTOR_WEIGHT
            加完这些所有权重之后排序一下，这里依据温度和权重选出一首，（我不知道具体的方法，不过它的效果应该是温度大时让排名靠前的歌曲被选中的几率变小，让泡沫靠后的歌选择的几率变大）加入uncertainList
            像上面那样更新artistDuplicateWeights、albumDuplicateWeights、songTypeWeight
            新建一个表模拟integratedParameter的增加和衰减，格式也一样是<MusicScanTaskEntity>的列表
            将列表里面那首歌的上次播放时间改为今天，integratedParameter先照上面的公式衰减，再加一个integratedParameterWeight
            重复迭代直到有80首歌或者所有歌都被选完了。
            返回这个uncertainList。当然可能得换个名字不能和传入的uncertainlist搞混

            值得一提的是，只有计算integretedTimeParameter的时候要用到系统时间，其余的惩罚权重，艺术家专辑查重权重，都需要使用并且更新TimeTillNow变量，我还没有读下面这一大段，所以，你也许可以在我发现之前修复。

        如果 usingTemplate.roamingType == 1:
            经过我的头脑风暴我发现了它与上面方法的类似之处。
            首先读取SessionParameters里面的lineageRoamingDirection
            如果没有的话，那么说明这个时候就只有一首歌。跑一边中心扩散的逻辑，选出和它最相近的歌，把两个向量减一减，就是lineageRoamingDirection了。使用回调的方法存到SessionParameters里面去。那么这首歌就相当于是加到列表里面了，就不要算超级向量，直接跑后续算查重权重，惩罚权重，温度加权的逻辑就好
            这个时候读取模板里面的回归长度和directionRatio参数，再读取最后一首歌的向量，进行线性扩散的超级向量计算。超级向量计算方法是：
            先把中心回归向量乘以一个initialRegressionWeight，再乘（1-timeTillNow/regressionLength），如果这一项算出来小于零，那么就不考虑这个中心回归向量。
            再加上（directionRatio*lineageRoamingDirection+最后一首歌的向量*（1-directionRatio））乘（timeTillNow/regressionLength）
            有了超级向量之后，和中心扩散一样，根据超级权重算出相似度Top300，后面的步骤都一样。唯一的区别是不要忘了迭代超级向量本身。
        }
        """

        if (usingTemplate.roamingType == 0) {
            val seed = certainList.firstOrNull() ?: return AlgorithmResult.empty()
            val seedEmbedding = seed.embedding ?: return AlgorithmResult.empty()

            val similarSongs = DatabaseManager.searchTopSimilarByEmbedding(seedEmbedding, limit = 2000)
                .filter { it.embedding != null }
                .filter { it.id !in baseExcludedIds }
            val now = System.currentTimeMillis()

            val topCandidates = similarSongs//integratedSimulated有他妈什么用我请问了？？？？？？
                .map { song -> ScoreEntry(song, baseScore(seedEmbedding, song, now)) }
                .sortedByDescending { it.score }
                .take(2000)
                .toMutableList()

            //ALL CHECKED UP TILL HERE
            val result = mutableListOf<MusicScanTaskEntity>()

            while (result.size < 80 && topCandidates.isNotEmpty()) {
                val nowLoop = System.currentTimeMillis()
                val elapsedMs = timeTillNow.toLong()
                val adjusted = topCandidates.map { entry ->
                    ScoreEntry(entry.song, adjustedScore(entry.score, entry.song, elapsedMs))
                }

                val pickIndex = softmaxPick(adjusted, usingTemplate.temperature)
                if (pickIndex !in adjusted.indices) break

                val chosen = adjusted[pickIndex].song
                result.add(chosen)
                topCandidates.removeAt(pickIndex)

                applyWeightsFromSong(chosen)

                val state = getIntegratedState(chosen)//你这simulate了个几把
                val decayed = decayedIntegrated(chosen, nowLoop)
                state.integratedTimeParameter = decayed + usingTemplate.integratedParameterWeight
                state.lastPlayedDate = nowLoop
            }

            val displayItems = buildList {
                addAll(certainList)
                addAll(recentUncertain)
                addAll(result)
            }
            val breakdowns = buildBreakdownsForDisplay(seedEmbedding, displayItems, sessionParameters, usingTemplate)
            return AlgorithmResult(result, breakdowns)
        }

        /////////template=0 检查终了

        if (usingTemplate.roamingType == 1) {
            val seed = certainList.firstOrNull() ?: return AlgorithmResult.empty()
            val seedEmbedding = seed.embedding ?: return AlgorithmResult.empty()

            val result = mutableListOf<MusicScanTaskEntity>()
            val excludedIds = baseExcludedIds.toMutableSet()

            if (sessionParameters.lineageRoamingDirection == null) {
                val firstCandidates = DatabaseManager.searchTopSimilarByEmbedding(seedEmbedding, limit = 2000)
                    .filter { it.embedding != null }
                    .filter { it.id !in excludedIds }
                val now = System.currentTimeMillis()
                val best = firstCandidates
                    .map { song -> ScoreEntry(song, baseScore(seedEmbedding, song, now)) }
                    .maxByOrNull { it.score }
                    ?.song//这里不得SoftMax一下？

                val bestEmbedding = best?.embedding
                if (best != null && bestEmbedding != null) {
                    sessionParameters.lineageRoamingDirection = subtractVectors(bestEmbedding, seedEmbedding)
                    result.add(best)
                    excludedIds.add(best.id)

                    applyWeightsFromSong(best)
                    val state = getIntegratedState(best)
                    val decayed = decayedIntegrated(best, now)
                    state.integratedTimeParameter = decayed + usingTemplate.integratedParameterWeight
                    state.lastPlayedDate = now
                }
            }

            while (result.size < 80) {
                val direction = sessionParameters.lineageRoamingDirection ?: break
                val lastEmbedding = lastChosen?.embedding ?: seedEmbedding
                val superVector = buildSuperVector(
                    seedEmbedding = seedEmbedding,
                    lastEmbedding = lastEmbedding,
                    direction = direction,
                    timeTillNow = timeTillNow,
                    regressionLength = usingTemplate.regressionLength.toFloat(),
                    initialRegressionWeight = usingTemplate.initialRegressionWeight,
                    directionRatio = usingTemplate.directionRatio
                ) ?: break

                val candidates = DatabaseManager.searchTopSimilarByEmbedding(superVector, limit = 300)
                    .filter { it.embedding != null }
                    .filter { it.id !in excludedIds }
                if (candidates.isEmpty()) break

                val nowLoop = System.currentTimeMillis()
                val elapsedMs = timeTillNow.toLong()
                val topCandidates = candidates
                    .map { song -> ScoreEntry(song, baseScore(superVector, song, nowLoop)) }
                    .sortedByDescending { it.score }
                    .take(300)

                val adjusted = topCandidates.map { entry ->
                    ScoreEntry(entry.song, adjustedScore(entry.score, entry.song, elapsedMs))
                }

                val pickIndex = softmaxPick(adjusted, usingTemplate.temperature)
                if (pickIndex !in adjusted.indices) break

                val chosen = adjusted[pickIndex].song
                result.add(chosen)
                excludedIds.add(chosen.id)

                applyWeightsFromSong(chosen)

                val state = getIntegratedState(chosen)
                val decayed = decayedIntegrated(chosen, nowLoop)
                state.integratedTimeParameter = decayed + usingTemplate.integratedParameterWeight
                state.lastPlayedDate = nowLoop
            }

            val displayItems = buildList {
                addAll(certainList)
                addAll(recentUncertain)
                addAll(result)
            }
            val breakdowns = buildBreakdownsForDisplay(seedEmbedding, displayItems, sessionParameters, usingTemplate)
            return AlgorithmResult(result, breakdowns)
        }

        return AlgorithmResult.empty()
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

    private fun subtractVectors(a: FloatArray, b: FloatArray): FloatArray {
        val size = minOf(a.size, b.size)
        val out = FloatArray(size)
        for (i in 0 until size) {
            out[i] = a[i] - b[i]
        }
        return out
    }

    private fun buildSuperVector(
        seedEmbedding: FloatArray,
        lastEmbedding: FloatArray,
        direction: FloatArray,
        timeTillNow: Float,
        regressionLength: Float,
        initialRegressionWeight: Float,
        directionRatio: Float
    ): FloatArray? {
        val t: Float
        val centerWeight: Float
        if (regressionLength <= 0f) {
            // 物理意义：漫游长度为 0，意味着飞船瞬间摆脱了母星（Seed Song）的引力
            // 进度直接拉满到 100%，母星引力强制归零
            t = 1f
            centerWeight = 0f
        } else {
            // 物理意义：正常航行，引力随着时间线性衰减
            t = (timeTillNow / regressionLength).coerceIn(0f, 1f)
            centerWeight = (initialRegressionWeight * (1f - t)).coerceAtLeast(0f)
        }



        val size = minOf(seedEmbedding.size, lastEmbedding.size, direction.size)
        if (size == 0) return null//冗余逻辑，不过留着也行

        val out = FloatArray(size)
        val directionComponentWeight = directionRatio
        val lastComponentWeight = 1f - directionRatio

        for (i in 0 until size) {
            val centerComponent = seedEmbedding[i] * centerWeight
            val directionComponent = ((direction[i] * directionComponentWeight) + (lastEmbedding[i] * lastComponentWeight))*(1f-centerWeight)
            out[i] = centerComponent + directionComponent
        }
        var sumOfSquares = 0f
        for (i in 0 until size) {
            sumOfSquares += out[i] * out[i]
        }
        val norm = kotlin.math.sqrt(sumOfSquares)
        if (norm > 0f) {
            for (i in 0 until size) {
                out[i] /= norm
            }
        }

        return out
    }

    private data class ScoreEntry(
        val song: MusicScanTaskEntity,
        val score: Float
    )







    private fun buildBreakdownsForDisplay(
        seedEmbedding: FloatArray,
        items: List<MusicScanTaskEntity>,
        sessionParameters: SessionParameters,
        template: Template
    ): Map<Long, WeightBreakdown> {
        if (items.isEmpty()) return emptyMap()

        val artistDuplicateWeights: MutableMap<String, Float> = mutableMapOf()
        val albumDuplicateWeights: MutableMap<String, Float> = mutableMapOf()
        val songTypeWeight: MutableMap<String, Float> = mutableMapOf()

        val artistPardonFloor = -template.artistPardonTime.toFloat() * template.artistDuplicateCoefficient
        val albumPardonFloor = -template.albumPardonTime.toFloat() * template.albumDuplicateCoefficient

        var timeTillNow = 0f
        var previous: MusicScanTaskEntity? = null
        val now = System.currentTimeMillis()
        val output = mutableMapOf<Long, WeightBreakdown>()

        for (song in items) {
            val elapsedMs = timeTillNow.toLong()
            val embedding = song.embedding
            val baseSimilarity = if (embedding != null) cosineSimilarity(seedEmbedding, embedding) else 0f

            val integrated = if (song.lastPlayedDate > 0L && template.integratedParameterHalfLife > 0) {
                val days = (now - song.lastPlayedDate).toFloat() / (24f * 60f * 60f * 1000f)
                val decay = exp(ln(0.5f) * (days / template.integratedParameterHalfLife))
                song.integratedTimeParameter * decay
            } else {
                song.integratedTimeParameter
            }

            val baseScore = baseSimilarity - integrated

            val artistWeight = artistDuplicateWeights[song.artist] ?: 0f
            val artistPenalty = if (artistWeight > 0f) artistWeight * template.COEFFICIENT_OF_UNRELATED else 0f

            val albumWeight = albumDuplicateWeights[song.album] ?: 0f
            val albumPenalty = if (albumWeight > 0f) albumWeight * template.COEFFICIENT_OF_UNRELATED else 0f

            val typeKey = when (song.musicType) {
                0 -> "PureMusic"
                1 -> "Song"
                else -> null
            }
            val typeWeight = typeKey?.let { songTypeWeight[it] ?: 0f } ?: 0f
            val typePenalty = if (typeWeight > 0f) typeWeight * template.COEFFICIENT_OF_UNRELATED else 0f

            var punishmentWeight = 0f
            var punishmentPenalty = 0f
            if (template.punishmentVectorWeight > 0f && template.punishmentVectorFadeTime > 0) {
                for ((time, weightsBySong) in sessionParameters.punishmentWeights) {
                    val weight = weightsBySong[song.id] ?: continue
                    val age = elapsedMs - time
                    if (age <= template.punishmentVectorFadeTime) {
                        val decay = 1f - (age.toFloat() / template.punishmentVectorFadeTime.toFloat())
                        punishmentWeight += weight * decay
                    }
                }
            }
            punishmentPenalty = punishmentWeight * template.punishmentVectorWeight

            val finalScore = baseScore - artistPenalty - albumPenalty - typePenalty - punishmentPenalty
            output[song.id] = WeightBreakdown(
                baseSimilarity = baseSimilarity,
                integratedParameter = integrated,
                baseScore = baseScore,
                artistDuplicateWeight = artistWeight,
                albumDuplicateWeight = albumWeight,
                songTypeWeight = typeWeight,
                punishmentWeight = punishmentWeight,
                artistDuplicatePenalty = artistPenalty,
                albumDuplicatePenalty = albumPenalty,
                songTypePenalty = typePenalty,
                punishmentPenalty = punishmentPenalty,
                finalScore = finalScore
            )

            val duration = song.durationMs.toFloat()
            timeTillNow += duration

            if (previous != null && previous?.artist != song.artist) {
                val existing = artistDuplicateWeights[song.artist]
                artistDuplicateWeights[song.artist] = if (existing != null && existing < 0f) {
                    artistPardonFloor
                } else {
                    (existing ?: 0f) + artistPardonFloor
                }
            }

            if (previous != null && previous?.album != song.album) {
                val existing = albumDuplicateWeights[song.album]
                albumDuplicateWeights[song.album] = if (existing != null && existing < 0f) {
                    albumPardonFloor
                } else {
                    (existing ?: 0f) + albumPardonFloor
                }
            }

            artistDuplicateWeights[song.artist] =
                (artistDuplicateWeights[song.artist] ?: 0f) + template.artistDuplicateCoefficient * duration

            if (template.artistDuplicateFadeTime > 0) {
                val decay = duration / template.artistDuplicateFadeTime
                val keys = artistDuplicateWeights.keys.toList()
                for (key in keys) {
                    if (key != song.artist) {
                        val next = (artistDuplicateWeights[key] ?: 0f) - decay
                        artistDuplicateWeights[key] = if (next < artistPardonFloor) artistPardonFloor else next
                    }
                }
            }

            albumDuplicateWeights[song.album] =
                (albumDuplicateWeights[song.album] ?: 0f) + template.albumDuplicateCoefficient * duration

            if (template.albumDuplicateFadeTime > 0) {
                val decay = duration / template.albumDuplicateFadeTime
                val keys = albumDuplicateWeights.keys.toList()
                for (key in keys) {
                    if (key != song.album) {
                        val next = (albumDuplicateWeights[key] ?: 0f) - decay
                        albumDuplicateWeights[key] = if (next < albumPardonFloor) albumPardonFloor else next
                    }
                }
            }

            if (song.musicType == 0) {
                songTypeWeight["PureMusic"] =
                    (songTypeWeight["PureMusic"] ?: 0f) + (1 - template.songProportion) * duration
                songTypeWeight["Song"] =
                    (songTypeWeight["Song"] ?: 0f) - template.songProportion * duration
            } else if (song.musicType == 1) {
                songTypeWeight["Song"] =
                    (songTypeWeight["Song"] ?: 0f) + template.songProportion * duration
                songTypeWeight["PureMusic"] =
                    (songTypeWeight["PureMusic"] ?: 0f) - (1 - template.songProportion) * duration
            }

            previous = song
        }

        return output
    }
}

data class AlgorithmResult(
    val songs: List<MusicScanTaskEntity>,
    val breakdowns: Map<Long, WeightBreakdown>
) {
    companion object {
        fun empty(): AlgorithmResult = AlgorithmResult(emptyList(), emptyMap())
    }
}
