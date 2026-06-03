package com.bbblllllocck.canned_emotions.ui.features.algorithmScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbblllllocck.canned_emotions.core.algorithm.Template
import com.bbblllllocck.canned_emotions.core.algorithm.TemplateManager
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlgorithmScreenState(
    val templates: List<Template> = emptyList(),
    val usingTemplateId: String? = null,
    val isEditing: Boolean = false,
    val editingTemplate: Template? = null,
    val showWeightDetails: Boolean = false,
    val showUncertaintyArea: Boolean = true
)

class AlgorithmViewModel : ViewModel() {

    private val _state = MutableStateFlow(AlgorithmScreenState())
    val state: StateFlow<AlgorithmScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            TemplateManager.observeAll().collectLatest { templates ->
                _state.update { current ->
                    val editing = current.editingTemplate
                    val refreshedEditing = editing?.let { editor ->
                        templates.firstOrNull { it.id == editor.id }?.let { editor } ?: editor
                    }
                    current.copy(templates = templates, editingTemplate = refreshedEditing)
                }
            }
        }
        viewModelScope.launch {
            TemplateManager.observeUsingTemplateId().collectLatest { id ->
                _state.update { it.copy(usingTemplateId = id) }
            }
        }
        viewModelScope.launch {
            TemplateManager.observeWeightDisplayEnabled().collectLatest { enabled ->
                _state.update { it.copy(showWeightDetails = enabled) }
            }
        }
        viewModelScope.launch {
            TemplateManager.observeShowUncertaintyAreaEnabled().collectLatest { enabled ->
                _state.update { it.copy(showUncertaintyArea = enabled) }
            }
        }
    }

    fun selectTemplate(id: String) {
        TemplateManager.setUsingTemplate(id)
    }

    fun deleteTemplate(id: String) {
        TemplateManager.removeTemplate(id)
        _state.update { current ->
            val isEditing = current.editingTemplate?.id == id && current.isEditing
            current.copy(
                isEditing = if (isEditing) false else current.isEditing,
                editingTemplate = if (current.editingTemplate?.id == id) null else current.editingTemplate
            )
        }
    }

    fun startEditing(id: String) {
        val template = _state.value.templates.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(isEditing = true, editingTemplate = template.copy()) }
    }

    fun createAndEditTemplate() {
        val template = Template(id = UUID.randomUUID().toString(), name = "新模板")
        _state.update { it.copy(isEditing = true, editingTemplate = template) }
    }

    fun finishEditing() {
        _state.update { it.copy(isEditing = false) }
    }

    fun commitEditingIfNeeded() {
        val editing = _state.value.editingTemplate ?: return
        TemplateManager.addTemplate(editing)
        _state.update { it.copy(editingTemplate = null) }
    }

    fun updateName(value: String) {
        updateEditing { it.copy(name = value) }
    }

    fun updateCoefficientOfUnrelated(value: Float) {
        updateEditing { it.copy(COEFFICIENT_OF_UNRELATED = value) }
    }

    fun updateIntegratedParameterWeight(value: Float) {
        updateEditing { it.copy(integratedParameterWeight = value, savedIntegratedParameterWeight = value) }
    }

    fun updateEnableIntegratedParameter(value: Boolean) {
        updateEditing {
            val saved = if (!value && it.integratedParameterWeight > 0f) it.integratedParameterWeight else it.savedIntegratedParameterWeight
            it.copy(
                enableIntegratedParameter = value,
                savedIntegratedParameterWeight = saved,
                integratedParameterWeight = if (value) saved else 0f
            )
        }
    }

    fun updateIntegratedParameterHalfLife(value: Int) {
        updateEditing { it.copy(integratedParameterHalfLife = value) }
    }

    fun updateArtistDuplicateCoefficient(value: Float) {
        updateEditing { it.copy(artistDuplicateCoefficient = value, savedArtistDuplicateCoefficient = value) }
    }

    fun updateEnableArtistDuplicate(value: Boolean) {
        updateEditing {
            val saved = if (!value && it.artistDuplicateCoefficient > 0f) it.artistDuplicateCoefficient else it.savedArtistDuplicateCoefficient
            it.copy(
                enableArtistDuplicate = value,
                savedArtistDuplicateCoefficient = saved,
                artistDuplicateCoefficient = if (value) saved else 0f
            )
        }
    }

    fun updateArtistDuplicateFadeTime(value: Int) {
        updateEditing { it.copy(artistDuplicateFadeTime = value) }
    }

    fun updateArtistPardonTime(value: Int) {
        updateEditing { it.copy(artistPardonTime = value) }
    }

    fun updateAlbumDuplicateCoefficient(value: Float) {
        updateEditing { it.copy(albumDuplicateCoefficient = value, savedAlbumDuplicateCoefficient = value) }
    }

    fun updateEnableAlbumDuplicate(value: Boolean) {
        updateEditing {
            val saved = if (!value && it.albumDuplicateCoefficient > 0f) it.albumDuplicateCoefficient else it.savedAlbumDuplicateCoefficient
            it.copy(
                enableAlbumDuplicate = value,
                savedAlbumDuplicateCoefficient = saved,
                albumDuplicateCoefficient = if (value) saved else 0f
            )
        }
    }

    fun updateAlbumDuplicateFadeTime(value: Int) {
        updateEditing { it.copy(albumDuplicateFadeTime = value) }
    }

    fun updateAlbumPardonTime(value: Int) {
        updateEditing { it.copy(albumPardonTime = value) }
    }

    fun updateTemperature(value: Float) {
        updateEditing { it.copy(temperature = value) }
    }

    fun updatePunishmentVectorFadeTime(value: Int) {
        updateEditing { it.copy(punishmentVectorFadeTime = value) }
    }

    fun updatePunishmentVectorWeight(value: Float) {
        updateEditing { it.copy(punishmentVectorWeight = value, savedPunishmentVectorWeight = value) }
    }

    fun updateEnablePunishment(value: Boolean) {
        updateEditing {
            val saved = if (!value && it.punishmentVectorWeight > 0f) it.punishmentVectorWeight else it.savedPunishmentVectorWeight
            it.copy(
                enablePunishment = value,
                savedPunishmentVectorWeight = saved,
                punishmentVectorWeight = if (value) saved else 0f
            )
        }
    }

    fun updatePunishmentVectorWeightWhenSwitch(value: Float) {
        updateEditing { it.copy(punishmentVectorWeightWhenSwitch = value) }
    }

    fun updateSongProportion(value: Float) {
        updateEditing { it.copy(songProportion = value) }
    }

    fun updateTolerance(value: Int) {
        updateEditing { it.copy(tolerance = value) }
    }

    fun updateRoamingType(value: Int) {
        updateEditing { it.copy(roamingType = value) }
    }

    fun updateRegressionLength(value: Int) {
        updateEditing { it.copy(regressionLength = value) }
    }

    fun updateInitialRegressionWeight(value: Float) {
        updateEditing { it.copy(initialRegressionWeight = value) }
    }

    fun updateDirectionRatio(value: Float) {
        updateEditing { it.copy(directionRatio = value) }
    }

    fun setWeightDetailsEnabled(enabled: Boolean) {
        TemplateManager.setWeightDisplayEnabled(enabled)
    }

    fun setShowUncertaintyAreaEnabled(enabled: Boolean) {
        TemplateManager.setShowUncertaintyAreaEnabled(enabled)
    }

    private fun updateEditing(transform: (Template) -> Template) {
        _state.update { current ->
            val editing = current.editingTemplate ?: return@update current
            current.copy(editingTemplate = transform(editing))
        }
    }
}