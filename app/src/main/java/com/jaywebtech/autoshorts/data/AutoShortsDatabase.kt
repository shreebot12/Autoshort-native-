package com.jaywebtech.autoshorts.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoShortsDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProject(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProject(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTranscript(transcript: TranscriptEntity)

    @Query("SELECT * FROM transcripts WHERE projectId = :projectId")
    suspend fun getTranscript(projectId: String): TranscriptEntity?

    @Query("SELECT * FROM candidates WHERE projectId = :projectId ORDER BY rank ASC")
    fun observeCandidates(projectId: String): Flow<List<CandidateEntity>>

    @Query("DELETE FROM candidates WHERE projectId = :projectId")
    suspend fun clearCandidates(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<CandidateEntity>)

    @Update
    suspend fun updateCandidate(candidate: CandidateEntity)

    @Transaction
    suspend fun replaceCandidates(projectId: String, candidates: List<CandidateEntity>) {
        clearCandidates(projectId)
        insertCandidates(candidates)
    }
}

@Database(
    entities = [ProjectEntity::class, TranscriptEntity::class, CandidateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AutoShortsDatabase : RoomDatabase() {
    abstract fun dao(): AutoShortsDao

    companion object {
        @Volatile
        private var instance: AutoShortsDatabase? = null

        fun get(context: Context): AutoShortsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AutoShortsDatabase::class.java,
                "autoshorts.db",
            ).build().also { instance = it }
        }
    }
}