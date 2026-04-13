package com.bbblllllocck.canned_emotions.core.scan

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scanDirectoryDataStore by preferencesDataStore(name = "scan_directory_store")

class ScanDirectoryStore(private val context: Context) {

    data class PersistedDirectory(
        val treeUri: String,
        val displayPath: String
    )

    private val directoryRecordsKey = stringSetPreferencesKey("scan_directory_records")

    val directoriesFlow: Flow<List<PersistedDirectory>> = context.scanDirectoryDataStore.data.map { prefs ->
        prefs[directoryRecordsKey]
            .orEmpty()
            .mapNotNull(::decodeRecord)
            .distinctBy { it.treeUri }
            .sortedBy { it.displayPath }
    }

    suspend fun addOrReplaceDirectory(treeUri: String, displayPath: String) {
        context.scanDirectoryDataStore.edit { prefs ->
            val existing = prefs[directoryRecordsKey].orEmpty()
            val next = existing
                .filterNot { record -> decodeRecord(record)?.treeUri == treeUri }
                .toMutableSet()
            next.add(encodeRecord(treeUri, displayPath))
            prefs[directoryRecordsKey] = next
        }
    }

    private fun encodeRecord(treeUri: String, displayPath: String): String {
        return "${Uri.encode(treeUri)}|${Uri.encode(displayPath)}"
    }

    private fun decodeRecord(raw: String): PersistedDirectory? {
        val parts = raw.split("|", limit = 2)
        if (parts.size != 2) return null

        return PersistedDirectory(
            treeUri = Uri.decode(parts[0]),
            displayPath = Uri.decode(parts[1])
        )
    }
}

