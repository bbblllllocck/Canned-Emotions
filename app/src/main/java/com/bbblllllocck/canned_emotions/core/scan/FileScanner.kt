package com.bbblllllocck.canned_emotions.core.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.MusicScanTaskEntity
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.AbstractTagFrame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

object FileScanner {
    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus"
    )

    private const val TAG = "FileScanner"
    private val preferredLyricsKeys = listOf("Lyrics", "LYRICS", "lyrics", "Lyric", "LYRC", "LYR")

    init {
        // 物理静音 Jaudiotagger 底层报错
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private fun resolveMusicType(title: String, fileName: String, lyrics: String?): Int {
        if (title.contains("（伴奏）") || fileName.contains("（伴奏）")) {
            Log.d(TAG, "🎵 [类型 0] 纯音乐 (标题/文件名带伴奏): $title")
            return 0
        }
        if (title.lowercase().contains("(instrumental)") || fileName.lowercase().contains("(instrumental)")) {
            Log.d(TAG, "🎵 [类型 0] 纯音乐 (标题带instrumental): $title")
            return 0
        }
        if (lyrics?.contains("纯音乐，请欣赏") == true) {
            Log.d(TAG, "🎵 [类型 0] 纯音乐 (歌词硬匹配): $title")
            return 0
        }

        // 统计非空行数
        val lineCount = lyrics.orEmpty().lineSequence().count { it.isNotBlank() }

        // 💥 强力日志：把行数极其清晰地打在公屏上！
        Log.d(TAG, "📊 歌词行数扫描: [$lineCount 行] | 歌曲: $title")

        return if (lineCount < 15) { // 阈值已上调至 15
            Log.d(TAG, "🎵 [类型 0] 纯音乐 (行数 $lineCount < 15): $title")
            0
        } else {
            // Log.d(TAG, "🎤 [类型 1] 包含人声单曲 (行数 $lineCount >= 15): $title")
            1
        }
    }

    /**
     * 终极解包机器
     */
    private fun extractLyricsByJaudiotagger(file: File, title: String): String? {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null

            // 1. 标准防线
            val fromField = runCatching { tag.getFirst(FieldKey.LYRICS) }.getOrNull()
            if (!fromField.isNullOrBlank()) return fromField

            // 2. FLAC/OGG 遍历防线
            val iterator = tag.fields
            while (iterator.hasNext()) {
                val field = iterator.next()
                if (preferredLyricsKeys.any { it.equals(field.id, ignoreCase = true) }) {
                    val text = field.toString()
                    if (text.isNotBlank()) return text
                }
            }

            // 3. MP3 TXXX 终极流氓防线
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
     * 常规 File 路径扫描 (绝对路径)
     */
    private fun toTaskEntity(file: File): MusicScanTaskEntity {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()

            val jaudioLyrics = extractLyricsByJaudiotagger(file, title)
            val sidecarLyrics = if (jaudioLyrics.isNullOrBlank()) readSidecarLyrics(file) else null
            val lyrics = jaudioLyrics ?: sidecarLyrics

            if (!lyrics.isNullOrBlank()) {
                Log.d(TAG, "🟢 成功抓取(File)! 长度=${lyrics.length} 歌曲=$title")
            } else {
                Log.e(TAG, "🔴 失败(File)! 无歌词: 歌曲=$title")
            }

            MusicScanTaskEntity(
                filePath = file.absolutePath,
                title = title,
                album = album,
                artist = artist,
                musicType = resolveMusicType(title, file.name, lyrics),
                updatedAtMillis = System.currentTimeMillis()
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * SAF 框架 Uri 扫描 (含临时文件桥接以支持 Jaudiotagger)
     */
    private fun toTaskEntity(context: Context, file: DocumentFile): MusicScanTaskEntity {
        val retriever = MediaMetadataRetriever()
        var tempFile: File? = null
        return try {
            retriever.setDataSource(context, file.uri)
            val fallbackName = file.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: file.uri.lastPathSegment.orEmpty()
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: fallbackName
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()

            // 【核心手术】SAF 破壁：将 Uri 复制到缓存目录变成物理 File，喂给 Jaudiotagger
            var jaudioLyrics: String? = null
            try {
                val ext = file.name?.substringAfterLast('.') ?: "tmp"
                tempFile = File(context.cacheDir, "saf_bridge_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                jaudioLyrics = extractLyricsByJaudiotagger(tempFile, title)
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ [SAF桥接失败]: ${e.message}")
            }

            val sidecarLyrics = if (jaudioLyrics.isNullOrBlank()) readSidecarLyrics(context, file) else null
            val lyrics = jaudioLyrics ?: sidecarLyrics

            if (!lyrics.isNullOrBlank()) {
                Log.d(TAG, "🟢 成功抓取(SAF)! 长度=${lyrics.length} 歌曲=$title")
            } else {
                Log.e(TAG, "🔴 失败(SAF)! 无歌词: 歌曲=$title")
            }

            MusicScanTaskEntity(
                filePath = file.uri.toString(),
                title = title,
                album = album,
                artist = artist,
                musicType = resolveMusicType(title, file.name.orEmpty(), lyrics),
                updatedAtMillis = System.currentTimeMillis()
            )
        } finally {
            retriever.release()
            // 物理超度：用完立刻销毁，绝不占用手机存储！
            tempFile?.delete()
        }
    }

    fun scanDirectory(directoryPath: String): List<MusicScanTaskEntity> {
        Log.e(TAG, "🚀 开始执行 File 扫描引擎，目标路径: $directoryPath")
        val root = File(directoryPath)
        if (!root.exists() || !root.isDirectory) return emptyList()

        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .mapNotNull { file -> runCatching { toTaskEntity(file) }.getOrNull() }
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
        Log.e(TAG, "🚀 开始执行 SAF 扫描引擎，目标 Uri: $treeUri")
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!root.exists() || !root.isDirectory) return emptyList()

        return walkTree(root)
            .filter { it.isFile && isSupportedAudioName(it.name) }
            .mapNotNull { file -> runCatching { toTaskEntity(context, file) }.getOrNull() }
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
        val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
        return ext in supportedExtensions
    }
}