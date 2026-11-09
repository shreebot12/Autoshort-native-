package com.jaywebtech.autoshorts.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUri: String,
    val durationMs: Long,
    val status: String = "Imported",
    val progress: Int = 0,
    val stage: String = "Ready to analyze",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val youtubeUrl: String? = null,
)

@Entity(
    tableName = "transcripts",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("projectId")],
)
data class TranscriptEntity(
    @PrimaryKey val projectId: String,
    val engine: String,
    val language: String,
    val durationMs: Long,
    val segmentsJson: String,
)

@Entity(
    tableName = "candidates",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("projectId")],
)
data class CandidateEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val startMs: Long,
    val endMs: Long,
    val score: Double,
    val hook: String,
    val rationale: String,
    val rank: Int,
    val selected: Boolean = true,
    val renderStatus: String = "Not exported",
    val outputUri: String? = null,
    val captionUri: String? = null,
    @ColumnInfo(defaultValue = "'AI'") val source: String = "AI",
)