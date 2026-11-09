package com.jaywebtech.autoshorts

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaywebtech.autoshorts.data.AutoShortsRepository
import com.jaywebtech.autoshorts.data.CandidateEntity
import com.jaywebtech.autoshorts.data.ProjectEntity
import com.jaywebtech.autoshorts.data.TranscriptSegment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AutoShortsRepository(application)
    val settings = repository.settings
    val projects: StateFlow<List<ProjectEntity>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProject: StateFlow<ProjectEntity?> = selectedProjectId
        .flatMapLatest { id -> id?.let(repository::observeProject) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val candidates: StateFlow<List<CandidateEntity>> = selectedProjectId
        .flatMapLatest { id -> id?.let(repository::observeCandidates) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _progress = MutableStateFlow<PipelineProgress?>(null)
    val progress: StateFlow<PipelineProgress?> = _progress
    private val _workingCandidateId = MutableStateFlow<String?>(null)
    val workingCandidateId: StateFlow<String?> = _workingCandidateId
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun openProject(id: String) {
        selectedProjectId.value = id
        _message.value = null
    }

    fun closeProject() {
        selectedProjectId.value = null
    }

    fun importMedia(uri: Uri, onImported: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.importMedia(uri) }
                .onSuccess { project ->
                    openProject(project.id)
                    onImported(project.id)
                }
                .onFailure { _message.value = it.message ?: "Unable to import this media." }
        }
    }

    fun analyze() {
        val projectId = selectedProjectId.value ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { repository.analyze(projectId) { stage, percent -> _progress.value = PipelineProgress(stage, percent) } }
                .onFailure { _message.value = it.message ?: "Analysis failed." }
            _busy.value = false
            _progress.value = null
        }
    }

    fun updateCandidate(candidate: CandidateEntity) {
        viewModelScope.launch { repository.dao.updateCandidate(candidate) }
    }

    fun setSelected(candidate: CandidateEntity, selected: Boolean) {
        updateCandidate(candidate.copy(selected = selected))
    }

    fun export(candidate: CandidateEntity) {
        if (_workingCandidateId.value != null) return
        viewModelScope.launch {
            _workingCandidateId.value = candidate.id
            _message.value = null
            runCatching { repository.exportCandidate(candidate) {} }
                .onFailure { _message.value = it.message ?: "Clip export failed." }
            _workingCandidateId.value = null
        }
    }

    fun delete(project: ProjectEntity) {
        viewModelScope.launch {
            repository.dao.deleteProject(project.id)
            if (selectedProjectId.value == project.id) closeProject()
        }
    }

    fun renameProject(projectId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.dao.getProject(projectId)?.let { project ->
                repository.dao.updateProject(project.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}

data class PipelineProgress(val stage: String, val percent: Int)