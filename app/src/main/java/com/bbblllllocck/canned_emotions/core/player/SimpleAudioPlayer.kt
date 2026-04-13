package com.bbblllllocck.canned_emotions.core.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

class SimpleAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSource: String? = null

    fun playOrToggle(
        source: String,
        onPreparing: () -> Unit,
        onPlaying: () -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        runCatching {
            val activePlayer = mediaPlayer
            if (activePlayer != null && currentSource == source) {
                if (activePlayer.isPlaying) {
                    activePlayer.pause()
                } else {
                    activePlayer.start()
                    onPlaying()
                }
                return
            }

            releaseInternal()
            onPreparing()

            val uri = source.toPlayableUri()
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener {
                    it.start()
                    onPlaying()
                }
                setOnCompletionListener {
                    onCompleted()
                }
                setOnErrorListener { _, what, extra ->
                    onError("播放器错误 what=$what extra=$extra")
                    true
                }
                setDataSource(context, uri)
                prepareAsync()
            }
            currentSource = source
        }.onFailure {
            onError(it.message ?: "未知播放错误")
            releaseInternal()
        }
    }

    fun stop() {
        runCatching { mediaPlayer?.stop() }
        releaseInternal()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun currentSource(): String? = currentSource

    fun currentPositionMs(): Int {
        return runCatching { mediaPlayer?.currentPosition ?: 0 }
            .getOrDefault(0)
            .coerceAtLeast(0)
    }

    fun durationMs(): Int {
        return runCatching { mediaPlayer?.duration ?: 0 }
            .getOrDefault(0)
            .coerceAtLeast(0)
    }

    fun seekToMs(positionMs: Int) {
        val target = positionMs.coerceAtLeast(0)
        runCatching {
            val player = mediaPlayer ?: return
            val duration = player.duration.coerceAtLeast(0)
            val clampedTarget = if (duration > 0) target.coerceAtMost(duration) else target
            player.seekTo(clampedTarget)
        }
    }

    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentSource = null
    }

    private fun String.toPlayableUri(): Uri {
        return if (startsWith("content://")) Uri.parse(this) else Uri.fromFile(File(this))
    }
}

