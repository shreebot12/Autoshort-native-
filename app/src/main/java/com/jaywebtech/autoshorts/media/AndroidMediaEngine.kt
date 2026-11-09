package com.jaywebtech.autoshorts.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidMediaEngine(private val context: Context) {
    fun durationMs(uri: Uri): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    private fun hasVideo(uri: Uri): Boolean = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                ?.equals("yes", ignoreCase = true) == true
        } finally {
            retriever.release()
        }
    }.getOrDefault(false)

    suspend fun extractAudio(source: Uri, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        if (!hasVideo(source)) {
            val extension = when (context.contentResolver.getType(source)?.substringAfter('/')?.lowercase()) {
                "mpeg" -> "mp3"
                "wav", "x-wav" -> "wav"
                "aac" -> "aac"
                "flac" -> "flac"
                "ogg", "opus" -> "ogg"
                else -> "m4a"
            }
            val originalAudio = File(context.cacheDir, "audio-${UUID.randomUUID()}.$extension")
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    originalAudio.outputStream().use(input::copyTo)
                } ?: error("Could not open the selected audio file.")
                onProgress(100)
                return@withContext originalAudio
            } catch (error: Throwable) {
                originalAudio.delete()
                throw error
            }
        }

        val output = File(context.cacheDir, "audio-${UUID.randomUUID()}.m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, source, null)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { track ->
                extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This file does not contain an audio track.")
            val inputFormat = extractor.getTrackFormat(audioTrack)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(inputFormat)
            extractor.selectTrack(audioTrack)
            muxer.start()

            val buffer = java.nio.ByteBuffer.allocateDirect(1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            val durationUs = inputFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            var lastProgress = -1
            while (true) {
                buffer.clear()
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                val sampleFlags = extractor.sampleFlags
                check(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                    "Encrypted media cannot be exported."
                }
                info.flags = 0
                if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    info.flags = info.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                    info.flags = info.flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                muxer.writeSampleData(outputTrack, buffer, info)
                val progress = ((info.presentationTimeUs * 100) / durationUs).toInt().coerceIn(0, 100)
                if (progress != lastProgress) {
                    onProgress(progress)
                    lastProgress = progress
                }
                extractor.advance()
            }
            onProgress(100)
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    @UnstableApi
    suspend fun exportPortraitClip(
        source: Uri,
        startMs: Long,
        endMs: Long,
        displayName: String,
    ): Uri {
        require(endMs > startMs) { "Clip end must be after its start." }
        val temporary = File(context.cacheDir, "${UUID.randomUUID()}.mp4")
        val editedMediaItem = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(source)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build(),
                )
                .build(),
        ).setEffects(
            Effects(
                emptyList(),
                if (hasVideo(source)) listOf(
                    Presentation.createForWidthAndHeight(
                        1080,
                        1920,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                    ),
                ) else emptyList(),
            ),
        ).build()

        try {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val transformer = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(
                                composition: androidx.media3.transformer.Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        })
                        .build()
                    continuation.invokeOnCancellation { transformer.cancel() }
                    transformer.start(editedMediaItem, temporary.absolutePath)
                }
            }
            return withContext(Dispatchers.IO) { publishVideo(temporary, displayName) }
        } finally {
            temporary.delete()
        }
    }

    suspend fun exportCaptions(displayName: String, content: String): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.srt")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AutoShorts")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create the caption file.")
            try {
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                    ?: error("Could not write the caption file.")
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, ready, null, null)
                uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AutoShorts")
            folder.mkdirs()
            val file = File(folder, "$displayName.srt")
            file.writeText(content)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }
    }

    private fun publishVideo(source: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AutoShorts")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val destination = resolver.insert(collection, values) ?: error("Could not create the exported video.")
        try {
            resolver.openOutputStream(destination)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: error("Could not open the exported video destination.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(destination, ready, null, null)
            }
            return destination
        } catch (error: Throwable) {
            resolver.delete(destination, null, null)
            throw error
        }
    }
}