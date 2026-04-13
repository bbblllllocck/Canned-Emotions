package com.bbblllllocck.canned_emotions.core.scan

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object ScanPathResolver {
    /**
     * Resolve SAF tree Uri to a local absolute path for File-based scanner.
     * Returns null when the Uri cannot be represented as a local file path.
     * 纯ai但是我觉得应该也不用看了。
     */
    fun resolveTreeUriToAbsolutePath(treeUri: Uri): String? {
        if (treeUri.scheme == "file") {
            return treeUri.path
        }

        if (!DocumentsContract.isTreeUri(treeUri)) return null

        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parts = treeDocId.split(":", limit = 2)
        if (parts.isEmpty()) return null

        val volume = parts[0]
        val relativePath = parts.getOrNull(1).orEmpty()

        return when {
            volume.equals("primary", ignoreCase = true) -> {
                buildPath(Environment.getExternalStorageDirectory(), relativePath)
            }
            volume.equals("home", ignoreCase = true) -> {
                buildPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), relativePath)
            }
            else -> {
                // Non-primary volumes are not guaranteed to expose stable local paths.
                null
            }
        }
    }

    private fun buildPath(base: File, relativePath: String): String {
        if (relativePath.isBlank()) return base.absolutePath
        return File(base, relativePath).absolutePath
    }

    fun takePersistableReadPermission(context: Context, uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }
}


