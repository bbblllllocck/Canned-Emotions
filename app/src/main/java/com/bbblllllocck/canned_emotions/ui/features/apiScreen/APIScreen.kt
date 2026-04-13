package com.bbblllllocck.canned_emotions.ui.features.apiScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun APIScreen() {
	val apiViewModel: APIViewModel = viewModel()
	val state by apiViewModel.state.collectAsState()

	Scaffold { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.padding(horizontal = 16.dp, vertical = 12.dp)
		) {
			Text(text = "API 管理", style = MaterialTheme.typography.headlineSmall)
			Text(
				text = "已保存的 Key 仅显示后四位",
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
			)

			HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				items(state.items, key = { it.id }) { item ->
					ApiRow(
						item = item,
						onEdit = { apiViewModel.showEditDialog(item.id) },
						onDelete = { apiViewModel.requestDelete(item.id) }
					)
				}
			}

			Button(
				onClick = apiViewModel::showAddDialog,
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 12.dp)
			) {
				Text("+ 添加 API")
			}
		}
	}

	if (state.isDialogVisible) {
		ApiEditDialog(
			state = state,
			onDismiss = apiViewModel::dismissDialog,
			onNameChange = apiViewModel::onNameChange,
			onKeyChange = apiViewModel::onKeyChange,
			onSave = apiViewModel::saveDialog
		)
	}

	if (state.deletingId != null) {
		DeleteConfirmDialog(
			name = state.deletingName,
			onDismiss = apiViewModel::dismissDeleteConfirm,
			onConfirm = apiViewModel::confirmDelete
		)
	}
}

@Composable
private fun ApiRow(
	item: ApiItemUi,
	onEdit: () -> Unit,
	onDelete: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 6.dp),
		horizontalArrangement = Arrangement.SpaceBetween
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(text = item.name, style = MaterialTheme.typography.titleMedium)
			Text(text = item.maskedKey, style = MaterialTheme.typography.bodyMedium)
		}

		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			TextButton(onClick = onEdit) { Text("编辑") }
			TextButton(onClick = onDelete) { Text("删除") }
		}
	}
	HorizontalDivider()
}

@Composable
private fun ApiEditDialog(
	state: ApiScreenState,
	onDismiss: () -> Unit,
	onNameChange: (String) -> Unit,
	onKeyChange: (String) -> Unit,
	onSave: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(if (state.dialogMode == DialogMode.Add) "添加 API" else "编辑 API")
		},
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = state.nameInput,
					onValueChange = onNameChange,
					singleLine = true,
					label = { Text("名称") },
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = state.keyInput,
					onValueChange = onKeyChange,
					singleLine = true,
					label = { Text("API Key") },
					placeholder = { Text(state.keyHint) },
					modifier = Modifier.fillMaxWidth()
				)
				state.errorMessage?.let {
					Text(text = it, color = MaterialTheme.colorScheme.error)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onSave) { Text("保存") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("取消") }
		}
	)
}

@Composable
private fun DeleteConfirmDialog(
	name: String,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("确认删除") },
		text = { Text("确定要删除 API \"$name\" 吗？此操作不可撤销。") },
		confirmButton = {
			TextButton(onClick = onConfirm) { Text("删除") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("取消") }
		}
	)
}

