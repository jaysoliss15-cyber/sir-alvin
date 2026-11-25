package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ElevenLabsKeyEntry
import com.example.model.PromptTemplate
import com.example.model.PronunciationEntry
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.toonForgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "toonforge_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val PROMPT_TEMPLATES_JSON = stringPreferencesKey("prompt_templates_json")
        val ACTIVE_PROMPT_ID = stringPreferencesKey("active_prompt_id")
        val ELEVENLABS_KEYS_JSON = stringPreferencesKey("elevenlabs_keys_json")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        val AI_HORDE_KEY = stringPreferencesKey("ai_horde_key")
        val ALLOW_OFFLINE_TTS_FALLBACK = booleanPreferencesKey("allow_offline_tts_fallback")
        val CUSTOM_SPRITE_PATHS_JSON = stringPreferencesKey("custom_sprite_paths_json")
        val SPRITE_STICKER_OUTLINE = booleanPreferencesKey("sprite_sticker_outline")
        val CUSTOM_BGM_PATH = stringPreferencesKey("custom_bgm_path")
        val CUSTOM_BGM_NAME = stringPreferencesKey("custom_bgm_name")
        val PRONUNCIATION_DICT_JSON = stringPreferencesKey("pronunciation_dict_json")
    }

    val settingsFlow: Flow<StudioSettings> = context.toonForgeDataStore.data.map { prefs ->
        val templates = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON])
        val activeId = prefs[Keys.ACTIVE_PROMPT_ID]
            ?.takeIf { id -> templates.any { it.id == id } }
            ?: templates.first().id

        StudioSettings(
            promptTemplates = templates,
            activePromptId = activeId,
            elevenLabsKeys = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON]),
            selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID]?.ifBlank { null }
                ?: BuiltInPrompts.VOICE_PRESETS.first().id,
            groqApiKey = prefs[Keys.GROQ_API_KEY] ?: "",
            openRouterApiKey = prefs[Keys.OPENROUTER_API_KEY] ?: "",
            customBaseUrl = prefs[Keys.CUSTOM_BASE_URL] ?: "",
            customModel = prefs[Keys.CUSTOM_MODEL] ?: "",
            customApiKey = prefs[Keys.CUSTOM_API_KEY] ?: "",
            aiHordeKey = prefs[Keys.AI_HORDE_KEY]?.ifBlank { null } ?: "0000000000",
            allowOfflineTtsFallback = prefs[Keys.ALLOW_OFFLINE_TTS_FALLBACK] ?: true,
            customSpritePaths = parseSpritePaths(prefs[Keys.CUSTOM_SPRITE_PATHS_JSON]),
            spriteStickerOutline = prefs[Keys.SPRITE_STICKER_OUTLINE] ?: true,
            customBgmPath = prefs[Keys.CUSTOM_BGM_PATH] ?: "",
            customBgmName = prefs[Keys.CUSTOM_BGM_NAME] ?: "",
            pronunciationDictionary = parsePronunciationDict(prefs[Keys.PRONUNCIATION_DICT_JSON])
        )
    }

    suspend fun getSnapshot(): StudioSettings = settingsFlow.first()

    suspend fun setActivePrompt(templateId: String) {
        context.toonForgeDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROMPT_ID] = templateId
        }
    }

    suspend fun importTxtTemplate(name: String, content: String): PromptTemplate {
        val cleanName = name.removeSuffix(".txt").removeSuffix(".TXT").trim().ifBlank { "Imported Prompt" }
        val newTemplate = PromptTemplate(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            content = content.trim(),
            isBuiltIn = false
        )
        context.toonForgeDataStore.edit { prefs ->
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON]).toMutableList()
            current.add(newTemplate)
            prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(current)
            prefs[Keys.ACTIVE_PROMPT_ID] = newTemplate.id
        }
        return newTemplate
    }

    suspend fun updateTemplateContent(templateId: String, newName: String, newContent: String) {
        context.toonForgeDataStore.edit { prefs ->
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON]).map { item ->
                if (item.id == templateId) {
                    item.copy(
                        name = newName.trim().ifBlank { item.name },
                        content = newContent
                    )
                } else {
                    item
                }
            }
            prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(current)
        }
    }

    suspend fun resetTemplate(templateId: String): String {
        var restoredContent = ""
        context.toonForgeDataStore.edit { prefs ->
            val defaultTemplate = BuiltInPrompts.getDefaultById(templateId)
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON]).map { item ->
                if (item.id == templateId && defaultTemplate != null) {
                    restoredContent = defaultTemplate.content
                    defaultTemplate
                } else {
                    if (item.id == templateId) restoredContent = item.content
                    item
                }
            }
            prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(current)
        }
        return restoredContent
    }

    suspend fun deleteTemplate(templateId: String) {
        context.toonForgeDataStore.edit { prefs ->
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON])
            val target = current.firstOrNull { it.id == templateId }
            if (target != null && !target.isBuiltIn && current.size > 1) {
                val filtered = current.filterNot { it.id == templateId }
                prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(filtered)
                if (prefs[Keys.ACTIVE_PROMPT_ID] == templateId) {
                    prefs[Keys.ACTIVE_PROMPT_ID] = filtered.first().id
                }
            }
        }
    }

    suspend fun setSelectedVoiceId(voiceId: String) {
        context.toonForgeDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE_ID] = voiceId.trim()
        }
    }

    suspend fun addElevenLabsKey(rawKey: String) {
        val clean = rawKey.trim()
        if (clean.isEmpty()) return
        context.toonForgeDataStore.edit { prefs ->
            val list = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON]).toMutableList()
            if (list.none { it.key == clean }) {
                list.add(ElevenLabsKeyEntry(key = clean))
                prefs[Keys.ELEVENLABS_KEYS_JSON] = serializeElevenLabsKeys(list)
            }
        }
    }

    suspend fun removeElevenLabsKey(key: String) {
        context.toonForgeDataStore.edit { prefs ->
            val list = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON])
                .filterNot { it.key == key }
            prefs[Keys.ELEVENLABS_KEYS_JSON] = serializeElevenLabsKeys(list)
        }
    }

    suspend fun updateElevenLabsKeyEntry(updated: ElevenLabsKeyEntry) {
        context.toonForgeDataStore.edit { prefs ->
            val list = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON]).map {
                if (it.key == updated.key) updated else it
            }
            prefs[Keys.ELEVENLABS_KEYS_JSON] = serializeElevenLabsKeys(list)
        }
    }

    suspend fun saveLlmSettings(
        groqApiKey: String,
        openRouterApiKey: String,
        customBaseUrl: String,
        customModel: String,
        customApiKey: String,
        aiHordeKey: String,
        allowOfflineTtsFallback: Boolean
    ) {
        context.toonForgeDataStore.edit { prefs ->
            prefs[Keys.GROQ_API_KEY] = groqApiKey.trim()
            prefs[Keys.OPENROUTER_API_KEY] = openRouterApiKey.trim()
            prefs[Keys.CUSTOM_BASE_URL] = customBaseUrl.trim()
            prefs[Keys.CUSTOM_MODEL] = customModel.trim()
            prefs[Keys.CUSTOM_API_KEY] = customApiKey.trim()
            prefs[Keys.AI_HORDE_KEY] = aiHordeKey.trim().ifBlank { "0000000000" }
            prefs[Keys.ALLOW_OFFLINE_TTS_FALLBACK] = allowOfflineTtsFallback
        }
    }

    suspend fun setCustomSpritePath(roleName: String, path: String?) {
        context.toonForgeDataStore.edit { prefs ->
            val map = parseSpritePaths(prefs[Keys.CUSTOM_SPRITE_PATHS_JSON]).toMutableMap()
            if (path.isNullOrBlank()) {
                map.remove(roleName)
            } else {
                map[roleName] = path
            }
            prefs[Keys.CUSTOM_SPRITE_PATHS_JSON] = serializeSpritePaths(map)
        }
    }

    suspend fun setSpriteStickerOutline(enabled: Boolean) {
        context.toonForgeDataStore.edit { prefs ->
            prefs[Keys.SPRITE_STICKER_OUTLINE] = enabled
        }
    }

    suspend fun setCustomBgm(path: String, displayName: String) {
        context.toonForgeDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_BGM_PATH] = path
            prefs[Keys.CUSTOM_BGM_NAME] = displayName
        }
    }

    suspend fun upsertPronunciationEntry(original: String, spoken: String) {
        val cleanOrig = original.trim()
        val cleanSpoken = spoken.trim()
        if (cleanOrig.isEmpty() || cleanSpoken.isEmpty()) return
        context.toonForgeDataStore.edit { prefs ->
            val list = parsePronunciationDict(prefs[Keys.PRONUNCIATION_DICT_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.original.equals(cleanOrig, ignoreCase = true) }
            val entry = PronunciationEntry(cleanOrig, cleanSpoken)
            if (idx >= 0) {
                list[idx] = entry
            } else {
                list.add(0, entry)
            }
            prefs[Keys.PRONUNCIATION_DICT_JSON] = serializePronunciationDict(list)
        }
    }

    suspend fun deletePronunciationEntry(original: String) {
        context.toonForgeDataStore.edit { prefs ->
            val list = parsePronunciationDict(prefs[Keys.PRONUNCIATION_DICT_JSON])
                .filterNot { it.original.equals(original, ignoreCase = true) }
            prefs[Keys.PRONUNCIATION_DICT_JSON] = serializePronunciationDict(list)
        }
    }

    suspend fun resetPronunciationDictionary() {
        context.toonForgeDataStore.edit { prefs ->
            prefs[Keys.PRONUNCIATION_DICT_JSON] =
                serializePronunciationDict(BuiltInPrompts.DEFAULT_PRONUNCIATION_DICTIONARY)
        }
    }

    private fun parseTemplates(json: String?): List<PromptTemplate> {
        if (json.isNullOrBlank()) return BuiltInPrompts.DEFAULT_TEMPLATES
        return try {
            val arr = JSONArray(json)
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        PromptTemplate(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            content = obj.getString("content"),
                            isBuiltIn = obj.optBoolean("isBuiltIn", false)
                        )
                    )
                }
            }
            if (parsed.isEmpty()) BuiltInPrompts.DEFAULT_TEMPLATES else parsed
        } catch (_: Exception) {
            BuiltInPrompts.DEFAULT_TEMPLATES
        }
    }

    private fun serializeTemplates(list: List<PromptTemplate>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("content", t.content)
                    put("isBuiltIn", t.isBuiltIn)
                }
            )
        }
        return arr.toString()
    }

    private fun parseElevenLabsKeys(json: String?): List<ElevenLabsKeyEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ElevenLabsKeyEntry(
                            key = obj.getString("key"),
                            remainingChars = if (obj.has("remainingChars") && !obj.isNull("remainingChars")) {
                                obj.getInt("remainingChars")
                            } else null,
                            limitChars = if (obj.has("limitChars") && !obj.isNull("limitChars")) {
                                obj.getInt("limitChars")
                            } else null,
                            tier = obj.optString("tier", "").ifBlank { null },
                            lastError = obj.optString("lastError", "").ifBlank { null },
                            lastCheckedAtMs = obj.optLong("lastCheckedAtMs", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeElevenLabsKeys(list: List<ElevenLabsKeyEntry>): String {
        val arr = JSONArray()
        list.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("key", entry.key)
                    if (entry.remainingChars != null) put("remainingChars", entry.remainingChars)
                    if (entry.limitChars != null) put("limitChars", entry.limitChars)
                    if (entry.tier != null) put("tier", entry.tier)
                    if (entry.lastError != null) put("lastError", entry.lastError)
                    put("lastCheckedAtMs", entry.lastCheckedAtMs)
                }
            )
        }
        return arr.toString()
    }

    private fun parseSpritePaths(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    val path = obj.optString(key, "")
                    if (path.isNotBlank()) put(key, path)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeSpritePaths(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun parsePronunciationDict(json: String?): List<PronunciationEntry> {
        if (json == null) return BuiltInPrompts.DEFAULT_PRONUNCIATION_DICTIONARY
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val orig = obj.optString("original", "").trim()
                    val spoken = obj.optString("spoken", "").trim()
                    if (orig.isNotEmpty() && spoken.isNotEmpty()) {
                        add(PronunciationEntry(orig, spoken))
                    }
                }
            }
        } catch (_: Exception) {
            BuiltInPrompts.DEFAULT_PRONUNCIATION_DICTIONARY
        }
    }

    private fun serializePronunciationDict(list: List<PronunciationEntry>): String {
        val arr = JSONArray()
        list.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("original", entry.original)
                    put("spoken", entry.spoken)
                }
            )
        }
        return arr.toString()
    }
}
