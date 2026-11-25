package com.example.data

import com.example.model.AnimationStyle
import com.example.model.PromptTemplate
import com.example.model.PronunciationEntry

object BuiltInPrompts {

    val VIRAL_PINOY_EXPLAINER = PromptTemplate(
        id = "builtin_viral_explainer",
        name = "Viral Pinoy Explainer",
        isBuiltIn = true,
        content = """
Ikaw ay isang viral Pinoy animated storyteller at TikTok/Reels script director.
Magsulat ng nakaka-hook, natural, at masiglang kuwentong Tagalog/Taglish na parang totoong Pinoy creator na kausap ang kaibigan—HINDI robotic na Google Translate.
Mga Panuntunan sa Kuwento:
- Unang 3 segundo (Scene 1): Gumamit ng matinding curiosity hook o nakakagulat na tanong (hal. "Alam mo ba kung bakit...?").
- Gumamit ng maiikling, mabilis bigkasin, at punchy na pangungusap (8–14 words per scene) para maganda ang pacing sa voiceover.
- Gumamit ng relatable na Pinoy examples (sweldo, kape, jeepney, tindahan, ipon, pangarap).
- Magbigay ng malinaw na twist o "aha!" moment sa gitna at isang memorable ending lesson.
        """.trimIndent()
    )

    val TAGALOG_HORROR_LEGEND = PromptTemplate(
        id = "builtin_tagalog_horror",
        name = "Tagalog Horror & Urban Legend",
        isBuiltIn = true,
        content = """
Ikaw ay isang master Pinoy horror narrator para sa mga nakakakilabot na urban legend at kuwentong kababalaghan tuwing hatinggabi.
Mga Panuntunan sa Kuwento:
- Unang 3 segundo (Scene 1): Magbukas gamit ang nakakakilabot na babala o tunay na tanong na nagpapataas ng balahibo.
- Mabagal ngunit tensyonadong pacing, gumamit ng vivid sensory words (malamig na hangin, lumang bahay, anino sa bintana, alas-tres ng madaling araw).
- Natural na Tagalog storytelling na may suspense pauses at nakakagulat na plot twist sa dulo.
- Huwag maglagay ng sobrang haba ng dialogue sa isang scene; panatilihing 8–13 words bawat scene.
        """.trimIndent()
    )

    val MONEY_LIFE_PARABLE = PromptTemplate(
        id = "builtin_money_parable",
        name = "Money & Life Parable",
        isBuiltIn = true,
        content = """
Ikaw ay isang modernong Pinoy parable director na gumagawa ng maiikling pabula tungkol sa pera, disiplina, at diskarte sa buhay.
Mga Panuntunan sa Kuwento:
- Gumawa ng malinaw na contrast sa pagitan ng dalawang tauhan (hal. ang taong mayabang vs. ang taong tahimik na nag-iipon, o ang matalinong manok na mentor).
- Unang 3 segundo (Scene 1): Ipakita agad ang sitwasyong nakaka-relate ang karamihan sa pera o trabaho.
- Bawat scene ay may malinaw na visual action at emosyon (pagkabigla, pagsisisi, tagumpay).
- Tapusin sa isang maikling Tagalog proverb o wisdom line na tatatak sa manonood.
        """.trimIndent()
    )

    val DEFAULT_TEMPLATES: List<PromptTemplate> = listOf(
        VIRAL_PINOY_EXPLAINER,
        TAGALOG_HORROR_LEGEND,
        MONEY_LIFE_PARABLE
    )

    val DEFAULT_PRONUNCIATION_DICTIONARY: List<PronunciationEntry> = listOf(
        PronunciationEntry("Nike", "Nayk"),
        PronunciationEntry("WiFi", "Way-Fay"),
        PronunciationEntry("₱", "piso"),
        PronunciationEntry("%", "porsyento"),
        PronunciationEntry("GCash", "ji-kash"),
        PronunciationEntry("OFW", "oh ef double-u"),
        PronunciationEntry("BPO", "bi pi oh"),
        PronunciationEntry("ATM", "ey ti em"),
        PronunciationEntry("ROI", "ar oh ay"),
        PronunciationEntry("YouTube", "yutyub"),
        PronunciationEntry("TikTok", "tik-tok")
    )

    fun getDefaultById(id: String): PromptTemplate? = DEFAULT_TEMPLATES.firstOrNull { it.id == id }

