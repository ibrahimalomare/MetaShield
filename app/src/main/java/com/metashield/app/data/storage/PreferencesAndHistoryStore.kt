package com.metashield.app.data.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.metashield.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "metashield_prefs")

class PreferencesAndHistoryStore(
    private val context: Context
) {
    private object Keys {
        val REMOVE_C2PA = booleanPreferencesKey("default_remove_c2pa")
        val REMOVE_EXIF = booleanPreferencesKey("default_remove_exif")
        val REMOVE_XMP = booleanPreferencesKey("default_remove_xmp")
        val REMOVE_IPTC = booleanPreferencesKey("default_remove_iptc")
        val REMOVE_OTHER = booleanPreferencesKey("default_remove_other")
        val PRESERVE_ICC = booleanPreferencesKey("default_preserve_icc")
        val ZERO_FILL_SLACK = booleanPreferencesKey("default_zero_fill_slack")
        val RECENT_OPS_JSON = stringPreferencesKey("recent_operations_json")
    }

    val defaultCleaningOptionsFlow: Flow<CleaningOptions> = context.dataStore.data.map { prefs ->
        CleaningOptions(
            removeC2pa = prefs[Keys.REMOVE_C2PA] ?: true,
            removeExif = prefs[Keys.REMOVE_EXIF] ?: true,
            removeXmp = prefs[Keys.REMOVE_XMP] ?: true,
            removeIptc = prefs[Keys.REMOVE_IPTC] ?: true,
            removeOtherMetadata = prefs[Keys.REMOVE_OTHER] ?: true,
            preserveIccColorProfile = prefs[Keys.PRESERVE_ICC] ?: true,
            zeroFillSlackSpace = prefs[Keys.ZERO_FILL_SLACK] ?: true,
            userAuthorizedProvenanceRemoval = false
        )
    }

    suspend fun updateDefaultCleaningOptions(options: CleaningOptions) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMOVE_C2PA] = options.removeC2pa
            prefs[Keys.REMOVE_EXIF] = options.removeExif
            prefs[Keys.REMOVE_XMP] = options.removeXmp
            prefs[Keys.REMOVE_IPTC] = options.removeIptc
            prefs[Keys.REMOVE_OTHER] = options.removeOtherMetadata
            prefs[Keys.PRESERVE_ICC] = options.preserveIccColorProfile
            prefs[Keys.ZERO_FILL_SLACK] = options.zeroFillSlackSpace
        }
    }

    val recentOperationsFlow: Flow<List<RecentOperation>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.RECENT_OPS_JSON] ?: "[]"
        parseOperationsJson(raw)
    }

    suspend fun appendOperation(operation: RecentOperation) {
        context.dataStore.edit { prefs ->
            val current = parseOperationsJson(prefs[Keys.RECENT_OPS_JSON] ?: "[]")
            val updated = (listOf(operation) + current).take(25)
            prefs[Keys.RECENT_OPS_JSON] = serializeOperationsJson(updated)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { prefs ->
            prefs[Keys.RECENT_OPS_JSON] = "[]"
        }
    }

    private fun parseOperationsJson(jsonStr: String): List<RecentOperation> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<RecentOperation>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RecentOperation(
                        id = obj.optString("id"),
                        fileName = obj.optString("fileName"),
                        format = runCatching { ImageFormat.valueOf(obj.optString("format", "JPEG")) }.getOrDefault(ImageFormat.JPEG),
                        originalSizeBytes = obj.optLong("originalSizeBytes", 0L),
                        outputSizeBytes = if (obj.has("outputSizeBytes")) obj.optLong("outputSizeBytes") else null,
                        bytesRemoved = obj.optLong("bytesRemoved", 0L),
                        c2paStatusBefore = runCatching {
                            C2paValidationStatus.valueOf(obj.optString("c2paStatusBefore", "NO_CREDENTIALS_FOUND"))
                        }.getOrDefault(C2paValidationStatus.NO_CREDENTIALS_FOUND),
                        c2paRemoved = obj.optBoolean("c2paRemoved", false),
                        exifRemoved = obj.optBoolean("exifRemoved", false),
                        xmpRemoved = obj.optBoolean("xmpRemoved", false),
                        iptcRemoved = obj.optBoolean("iptcRemoved", false),
                        verdict = runCatching {
                            VerificationVerdict.valueOf(obj.optString("verdict", "VERIFIED_CLEAN"))
                        }.getOrDefault(VerificationVerdict.VERIFIED_CLEAN),
                        timestampEpochMs = obj.optLong("timestampEpochMs", 0L),
                        outputFilePath = obj.optString("outputFilePath").ifBlank { null }
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeOperationsJson(ops: List<RecentOperation>): String {
        val arr = JSONArray()
        for (op in ops) {
            val obj = JSONObject().apply {
                put("id", op.id)
                put("fileName", op.fileName)
                put("format", op.format.name)
                put("originalSizeBytes", op.originalSizeBytes)
                op.outputSizeBytes?.let { put("outputSizeBytes", it) }
                put("bytesRemoved", op.bytesRemoved)
                put("c2paStatusBefore", op.c2paStatusBefore.name)
                put("c2paRemoved", op.c2paRemoved)
                put("exifRemoved", op.exifRemoved)
                put("xmpRemoved", op.xmpRemoved)
                put("iptcRemoved", op.iptcRemoved)
                put("verdict", op.verdict.name)
                put("timestampEpochMs", op.timestampEpochMs)
                op.outputFilePath?.let { put("outputFilePath", it) }
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
