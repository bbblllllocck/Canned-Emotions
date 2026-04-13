package com.bbblllllocck.canned_emotions.core.database

import android.util.Log
import com.bbblllllocck.canned_emotions.core.database.geminiRequestCall.EmbeddingCall
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Collections

object DatabaseVectorStorgeTaskLogic {

	private var maxConcurrent: Int = 1
	private val processingIds = Collections.synchronizedSet(mutableSetOf<Long>())
	private var observeJob: Job? = null

	private val _isRunning = MutableStateFlow(false)
	val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

	fun start(
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        maxConcurrent: Int = 2
	) {
		stop()
		this.maxConcurrent = maxConcurrent
		_isRunning.value = true

		observeJob = scope.launch {
			DatabaseManager.observeAllMusicTasksFlow().collectLatest { tasks ->
				val toRun = synchronized(processingIds) {
					val available = this@DatabaseVectorStorgeTaskLogic.maxConcurrent - processingIds.size
					if (available <= 0) {
						emptyList()
					} else {
						tasks.asSequence()
							.filter { it.status == MusicScanTaskEntity.PENDING }
							.filter { it.id !in processingIds }
							.take(available)
							.toList()
							.also { picked -> picked.forEach { processingIds += it.id } }
					}
				}

				toRun.forEach { task ->
					scope.launch {
						try {
							val vector = EmbeddingCall.embed(audioPath = task.filePath)
							Log.d("123456", "audio vector size=${vector.size}, taskId=${task.id}")
							val box = DatabaseManager.musicTaskBox()
							val latest = box.get(task.id) ?: return@launch
							latest.embedding = vector
							latest.status = MusicScanTaskEntity.DONE
							latest.updatedAtMillis = System.currentTimeMillis()
							box.put(latest)
						} catch (ce: CancellationException) {
							throw ce
						} catch (e: Exception) {
							Log.e("123456", "embed failed, taskId=${task.id}, filePath=${task.filePath}", e)
						} finally {
							processingIds.remove(task.id)
						}
					}
				}
			}
		}
	}

	fun stop() {
		observeJob?.cancel()
		observeJob = null
		processingIds.clear()
		_isRunning.value = false
	}
}