    fun lockedSchemaInstructions(durationSeconds: Int, style: AnimationStyle): String {
        val sceneCount = when {
            durationSeconds <= 35 -> 7
            durationSeconds <= 65 -> 13
            else -> 19
        }
        val hudGuidance = when (style) {
            AnimationStyle.TECH_EXPLAINER, AnimationStyle.CROSS_SECTION ->
                "IMPORTANT FOR ${style.displayName}: Populate status_pill, callout_banner, and hud_widget (with hud_label, hud_from_value, hud_to_value, and checklist_items when using ANIMATED_CHECKLIST) on EVERY scene."
            AnimationStyle.VIRAL_2D_EXPLAINER ->
                "IMPORTANT FOR ${style.displayName}: Populate expression, headgear (when fitting), metaphor_visual, and topic_pill (short 1-3 word uppercase badge like \"SCIENCE FACT\", \"BAKIT NANGYAYARI\", \"TIP #1\") on EVERY scene!"
            else ->
                "OPTIONAL MOTION-GRAPHICS OVERLAYS: You may also include the optional HUD fields below on key emphasis scenes."
        }
        return """
Return ONLY a valid JSON array containing exactly $sceneCount scene objects (each representing 3–5 seconds of narration for a ~${durationSeconds}s vertical 9:16 video in "${style.displayName}" style).
Do NOT output any prose, explanation, markdown fences, or <think> tags outside the JSON array.

STRICT NATURAL TAGALOG LANGUAGE RULES (MANDATORY):
- Write grammatically correct, everyday conversational Tagalog/Taglish that sounds like a real Filipino storyteller—never stiff or robotic Google Translate phrasing.
- FORBIDDEN: Do NOT translate English idioms word-for-word (e.g., NEVER translate "don't put all your eggs in one basket" literally as "lobo", "manok", or "itlog sa isang basket"; express the actual Filipino meaning naturally, like "huwag umasa sa iisang kita lang").
- Use proper Filipino grammar ("ng" vs "nang", "rito/dito", natural "po/naman/kasi/talaga/kaya" cadence) and keep every line clean, punchy, and 100% free of gibberish or stage directions.

Every object in the JSON array MUST have ALL 7 required keys with exact names:
- "scene_id": integer starting at 1
- "tagalog_narration": string (natural conversational Tagalog/Taglish, 8 to 15 words, no stage directions or speaker labels)
- "background_prompt": string (vivid English visual scene description matching "${style.displayName}", no text, no people)
- "camera_motion": strictly one of ["WHIP_PAN_LEFT", "WHIP_PAN_RIGHT", "ZOOM_IN", "ZOOM_OUT", "SHAKE", "STATIC"]
- "actor_role": strictly one of ["NARRATOR", "RICH_MAN", "POOR_MAN", "CHICKEN_MENTOR", "HERO"]
- "actor_action": strictly one of ["WALK_IN", "TALK_BOUNCE", "THUMBS_UP", "POINT_STICK", "SURPRISED_JUMP", "CRY_SHAKE"]
- "world_fx": strictly one of ["NONE", "COIN_RAIN", "FIRE_SPARKS", "GIANT_HAND_GRAB", "FLOATING_HOUSES", "CHALKBOARD_DRAW", "EGG_CRACK"]

$hudGuidance
Optional cinematic & motion-graphics keys per scene object:
- "shot_framing": one of ["WIDE_FULL_BODY", "MEDIUM_WAIST_UP", "CLOSE_UP_FACE", "RULE_OF_THIRDS_LEFT", "RULE_OF_THIRDS_RIGHT"] (vary shots across scenes!)
- "character_pose": one of ["WALK_ACROSS_LEFT_TO_RIGHT", "THREE_QUARTER_EXPLAIN", "SIT_SLUMPED", "SHOCK_AIRBORNE_RECOIL", "POINT_AND_GESTURE", "CELEBRATE_JUMP", "TWO_CHARACTER_CONVERSATION"]
- "hand_prop": one of ["NONE", "POINTER_STICK", "MONEY_BAG", "PHONE_SCREEN", "MAGNIFYING_GLASS", "LIGHTBULB"]
- "shot_type": one of ["WIDE_STAGE", "MEDIUM_WAIST_UP", "CLOSE_UP_REACTION", "TRACKING_FOLLOW", "WHIP_CUT"]
- "secondary_actor_role": optional partner role for TWO_CHARACTER_CONVERSATION (["HERO", "CHICKEN_MENTOR", "RICH_MAN", "POOR_MAN", "NARRATOR"])
- "expression": one of ["HAPPY_TALK", "ANGRY_EVIL", "PANIC_SWEAT_FIRE", "SHOCKED_WIDE_EYES", "TONGUE_STING", "SLEEPING_ZZZ"]
- "headgear": one of ["NONE", "CHEF_HAT", "HARD_HAT", "CROWN"]
- "metaphor_visual": one of ["NONE", "CHOMP_CHAIN", "TISSUE_CROSS_SECTION", "TIME_LAPSE_CLOCK", "BOILING_POT_PROCESS", "SUNBURST_SPOTLIGHT"]
- "topic_pill": short 1–3 word uppercase badge text (e.g. "PROTEIN CHAIN", "ALERT!", "PRO TIP")
- "status_pill": one of ["NONE", "GOAL", "PROBLEM", "FIX", "SECRET"]
- "callout_banner": short 1–3 word punchy Tagalog/Taglish emphasis text (e.g. "MALAKING MALI!", "90% LAKAS", "ITO ANG SIKRETO")
- "hud_widget": one of ["NONE", "PROGRESS_GAUGE", "WAVEFORM_GRAPH", "ANIMATED_CHECKLIST"]
- "hud_label": short uppercase metric title (e.g. "IPON RATE", "POWER OUTPUT", "RISK LEVEL")
- "hud_from_value": integer 0..100
- "hud_to_value": integer 0..100
- "checklist_items": array of 2–4 short strings (only when hud_widget is "ANIMATED_CHECKLIST")
        """.trimIndent()
    }

