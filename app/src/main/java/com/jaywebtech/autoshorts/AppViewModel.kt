package com.jaywebtech.autoshorts

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.getWorkInfosForUniqueWorkFlow
import androidx.work.workDataOf
import com.jaywebtech.autoshorts.data.AutoShortsRepository
import com.jaywebtech.autoshorts.data.CandidateEntity
import com.jaywebtech.autoshorts.data.ProjectEntity
import com.jaywebtech.autoshorts.data.TranscriptSegment
import com.jaywebtech.autoshorts.work.AnalysisWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AutoShortsRepository(application)
    private val workManager = WorkManager.getInstance(application)
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

    private val selectedWorkInfos = selectedProjectId.flatMapLatest { id ->
        id?.let { workManager.getWorkInfosForUniqueWorkFlow(AnalysisWorker.uniqueName(it)) }
            ?: flowOf<List<WorkInfo>>(emptyList())
    }
    val busy: StateFlow<Boolean> = selectedWorkInfos
        .map { infos -> infos.any { !it.state.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val progress: StateFlow<PipelineProgress?> = selectedWorkInfos
        .map { infos ->
            infos.firstOrNull { !it.state.isFinished }?.let { workInfo ->
                PipelineProgress(
                    stage = workInfo.progress.getString(AnalysisWorker.KEY_STAGE) ?: "Queued",
                    percent = workInfo.progress.getInt(AnalysisWorker.KEY_PERCENT, 0),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
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

    fun importMedia(uris: List<Uri>, youtubeUrl: String? = null, onImported: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _message.value = null
            var lastImported: ProjectEntity? = null
            uris.forEach { uri ->
                runCatching { repository.importMedia(uri, youtubeUrl.takeIf { uris.size == 1 }) }
                    .onSuccess { lastImported = it }
                    .onFailure { _message.value = it.message ?: "Unable to import this media." }
            }
            lastImported?.let { project ->
                openProject(project.id)
                onImported(project.id)
            }
        }
    }

    fun showMessage(message: String) {
        _message.value = message
    }

    fun analyze() {
        val projectId = selectedProjectId.value ?: return
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf(AnalysisWorker.KEY_PROJECT_ID to projectId))
            .build()
        workManager.enqueueUniqueWork(AnalysisWorker.uniqueName(projectId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAnalysis() {
        selectedProjectId.value?.let { workManager.cancelUniqueWork(AnalysisWorker.uniqueName(it)) }
    }

    fun updateCandidate(candidate: CandidateEntity) {
        viewModelScope.launch { repository.dao.updateCandidate(candidate) }
    }

    fun addManualClip(startMs: Long, endMs: Long, title: String) {
        val projectId = selectedProjectId.value ?: return
        viewModelScope.launch {
            runCatching { repository.addManualCandidate(projectId, startMs, endMs, title) }
                .onFailure { _message.value = it.message ?: "Unable to create the clip." }
        }
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