package com.bbblllllocck.canned_emotions.core.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import java.io.File

//this scanner is way too slow, required redo
object FileScanner {
    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus"
    )

    private fun toTaskEntity(file: File): MusicScanTaskEntity {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val now = System.currentTimeMillis()

            MusicScanTaskEntity(
                filePath = file.absolutePath,
                title = title,
                album = album,
                artist = artist,
                updatedAtMillis = now
            )
        } finally {
            retriever.release()
        }
    }

    private fun toTaskEntity(context: Context, file: DocumentFile): MusicScanTaskEntity {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)

            val fallbackName = file.name
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: file.uri.lastPathSegment.orEmpty()
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackName
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val now = System.currentTimeMillis()

            MusicScanTaskEntity(
                // SAF directory scanning stores the content Uri as a stable unique key.
                filePath = file.uri.toString(),
                title = title,
                album = album,
                artist = artist,
                updatedAtMillis = now
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * Scan a local directory path and return parsed tasks.
     * Note: this requires file-system readable paths.
     */

    fun scanDirectory(directoryPath: String): List<MusicScanTaskEntity> {
        val root = File(directoryPath)
        if (!root.exists() || !root.isDirectory) return emptyList()

        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .mapNotNull { file ->
                runCatching { toTaskEntity(file) }.getOrNull()
            }
            .toList()
    }

    fun scanAndSave(directoryPath: String): Int {
        val tasks = scanDirectory(directoryPath)
        return DatabaseManager.upsertMusicTasks(tasks)
    }

    fun scanAndSave(context: Context, treeUri: Uri): Int {
        val tasks = scanTreeUri(context, treeUri)
        return DatabaseManager.upsertMusicTasks(tasks)
    }

    fun scanTreeUri(context: Context, treeUri: Uri): List<MusicScanTaskEntity> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!root.exists() || !root.isDirectory) return emptyList()

        return walkTree(root)
            .filter { it.isFile && isSupportedAudioName(it.name) }
            .mapNotNull { file ->
                runCatching { toTaskEntity(context, file) }.getOrNull()
            }
            .toList()
    }

    private fun walkTree(root: DocumentFile): Sequence<DocumentFile> = sequence {
        yield(root)
        if (root.isDirectory) {
            root.listFiles().forEach { child ->
                yieldAll(walkTree(child))
            }
        }
    }

    private fun isSupportedAudioName(name: String?): Boolean {
        val ext = name
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            .orEmpty()
        return ext in supportedExtensions
    }

}
