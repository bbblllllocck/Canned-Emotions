package com.bbblllllocck.canned_emotions.core.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbblllllocck.canned_emotions.core.api.AppContextProvider
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PLAYLIST_STORE_NAME = "playlist_store"

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PLAYLIST_STORE_NAME
)

object PlaylistPersistence {

    private val appContext: Context by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContextProvider.get() }
    private val dataStore: DataStore<Preferences> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        appContext.playlistDataStore
    }

    private val keyCertainIds = stringPreferencesKey("certain_ids")
    private val keyUncertainIds = stringPreferencesKey("uncertain_ids")
    private val keyDeletedIds = stringPreferencesKey("deleted_ids")
    private val keyCurrentIndex = intPreferencesKey("current_index")
    private val keyBufferSize = intPreferencesKey("buffer_size")
    private val keySessionParams = stringPreferencesKey("session_params")

    /**
     * 保存播放列表状态到 DataStore。应在每次列表变更后调用。
     */
    suspend fun save(
        certainList: List<MusicScanTaskEntity>,
        uncertainList: List<MusicScanTaskEntity>,
        deletedSongs: List<MusicScanTaskEntity>,
        currentIndex: Int,
        certainBufferSize: Int,
        sessionParameters: SessionParameters
    ) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[keyCertainIds] = encodeIdList(certainList.map { it.id })
                prefs[keyUncertainIds] = encodeIdList(uncertainList.take(50).map { it.id })
                prefs[keyDeletedIds] = encodeIdList(deletedSongs.map { it.id })
                prefs[keyCurrentIndex] = currentIndex
                prefs[keyBufferSize] = certainBufferSize
                prefs[keySessionParams] = encodeSessionParameters(sessionParameters)
            }
        }
    }

    /**
     * 从 DataStore 恢复播放列表状态。返回 null 如果没有保存的状态。
     */
    fun restore(): RestoredPlaylistState? {
        return runBlocking {
            val prefs = dataStore.data.first()
            val certainIdsRaw = prefs[keyCertainIds] ?: return@runBlocking null
            val uncertainIdsRaw = prefs[keyUncertainIds] ?: ""
            val deletedIdsRaw = prefs[keyDeletedIds] ?: ""
            val currentIndex = prefs[keyCurrentIndex] ?: 0
            val bufferSize = prefs[keyBufferSize] ?: 5
            val sessionRaw = prefs[keySessionParams] ?: ""

            val certainIds = decodeIdList(certainIdsRaw)
            if (certainIds.isEmpty()) return@runBlocking null

            val uncertainIds = decodeIdList(uncertainIdsRaw)
            val deletedIds = decodeIdList(deletedIdsRaw)

            // 从数据库批量查找实体
            val allNeededIds = (certainIds + uncertainIds + deletedIds).toSet()
            val allSongs = DatabaseManager.listVectorReadySongs()
            val songMap = allSongs.associateBy { it.id }

            val certainList = certainIds.mapNotNull { songMap[it] }
            val uncertainList = uncertainIds.mapNotNull { songMap[it] }
            val deletedSongs = deletedIds.mapNotNull { songMap[it] }

            if (certainList.isEmpty()) return@runBlocking null

            val sessionParameters = if (sessionRaw.isNotBlank()) {
                decodeSessionParameters(sessionRaw)
            } else {
                SessionParameters()
            }

            RestoredPlaylistState(
                certainList = certainList,
                uncertainList = uncertainList,
                deletedSongs = deletedSongs,
                currentIndex = currentIndex.coerceIn(0, (certainList.size - 1).coerceAtLeast(0)),
                certainBufferSize = bufferSize,
                sessionParameters = sessionParameters
            )
        }
    }

    /**
     * 清除保存的播放列表状态。
     */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    // ── 编解码工具 ──

    private fun encodeIdList(ids: List<Long>): String {
        return JSONArray().apply { ids.forEach { put(it) } }.toString()
    }

    private fun decodeIdList(raw: String): List<Long> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getLong(it) }
        }.getOrDefault(emptyList())
    }

    private fun encodeSessionParameters(params: SessionParameters): String {
        val obj = JSONObject()

        // punishmentWeights: Map<Long, Map<Long, Float>>
        val pwObj = JSONObject()
        for ((timeKey, weightsMap) in params.punishmentWeights) {
            val innerObj = JSONObject()
            for ((songId, weight) in weightsMap) {
                innerObj.put(songId.toString(), weight.toDouble())
            }
            pwObj.put(timeKey.toString(), innerObj)
        }
        obj.put("punishmentWeights", pwObj)

        // lineageRoamingDirection: FloatArray?
        val dirArray = params.lineageRoamingDirection
        if (dirArray != null) {
            val dirJson = JSONArray()
            for (v in dirArray) {
                dirJson.put(v.toDouble())
            }
            obj.put("lineageRoamingDirection", dirJson)
        }

        return obj.toString()
    }

    private fun decodeSessionParameters(raw: String): SessionParameters {
        val params = SessionParameters()
        runCatching {
            val obj = JSONObject(raw)

            val pwObj = obj.optJSONObject("punishmentWeights")
            if (pwObj != null) {
                val keys = pwObj.keys()
                while (keys.hasNext()) {
                    val timeKeyStr = keys.next()
                    val timeKey = timeKeyStr.toLongOrNull() ?: continue
                    val innerObj = pwObj.optJSONObject(timeKeyStr) ?: continue
                    val innerMap = mutableMapOf<Long, Float>()
                    val innerKeys = innerObj.keys()
                    while (innerKeys.hasNext()) {
                        val songIdStr = innerKeys.next()
                        val songId = songIdStr.toLongOrNull() ?: continue
                        innerMap[songId] = innerObj.optDouble(songIdStr, 0.0).toFloat()
                    }
                    params.punishmentWeights[timeKey] = innerMap
                }
            }

            val dirJson = obj.optJSONArray("lineageRoamingDirection")
            if (dirJson != null && dirJson.length() > 0) {
                val arr = FloatArray(dirJson.length()) { dirJson.optDouble(it, 0.0).toFloat() }
                params.lineageRoamingDirection = arr
            }
        }
        return params
    }
}

data class RestoredPlaylistState(
    val certainList: List<MusicScanTaskEntity>,
    val uncertainList: List<MusicScanTaskEntity>,
    val deletedSongs: List<MusicScanTaskEntity>,
    val currentIndex: Int,
    val certainBufferSize: Int,
    val sessionParameters: SessionParameters
)
