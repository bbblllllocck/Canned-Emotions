package com.bbblllllocck.canned_emotions.core.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.AbstractTagFrame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

object FileScanner {
    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus"
    )

    private const val TAG = "FileScanner"
    private val preferredLyricsKeys = listOf("Lyrics", "LYRICS", "lyrics", "Lyric", "LYRC", "LYR")

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private fun resolveMusicType(title: String, fileName: String, lyrics: String?): Int {
        if (title.contains("（伴奏）") || fileName.contains("（伴奏）")) return 0
        if (title.lowercase().contains("(instrumental)") || fileName.lowercase().contains("(instrumental)")) return 0
        if (lyrics?.contains("纯音乐，请欣赏") == true) return 0

        val lineCount = lyrics.orEmpty().lineSequence().count { it.isNotBlank() }
        return if (lineCount < 15) 0 else 1
    }

    private fun extractLyricsFromTag(tag: Tag?): String? {
        if (tag == null) return null
        return try {
            val fromField = runCatching { tag.getFirst(FieldKey.LYRICS) }.getOrNull()
            if (!fromField.isNullOrBlank()) return fromField

            val iterator = tag.fields
            while (iterator.hasNext()) {
                val field = iterator.next()
                if (preferredLyricsKeys.any { it.equals(field.id, ignoreCase = true) }) {
                    val text = field.toString()
                    if (text.isNotBlank()) return text
                }
            }

            val id3Tag = tag as? AbstractID3v2Tag
            if (id3Tag != null) {
                val txxxObject = runCatching { id3Tag.getFrame("TXXX") }.getOrNull()
                val txxxFrames = when (txxxObject) {
                    is List<*> -> txxxObject.mapNotNull { it as? AbstractTagFrame }
                    is AbstractTagFrame -> listOf(txxxObject)
                    else -> emptyList()
                }

                for (frame in txxxFrames) {
                    val body = frame.body as? FrameBodyTXXX
                    if (preferredLyricsKeys.any { it.equals(body?.description, ignoreCase = true) }) {
                        val text = runCatching { body?.text }.getOrNull()
                        if (!text.isNullOrBlank()) return text
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun readSidecarLyrics(file: File): String? {
        val parent = file.parentFile ?: return null
        val target = File(parent, "${file.nameWithoutExtension}.lrc")
        if (!target.exists()) return null
        return runCatching { target.readText() }.getOrNull()
    }

    private fun readSidecarLyrics(context: Context, file: DocumentFile): String? {
        val parent = file.parentFile ?: return null
        val baseName = file.name?.substringBeforeLast('.') ?: return null
        val match = parent.listFiles().firstOrNull { child ->
            child.isFile && child.name?.equals("$baseName.lrc", ignoreCase = true) == true
        } ?: return null

        return runCatching {
            context.contentResolver.openInputStream(match.uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /**
     * 带断点校验的缓冲区大块拷贝机制
     * 解决 I/O 拥堵导致的流截断和 0 byte 假文件问题
     */
    private fun copyUriToTempFileSafely(context: Context, uri: Uri, tempFile: File): Boolean {
        val maxRetries = 2
        for (attempt in 1..maxRetries) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 1024L) {
                    return true
                } else {
                    Log.w(TAG, "⚠️ 拷贝异常 (生成了残缺文件 ${tempFile.length()} 字节)，准备重试...")
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 跨进程流读取受阻 (第 $attempt 次尝试): ${e.message}")
            }
            Thread.sleep(150)
        }
        return false
    }

    // ========== File 模式解析 ==========

    private fun toTaskEntity(file: File): MusicScanTaskEntity {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val fallbackTitle = file.nameWithoutExtension
            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val album = tag?.getFirst(FieldKey.ALBUM).orEmpty()
            val artist = tag?.getFirst(FieldKey.ARTIST).orEmpty()
            val durationMs = (header?.trackLength?.toLong() ?: -1L) * 1000L

            val jaudioLyrics = extractLyricsFromTag(tag)
            val sidecarLyrics = if (jaudioLyrics.isNullOrBlank()) readSidecarLyrics(file) else null
            val lyrics = jaudioLyrics ?: sidecarLyrics
            val musicType = resolveMusicType(title, file.name, lyrics)

            Log.d(TAG, "🟢 [File 深度解析] $title | 歌词: ${if (lyrics != null) "✔" else "✘"}")

            MusicScanTaskEntity(
                filePath = file.absolutePath, title = title, album = album, artist = artist,
                musicType = musicType, durationMs = durationMs, updatedAtMillis = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            fallbackToRetriever(file)
        }
    }

    private fun fallbackToRetriever(file: File): MusicScanTaskEntity {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
            val lyrics = readSidecarLyrics(file)
            val musicType = resolveMusicType(title, file.name, lyrics)

            MusicScanTaskEntity(
                filePath = file.absolutePath, title = title, album = album, artist = artist,
                musicType = musicType, durationMs = durationMs, updatedAtMillis = System.currentTimeMillis()
            )
        } finally {
            retriever.release()
        }
    }

    // ========== SAF 模式解析 ==========

    private fun toTaskEntity(context: Context, file: DocumentFile): MusicScanTaskEntity {
        var tempFile: File? = null
        val fallbackName = file.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: file.uri.lastPathSegment.orEmpty()

        return try {
            val ext = file.name?.substringAfterLast('.') ?: "tmp"
            // 【核心修复】：使用 UUID 保证高并发写入时的文件名绝对唯一，防止数据交叉覆盖
            tempFile = File(context.cacheDir, "saf_bridge_${UUID.randomUUID()}.$ext")

            val isCopiedSuccessfully = copyUriToTempFileSafely(context, file.uri, tempFile)

            if (!isCopiedSuccessfully) {
                throw IllegalStateException("跨进程流拷贝彻底失败")
            }

            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() } ?: fallbackName
            val album = tag?.getFirst(FieldKey.ALBUM).orEmpty()
            val artist = tag?.getFirst(FieldKey.ARTIST).orEmpty()
            val durationMs = (header?.trackLength?.toLong() ?: -1L) * 1000L

            val jaudioLyrics = extractLyricsFromTag(tag)
            val sidecarLyrics = if (jaudioLyrics.isNullOrBlank()) readSidecarLyrics(context, file) else null
            val lyrics = jaudioLyrics ?: sidecarLyrics
            val musicType = resolveMusicType(title, file.name.orEmpty(), lyrics)

            Log.d(TAG, "🟢 [SAF 深度解析] $title | 歌词: ${if (lyrics != null) "✔" else "✘"}")

            MusicScanTaskEntity(
                filePath = file.uri.toString(), title = title, album = album, artist = artist,
                musicType = musicType, durationMs = durationMs, updatedAtMillis = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            fallbackToRetrieverSafely(context, file.uri, fallbackName)
        } finally {
            tempFile?.delete()
        }
    }

    private fun fallbackToRetrieverSafely(context: Context, uri: Uri, fallbackTitle: String): MusicScanTaskEntity {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L

            Log.d(TAG, "🟠 [SAF 降级保护] 提取成功，但跳过歌词解析: $title")

            MusicScanTaskEntity(
                filePath = uri.toString(), title = title, album = album, artist = artist,
                musicType = 0,
                durationMs = durationMs, updatedAtMillis = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "🔴 极端异常，无法识别的文件: $uri")
            MusicScanTaskEntity(filePath = uri.toString(), title = fallbackTitle, updatedAtMillis = System.currentTimeMillis())
        } finally {
            retriever.release()
        }
    }

    // --- 核心调度层 ---

    fun scanDirectory(directoryPath: String): List<MusicScanTaskEntity> {
        Log.e(TAG, "🚀 File 引擎：开始遍历")
        val root = File(directoryPath)
        if (!root.exists() || !root.isDirectory) return emptyList()

        val validFiles = root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .toList()

        val fileSemaphore = Semaphore(8)
        return runBlocking(Dispatchers.IO) {
            validFiles.map { file ->
                async {
                    fileSemaphore.withPermit {
                        runCatching { toTaskEntity(file) }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    fun scanTreeUri(context: Context, treeUri: Uri): List<MusicScanTaskEntity> {
        Log.e(TAG, "🚀 SAF 引擎：开始深度遍历树...")
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!root.exists() || !root.isDirectory) return emptyList()

        val validFiles = walkTree(root)
            .filter { it.isFile && isSupportedAudioName(it.name) }
            .toList()

        Log.e(TAG, "🚀 SAF 引擎：发现 ${validFiles.size} 首歌曲，开始克制并发解析")

        val safSemaphore = Semaphore(4)

        return runBlocking(Dispatchers.IO) {
            validFiles.mapIndexed { index, file ->
                async {
                    safSemaphore.withPermit {
                        if (index % 50 == 0) Log.e(TAG, "⏳ 解析进度: $index / ${validFiles.size}")
                        runCatching { toTaskEntity(context, file) }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    fun scanAndSave(directoryPath: String): Int {
        val tasks = scanDirectory(directoryPath)
        return DatabaseManager.upsertMusicTasks(tasks)
    }

    fun scanAndSave(context: Context, treeUri: Uri): Int {
        val tasks = scanTreeUri(context, treeUri)
        return DatabaseManager.upsertMusicTasks(tasks)
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
        val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
        return ext in supportedExtensions
    }
}