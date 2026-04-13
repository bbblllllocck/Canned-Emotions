package com.bbblllllocck.canned_emotions.core.database.geminiRequestCall

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.bbblllllocck.canned_emotions.core.api.AppContextProvider
import com.google.genai.types.Blob
import com.google.genai.types.Content
import com.google.genai.types.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object EmbeddingCall {

    private const val MODEL_NAME = "gemini-embedding-2-preview"


    val context: Context get() = AppContextProvider.get()


    @OptIn(UnstableApi::class)
    suspend fun embed(
        audioPath: String = "",
        textInput: String = ""
        /*picture: I don't know what to input*/
    ): FloatArray = withContext(Dispatchers.IO) {

        val partsList = mutableListOf<Part>()

        //生成 the content that gives to the model，真是有够烦
        if (audioPath.isNotEmpty()) {
            val tmpDir = File(context.cacheDir, "embedding_audio_tmp").apply { mkdirs() }

            // Use a true unique file to avoid collisions when multiple tasks export concurrently.
            val outputFile = File.createTempFile("embedding_", ".aac", tmpDir)

            val outputPathString = outputFile.absolutePath

            val uri = audioPath.toUri()



            //val audioPart = suspendCancellableCoroutine<Part> { continuation ->

            /*
             * NOTE (debug record, keep for now):
             * 1) We intentionally run Transformer on Main.immediate because Media3 Transformer requires
             *    create/listener/startScreen to stay on the same looper thread.
             * 哦对这块要修，至于暂停的问题，我打算给UI加个延迟。
             *
             * 2) We have seen intermittent ExportException on some devices/codecs (for example FLAC decoder)
             *    when app goes background/foreground or when queue accidentally overlaps the same source.
             * 应该好了
             *
             *
             *
             * 3) Current decision: do not add retry/fallback logic yet; keep behavior simple first, observe.
             * 4) When we come back to fix this, preferred order is:
             *    - prevent same task double-startScreen in queue layer,
             *    - add one lightweight retry around export,
             *    - optionally fallback to text-only embedding when audio export keeps failing.
             * 还有api那块异常处理要做。
             */
            val audioPart = withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<Part> { continuation ->
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)       // 从头开始 (可选)
                        .setEndPositionMs(80_000L)    // 关键：强制在 80 秒处剪断
                        .build()
                )
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder()
                        .setBitrate(192_000)
                        .build()
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC) // 输出为 AAC 格式
                .setEncoderFactory(encoderFactory)     // 注入我们刚才写的 192kbps 限制
                .build()

            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {

                    val audioBytes = outputFile.readBytes() // 直接读取为内存字节数组

                    val audioPart = Part.builder()
                        .inlineData(
                            Blob.builder()
                                .data(audioBytes)
                                .mimeType("audio/aac")
                                .build()
                        )
                        .build()
                    outputFile.delete()
                    continuation.resume(audioPart)
                }
                //这不是我自愿的，好吧？如果真触发了这个该死的玩意我得狠狠加log
                override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                    continuation.resumeWithException(exception)
                }
            }
            )

            transformer.start(editedMediaItem, outputPathString)
            }
            }
            partsList.add(audioPart)
        }



        if (textInput.isNotEmpty()){
            val textPart = Part.builder()
                .text(textInput)
                .build()
            partsList.add(textPart)
        }

        val integratedContent = Content.builder()
            .parts(partsList)
            .build()



        ////////copilot generated

        val response = try {
            ClientManager.executeWithRetry { client ->
                client.models.embedContent(
                    MODEL_NAME,
                    integratedContent,
                    null
                )
            }
        } catch (e: Exception) {
            Log.e("123456", "embedContent failed: ${e.message}", e)
            throw e
        }
        Log.d("123456", "embed response=$response")

        val vectorList = response.embeddings()
            .orElse(emptyList())
            .firstOrNull()
            ?.values()
            ?.orElse(emptyList())
            ?: emptyList()

        return@withContext vectorList.toFloatArray()



    }
}
