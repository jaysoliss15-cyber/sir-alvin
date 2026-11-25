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

enum class StatusPill {
    NONE,
    GOAL,
    PROBLEM,
    FIX,
    SECRET;

    companion object {
        fun fromRaw(raw: String?): StatusPill {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class HudWidget {
    NONE,
    PROGRESS_GAUGE,
    WAVEFORM_GRAPH,
    ANIMATED_CHECKLIST;

    companion object {
        fun fromRaw(raw: String?): HudWidget {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class ProductionMode(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    FULL_AI_STORY(
        title = "Full AI Story",
        subtitle = "Script, AI diorama, animated puppets & voiceover",
        badge = "AI STORY"
    ),
    CLIP_AUTO_EDITOR(
        title = "Clip + Auto-HUD",
        subtitle = "Import .mp4/.jpg/.png clips + auto voiceover, pills & HUD",
        badge = "AUTO-HUD"
    ),
    CROSS_SECTION_EXPLAINER(
        title = "2D Cutaway",
        subtitle = "Kurzgesagt cross-sections, pipes, fans & energy flow",
        badge = "CUTAWAY"
    );

    companion object {
        fun fromRaw(raw: String?): ProductionMode {
            val clean = raw?.trim()?.uppercase() ?: return FULL_AI_STORY
            return entries.firstOrNull { it.name == clean } ?: FULL_AI_STORY
        }
    }
}

enum class ShotFraming {
    AUTO,
    WIDE_FULL_BODY,
    MEDIUM_WAIST_UP,
    CLOSE_UP_FACE,
    RULE_OF_THIRDS_LEFT,
    RULE_OF_THIRDS_RIGHT;

    companion object {
        fun fromRaw(raw: String?): ShotFraming {
            val clean = raw?.trim()?.uppercase() ?: return AUTO
            return entries.firstOrNull { it.name == clean } ?: AUTO
        }
    }
}

enum class CharacterPose {
    AUTO,
    WALK_ACROSS_LEFT_TO_RIGHT,
    THREE_QUARTER_EXPLAIN,
    SIT_SLUMPED,
    SHOCK_AIRBORNE_RECOIL,
    POINT_AND_GESTURE,
    CELEBRATE_JUMP,
    TWO_CHARACTER_CONVERSATION;

    companion object {
        fun fromRaw(raw: String?): CharacterPose {
            val clean = raw?.trim()?.uppercase() ?: return AUTO
            return entries.firstOrNull { it.name == clean } ?: AUTO
        }
    }
}

enum class HandProp {
    NONE,
    POINTER_STICK,
    MONEY_BAG,
    PHONE_SCREEN,
    MAGNIFYING_GLASS,
    LIGHTBULB;

    companion object {
        fun fromRaw(raw: String?): HandProp {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class ShotType {
    AUTO,
    WIDE_STAGE,
    MEDIUM_WAIST_UP,
    CLOSE_UP_REACTION,
    TRACKING_FOLLOW,
    WHIP_CUT;

    companion object {
        fun fromRaw(raw: String?): ShotType {
            val clean = raw?.trim()?.uppercase() ?: return AUTO
            return entries.firstOrNull { it.name == clean } ?: AUTO
        }
    }
}

enum class ActorExpression {
    HAPPY_TALK,
    ANGRY_EVIL,
    PANIC_SWEAT_FIRE,
    SHOCKED_WIDE_EYES,
    TONGUE_STING,
    SLEEPING_ZZZ;

    companion object {
        fun fromRaw(raw: String?): ActorExpression {
            val clean = raw?.trim()?.uppercase() ?: return HAPPY_TALK
            return entries.firstOrNull { it.name == clean } ?: HAPPY_TALK
        }
    }
}

enum class ActorHeadgear {
    NONE,
    CHEF_HAT,
    HARD_HAT,
    CROWN;

    companion object {
        fun fromRaw(raw: String?): ActorHeadgear {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class MetaphorVisual {
    NONE,
    CHOMP_CHAIN,
    TISSUE_CROSS_SECTION,
    TIME_LAPSE_CLOCK,
    BOILING_POT_PROCESS,
    SUNBURST_SPOTLIGHT;

    companion object {
        fun fromRaw(raw: String?): MetaphorVisual {
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
    val worldFx: WorldFx,
    val statusPill: StatusPill = StatusPill.NONE,
    val calloutBanner: String = "",
    val hudWidget: HudWidget = HudWidget.NONE,
    val hudLabel: String = "",
    val hudFromValue: Int = 0,
    val hudToValue: Int = 100,
    val checklistItems: List<String> = emptyList(),
    val shotFraming: ShotFraming = ShotFraming.AUTO,
    val characterPose: CharacterPose = CharacterPose.AUTO,
    val handProp: HandProp = HandProp.NONE,
    val shotType: ShotType = ShotType.AUTO,
    val secondaryActorRole: ActorRole? = null,
    val expression: ActorExpression = ActorExpression.HAPPY_TALK,
    val headgear: ActorHeadgear = ActorHeadgear.NONE,
    val metaphorVisual: MetaphorVisual = MetaphorVisual.NONE,
    val topicPillLabel: String = ""
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

data class PronunciationEntry(
    val original: String,
    val spoken: String
)

data class ImportedClipItem(
    val uriString: String,
    val displayName: String,
    val isVideo: Boolean
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
    val allowOfflineTtsFallback: Boolean = true,
    val customSpritePaths: Map<String, String> = emptyMap(),
    val spriteStickerOutline: Boolean = true,
    val customBgmPath: String = "",
    val customBgmName: String = "",
    val pronunciationDictionary: List<PronunciationEntry> = emptyList()
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
