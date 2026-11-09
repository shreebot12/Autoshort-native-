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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidate(candidate: CandidateEntity)

    @Query("SELECT COALESCE(MAX(rank), 0) FROM candidates WHERE projectId = :projectId")
    suspend fun maxCandidateRank(projectId: String): Int

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
    version = 3,
    exportSchema = false,
)
abstract class AutoShortsDatabase : RoomDatabase() {
    abstract fun dao(): AutoShortsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN youtubeUrl TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE candidates ADD COLUMN source TEXT NOT NULL DEFAULT 'AI'")
            }
        }

        @Volatile
        private var instance: AutoShortsDatabase? = null

        fun get(context: Context): AutoShortsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AutoShortsDatabase::class.java,
                "autoshorts.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}