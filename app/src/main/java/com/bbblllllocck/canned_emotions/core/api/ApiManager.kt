package com.bbblllllocck.canned_emotions.core.api

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

private const val APIS_STORAGE_KEY = "apis_json"
private const val SECURE_DATASTORE_NAME = "secure_api_store"
private const val TINK_KEYSET_PREFS_NAME = "tink_api_keyset"
private const val TINK_KEYSET_NAME = "api_aead_keyset"
private const val TINK_MASTER_KEY_URI = "android-keystore://api_store_master_key"
private const val AAD_CONTEXT = "api_credentials_store_v1"

private val Context.secureApiDataStore: DataStore<Preferences> by preferencesDataStore(
	name = SECURE_DATASTORE_NAME
)

data class ApiCredential(
	val id: String,
	val name: String,
	val apiKey: String
)

object ApiManager {

	private val appContext: Context by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContextProvider.get() }
	private val dataStore: DataStore<Preferences> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
		appContext.secureApiDataStore
	}
	private val apiPayloadKey = stringPreferencesKey(APIS_STORAGE_KEY)
	private val aead: Aead by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { TinkAeadProvider(appContext).aead() }
	private val apisState = MutableStateFlow(readAllOrNull() ?: emptyList())
	//private val addedApiEvents = MutableSharedFlow<ApiCredential>(extraBufferCapacity = 1)

	fun getAll(): List<ApiCredential> {
		return apisState.value
	}

	fun observeAll(): Flow<List<ApiCredential>> = apisState.asStateFlow()

	val apisFlow: Flow<List<ApiCredential>>
		get() = observeAll()

	//fun observeAddedApi(): Flow<ApiCredential> = addedApiEvents.asSharedFlow()

	fun insert(credential: ApiCredential): Boolean {
		if (apisState.value.any { it.id == credential.id }) {
			return false
		}
		val next = apisState.value + credential
		saveAll(next)
		apisState.value = next
		return true
	}

	fun upsert(credential: ApiCredential) {
		val current = apisState.value.toMutableList()
		val index = current.indexOfFirst { it.id == credential.id }
		if (index >= 0) {
			current[index] = credential
		} else {
			current.add(credential)
//			addedApiEvents.tryEmit(credential)
		}
		val next = current.toList()
		saveAll(next)
		apisState.value = next
	}

	fun delete(id: String) {
		val next = apisState.value.filterNot { it.id == id }
		saveAll(next)
		apisState.value = next
	}

	private fun readAllOrNull(): List<ApiCredential>? {
		return runBlocking {
			val encrypted = dataStore.data.first()[apiPayloadKey] ?: return@runBlocking emptyList()
			val decrypted = runCatching { decrypt(encrypted) }.getOrNull() ?: return@runBlocking null
			runCatching { decode(decrypted) }.getOrNull()
		}
	}

	private fun saveAll(items: List<ApiCredential>) {
		val encrypted = runCatching { encrypt(encode(items)) }.getOrNull() ?: return
		runBlocking {
			dataStore.edit { prefs ->
				prefs[apiPayloadKey] = encrypted
			}
		}
	}

	private fun encrypt(plainText: String): String {
		val cipherBytes = aead.encrypt(
			plainText.toByteArray(Charsets.UTF_8),
			AAD_CONTEXT.toByteArray(Charsets.UTF_8)
		)
		return Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
	}

	private fun decrypt(encodedPayload: String): String {
		val cipherBytes = Base64.decode(encodedPayload, Base64.NO_WRAP)
		val plainBytes = aead.decrypt(cipherBytes, AAD_CONTEXT.toByteArray(Charsets.UTF_8))
		return plainBytes.toString(Charsets.UTF_8)
	}

	private fun encode(items: List<ApiCredential>): String {
		val array = JSONArray()
		items.forEach { item ->
			array.put(
				JSONObject().apply {
					put("id", item.id)
					put("name", item.name)
					put("apiKey", item.apiKey)
				}
			)
		}
		return array.toString()
	}

	private fun decode(raw: String): List<ApiCredential> {
		val array = JSONArray(raw)
		return List(array.length()) { index ->
			val obj = array.getJSONObject(index)
			ApiCredential(
				id = obj.optString("id"),
				name = obj.optString("name"),
				apiKey = obj.optString("apiKey")
			)
		}.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.apiKey.isNotBlank() }
	}

}

private class TinkAeadProvider(context: Context) {

	private val appContext = context.applicationContext

	fun aead(): Aead {
		AeadConfig.register()
		val keysetHandle = AndroidKeysetManager.Builder()
			.withSharedPref(appContext, TINK_KEYSET_NAME, TINK_KEYSET_PREFS_NAME)
			.withKeyTemplate(AeadKeyTemplates.AES256_GCM)
			.withMasterKeyUri(TINK_MASTER_KEY_URI)
			.build()
			.keysetHandle
		return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
	}
}
