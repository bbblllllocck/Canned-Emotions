package com.bbblllllocck.canned_emotions.core.database.objectboxFunctions

import android.content.Context
import com.bbblllllocck.canned_emotions.core.algorithm.TemplateManager
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.exp
import kotlin.math.ln

object DatabaseManager {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }



    fun musicTaskBox(): Box<MusicScanTaskEntity> {
        check(::store.isInitialized) {
            "ObjectBox is not initialized. Call DatabaseManager.init() in Application first."
        }
        return store.boxFor(MusicScanTaskEntity::class.java)
    }

    /**
     * Upsert by unique filePath, preserving the existing ID when present.
     * created for database screen
     */
    fun upsertMusicTasks(tasks: List<MusicScanTaskEntity>): Int {
        if (tasks.isEmpty()) return 0

        val box = musicTaskBox()
        val now = System.currentTimeMillis()

        tasks.forEach { incoming ->
            val existing = box.query(MusicScanTaskEntity_.filePath.equal(incoming.filePath))
                .build()
                .findFirst()

            if (existing != null) {
                incoming.id = existing.id
                incoming.createdAtMillis = existing.createdAtMillis
                incoming.status = existing.status
                incoming.embedding = existing.embedding
                incoming.updatedAtMillis = now
            } else {
                incoming.createdAtMillis = now
                incoming.updatedAtMillis = now
            }
        }

        box.put(tasks)
        return tasks.size
    }

    // Shared source for UI/task consumers: all tasks ordered by latest updates first.
    fun observeAllMusicTasksFlow(): Flow<List<MusicScanTaskEntity>> = callbackFlow {
        val query = musicTaskBox()
            .query()
            .orderDesc(MusicScanTaskEntity_.updatedAtMillis)
            .build()

        trySend(query.find())

        val subscription = query.subscribe().observer { tasks: List<MusicScanTaskEntity>? ->
            trySend(tasks.orEmpty())
        }

        awaitClose {
            subscription.cancel()
            query.close()
        }
    }

    fun searchTopSimilarByEmbedding(queryVector: FloatArray, limit: Int = 5): List<MusicScanTaskEntity> {
        if (queryVector.isEmpty()) return emptyList()
        if (limit <= 0) return emptyList()

        val query = musicTaskBox()
            .query(MusicScanTaskEntity_.embedding.nearestNeighbors(queryVector, limit))
            .build()

        return query.use { it.find() }
    }

    //AI generated below
    fun listVectorReadySongs(): List<MusicScanTaskEntity> {
        val query = musicTaskBox().query().build()
        return query.use {
            it.find()
                .asSequence()
                .filter { task ->
                    task.status == MusicScanTaskEntity.DONE &&
                        task.embedding != null &&
                        task.filePath.isNotBlank()
                }
                .sortedBy { task -> task.title.ifBlank { task.filePath }.lowercase() }
                .toList()
        }
    }

    fun updateIntegratedParameter(songId: Long) {
        val box = musicTaskBox()
        val entity = box.get(songId) ?: return
        val template = TemplateManager.getUsingTemplate() ?: return

        val now = System.currentTimeMillis()
        val lastPlayed = entity.lastPlayedDate
        var value = entity.integratedTimeParameter

        if (lastPlayed > 0L && template.integratedParameterHalfLife > 0) {
            val days = (now - lastPlayed).toFloat() / (24f * 60f * 60f * 1000f)
            val decay = exp(ln(0.5f) * (days / template.integratedParameterHalfLife))
            value *= decay
        }

        value += template.integratedParameterWeight
        entity.integratedTimeParameter = value
        entity.lastPlayedDate = now
        entity.updatedAtMillis = now
        box.put(entity)
    }

    fun scaleIntegratedParameters(ratio: Float) {
        if (ratio.isNaN() || ratio.isInfinite()) return
        if (ratio == 1f) return

        val box = musicTaskBox()
        val items = box.all
        if (items.isEmpty()) return

        items.forEach { item ->
            item.integratedTimeParameter *= ratio
            item.updatedAtMillis = System.currentTimeMillis()
        }

        box.put(items)
    }

}
