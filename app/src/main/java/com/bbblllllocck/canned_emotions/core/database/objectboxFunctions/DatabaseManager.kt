package com.bbblllllocck.canned_emotions.core.database.objectboxFunctions

import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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


}
