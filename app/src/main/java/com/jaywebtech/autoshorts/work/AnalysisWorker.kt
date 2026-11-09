package com.jaywebtech.autoshorts.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jaywebtech.autoshorts.data.AutoShortsRepository
import kotlinx.coroutines.CancellationException

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID) ?: return Result.failure()
        val repository = AutoShortsRepository(applicationContext)
        return try {
            repository.analyze(projectId) { stage, percent ->
                setProgressAsync(workDataOf(KEY_STAGE to stage, KEY_PERCENT to percent))
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Analysis failed.")))
        }
    }

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        const val KEY_ERROR = "error"

        fun uniqueName(projectId: String): String = "analysis:$projectId"
    }
}