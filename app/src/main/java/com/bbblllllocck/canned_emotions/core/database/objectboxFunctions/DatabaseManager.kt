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

    // --- Export / Import ---

    fun exportToJson(outputStream: java.io.OutputStream) {
        val box = musicTaskBox()
        val writer = android.util.JsonWriter(java.io.OutputStreamWriter(outputStream, Charsets.UTF_8))
        writer.setIndent("  ")
        writer.beginArray()
        
        box.all.forEach { item ->
            writer.beginObject()
            writer.name("id").value(item.id)
            writer.name("filePath").value(item.filePath)
            writer.name("title").value(item.title)
            writer.name("album").value(item.album)
            writer.name("artist").value(item.artist)
            writer.name("status").value(item.status)
            writer.name("createdAtMillis").value(item.createdAtMillis)
            writer.name("updatedAtMillis").value(item.updatedAtMillis)
            writer.name("musicType").value(item.musicType)
            writer.name("durationMs").value(item.durationMs)
            writer.name("lastPlayedDate").value(item.lastPlayedDate)
            writer.name("integratedTimeParameter").value(item.integratedTimeParameter)
            
            if (item.embedding != null) {
                writer.name("embedding")
                writer.beginArray()
                item.embedding!!.forEach { v ->
                    writer.value(v)
                }
                writer.endArray()
            }
            writer.endObject()
        }
        
        writer.endArray()
        writer.close()
    }

    fun importFromJson(inputStream: java.io.InputStream) {
        val reader = android.util.JsonReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8))
        val box = musicTaskBox()
        val batch = mutableListOf<MusicScanTaskEntity>()
        
        reader.beginArray()
        while (reader.hasNext()) {
            val item = MusicScanTaskEntity()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "filePath" -> item.filePath = reader.nextString()
                    "title" -> item.title = reader.nextString()
                    "album" -> item.album = reader.nextString()
                    "artist" -> item.artist = reader.nextString()
                    "status" -> item.status = reader.nextInt()
                    "createdAtMillis" -> item.createdAtMillis = reader.nextLong()
                    "updatedAtMillis" -> item.updatedAtMillis = reader.nextLong()
                    "musicType" -> item.musicType = reader.nextInt()
                    "durationMs" -> item.durationMs = reader.nextLong()
                    "lastPlayedDate" -> item.lastPlayedDate = reader.nextLong()
                    "integratedTimeParameter" -> item.integratedTimeParameter = reader.nextDouble().toFloat()
                    "embedding" -> {
                        val list = mutableListOf<Float>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            list.add(reader.nextDouble().toFloat())
                        }
                        reader.endArray()
                        item.embedding = list.toFloatArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            
            val existing = box.query(MusicScanTaskEntity_.filePath.equal(item.filePath)).build().findFirst()
            if (existing != null) {
                item.id = existing.id
            }
            
            batch.add(item)
            
            if (batch.size >= 100) {
                box.put(batch)
                batch.clear()
            }
        }
        reader.endArray()
        
        if (batch.isNotEmpty()) {
            box.put(batch)
        }
        reader.close()
    }

    private fun getDbDir(context: Context): java.io.File {
        return java.io.File(context.filesDir, "objectbox/objectbox")
    }

    fun exportNativeDatabase(outputStream: java.io.OutputStream, context: Context) {
        val dbFile = java.io.File(getDbDir(context), "data.mdb")
        if (dbFile.exists()) {
            dbFile.inputStream().use { input ->
                input.copyTo(outputStream)
            }
        }
    }

    fun restoreNativeDatabase(inputStream: java.io.InputStream, context: Context) {
        store.close()
        
        val dbFile = java.io.File(getDbDir(context), "data.mdb")
        if (dbFile.exists()) {
            dbFile.delete()
        }
        
        java.io.FileOutputStream(dbFile).use { out ->
            inputStream.copyTo(out)
        }
        
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }
}
