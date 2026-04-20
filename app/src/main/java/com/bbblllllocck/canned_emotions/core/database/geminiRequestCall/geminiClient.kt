package com.bbblllllocck.canned_emotions.core.database.geminiRequestCall

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbblllllocck.canned_emotions.core.api.AppContextProvider
import com.bbblllllocck.canned_emotions.core.api.ApiCredential
import com.google.genai.Client
import com.google.genai.errors.ClientException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClientNode(
    val id: String,
    val client: Client
) {
    var inFlight: Boolean = false
}

class AllQuotaExhaustedException(message: String) : Exception(message)

object ClientManager {

    private val mutex = Mutex()
    private val clientPool = mutableListOf<ClientNode>()
    private val allConfigsForPoolSync = listOf("embedding", "3-flash")


    fun initialize(apisFlow: Flow<List<ApiCredential>> , scope: CoroutineScope) {
        scope.launch {
            apisFlow.collect { newCredentials ->
                val removedIndexes = mutableListOf<Int>()
                var addedCount = 0

                mutex.withLock {
                    val newIds = newCredentials.map { it.id }.toSet()

                    // 移除不再存在的 key
                    for (index in clientPool.lastIndex downTo 0) {
                        if (clientPool[index].id !in newIds) {
                            clientPool.removeAt(index)
                            removedIndexes.add(index)
                        }
                    }

                    // 新增不存在的 key
                    for (cred in newCredentials) {
                        val exists = clientPool.any { it.id == cred.id }
                        if (!exists) {
                            val newGenaiClient = Client.builder().apiKey(cred.apiKey).build()//.clientOptions选项，可以看一下这样就可以引入重试逻辑
                            val node = ClientNode(id = cred.id, client = newGenaiClient)
                            clientPool.add(node)
                            addedCount += 1
                        }
                    }
                }

                allConfigsForPoolSync.forEach { config ->
                    Burned.removeByIds(config, removedIndexes)
                    Burned.add(config, addedCount)
                    BurnedOut.removeByIds(config, removedIndexes)
                    BurnedOut.add(config, addedCount)

                    // 只在“当前位置之前”的节点被删除时，当前指针才左移。
                    val storedIndex = CurrentIndex.read(config).value
                    val shiftLeftCount = removedIndexes.count { it < storedIndex }
                    if (shiftLeftCount > 0) {
                        CurrentIndex.write(config, (storedIndex - shiftLeftCount).coerceAtLeast(0))
                    }
                }
            }
        }
    }

    private suspend fun releaseNode(node: ClientNode) {
        mutex.withLock {
            node.inFlight = false
        }
    }


    private suspend fun getAvailableNode(config: String): ClientNode {
        val startIndexFromStore = CurrentIndex.read(config).value

        return mutex.withLock {
            if (clientPool.isEmpty()) {
                throw IllegalStateException("API Client 池为空，请确保已添加 API Key")
            }

            // 正常请求从 currentIndex 开始扫描，只要节点不在 inFlight 即可使用。
            val size = clientPool.size
            val startIndex = ((startIndexFromStore % size) + size) % size
            for (offset in 0 until size) {
                val index = (startIndex + offset) % size
                val node = clientPool[index]
                if (!node.inFlight) {
                    node.inFlight = true
                    CurrentIndex.write(config, (index + 1) % size)
                    return@withLock node
                }
            }

            throw IllegalStateException("没有可用 API Client（全部 inFlight），请检查调用并发策略")
        }
    }

    /**
     * 429：标记 burned 并推进 currentIndex；非 429 直接上抛。
     */
    private suspend fun handleException(config: String, node: ClientNode, exception: Exception) {
        val isRateLimitError = exception is ClientException && exception.code() == 429
        if (!isRateLimitError) {
            throw exception
        }

        val nodeIndex = mutex.withLock { clientPool.indexOf(node) }
        if (nodeIndex < 0) return

        val alreadyBurned = Burned.get(config, nodeIndex)
        if (alreadyBurned) {
            // 被标记后再次访问仍 429，记录为二次失败（按 config 隔离）。
            BurnedOut.set(config, nodeIndex, true)
        } else {
            Burned.set(config, nodeIndex, true)
            BurnedOut.set(config, nodeIndex, false)
            val nextIndex = mutex.withLock {
                if (clientPool.isEmpty()) null else (nodeIndex + 1) % clientPool.size
            }
            if (nextIndex != null) {
                CurrentIndex.write(config, nextIndex)
            }
        }

        val burnedItems = Burned.read(config)
        val shouldPause = mutex.withLock {
            clientPool.isNotEmpty() && clientPool.indices.all { index ->
                burnedItems.getOrNull(index) == true && BurnedOut.get(config, index)
            }
        }
        if (shouldPause) {
            throw AllQuotaExhaustedException("所有 API Key 在 burned 后再次访问仍失败，队列已暂停，请稍后重试。")
        }
    }

