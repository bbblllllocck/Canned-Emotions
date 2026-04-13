package com.bbblllllocck.canned_emotions.ui.features.databaseScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbblllllocck.canned_emotions.core.database.DatabaseVectorStorgeTaskLogic
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DatabaseScreen() {
	val allTasksFlow = remember { DatabaseManager.observeAllMusicTasksFlow() }
	val allTasks by allTasksFlow.collectAsState(initial = emptyList())
	val isRunning by DatabaseVectorStorgeTaskLogic.isRunning.collectAsState()
	var isPauseCoolingDown by remember { mutableStateOf(false) }
	val uiScope = rememberCoroutineScope()

	val (unprocessed, processed) = remember(allTasks) {
		allTasks.partition { it.status != MusicScanTaskEntity.DONE }
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text(
			text = "数据库任务 (${allTasks.size})",
			style = MaterialTheme.typography.titleLarge
		)

		Button(
			enabled = !isPauseCoolingDown,
			onClick = {
				if (isRunning) {
					DatabaseVectorStorgeTaskLogic.stop()
					isPauseCoolingDown = true
					uiScope.launch {
						delay(6000)
						isPauseCoolingDown = false
					}
				} else {
					DatabaseVectorStorgeTaskLogic.start(maxConcurrent = 2)
				}
			}
		) {
			Text(
				when {
					isPauseCoolingDown -> "暂停中..."
					isRunning -> "暂停"
					else -> "开始"
				}
			)
			//////////////////////这个按钮要和实际状态同步！
		}

		Row(
			modifier = Modifier.fillMaxSize(),
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			TaskColumn(
				title = "未处理 (${unprocessed.size})",
				tasks = unprocessed,
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight()
			)
			TaskColumn(
				title = "已处理 (${processed.size})",
				tasks = processed,
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight()
			)
		}
	}
}

@Composable
private fun TaskColumn(
	title: String,
	tasks: List<MusicScanTaskEntity>,
	modifier: Modifier = Modifier
) {
	Card(modifier = modifier) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(10.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(text = title, style = MaterialTheme.typography.titleMedium)
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.spacedBy(6.dp)
			) {
				items(tasks, key = { it.id }) { task ->
					TaskItem(task = task)
				}
			}
		}
	}
}

@Composable
private fun TaskItem(task: MusicScanTaskEntity) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp),
			verticalArrangement = Arrangement.spacedBy(3.dp)
		) {
			Text(
				text = task.title.ifBlank { "(无标题)" },
				style = MaterialTheme.typography.bodyLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = "${task.artist.ifBlank { "未知艺术家" }} - ${task.album.ifBlank { "未知专辑" }}",
				style = MaterialTheme.typography.bodySmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = statusLabel(task.status),
				style = MaterialTheme.typography.labelSmall
			)
		}
	}
}

private fun statusLabel(status: Int): String {
	return when (status) {
		MusicScanTaskEntity.PENDING -> "状态: PENDING(待处理)"
		MusicScanTaskEntity.DONE -> "状态: DONE(已处理)"
		MusicScanTaskEntity.UNEXIST -> "状态: UNEXIST(文件不存在)"
		else -> "状态: UNKNOWN($status)"
	}
}
