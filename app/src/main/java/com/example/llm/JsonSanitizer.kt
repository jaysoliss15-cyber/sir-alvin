package com.example.llm

import com.example.model.ActorAction
import com.example.model.ActorRole
import com.example.model.CameraMotion
import com.example.model.HudWidget
import com.example.model.SceneScript
import com.example.model.StatusPill
import com.example.model.WorldFx
import org.json.JSONArray
import org.json.JSONObject

class MalformedSceneJsonException(message: String, cause: Throwable? = null) : Exception(message, cause)

object JsonSanitizer {

    private val THINK_BLOCK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
    private val UNCLOSED_THINK_REGEX = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val CODE_FENCE_START = Regex("```(?:json)?\\s*", RegexOption.IGNORE_CASE)
    private val CODE_FENCE_END = Regex("```")

    private val REQUIRED_KEYS = listOf(
        "scene_id",
        "tagalog_narration",
        "background_prompt",
        "camera_motion",
        "actor_role",
        "actor_action",
        "world_fx"
    )

    fun extractJsonArrayText(rawOutput: String): String {
        var cleaned = rawOutput
            .replace(THINK_BLOCK_REGEX, "")
            .replace(UNCLOSED_THINK_REGEX, "")
            .replace(CODE_FENCE_START, "")
            .replace(CODE_FENCE_END, "")
            .trim()

        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket == -1 || lastBracket == -1 || lastBracket <= firstBracket) {
            throw MalformedSceneJsonException("No JSON array brackets found in LLM response")
        }
        cleaned = cleaned.substring(firstBracket, lastBracket + 1)
        return cleaned
    }

    fun parseAndValidateScenes(rawOutput: String): List<SceneScript> {
        val jsonText = extractJsonArrayText(rawOutput)
        val array = try {
            JSONArray(jsonText)
        } catch (e: Exception) {
            throw MalformedSceneJsonException("Invalid JSONArray syntax: ${e.message}", e)
        }

        if (array.length() == 0) {
            throw MalformedSceneJsonException("Scene JSONArray is empty")
        }

        val scenes = mutableListOf<SceneScript>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
                ?: throw MalformedSceneJsonException("Element at index $i is not a JSONObject")
            scenes.add(parseSceneObject(obj, i))
        }
        return scenes
    }

    private fun parseSceneObject(obj: JSONObject, index: Int): SceneScript {
        for (key in REQUIRED_KEYS) {
            if (!obj.has(key) || obj.isNull(key)) {
                throw MalformedSceneJsonException("Scene[$index] missing required key '$key'")
            }
        }

        val sceneId = obj.optInt("scene_id", index + 1)
        val narration = obj.optString("tagalog_narration", "").trim()
        val bgPrompt = obj.optString("background_prompt", "").trim()
        val camRaw = obj.optString("camera_motion", "").trim().uppercase()
        val roleRaw = obj.optString("actor_role", "").trim().uppercase()
        val actionRaw = obj.optString("actor_action", "").trim().uppercase()
        val fxRaw = obj.optString("world_fx", "").trim().uppercase()

        if (narration.isEmpty()) {
            throw MalformedSceneJsonException("Scene[$index] has empty tagalog_narration")
        }
        if (bgPrompt.isEmpty()) {
            throw MalformedSceneJsonException("Scene[$index] has empty background_prompt")
        }

        // Backward-compatible optional HUD & kinetic motion-graphics fields
        val statusPill = StatusPill.fromRaw(obj.optString("status_pill", "NONE"))
        val calloutBanner = obj.optString("callout_banner", "").trim()
        val hudWidget = HudWidget.fromRaw(obj.optString("hud_widget", "NONE"))
        val hudLabel = obj.optString("hud_label", "").trim()
        val hudFrom = obj.optInt("hud_from_value", 0).coerceIn(0, 100)
        val hudTo = obj.optInt("hud_to_value", 100).coerceIn(0, 100)

        val checklistArray = obj.optJSONArray("checklist_items")
        val checklistItems = if (checklistArray != null) {
            buildList {
                for (k in 0 until minOf(4, checklistArray.length())) {
                    val item = checklistArray.optString(k, "").trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        } else {
            emptyList()
        }

        return SceneScript(
            sceneId = sceneId,
            tagalogNarration = narration,
            backgroundPrompt = bgPrompt,
            cameraMotion = CameraMotion.fromRaw(camRaw),
            actorRole = ActorRole.fromRaw(roleRaw),
            actorAction = ActorAction.fromRaw(actionRaw),
            worldFx = WorldFx.fromRaw(fxRaw),
            statusPill = statusPill,
            calloutBanner = calloutBanner,
            hudWidget = hudWidget,
            hudLabel = hudLabel,
            hudFromValue = hudFrom,
            hudToValue = hudTo,
            checklistItems = checklistItems
        )
    }
}
