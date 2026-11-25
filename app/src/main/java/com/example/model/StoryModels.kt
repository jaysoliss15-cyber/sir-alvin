package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class CameraMotion {
    WHIP_PAN_LEFT,
    WHIP_PAN_RIGHT,
    ZOOM_IN,
    ZOOM_OUT,
    SHAKE,
    STATIC;

    companion object {
        fun fromRaw(raw: String?): CameraMotion {
            val clean = raw?.trim()?.uppercase() ?: return STATIC
            return entries.firstOrNull { it.name == clean } ?: STATIC
        }
    }
}

enum class ActorRole {
    NARRATOR,
    RICH_MAN,
    POOR_MAN,
    CHICKEN_MENTOR,
    HERO;

    companion object {
        fun fromRaw(raw: String?): ActorRole {
            val clean = raw?.trim()?.uppercase() ?: return NARRATOR
            return entries.firstOrNull { it.name == clean } ?: NARRATOR
        }
    }
}

enum class ActorAction {
    WALK_IN,
    TALK_BOUNCE,
    THUMBS_UP,
    POINT_STICK,
    SURPRISED_JUMP,
    CRY_SHAKE;

    companion object {
        fun fromRaw(raw: String?): ActorAction {
            val clean = raw?.trim()?.uppercase() ?: return TALK_BOUNCE
            return entries.firstOrNull { it.name == clean } ?: TALK_BOUNCE
        }
    }
}

enum class WorldFx {
    NONE,
    COIN_RAIN,
    FIRE_SPARKS,
    GIANT_HAND_GRAB,
    FLOATING_HOUSES,
    CHALKBOARD_DRAW,
    EGG_CRACK;

    companion object {
        fun fromRaw(raw: String?): WorldFx {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

data class SceneScript(
    val sceneId: Int,
    val tagalogNarration: String,
    val backgroundPrompt: String,
    val cameraMotion: CameraMotion,
    val actorRole: ActorRole,
    val actorAction: ActorAction,
    val worldFx: WorldFx
)

data class WordTiming(
    val word: String,
    val startMs: Double,
    val endMs: Double
)

data class TimelineScene(
    val scene: SceneScript,
    val startMs: Double,
    val endMs: Double,
    val wordTimings: List<WordTiming>,
    val backdropBitmap: Bitmap? = null
) {
    val durationMs: Double
        get() = (endMs - startMs).coerceAtLeast(1.0)
}

data class PromptTemplate(
    val id: String,
    val name: String,
    val content: String,
    val isBuiltIn: Boolean = false
)

data class ElevenLabsKeyEntry(
    val key: String,
    val remainingChars: Int? = null,
    val limitChars: Int? = null,
    val tier: String? = null,
    val lastError: String? = null,
    val lastCheckedAtMs: Long = 0L
) {
    val maskedKey: String
        get() = if (key.length <= 10) "****" else "${key.take(6)}...${key.takeLast(4)}"
}

data class StudioSettings(
    val promptTemplates: List<PromptTemplate> = emptyList(),
    val activePromptId: String = "",
    val elevenLabsKeys: List<ElevenLabsKeyEntry> = emptyList(),
    val selectedVoiceId: String = "pNInz6obpgDQGcFmaJgB",
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val customBaseUrl: String = "",
    val customModel: String = "",
    val customApiKey: String = "",
    val aiHordeKey: String = "0000000000",
    val allowOfflineTtsFallback: Boolean = true
) {
    val activeTemplate: PromptTemplate?
        get() = promptTemplates.firstOrNull { it.id == activePromptId } ?: promptTemplates.firstOrNull()
}

enum class PipelineStep(val label: String, val index: Int) {
    IDLE("Ready", 0),
    SCRIPT("Script", 1),
    AUDIO("Audio", 2),
    ART("Art", 3),
    PUPPET_RENDER("Puppet Render", 4),
    MUXING("Muxing", 5),
    COMPLETED("Done", 6),
    ERROR("Error", -1)
}

data class GenerationProgress(
    val step: PipelineStep = PipelineStep.IDLE,
    val stepProgress: Float = 0f,
    val overallProgress: Float = 0f,
    val statusMessage: String = "Ready to forge your Tagalog story",
    val sceneCount: Int = 0,
    val currentSceneIndex: Int = 0,
    val providerInfo: String = "",
    val outputVideoUri: Uri? = null,
    val outputDisplayName: String? = null,
    val errorMessage: String? = null,
    val isRunning: Boolean = false,
    val generatedScenesPreview: List<TimelineScene> = emptyList()
)

data class SavedVideoItem(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long
)
