package com.bbblllllocck.canned_emotions.core.database.geminiRequestCall

import android.util.Log
import com.bbblllllocck.canned_emotions.core.api.ApiCredential
import com.google.genai.Client
import com.google.genai.errors.ClientException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ClientStatus {
    AVAILABLE,
    RPM_COOLDOWN,
    RPD_EXHAUSTED
}
class ClientNode(
    val client: Client
) {
    var status: ClientStatus = ClientStatus.AVAILABLE
    var cooldownUntil: Long = 0L
    var inFlight: Boolean = false
}
class AllQuotaExhaustedException(message: String) : Exception(message)

object ClientManager {

    private val mutex = Mutex()
    private val clientPool = mutableMapOf<String, ClientNode>()

    fun initialize(apisFlow: Flow<List<ApiCredential>> , scope: CoroutineScope) {
        scope.launch {
            apisFlow.collect { newCredentials ->
                updatePool(newCredentials)
            }
        }
    }

    private suspend fun updatePool(newCredentials: List<ApiCredential>) {
        mutex.withLock {
            val newIds = newCredentials.map { it.id }.toSet()

            clientPool.keys.retainAll(newIds)

            for (cred in newCredentials) {
                if (!clientPool.containsKey(cred.id)) {

                    val newGenaiClient = Client.builder().apiKey(cred.apiKey).build()//.clientOptions选项，可以看一下这样就可以引入重试逻辑
                    clientPool[cred.id] = ClientNode(newGenaiClient)

                }
            }
        }
    }


    private suspend fun getAvailableNode(): ClientNode {
        while (true) {
            var waitTimeMs = 0L

            mutex.withLock {
                if (clientPool.isEmpty()) {
                    throw IllegalStateException("API Client 池为空，请确保已添加 API Key")
                }

                val now = System.currentTimeMillis()
                val nodes = clientPool.values

                nodes.filter { it.status == ClientStatus.RPM_COOLDOWN && now >= it.cooldownUntil }
                    .forEach { it.status = ClientStatus.AVAILABLE }

                val availableNode = nodes.firstOrNull { it.status == ClientStatus.AVAILABLE && !it.inFlight }
                if (availableNode != null) {
                    availableNode.inFlight = true
                    return availableNode
                }

                val isAllDead = nodes.all { it.status == ClientStatus.RPD_EXHAUSTED }
                if (isAllDead) {
                    throw AllQuotaExhaustedException("所有 API Key 的今日配额均已耗尽，请明日 15:00 后重试。")
                }

                val nextAvailableNode = nodes
                    .filter { it.status == ClientStatus.RPM_COOLDOWN }
                    .minByOrNull { it.cooldownUntil }

                waitTimeMs = if (nextAvailableNode != null) {
                    (nextAvailableNode.cooldownUntil - now).coerceAtLeast(100L)
                } else {
                    // 没有冷却中的节点时，说明当前都是 inFlight，短暂等待后重试。
                    100L
                }
            }
            if (waitTimeMs > 0) {
                delay(waitTimeMs)
            }
        }
    }

    private suspend fun releaseNode(node: ClientNode) {
        mutex.withLock {
            node.inFlight = false
        }
    }

    /**
     * [核心逻辑 3]：异常解析与状态翻转
     */
    private suspend fun handleException(node: ClientNode, exception: Exception) {
        val errorMessage = exception.message?.lowercase() ?: ""

        val isRateLimitError = exception is ClientException && exception.code() == 429

        if (isRateLimitError) {
            mutex.withLock {
                if (errorMessage.contains("per day") || errorMessage.contains("daily")) {
                    node.status = ClientStatus.RPD_EXHAUSTED
                } else {
                    // 分钟限额枯竭，关小黑屋冷却 65 秒 (预留 5 秒网络请求时间差的冗余)
                    node.status = ClientStatus.RPM_COOLDOWN
                    node.cooldownUntil = System.currentTimeMillis() + 65_000L
                }
            }
        } else {
            throw exception
        }
    }

    suspend fun <T> executeWithRetry(block: suspend (Client) -> T): T {
        while (true) {
            val node = getAvailableNode()

            try {
                return block(node.client)
            } catch (e: Exception) {
                Log.e("123456", "executeWithRetry error=${e.message}", e)
                handleException(node, e)
                throw e
            } finally {
                releaseNode(node)
            }
        }
    }
}