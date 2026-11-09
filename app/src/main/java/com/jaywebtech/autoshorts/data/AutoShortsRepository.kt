package com.jaywebtech.autoshorts.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.jaywebtech.autoshorts.media.AndroidMediaEngine
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

class AutoShortsRepository(context: Context) {
    private val appContext = context.applicationContext
    val dao = AutoShortsDatabase.get(appContext).dao()
    val settings = AppSettings(appContext)
    val media = AndroidMediaEngine(appContext)
    private val api = AiApi(settings)

    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()
    fun observeProject(id: String): Flow<ProjectEntity?> = dao.observeProject(id)
    fun observeCandidates(id: String): Flow<List<CandidateEntity>> = dao.observeCandidates(id)

    suspend fun importMedia(uri: Uri): ProjectEntity = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val duration = media.durationMs(uri)
        require(duration > 0) { "Could not read the selected media duration." }
        val name = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.substringBeforeLast('.')?.takeIf(String::isNotBlank) ?: "New project"
        ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            sourceUri = uri.toString(),
            durationMs = duration,
        ).also { dao.insertProject(it) }
    }

    suspend fun analyze(projectId: String, onStage: (String, Int) -> Unit) {
        val project = dao.getProject(projectId) ?: error("Project not found.")
        require(settings.deepgramKey.isNotBlank()) { "Add a Deepgram API key in Settings before analyzing media." }
        require(settings.llmKey.isNotBlank()) { "Add an API key for ${settings.llmProvider.label} in Settings." }

        try {
            updateStage(project, "Preparing audio", 8, onStage)
            val audioFile = media.extractAudio(Uri.parse(project.sourceUri)) { progress ->
                onStage("Preparing audio", 8 + progress * 22 / 100)
            }
            val transcript = withContext(Dispatchers.IO) { api.transcribe(audioFile) }
            audioFile.delete()
            val segmentJson = JSONArray().apply {
                transcript.segments.forEach { segment ->
                    put(JSONObject().apply {
                        put("startMs", segment.startMs)
                        put("endMs", segment.endMs)
                        put("text", segment.text)
                    })
                }
            }
            dao.saveTranscript(
                TranscriptEntity(
                    projectId = projectId,
                    engine = "Deepgram Nova-2",
                    language = transcript.language,
                    durationMs = transcript.durationMs,
                    segmentsJson = segmentJson.toString(),
                ),
            )
            updateStage(project, "Finding strong moments", 38, onStage)
            val candidates = withContext(Dispatchers.IO) {
                api.findCandidates(transcript.segments, transcript.durationMs, projectId)
            }
            require(candidates.isNotEmpty()) { "The selected model did not return usable clip moments." }
            dao.replaceCandidates(projectId, candidates)
            updateStage(project, "Ready to review", 100, onStage)
        } catch (error: Throwable) {
            val latest = dao.getProject(projectId) ?: project
            dao.updateProject(latest.copy(status = "Needs attention", stage = error.message ?: "Analysis failed", updatedAt = System.currentTimeMillis()))
            throw error
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    suspend fun exportCandidate(candidate: CandidateEntity, onComplete: (Uri) -> Unit) {
        val project = dao.getProject(candidate.projectId) ?: error("Project not found.")
        val working = candidate.copy(renderStatus = "Exporting")
        dao.updateCandidate(working)
        try {
            val uri = media.exportPortraitClip(
                source = Uri.parse(project.sourceUri),
                startMs = candidate.startMs,
                endMs = candidate.endMs,
                displayName = "${project.name.take(36)}_clip_${candidate.rank}",
            )
            val captions = runCatching {
                dao.getTranscript(project.id)?.let { transcript ->
                    media.exportCaptions(
                        displayName = "${project.name.take(36)}_clip_${candidate.rank}",
                        content = captionsFor(transcript, candidate),
                    )
                }
            }.getOrNull()
            dao.updateCandidate(
                working.copy(
                    renderStatus = "Exported",
                    outputUri = uri.toString(),
                    captionUri = captions?.toString(),
                ),
            )
            onComplete(uri)
        } catch (error: Throwable) {
            dao.updateCandidate(working.copy(renderStatus = "Export failed: ${error.message ?: "unknown error"}"))
            throw error
        }
    }

    private suspend fun updateStage(
        project: ProjectEntity,
        stage: String,
        progress: Int,
        onStage: (String, Int) -> Unit,
    ) {
        dao.updateProject(
            project.copy(
                status = if (progress >= 100) "Complete" else "Processing",
                stage = stage,
                progress = progress,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        onStage(stage, progress)
    }

    private fun captionsFor(transcript: TranscriptEntity, candidate: CandidateEntity): String {
        val segments = JSONArray(transcript.segmentsJson)
        val transcriptSegments = (0 until segments.length()).mapNotNull { index ->
            val segment = segments.optJSONObject(index) ?: return@mapNotNull null
            TranscriptSegment(
                startMs = segment.optLong("startMs"),
                endMs = segment.optLong("endMs"),
                text = segment.optString("text"),
            )
        }
        return CaptionFormatter.format(transcriptSegments, candidate.startMs, candidate.endMs)
    }
}