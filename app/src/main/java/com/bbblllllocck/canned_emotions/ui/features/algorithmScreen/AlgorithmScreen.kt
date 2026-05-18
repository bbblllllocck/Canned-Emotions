package com.bbblllllocck.canned_emotions.ui.features.algorithmScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bbblllllocck.canned_emotions.core.algorithm.Template
import kotlin.math.roundToInt

private enum class TimeUnitType(val label: String, val factorMs: Float) {
    Seconds("秒", 1000f),
    Minutes("分钟", 60_000f),
    Days("天", 86_400_000f)
}

private const val MS_PER_MINUTE = 60_000f

@Composable
fun AlgorithmScreen() {
    val viewModel: AlgorithmViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    if (state.isEditing) {
        state.editingTemplate?.let { template ->
            AlgorithmEditScreen(
                template = template,
                onNameChange = viewModel::updateName,
                onCoefficientOfUnrelatedChange = viewModel::updateCoefficientOfUnrelated,
                onIntegratedParameterWeightChange = viewModel::updateIntegratedParameterWeight,
                onIntegratedParameterHalfLifeChange = viewModel::updateIntegratedParameterHalfLife,
                onArtistDuplicateCoefficientChange = viewModel::updateArtistDuplicateCoefficient,
                onArtistDuplicateFadeTimeChange = viewModel::updateArtistDuplicateFadeTime,
                onArtistPardonTimeChange = viewModel::updateArtistPardonTime,
                onAlbumDuplicateCoefficientChange = viewModel::updateAlbumDuplicateCoefficient,
                onAlbumDuplicateFadeTimeChange = viewModel::updateAlbumDuplicateFadeTime,
                onAlbumPardonTimeChange = viewModel::updateAlbumPardonTime,
                onTemperatureChange = viewModel::updateTemperature,
                onPunishmentVectorFadeTimeChange = viewModel::updatePunishmentVectorFadeTime,
                onPunishmentVectorWeightChange = viewModel::updatePunishmentVectorWeight,
                onPunishmentVectorWeightWhenSwitchChange = viewModel::updatePunishmentVectorWeightWhenSwitch,
                onSongProportionChange = viewModel::updateSongProportion,
                onToleranceChange = viewModel::updateTolerance,
                onRoamingTypeChange = viewModel::updateRoamingType,
                onRegressionLengthChange = viewModel::updateRegressionLength,
                onInitialRegressionWeightChange = viewModel::updateInitialRegressionWeight,
                onDirectionRatioChange = viewModel::updateDirectionRatio,
                onDone = viewModel::finishEditing,
                onAutoSave = viewModel::commitEditingIfNeeded
            )
            return
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(text = "算法模板", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "点击模板切换当前算法参数，编辑进入详细设置页。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "显示权重", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.showWeightDetails,
                    onCheckedChange = viewModel::setWeightDetailsEnabled
                )
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            if (state.templates.isEmpty()) {
                Text(
                    text = "暂无模板，请先创建一个。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.templates, key = { _, item -> item.id }) { index, item ->
                        TemplateRow(
                            index = index,
                            template = item,
                            isSelected = item.id == state.usingTemplateId,
                            canDelete = state.templates.size > 1,
                            onSelect = { viewModel.selectTemplate(item.id) },
                            onEdit = { viewModel.startEditing(item.id) },
                            onDelete = { viewModel.deleteTemplate(item.id) }
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::createAndEditTemplate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("创建模板")
            }
        }
    }
}

@Composable
private fun TemplateRow(
    index: Int,
    template: Template,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "#${index + 1}", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = template.name, style = MaterialTheme.typography.titleMedium)
                if (isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "正在使用",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "温度 ${"%.2f".format(template.temperature)} · 惩罚 ${"%.2f".format(template.punishmentVectorWeight)} · 半衰期 ${template.integratedParameterHalfLife} 天",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete, enabled = canDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun AlgorithmEditScreen(
    template: Template,
    onNameChange: (String) -> Unit,
    onCoefficientOfUnrelatedChange: (Float) -> Unit,
    onIntegratedParameterWeightChange: (Float) -> Unit,
    onIntegratedParameterHalfLifeChange: (Int) -> Unit,
    onArtistDuplicateCoefficientChange: (Float) -> Unit,
    onArtistDuplicateFadeTimeChange: (Int) -> Unit,
    onArtistPardonTimeChange: (Int) -> Unit,
    onAlbumDuplicateCoefficientChange: (Float) -> Unit,
    onAlbumDuplicateFadeTimeChange: (Int) -> Unit,
    onAlbumPardonTimeChange: (Int) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onPunishmentVectorFadeTimeChange: (Int) -> Unit,
    onPunishmentVectorWeightChange: (Float) -> Unit,
    onPunishmentVectorWeightWhenSwitchChange: (Float) -> Unit,
    onSongProportionChange: (Float) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onRoamingTypeChange: (Int) -> Unit,
    onRegressionLengthChange: (Int) -> Unit,
    onInitialRegressionWeightChange: (Float) -> Unit,
    onDirectionRatioChange: (Float) -> Unit,
    onDone: () -> Unit,
    onAutoSave: () -> Unit
) {
    DisposableEffect(Unit) {
        onDispose { onAutoSave() }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "模板编辑", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onDone) { Text("完成") }
            }

            OutlinedTextField(
                value = template.name,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text("模板名称") },
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(title = "相关性与次数", subtitle = "设置重复惩罚与随时间衰减的权重。")
            FloatSliderWithInput(
                label = "不相关系数",
                description = "越大越排斥重复内容，影响整体权重。",
                value = template.COEFFICIENT_OF_UNRELATED,
                range = 0f..1f,
                onValueChange = onCoefficientOfUnrelatedChange
            )
            FloatSliderWithInput(
                label = "时间&次数权重",
                description = "听得越多，越容易被衰减。",
                value = template.integratedParameterWeight,
                range = 0f..1f,
                onValueChange = onIntegratedParameterWeightChange
            )
            IntSliderWithInput(
                label = "半衰期",
                description = "听歌次数影响在此天数内衰减一半。",
                value = template.integratedParameterHalfLife,
                range = 1..365,
                unitLabel = "天",
                onValueChange = onIntegratedParameterHalfLifeChange
            )

            SectionHeader(title = "查重策略", subtitle = "在赦免时间内允许重复，查重时间内降低权重，消逝时间后清除。")
            DuplicatePolicyGroup(
                title = "艺术家",
                coefficientPerMinute = template.artistDuplicateCoefficient * MS_PER_MINUTE,
                onCoefficientPerMinuteChange = { onArtistDuplicateCoefficientChange(it / MS_PER_MINUTE) },
                pardonTimeMs = template.artistPardonTime,
                onPardonTimeChange = onArtistPardonTimeChange,
                fadeTimeMs = template.artistDuplicateFadeTime,
                onFadeTimeChange = onArtistDuplicateFadeTimeChange
            )
            DuplicatePolicyGroup(
                title = "专辑",
                coefficientPerMinute = template.albumDuplicateCoefficient * MS_PER_MINUTE,
                onCoefficientPerMinuteChange = { onAlbumDuplicateCoefficientChange(it / MS_PER_MINUTE) },
                pardonTimeMs = template.albumPardonTime,
                onPardonTimeChange = onAlbumPardonTimeChange,
                fadeTimeMs = template.albumDuplicateFadeTime,
                onFadeTimeChange = onAlbumDuplicateFadeTimeChange
            )

            SectionHeader(title = "随机性", subtitle = "控制结果的随机程度。")
            FloatSliderWithInput(
                label = "温度",
                description = "越高越随机，越低越稳定。",
                value = template.temperature,
                range = 0f..1f,
                onValueChange = onTemperatureChange
            )

            SectionHeader(title = "惩罚向量", subtitle = "对被跳过歌曲的惩罚强度。")
            TimeSliderWithInput(
                label = "惩罚衰减时长",
                description = "惩罚在此秒数内逐步衰减。",
                valueMs = template.punishmentVectorFadeTime,
                rangeMs = 0..1_000_000,
                unit = TimeUnitType.Seconds,
                onValueChange = onPunishmentVectorFadeTimeChange
            )
            FloatSliderWithInput(
                label = "惩罚权重",
                description = "越大越强烈地避免近期被惩罚的歌曲。",
                value = template.punishmentVectorWeight,
                range = 0f..1f,
                onValueChange = onPunishmentVectorWeightChange
            )
            FloatSliderWithInput(
                label = "手动切歌惩罚",
                description = "手动切歌时惩罚向量额外权重。",
                value = template.punishmentVectorWeightWhenSwitch,
                range = 0f..1f,
                onValueChange = onPunishmentVectorWeightWhenSwitchChange
            )

            SectionHeader(title = "单曲比例", subtitle = "开启后才允许设置单曲与纯音乐比例。")
            ProportionToggleRow(
                enabled = template.songProportion >= 0f,
                onToggle = { enabled ->
                    onSongProportionChange(if (enabled) template.songProportion.coerceIn(0f, 1f) else -1f)
                }
            )
            FloatSliderWithInput(
                label = "单曲/纯音比例",
                description = "0 偏纯音乐，1 偏单曲。",
                value = template.songProportion.coerceIn(0f, 1f),
                range = 0f..1f,
                enabled = template.songProportion >= 0f,
                onValueChange = onSongProportionChange
            )
            TimeSliderWithInput(
                label = "比例容忍时间",
                description = "达到此时间后比例偏好影响更明显。",
                valueMs = template.tolerance,
                rangeMs = 0..1_000_000,
                unit = TimeUnitType.Seconds,
                onValueChange = onToleranceChange
            )

            SectionHeader(title = "扩散模式", subtitle = "控制算法在相似空间中的扩散方式。")
            IntSliderWithInput(
                label = "扩散类型",
                description = "0 为中心扩散，1 为线性扩散。",
                value = template.roamingType,
                range = 0..1,
                onValueChange = onRoamingTypeChange
            )
            val isLinear = template.roamingType == 1
            TimeSliderWithInput(
                label = "回归长度",
                description = "线性扩散中回归向量的作用时长。",
                valueMs = template.regressionLength,
                rangeMs = 0..7_200_000,
                unit = TimeUnitType.Minutes,
                enabled = isLinear,
                onValueChange = onRegressionLengthChange
            )
            FloatSliderWithInput(
                label = "回归初始权重",
                description = "线性扩散中回归向量的初始占比。",
                value = template.initialRegressionWeight,
                range = 0f..1f,
                enabled = isLinear,
                onValueChange = onInitialRegressionWeightChange
            )
            FloatSliderWithInput(
                label = "方向比例",
                description = "线性扩散方向在剩余权重中的占比。",
                value = template.directionRatio,
                range = 0f..1f,
                enabled = isLinear,
                onValueChange = onDirectionRatioChange
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun DuplicatePolicyGroup(
    title: String,
    coefficientPerMinute: Float,
    onCoefficientPerMinuteChange: (Float) -> Unit,
    pardonTimeMs: Int,
    onPardonTimeChange: (Int) -> Unit,
    fadeTimeMs: Int,
    onFadeTimeChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "$title 查重", style = MaterialTheme.typography.titleSmall)
            TimeSliderWithInput(
                label = "赦免时间",
                description = "这段时间内允许重复播放。",
                valueMs = pardonTimeMs,
                rangeMs = 0..1_800_000,
                unit = TimeUnitType.Minutes,
                onValueChange = onPardonTimeChange
            )
            FloatSliderWithInput(
                label = "查重强度（每分钟）",
                description = "每分钟累积的重复惩罚强度。",
                value = coefficientPerMinute,
                range = 0f..1f,
                onValueChange = onCoefficientPerMinuteChange
            )
            TimeSliderWithInput(
                label = "消逝时间",
                description = "惩罚在这段时间内逐步清除。",
                valueMs = fadeTimeMs,
                rangeMs = 0..1_800_000,
                unit = TimeUnitType.Minutes,
                onValueChange = onFadeTimeChange
            )
        }
    }
}

@Composable
private fun ProportionToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "启用比例控制", style = MaterialTheme.typography.bodyMedium)
            Text(text = "关闭后不限制单曲/纯音乐比例。", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun FloatSliderWithInput(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    unitLabel: String? = null,
    format: (Float) -> String = { "%.2f".format(it) }
) {
    var input by remember(value) { mutableStateOf(format(value)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = description, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = value.coerceIn(range),
                onValueChange = { onValueChange(it.coerceIn(range)) },
                valueRange = range,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { next ->
                    input = next
                    next.toFloatOrNull()?.let { parsed ->
                        onValueChange(parsed.coerceIn(range))
                    }
                },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.width(96.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = {
                    if (unitLabel != null) {
                        Text(unitLabel)
                    }
                }
            )
        }
    }
}

@Composable
private fun IntSliderWithInput(
    label: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    unitLabel: String? = null
) {
    var input by remember(value) { mutableStateOf(value.toString()) }
    val floatRange = range.first.toFloat()..range.last.toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = description, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = value.toFloat().coerceIn(floatRange),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(range)) },
                valueRange = floatRange,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { next ->
                    input = next
                    next.toIntOrNull()?.let { parsed ->
                        onValueChange(parsed.coerceIn(range))
                    }
                },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.width(96.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = {
                    if (unitLabel != null) {
                        Text(unitLabel)
                    }
                }
            )
        }
    }
}

@Composable
private fun TimeSliderWithInput(
    label: String,
    description: String,
    valueMs: Int,
    rangeMs: IntRange,
    unit: TimeUnitType,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true
) {
    val factor = unit.factorMs
    val valueInUnit = valueMs / factor
    val rangeInUnit = rangeMs.first / factor..rangeMs.last / factor
    FloatSliderWithInput(
        label = label,
        description = description,
        value = valueInUnit,
        range = rangeInUnit,
        unitLabel = unit.label,
        format = { "%.1f".format(it) },
        enabled = enabled,
        onValueChange = { unitValue ->
            onValueChange((unitValue * factor).roundToInt().coerceAtLeast(0))
        }
    )
}
