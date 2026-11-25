package com.example

import com.example.art.MagicByteValidator
import com.example.data.BuiltInPrompts
import com.example.llm.JsonSanitizer
import com.example.llm.MalformedSceneJsonException
import com.example.llm.TagalogNarrationValidator
import com.example.model.ActorAction
import com.example.model.ActorExpression
import com.example.model.ActorHeadgear
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import com.example.model.CameraMotion
import com.example.model.CharacterPose
import com.example.model.HandProp
import com.example.model.HudWidget
import com.example.model.MetaphorVisual
import com.example.model.ProductionMode
import com.example.model.PronunciationEntry
import com.example.model.SceneScript
import com.example.model.ShotFraming
import com.example.model.ShotType
import com.example.model.StatusPill
import com.example.model.WordTiming
import com.example.model.WorldFx
import com.example.render.CameraController
import com.example.render.CharacterSpriteProcessor
import com.example.render.MetaphorVisualRenderer
import com.example.render.ProceduralPuppetEngine
import com.example.render.PuppetSpringSolver
import com.example.tts.AudioPcmStitcher
import com.example.tts.TagalogPhoneticNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class ToonForgeCoreTest {

    @Test
    fun `jsonSanitizer strips think tags and markdown fences`() {
        val raw = """
            <think>
            Let me carefully plan the Tagalog scenes first...
            </think>
            ```json
            [
              {
                "scene_id": 1,
                "tagalog_narration": "Alam mo ba kung bakit laging ubos ang sahod mo?"
              }
            ]
            ```
        """.trimIndent()

        val extracted = JsonSanitizer.extractJsonArrayText(raw)
        assertTrue(extracted.startsWith("["))
        assertTrue(extracted.endsWith("]"))
        assertFalse(extracted.contains("<think>"))
        assertFalse(extracted.contains("```"))
    }

    @Test(expected = MalformedSceneJsonException::class)
    fun `jsonSanitizer rejects response without array brackets`() {
        JsonSanitizer.extractJsonArrayText("<think>only thoughts</think> no json here")
    }

    @Test
    fun `tagalogPhoneticNormalizer expands peso currency percentages and acronyms`() {
        val input = "Nakatipid ng ₱1,500 at 50% ang OFW gamit ang GCash budget."
        val out = TagalogPhoneticNormalizer.normalize(input)
        assertTrue(out.contains("isang libo limang daan piso"))
        assertTrue(out.contains("limampu porsyento"))
        assertTrue(out.contains("oh ef double-u"))
        assertTrue(out.contains("ji-kash"))
        assertTrue(out.contains("badyet"))
    }

    @Test
    fun `tagalogNarrationValidator accepts natural Tagalog and rejects gibberish or literal idioms`() {
        assertTrue(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Huwag umasa sa iisang kita lang dahil mabilis magbago ang panahon."
            )
        )
        assertTrue(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Nakakagulat pero ₱1,500 lang ang kailangan para magsimula ang OFW sa pag-iipon!"
            )
        )

        // Rejects literal English idiom slop
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Huwag mong ilagay ang lahat ng itlog sa isang basket ngayon."
            )
        )
        // Rejects gibberish consonant-smash words
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Alam mo ba kung bakit xzqprt ang sahod mo?"
            )
        )
        // Rejects non-Latin leakage
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Alam mo ba kung bakit привет ang sahod mo?"
            )
        )
        // Rejects pure English with zero Tagalog particles
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Never put every single coin inside one small pocket forever."
            )
        )
        // Cleans stray stage directions before TTS
        assertEquals(
            "Grabe talaga ang pagtitipid ng batang ito!",
            TagalogNarrationValidator.cleanForTts("Narrator: [excited] Grabe talaga ang pagtitipid ng batang ito!")
        )
    }

    @Test
    fun `magicByteValidator validates PNG JPEG WebP and rejects HTML`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        val webp = "RIFF1234WEBP".toByteArray(Charsets.US_ASCII)
        val html = "<html><body>401 Unauthorized</body></html>".toByteArray(Charsets.US_ASCII)

        assertEquals(MagicByteValidator.ImageFormat.PNG, MagicByteValidator.detectFormat(png))
        assertEquals(MagicByteValidator.ImageFormat.JPEG, MagicByteValidator.detectFormat(jpeg))
        assertEquals(MagicByteValidator.ImageFormat.WEBP, MagicByteValidator.detectFormat(webp))
        assertFalse(MagicByteValidator.isValidImageBytes(html))
    }

    @Test
    fun `twoLayerPromptComposition enforces natural Tagalog and locks json schema`() {
        val customPrompt = "Custom Pinoy comedy tone with punchy jokes."
        val composed = BuiltInPrompts.composeSystemPrompt(customPrompt, 30, AnimationStyle.PAPER_DIORAMA)
        assertTrue(composed.startsWith(customPrompt))
        assertTrue(composed.contains("MANDATORY TECHNICAL JSON FORMAT (DO NOT OVERRIDE)"))
        assertTrue(composed.contains("STRICT NATURAL TAGALOG LANGUAGE RULES"))
        assertTrue(composed.contains("\"shot_framing\""))
        assertTrue(composed.contains("\"tagalog_narration\""))
        assertTrue(composed.contains("\"world_fx\""))
        assertTrue(composed.contains("\"status_pill\""))
        assertTrue(composed.contains("\"hud_widget\""))
    }

    @Test
    fun `techExplainerAndStudioProModelsMaintainSafeDefaults`() {
        assertEquals(8, AnimationStyle.entries.size)
        assertTrue(AnimationStyle.TECH_EXPLAINER.usesNetworkArt)
        assertEquals("HUD EXPLAINER", AnimationStyle.TECH_EXPLAINER.badge)
        assertEquals("Viral HUD & Motion Explainer", AnimationStyle.TECH_EXPLAINER.displayName)
        assertFalse(AnimationStyle.CROSS_SECTION.usesNetworkArt)
        assertEquals("CUTAWAY 2D", AnimationStyle.CROSS_SECTION.badge)

        assertEquals(3, ProductionMode.entries.size)
        assertEquals(ProductionMode.CLIP_AUTO_EDITOR, ProductionMode.fromRaw("clip_auto_editor"))
        assertEquals(ProductionMode.CROSS_SECTION_EXPLAINER, ProductionMode.fromRaw("CROSS_SECTION_EXPLAINER"))
        assertEquals(ProductionMode.FULL_AI_STORY, ProductionMode.fromRaw("invalid"))

        assertEquals(ShotFraming.MEDIUM_WAIST_UP, ShotFraming.fromRaw("medium_waist_up"))
        assertEquals(ShotFraming.CLOSE_UP_FACE, ShotFraming.fromRaw("close_up_face"))
        assertEquals(ShotFraming.RULE_OF_THIRDS_LEFT, ShotFraming.fromRaw("RULE_OF_THIRDS_LEFT"))
        assertEquals(ShotFraming.AUTO, ShotFraming.fromRaw("unknown"))

        assertEquals(StatusPill.GOAL, StatusPill.fromRaw("goal"))
        assertEquals(StatusPill.PROBLEM, StatusPill.fromRaw("PROBLEM"))
        assertEquals(StatusPill.NONE, StatusPill.fromRaw("unknown_value"))
        assertEquals(StatusPill.NONE, StatusPill.fromRaw(null))

        assertEquals(HudWidget.PROGRESS_GAUGE, HudWidget.fromRaw("progress_gauge"))
        assertEquals(HudWidget.WAVEFORM_GRAPH, HudWidget.fromRaw("WAVEFORM_GRAPH"))
        assertEquals(HudWidget.ANIMATED_CHECKLIST, HudWidget.fromRaw("ANIMATED_CHECKLIST"))
        assertEquals(HudWidget.NONE, HudWidget.fromRaw("invalid"))

        val scene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Alam mo ba kung bakit mahalaga ang pag-iipon?",
            backgroundPrompt = "bg",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE
        )
        assertEquals(StatusPill.NONE, scene.statusPill)
        assertEquals(HudWidget.NONE, scene.hudWidget)
        assertEquals(ShotFraming.AUTO, scene.shotFraming)
        assertFalse(ProceduralPuppetEngine.hasActiveHudOverlay(scene, AnimationStyle.PAPER_DIORAMA))
        assertTrue(ProceduralPuppetEngine.hasActiveHudOverlay(scene, AnimationStyle.TECH_EXPLAINER))
        assertTrue(ProceduralPuppetEngine.hasActiveHudOverlay(scene, AnimationStyle.CROSS_SECTION))
        assertTrue(
            ProceduralPuppetEngine.hasActiveHudOverlay(
                scene,
                AnimationStyle.PAPER_DIORAMA,
                ProductionMode.CLIP_AUTO_EDITOR
            )
        )
        assertEquals(ShotFraming.MEDIUM_WAIST_UP, ProceduralPuppetEngine.resolveShotFraming(scene))
        assertEquals(ShotFraming.CLOSE_UP_FACE, ProceduralPuppetEngine.resolveShotFraming(scene.copy(sceneId = 4)))

        val techComposed = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.TECH_EXPLAINER)
        assertTrue(techComposed.contains("IMPORTANT FOR Viral HUD & Motion Explainer"))
        val cutawayComposed = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.CROSS_SECTION)
        assertTrue(cutawayComposed.contains("IMPORTANT FOR 2D Cutaway & Engineering Diagram"))
        assertTrue(cutawayComposed.contains("CLOSE_UP_FACE"))
    }

    @Test
    fun `audioPcmStitcher computes sample accurate offsets and mixes SFX plus auto-ducking BGM`() {
        // 44100 samples * 2 bytes = 88200 bytes = exactly 1000.0 ms
        val oneSecPcm = ByteArray(88200)
        val halfSecPcm = ByteArray(44100)
        val stitchedDry = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = listOf(oneSecPcm, halfSecPcm),
            localTimingsPerScene = listOf(
                listOf(WordTiming("Una", 100.0, 400.0)),
                listOf(WordTiming("Pangalawa", 50.0, 350.0))
            ),
            narrationPerScene = listOf("Una", "Pangalawa")
        )
        assertEquals(1500.0, stitchedDry.totalDurationMs, 0.01)
        assertEquals(0.0, stitchedDry.sceneStartMs[0], 0.01)
        assertEquals(1000.0, stitchedDry.sceneStartMs[1], 0.01)
        assertEquals(1050.0, stitchedDry.sceneWordTimings[1][0].startMs, 0.01)
        assertEquals(1350.0, stitchedDry.sceneWordTimings[1][0].endMs, 0.01)

        // Mix with scenes containing WHIP_PAN, COIN_RAIN, StatusPill, and ANIMATED_CHECKLIST
        val twoSecSilent = ByteArray(44100 * 2 * 2)
        val sampleScenes = listOf(
            SceneScript(
                sceneId = 1,
                tagalogNarration = "Alam mo ba ang sikreto sa mabilis na pag-iipon?",
                backgroundPrompt = "warm village street",
                cameraMotion = CameraMotion.WHIP_PAN_LEFT,
                actorRole = ActorRole.CHICKEN_MENTOR,
                actorAction = ActorAction.POINT_STICK,
                worldFx = WorldFx.COIN_RAIN,
                statusPill = StatusPill.SECRET,
                calloutBanner = "90% LAKAS",
                hudWidget = HudWidget.ANIMATED_CHECKLIST,
                checklistItems = listOf("Una", "Pangalawa", "Pangatlo")
            )
        )
        val stitchedMixed = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = listOf(twoSecSilent),
            localTimingsPerScene = listOf(emptyList()),
            narrationPerScene = listOf(sampleScenes[0].tagalogNarration),
            scenes = sampleScenes,
            style = AnimationStyle.TECH_EXPLAINER
        )
        assertEquals(twoSecSilent.size, stitchedMixed.unifiedPcm44100Mono.size)

        // Verify non-silent BGM + SFX energy present and safely within 16-bit PCM bounds
        val shorts = ShortArray(stitchedMixed.unifiedPcm44100Mono.size / 2)
        ByteBuffer.wrap(stitchedMixed.unifiedPcm44100Mono).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        var maxAmp = 0
        for (s in shorts) {
            val a = abs(s.toInt())
            if (a > maxAmp) maxAmp = a
        }
        assertTrue("Expected mixed BGM + SFX to produce audible non-zero PCM", maxAmp > 200)
        assertTrue("Expected mixed PCM not to overflow 16-bit range", maxAmp <= Short.MAX_VALUE.toInt())
    }

    @Test
    fun `editablePronunciationDictionaryAppliesWithoutMutatingCaptionDisplay`() {
        val dict = listOf(
            PronunciationEntry("Nike", "Nayk"),
            PronunciationEntry("WiFi", "Way-Fay"),
            PronunciationEntry("₱", "pesos"),
            PronunciationEntry("%", "percent")
        )
        val raw = "Bumili ng Nike at WiFi gamit ang ₱500 at 25% discount!"
        val spoken = TagalogPhoneticNormalizer.normalize(raw, dict)
        assertTrue(spoken.contains("Nayk"))
        assertTrue(spoken.contains("Way-Fay"))
        assertTrue(spoken.contains("limang daan pesos"))
        assertTrue(spoken.contains("dalawampu't lima percent"))
        // Original caption string remains untouched for display
        assertTrue(raw.contains("Nike") && raw.contains("WiFi") && raw.contains("₱500") && raw.contains("25%"))
    }

    @Test
    fun `characterSpriteAlphaErosionStripsEdgeHalosWhilePreservingCore`() {
        // 7x7 ARGB sprite with transparent outer border, 1px bright white semi-transparent halo ring, and 3x3 solid core
        val w = 7
        val h = 7
        val src = IntArray(w * h)
        for (y in 1..5) {
            for (x in 1..5) {
                val isCore = x in 2..4 && y in 2..4
                src[y * w + x] = if (isCore) {
                    0xFFFF4422.toInt()
                } else {
                    0x99FFFFFF.toInt() // 1px white halo edge
                }
            }
        }

        val eroded = CharacterSpriteProcessor.erodeAlphaBuffer(src, w, h, radiusPx = 1.5f)
        // Outer halo ring (touching alpha=0 neighbors) should be stripped cleanly
        assertEquals(0, (eroded[1 * w + 1] ushr 24) and 0xFF)
        assertEquals(0, (eroded[1 * w + 3] ushr 24) and 0xFF)
        // Center solid pixel remains 100% opaque
        assertEquals(255, (eroded[3 * w + 3] ushr 24) and 0xFF)
    }

    @Test
    fun `customBgmDucksTo10PercentDuringSpeechAnd28PercentInPausesAndProblemPlaysThud`() {
        assertEquals(0.10f, AudioPcmStitcher.BGM_DUCKED_LEVEL, 0.001f)
        assertEquals(0.28f, AudioPcmStitcher.BGM_PAUSE_LEVEL, 0.001f)

        // 1 second of silent dry voice + 100ms custom constant-amplitude BGM
        val oneSecDry = ByteArray(44100 * 2)
        val bgmShorts = ShortArray(4410) { 10_000 }
        val bgmBytes = ByteArray(bgmShorts.size * 2)
        ByteBuffer.wrap(bgmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(bgmShorts)

        val problemScene = listOf(
            SceneScript(
                sceneId = 2,
                tagalogNarration = "Malaking problema ito kung hindi ka mag-iipon ngayon.",
                backgroundPrompt = "city",
                cameraMotion = CameraMotion.STATIC,
                actorRole = ActorRole.NARRATOR,
                actorAction = ActorAction.TALK_BOUNCE,
                worldFx = WorldFx.NONE,
                statusPill = StatusPill.PROBLEM
            )
        )
        val mixed = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = listOf(oneSecDry),
            localTimingsPerScene = listOf(emptyList()),
            narrationPerScene = listOf(problemScene[0].tagalogNarration),
            scenes = problemScene,
            style = AnimationStyle.CROSS_SECTION,
            customBgmPcm44100Mono = bgmBytes
        )
        val outShorts = ShortArray(mixed.unifiedPcm44100Mono.size / 2)
        ByteBuffer.wrap(mixed.unifiedPcm44100Mono).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(outShorts)
        var peak = 0
        for (s in outShorts) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        // Custom BGM (~2800 in pauses) + PROBLEM sub-bass thud (~9800 peak) must produce clear non-zero audio
        assertTrue("Expected custom BGM + PROBLEM thud to exceed 3000 amplitude", peak > 3000)
    }

    @Test
    fun `characterPoseHandPropShotTypeAndCameraControllerWorkCohesively`() {
        assertEquals(8, CharacterPose.entries.size)
        assertEquals(6, HandProp.entries.size)
        assertEquals(6, ShotType.entries.size)
        assertEquals(-220f, ProceduralPuppetEngine.HUD_SAFE_LIFT_PX, 0.01f)

        assertEquals(CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT, CharacterPose.fromRaw("walk_across_left_to_right"))
        assertEquals(CharacterPose.TWO_CHARACTER_CONVERSATION, CharacterPose.fromRaw("TWO_CHARACTER_CONVERSATION"))
        assertEquals(CharacterPose.AUTO, CharacterPose.fromRaw("invalid"))
        assertEquals(HandProp.LIGHTBULB, HandProp.fromRaw("lightbulb"))
        assertEquals(HandProp.MONEY_BAG, HandProp.fromRaw("MONEY_BAG"))
        assertEquals(HandProp.NONE, HandProp.fromRaw(null))
        assertEquals(ShotType.TRACKING_FOLLOW, ShotType.fromRaw("tracking_follow"))
        assertEquals(ShotType.WHIP_CUT, ShotType.fromRaw("WHIP_CUT"))
        assertEquals(ShotType.AUTO, ShotType.fromRaw("invalid"))

        val schemaPrompt = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.PAPER_DIORAMA)
        assertTrue(schemaPrompt.contains("\"character_pose\""))
        assertTrue(schemaPrompt.contains("\"hand_prop\""))
        assertTrue(schemaPrompt.contains("\"shot_type\""))

        val scene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Lakad lang nang lakad habang may hawak na ilaw ng ideya!",
            backgroundPrompt = "warm street",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.WALK_IN,
            worldFx = WorldFx.NONE,
            characterPose = CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT,
            handProp = HandProp.LIGHTBULB,
            shotType = ShotType.TRACKING_FOLLOW,
            secondaryActorRole = ActorRole.CHICKEN_MENTOR
        )

        // CameraController zoom & tracking verification
        val mediumCam = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f,
            scene = scene.copy(shotType = ShotType.MEDIUM_WAIST_UP, characterPose = CharacterPose.THREE_QUARTER_EXPLAIN),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 1.0f, sceneDurationSec = 4f, globalTimeSec = 1.0f,
            hasActiveHud = true, isCutaway = false
        )
        assertEquals(1.45f, mediumCam.scale, 0.01f)

        val closeCam = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f,
            scene = scene.copy(shotType = ShotType.CLOSE_UP_REACTION, characterPose = CharacterPose.SHOCK_AIRBORNE_RECOIL),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 1, sceneElapsedSec = 1.0f, sceneDurationSec = 4f, globalTimeSec = 5.0f,
            hasActiveHud = false, isCutaway = false
        )
        assertEquals(1.90f, closeCam.scale, 0.01f)

        val trackStart = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = scene,
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 0.1f, sceneDurationSec = 4f, globalTimeSec = 0.1f,
            hasActiveHud = false, isCutaway = false
        )
        val trackLate = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = scene,
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 2.8f, sceneDurationSec = 4f, globalTimeSec = 2.8f,
            hasActiveHud = false, isCutaway = false
        )
        assertTrue(
            "TRACKING_FOLLOW should pan horizontally as walking character moves left-to-right",
            trackStart.translateX > trackLate.translateX + 100f
        )

        val whipStart = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f,
            scene = scene.copy(shotType = ShotType.WHIP_CUT),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 2, sceneElapsedSec = 0.05f, sceneDurationSec = 4f, globalTimeSec = 8.05f,
            hasActiveHud = false, isCutaway = false
        )
        assertTrue("WHIP_CUT should emit directional blur strength early in cut", whipStart.whipBlurStrength > 0.7f)

        // Spring solver follow-through lag verification during recoil launch
        val recoilSpring = PuppetSpringSolver.solve(
            pose = CharacterPose.SHOCK_AIRBORNE_RECOIL,
            sceneElapsedSec = 0.25f,
            sceneDurationSec = 4.0f
        )
        assertTrue(
            "Spring follow-through should produce non-zero head/hair lag during airborne recoil",
            abs(recoilSpring.hairHatTiltDeg) > 0.5f && abs(recoilSpring.trailingArmLagDeg) > 0.5f
        )

        // Two-character partner fallback verification
        assertEquals(
            ActorRole.CHICKEN_MENTOR,
            ProceduralPuppetEngine.resolvePartnerRole(ActorRole.HERO, null, sceneId = 1)
        )
        assertEquals(
            ActorRole.RICH_MAN,
            ProceduralPuppetEngine.resolvePartnerRole(ActorRole.HERO, null, sceneId = 2)
        )
    }

    @Test
    fun `viral2dExplainerEnumsMetaphorsAndSchemaStayBackwardCompatible`() {
        assertEquals(6, ActorExpression.entries.size)
        assertEquals(4, ActorHeadgear.entries.size)
        assertEquals(6, MetaphorVisual.entries.size)

        assertFalse(AnimationStyle.VIRAL_2D_EXPLAINER.usesNetworkArt)
        assertEquals("2D VIRAL", AnimationStyle.VIRAL_2D_EXPLAINER.badge)

        assertEquals(ActorExpression.PANIC_SWEAT_FIRE, ActorExpression.fromRaw("panic_sweat_fire"))
        assertEquals(ActorExpression.TONGUE_STING, ActorExpression.fromRaw("TONGUE_STING"))
        assertEquals(ActorExpression.HAPPY_TALK, ActorExpression.fromRaw("unknown"))

        assertEquals(ActorHeadgear.CHEF_HAT, ActorHeadgear.fromRaw("chef_hat"))
        assertEquals(ActorHeadgear.HARD_HAT, ActorHeadgear.fromRaw("HARD_HAT"))
        assertEquals(ActorHeadgear.CROWN, ActorHeadgear.fromRaw("crown"))
        assertEquals(ActorHeadgear.NONE, ActorHeadgear.fromRaw(null))

        assertEquals(MetaphorVisual.CHOMP_CHAIN, MetaphorVisual.fromRaw("chomp_chain"))
        assertEquals(MetaphorVisual.TISSUE_CROSS_SECTION, MetaphorVisual.fromRaw("TISSUE_CROSS_SECTION"))
        assertEquals(MetaphorVisual.BOILING_POT_PROCESS, MetaphorVisual.fromRaw("boiling_pot_process"))
        assertEquals(MetaphorVisual.SUNBURST_SPOTLIGHT, MetaphorVisual.fromRaw("sunburst_spotlight"))
        assertEquals(MetaphorVisual.NONE, MetaphorVisual.fromRaw("invalid"))

        val defaultScene = SceneScript(
            sceneId = 2,
            tagalogNarration = "Bakit nasisira ang tiyan kapag sobrang anghang?",
            backgroundPrompt = "kitchen",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE,
            statusPill = StatusPill.PROBLEM
        )
        assertEquals(ActorExpression.HAPPY_TALK, defaultScene.expression)
        assertEquals(ActorHeadgear.NONE, defaultScene.headgear)
        assertEquals(MetaphorVisual.NONE, defaultScene.metaphorVisual)

        assertEquals(
            MetaphorVisual.CHOMP_CHAIN,
            MetaphorVisualRenderer.resolveMetaphorVisual(defaultScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        assertEquals(
            MetaphorVisual.NONE,
            MetaphorVisualRenderer.resolveMetaphorVisual(defaultScene, AnimationStyle.PAPER_DIORAMA)
        )
        assertTrue(MetaphorVisualRenderer.isDiagramAlongsideCharacter(MetaphorVisual.BOILING_POT_PROCESS))
        assertFalse(MetaphorVisualRenderer.isDiagramAlongsideCharacter(MetaphorVisual.SUNBURST_SPOTLIGHT))

        assertEquals(
            ActorExpression.PANIC_SWEAT_FIRE,
            ProceduralPuppetEngine.resolveExpression(defaultScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        assertEquals(
            ActorExpression.HAPPY_TALK,
            ProceduralPuppetEngine.resolveExpression(defaultScene, AnimationStyle.PAPER_DIORAMA)
        )

        val viralPrompt = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.VIRAL_2D_EXPLAINER)
        assertTrue(viralPrompt.contains("\"expression\""))
        assertTrue(viralPrompt.contains("\"headgear\""))
        assertTrue(viralPrompt.contains("\"metaphor_visual\""))
        assertTrue(viralPrompt.contains("\"topic_pill\""))
        assertTrue(viralPrompt.contains("CHOMP_CHAIN"))
    }
}