    suspend fun <T> executeWithRetry(config: String = "embedding", block: suspend (Client) -> T): T {
        while (true) {
            val node = getAvailableNode(config)

            try {
                val result = block(node.client)
                val nodeIndex = mutex.withLock {
                    // 一次成功即可视为该 key 已恢复。
                    clientPool.indexOf(node)
                }
                if (nodeIndex >= 0) {
                    Burned.set(config, nodeIndex, false)
                    BurnedOut.set(config, nodeIndex, false)
                }
                return result
            } catch (e: Exception) {
                Log.e("123456", "executeWithRetry error=${e.message}", e)
                handleException(config, node, e)
                // 429 走轮换重试，不在这里终止调用链。
                continue
            } finally {
                releaseNode(node)
            }
        }
    }
}



private val Context.geminiClientStateStore: DataStore<Preferences> by preferencesDataStore(
    name = "gemini_client_state_store"
)

class Burned {
    companion object {
        suspend fun read(key: String): MutableList<Boolean> {
            val prefsKey = stringPreferencesKey("burned_$key")
            val prefs = AppContextProvider.get().geminiClientStateStore.data.first()
            val encoded = prefs[prefsKey].orEmpty()
            return if (encoded.isBlank()) {
                mutableListOf()
            } else {
                encoded.split(',').map { it.trim() == "1" }.toMutableList()
            }
        }

        suspend fun removeByIds(key: String, removedIndexes: List<Int>) {
            if (removedIndexes.isEmpty()) return
            val items = read(key)
            removedIndexes.distinct().sortedDescending().forEach { index ->
                if (index in items.indices) {
                    items.removeAt(index)
                }
            }
            write(key, items)
        }

        suspend fun add(key: String, count: Int = 1) {
            if (count <= 0) return
            val items = read(key)
            repeat(count) { items.add(false) }
            write(key, items)
        }

        suspend fun get(key: String, index: Int): Boolean {
            val items = read(key)
            return items.getOrNull(index) == true
        }

        suspend fun set(key: String, index: Int, value: Boolean) {
            if (index < 0) return
            val items = read(key)
            while (items.size <= index) {
                items.add(false)
            }
            items[index] = value
            write(key, items)
        }

        private suspend fun write(key: String, items: List<Boolean>) {
            val prefsKey = stringPreferencesKey("burned_$key")
            val encoded = items.joinToString(",") { if (it) "1" else "0" }
            AppContextProvider.get().geminiClientStateStore.edit { prefs ->
                prefs[prefsKey] = encoded
            }
        }
    }
}

class CurrentIndex(
    var value: Int
) {
    companion object {
        suspend fun read(key: String): CurrentIndex {
            val prefsKey = intPreferencesKey("current_index_$key")
            val prefs = AppContextProvider.get().geminiClientStateStore.data.first()
            return CurrentIndex(prefs[prefsKey] ?: 0)
        }

        suspend fun write(key: String, value: Int) {
            val prefsKey = intPreferencesKey("current_index_$key")
            AppContextProvider.get().geminiClientStateStore.edit { prefs ->
                prefs[prefsKey] = value
            }
        }
    }
}

class BurnedOut {
    companion object {
        private val states = mutableMapOf<String, MutableSet<Int>>()

        private fun stateOf(key: String): MutableSet<Int> = states.getOrPut(key) { mutableSetOf() }

        fun get(key: String, index: Int): Boolean {
            synchronized(states) {
                return stateOf(key).contains(index)
            }
        }

        fun set(key: String, index: Int, value: Boolean) {
            if (index < 0) return
            synchronized(states) {
                val state = stateOf(key)
                if (value) {
                    state.add(index)
                } else {
                    state.remove(index)
                }
            }
        }

        fun removeByIds(key: String, removedIndexes: List<Int>) {
            if (removedIndexes.isEmpty()) return
            synchronized(states) {
                val state = stateOf(key)
                removedIndexes.distinct().sortedDescending().forEach { removedIndex ->
                    val shifted = mutableSetOf<Int>()
                    state.forEach { idx ->
                        when {
                            idx == removedIndex -> Unit
                            idx > removedIndex -> shifted.add(idx - 1)
                            else -> shifted.add(idx)
                        }
                    }
                    state.clear()
                    state.addAll(shifted)
                }
            }
        }

        fun add(key: String, count: Int = 1) {
            if (count <= 0) return
            synchronized(states) {
                stateOf(key)
            }
        }
    }
}