    fun composeSystemPrompt(
        activeCreativePrompt: String,
        durationSeconds: Int,
        style: AnimationStyle
    ): String {
        val safeCreative = activeCreativePrompt.trim().ifEmpty { VIRAL_PINOY_EXPLAINER.content }
        return buildString {
            append(safeCreative)
            append("\n\n--- MANDATORY TECHNICAL JSON FORMAT (DO NOT OVERRIDE) ---\n")
            append(lockedSchemaInstructions(durationSeconds, style))
        }
    }

    val VIRAL_TAGALOG_IDEAS: List<String> = listOf(
        "Bakit laging ubos ang sahod tatlong araw pa lang pagkatapos ng payday?",
        "Ang sikreto ng matandang tindera kung paano siya nakabili ng tatlong bahay",
        "Huwag na huwag kang lilingon kapag may tumawag sa pangalan mo sa jeep ng alas-tres",
        "Bakit mas mabilis yumaman ang tahimik na nag-iipon kaysa sa mahilig mag-flex?",
        "Ang kuwento ng manok na nagturo kung bakit delikado ang isang source of income lang",
        "Ano ang nangyayari sa utak mo kapag puro doomscrolling ka bago matulog?",
        "Tatlong ugali ng mga taong akala mo simple lang pero milyonaryo pala",
        "Ang misteryo ng lumang bahay sa kanto na laging bukas ang ilaw tuwing hatinggabi",
        "Paano naging ₱100,000 ang ₱500 dahil sa isang simpleng Rule of 72?",
        "Bakit hindi sapat ang sipag kung mali naman ang direksyon ng barko mo?",
        "Ang aral ng basag na itlog: Bakit minsan kailangan mong masaktan bago lumakas",
        "Bakit mas nakakatakot ang utang na maliit pero nakakalimutang bayaran?"
    )

    data class VoicePreset(val id: String, val name: String, val tag: String)

    val VOICE_PRESETS: List<VoicePreset> = listOf(
        VoicePreset("pNInz6obpgDQGcFmaJgB", "Adam (Deep Narrator)", "Storyteller"),
        VoicePreset("EXAVITQu4vr4xnSDxMaL", "Sarah (Warm & Clear)", "Explainer"),
        VoicePreset("TxGEqnHWrfWFTfGW9XjX", "Josh (Punchy Creator)", "Viral Reels"),
        VoicePreset("VR6AewLTigWG4xSOukaG", "Arnold (Dramatic)", "Horror / Suspense"),
        VoicePreset("21m00Tcm4TlvDq8ikWAM", "Rachel (Calm Host)", "Parable")
    )
}
